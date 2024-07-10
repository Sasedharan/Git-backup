package com.gitrepository.gitrepository.service;

import com.gitrepository.gitrepository.dto.*;
import com.gitrepository.gitrepository.entity.ModifiedFileEntity;
import com.gitrepository.gitrepository.entity.PullRequestEntity;
import com.gitrepository.gitrepository.repository.PullRequestRepository;
import com.gitrepository.gitrepository.validation.ApiValidation;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.json.JSONArray;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class GitService {

    private static final Logger LOGGER = Logger.getLogger(GitService.class.getName());

    @Value("${git.base.directory}")
    private String baseDirectory;

    private final RedissonClient redissonClient;

    @Autowired
    public ApiValidation apiValidation;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    private final ModelMapper mapper = new ModelMapper();

    public GitService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void createRepository(String repoName, String description) throws Exception {
        String lockName = "lock:repo:" + repoName;
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(30, TimeUnit.SECONDS);
        try {
            File baseDir = new File(baseDirectory + "/" + repoName + ".git");
            apiValidation.CreateRepoValid(repoName, baseDir);
            String bareRepoPath = baseDirectory + "/" + repoName + ".git";
            Repository bareRepo = FileRepositoryBuilder.create(new File(bareRepoPath));
            bareRepo.create(true);
            Path tempRepoPath = Files.createTempDirectory(new File(baseDirectory).toPath(), repoName + "-temp-");
            Path readmeFilePath = tempRepoPath.resolve("README.md");
            Files.writeString(readmeFilePath, "# " + repoName + "\n\n" + description + "\n\n");
            try (Git git = Git.init().setDirectory(tempRepoPath.toFile()).call()) {
                git.add().addFilepattern("README.md").call();
                git.commit().setMessage("Initial commit").call();
                git.push().setRemote(bareRepoPath).call();
            }
            deleteDirectory(tempRepoPath.toFile());
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            lock.unlock();
        }
    }

    public ConsolidatedStatusDto addFile(String repoName, String branchName, String fileName,
                                         String fileContent, List<MultipartFile> files,
                                         String commitMessage) throws Exception {
        String lockName = "lock:files:" + repoName;
        RLock lock = redissonClient.getLock(lockName);

        boolean lockAcquired = false;
        File tempRepoDir = null;
        ConsolidatedStatus consolidatedStatus = new ConsolidatedStatus();
        try {
            lockAcquired = lock.tryLock(30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                throw new IllegalStateException("Could not acquire the lock");
            }

            File bareRepoDir = new File(baseDirectory, repoName.endsWith(".git") ? repoName : repoName + ".git");
            if (!bareRepoDir.exists() || !bareRepoDir.isDirectory()) {
                throw new IllegalStateException("Repository directory does not exist: " + bareRepoDir.getAbsolutePath());
            }

            tempRepoDir = Files.createTempDirectory("gitrepo_temp").toFile();
            LOGGER.info("Temporary directory created: " + tempRepoDir.getAbsolutePath());

            try (Git git = Git.cloneRepository()
                    .setURI(bareRepoDir.toURI().toString())
                    .setDirectory(tempRepoDir)
                    .call()) {

                LOGGER.info("Repository cloned to temporary directory");

                boolean stashNeeded = git.status().call().hasUncommittedChanges();

                RevCommit stashCommit = null;
                if (stashNeeded) {
                    stashCommit = git.stashCreate().call();
                    LOGGER.info("Created stash with ID: " + stashCommit.getName());
                }

                git.fetch().call();

                List<Ref> branchList = git.branchList()
                        .setListMode(ListBranchCommand.ListMode.REMOTE)
                        .call();
                boolean branchExists = branchList.stream()
                        .anyMatch(ref -> ref.getName().equals("refs/remotes/origin/" + branchName));

//                if (branchExists) {
//                    git.checkout()
//                            .setCreateBranch(true)
//                            .setName(branchName)
//                            .setStartPoint("origin/" + branchName)
//                            .call();
//                    LOGGER.info("Checked out branch: " + branchName);
//                } else {
//                    git.checkout()
//                            .setName(branchName)
//                            .call();
//                    LOGGER.info("Checked out existing master branch");
//                }

                if (branchExists) {
                    if ("master".equals(branchName)) {
                        git.checkout()
                                .setName(branchName)
                                .call();
                        LOGGER.info("Checked out existing master branch");
                    } else {
                        git.checkout()
                                .setCreateBranch(true)
                                .setName(branchName)
                                .setStartPoint("origin/" + branchName)
                                .call();
                        LOGGER.info("Checked out branch: " + branchName);
                    }
                } else {
                    LOGGER.info("The BranchName " + branchName + " doesn't exist");
                    throw new IllegalArgumentException("Branch does not exist: " + branchName);
                }

                git.pull().call();
                LOGGER.info("Pulled changes from remote");

                if (stashCommit != null) {
                    try {
                        git.stashApply().call();
                        LOGGER.info("Applied stash: " + stashCommit.getName());
                    } catch (GitAPIException e) {
                        LOGGER.warning("No stashes to apply or failed to apply stash");
                    }
                }

                if (fileName != null) {
                    File targetFile = new File(tempRepoDir, fileName);
                    try (FileWriter writer = new FileWriter(targetFile)) {
                        writer.write(fileContent);
                    }
                }
                if (files != null) {
                    for (MultipartFile file : files) {
                        File targetFile = new File(tempRepoDir, file.getOriginalFilename());
                        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                            targetFile.getParentFile().mkdirs();
                            LOGGER.info("Created parent directories for: " + targetFile.getAbsolutePath());
                        }
                        Files.copy(file.getInputStream(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                git.add().addFilepattern(".").call();
                LOGGER.info("Files are added");

                Status status = git.status().call();
                consolidatedStatus.addStatus(status);

                git.commit().setMessage(commitMessage).call();
                LOGGER.info("Committed changes with message: " + commitMessage);

                git.push().call();
                LOGGER.info("Pushed changes to remote repository");

                LOGGER.info("Git operations completed and pushed to remote");

                return convertToDto(consolidatedStatus);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error copying file to repository: " + e.getMessage(), e);
            throw new IOException("Error copying file to repository: " + e.getMessage(), e);
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Error performing Git operations: " + e.getMessage(), e);
            throw new GitAPIException("Error performing Git operations: " + e.getMessage(), e) {
            };
        } finally {
            if (tempRepoDir != null) {
                deleteDirectory(tempRepoDir);
                LOGGER.info("Temporary directory deleted: " + tempRepoDir.getAbsolutePath());
            }
            if (lockAcquired) {
                lock.unlock();
            }
        }
    }

    private ConsolidatedStatusDto convertToDto(ConsolidatedStatus status) {
        return mapper.map(status, ConsolidatedStatusDto.class);
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    public List<CommitDto> commitLog(String repoName, String branchName) throws IOException, GitAPIException {
        String lockName = "lock:repo:" + repoName;
        List<CommitDto> commitDetails = new ArrayList<>();
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(30, TimeUnit.SECONDS);

        boolean lockAcquired = false;
        try {
            lockAcquired = lock.tryLock(30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                throw new IllegalStateException("Could not acquire the lock");
            }
            try {
                File repoDir = new File(baseDirectory, repoName.endsWith(".git") ? repoName : repoName + ".git");

                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                Repository repository = builder.setGitDir(repoDir)
                        .readEnvironment()
                        .findGitDir()
                        .build();

                try (Git git = new Git(repository)) {
                    if (!repository.isBare()) {
                        git.checkout().setName(branchName).call();
                    } else {
                        branchName = "refs/heads/" + branchName;
                    }

                    Iterable<RevCommit> commits = git.log().add(repository.resolve(branchName)).call();

                    for (RevCommit commit : commits) {
                        CommitDto commitDto = new CommitDto();
                        commitDto.setCommitId(commit.getId().getName());
                        commitDto.setAuthor(commit.getAuthorIdent().getName());
                        commitDto.setDate(String.valueOf(commit.getAuthorIdent().getWhen()));
                        commitDto.setMessage(commit.getFullMessage());
                        commitDetails.add(commitDto);
                    }
                }
                return commitDetails;
            } catch (IOException e) {
                throw new IllegalStateException("Error to found commit Details: ", e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (lockAcquired) {
                lock.unlock();
            }
        }
    }

    public void pushChanges(String repoName, String remoteRepoName) throws IOException, GitAPIException, URISyntaxException {
        String lockName = "lock:repo:" + repoName;
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(30, TimeUnit.SECONDS);
        try {
            File repoDir = new File(baseDirectory, repoName);
            String remoteUrl = "file://" + new File(baseDirectory, remoteRepoName + ".git").getAbsolutePath();

            try (Git git = Git.open(repoDir)) {
                git.remoteAdd()
                        .setName("origin")
                        .setUri(new URIish(remoteUrl))
                        .call();
                git.push()
                        .setRemote("origin")
                        .call();
            }
        } finally {
            lock.unlock();
        }
    }

    public void pullChanges(String repoName, String branch) throws IOException, GitAPIException {
        String lockName = "lock:repo:" + repoName;
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(30, TimeUnit.SECONDS);
        try {
            File repoDir = new File(baseDirectory, repoName);

            try (Git git = Git.open(repoDir)) {
                git.pull().setRemoteBranchName(branch).call();
            }
        } finally {
            lock.unlock();
        }
    }

    public void createBranch(String repoName, String branchName) throws Exception {
        String lockName = "lock:repo:" + repoName;
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(30, TimeUnit.SECONDS);
        apiValidation.checkRepoNotNull(repoName);
        apiValidation.checkBranchNotNull(branchName);
        File repoDir = new File(baseDirectory, repoName);
        try (Git git = Git.open(repoDir)) {
            if (apiValidation.branchExists(git, branchName)) {
                throw new Exception("Branch " + branchName + " already exists.");
            }
            git.branchCreate()
                    .setName(branchName)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .call();
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            lock.unlock();
        }
    }

    public String getRepositoryUrl(String repoName) {
        return "file://" + new File(baseDirectory, repoName + ".git").getAbsolutePath();
    }

//    public List<FileDto> getRepositoryFiles(String repoName, String branchName) throws IOException, GitAPIException {
//        List<FileDto> files = new ArrayList<>();
//        File repoDir = new File(baseDirectory, repoName);
//
//        try (Git git = Git.open(repoDir)) {
//            Repository repository = git.getRepository();
//            ObjectId branchId = repository.resolve(branchName);
//
//            try (RevWalk revWalk = new RevWalk(repository)) {
//                RevCommit commit = revWalk.parseCommit(branchId);
//                revWalk.dispose();
//
//                try (TreeWalk treeWalk = new TreeWalk(repository)) {
//                    treeWalk.addTree(commit.getTree());
//                    treeWalk.setRecursive(true);
//                    while (treeWalk.next()) {
//                        String filePath = treeWalk.getPathString();
//                        ObjectId objectId = treeWalk.getObjectId(0);
//                        ObjectLoader loader = repository.open(objectId);
//
//                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                        loader.copyTo(outputStream);
//                        files.add(new FileDto(filePath));
//                    }
//                }
//            }
//        }
//        return files;
//    }

    public List<FileDto> getRepositoryFiles(String repoName, String branchName) throws IOException, GitAPIException {
        List<FileDto> files = new ArrayList<>();
        File repoDir = new File(baseDirectory, repoName);

        try (Git git = Git.open(repoDir)) {
            Repository repository = git.getRepository();
            ObjectId branchId = repository.resolve(branchName);

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(branchId);
                revWalk.dispose();

                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        String filePath = treeWalk.getPathString();
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        loader.copyTo(outputStream);
                        String content = outputStream.toString();

                        files.add(new FileDto(filePath, content));
                    }
                }
            }
        }
        return files;
    }

    public List<String> listBranches(String repoName) throws Exception {
        apiValidation.checkRepoNotNull(repoName);
        File repoDir = new File(baseDirectory, repoName);
        List<String> branches = new ArrayList<>();
        try (Git git = Git.open(repoDir)) {
            List<Ref> branchRefs = git.branchList().call();
            for (Ref ref : branchRefs) {
                branches.add(ref.getName());
            }
        }
        return branches;
    }

    public List<String> getRepositoryNames() {
        List<String> repositoryNames = new ArrayList<>();
        File directory = new File(baseDirectory);
        try {
            for (File file : directory.listFiles()) {
                repositoryNames.add(file.getName());
            }
        } catch (RuntimeException ex) {
            throw new RuntimeException("Repository not found.");
        }
        return repositoryNames;
    }

    public List<FileStructureResponse> getFolderStructure(String path) {
        List<FileStructureResponse> nodes = new ArrayList<>();
        File dir = new File(baseDirectory, path);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    nodes.add(new FileStructureResponse(file.getName(), true));
                } else {
                    nodes.add(new FileStructureResponse(file.getName(), false));
                }
            }
        }
        return nodes;
    }

    public String getFileContent(String path) {
        File file = new File(baseDirectory, path);
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader
                (new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to read file content.";
        }
        return contentBuilder.toString();
    }

    public void checkoutBranch(String repoName, String branchName) {
        File repoDir = new File(baseDirectory, repoName);
        List<String> branches = new ArrayList<>();
        try (Git git = Git.open(repoDir)) {
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("core", null, "bare", false);
            config.save();
            List<Ref> branchRefs = git.branchList().call();
            for (Ref ref : branchRefs) {
                branches.add(ref.getName().substring(ref.getName().lastIndexOf('/') + 1));
            }
            if (!branches.isEmpty()) {
                git.checkout().setName(branchName).call();
                System.out.println("Checked out branch: " + branchName);
            } else {
                System.out.println("No branches found in the repository.");
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }

    public String createPullRequest(String repoName, String title, String description, String sourceBranch, String targetBranch) throws IOException, GitAPIException {
        if (sourceBranch.equals(targetBranch)) {
            throw new IllegalArgumentException("Source branch and target branch must be different.");
        }

        File repoDir = new File(baseDirectory, repoName.endsWith(".git") ? repoName : repoName + ".git");

        try (Git git = Git.open(repoDir)) {
            Repository repository = git.getRepository();
            ObjectId sourceCommit = repository.resolve(sourceBranch);
            ObjectId targetCommit = repository.resolve(targetBranch);

            if (sourceCommit == null || targetCommit == null) {
                throw new IllegalArgumentException("Branch name(s) with zero commits.");
            }
            String projectName = "project-GIT";
            List<DiffEntry> diffs = getDiffs(git, sourceBranch, targetBranch);
            List<ModifiedFileDto> modifiedFileDtos = getModifiedFiles(diffs, repository, projectName, repoName);
            PullRequestDto pullRequestdto = new PullRequestDto(title, description, repoName, sourceBranch, targetBranch, modifiedFileDtos);
            savePullRequest(pullRequestdto);

            // ***************************
            List<String> conflictFiles = getConflictingFiles(diffs);
            boolean isUpToDate = isUpToDate(sourceCommit, targetCommit, repository);
            int commitCount = getCommitCount(sourceBranch, repository);
            int overallChangesCount = getOverallFileChangesCount(diffs);
            // ***************************

            return generateResponse(pullRequestdto, modifiedFileDtos, conflictFiles, isUpToDate, commitCount, overallChangesCount);
        }
    }

    private List<DiffEntry> getDiffs(Git git, String sourceBranch, String targetBranch) throws IOException, GitAPIException {
        CanonicalTreeParser sourceTreeParser = new CanonicalTreeParser();
        CanonicalTreeParser targetTreeParser = new CanonicalTreeParser();
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            sourceTreeParser.reset(reader, git.getRepository().resolve(sourceBranch + "^{tree}"));
            targetTreeParser.reset(reader, git.getRepository().resolve(targetBranch + "^{tree}"));
        }

        return git.diff()
                .setNewTree(sourceTreeParser)
                .setOldTree(targetTreeParser)
                .call();
    }

    private List<ModifiedFileDto> getModifiedFiles(List<DiffEntry> diffs, Repository repository, String projectName, String repoName) throws IOException {
        List<ModifiedFileDto> modifiedFileDtos = new ArrayList<>();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DiffFormatter diffFormatter = new DiffFormatter(outputStream)) {
            diffFormatter.setRepository(repository);
            for (DiffEntry diff : diffs) {
                diffFormatter.format(diff);
                String diffOutput = outputStream.toString();
                outputStream.reset();

                String fileName = diff.getNewPath();
                String fileUrl = String.format("C:/Users/com.codeshelf/%s/%s/src/main/java/%s", projectName, repoName, fileName);

                ModifiedFileDto modifiedFileDto = new ModifiedFileDto(fileName, diffOutput, fileUrl);
                modifiedFileDtos.add(modifiedFileDto);
            }
        }
        return modifiedFileDtos;
    }

    private void savePullRequest(PullRequestDto pullRequest) {
        PullRequestEntity pullRequestEntity = new PullRequestEntity();
        pullRequestEntity.setTitle(pullRequest.getTitle());
        pullRequestEntity.setDescription(pullRequest.getDescription());
        pullRequestEntity.setAuthorName("Anonymous");
        pullRequestEntity.setSourceBranch(pullRequest.getSourceBranch());
        pullRequestEntity.setTargetBranch(pullRequest.getTargetBranch());
        pullRequestEntity.setRepoName(pullRequest.getRepoName());
        pullRequestEntity.setCreatedAt(Timestamp.from(Instant.now()));
        pullRequestEntity.setUpdatedAt(Timestamp.from(Instant.now()));
        pullRequestEntity.setStatus("Open");

        List<ModifiedFileEntity> modifiedFileEntities = pullRequest.getModifiedFileDtos().stream()
                .map(modifiedFile -> {
                    ModifiedFileEntity entity = new ModifiedFileEntity();
                    entity.setFileName(modifiedFile.getFileName());
                    entity.setChanges(modifiedFile.getChanges());
                    entity.setFileUrl(modifiedFile.getFileUrl());

                    entity.setPullRequest(pullRequestEntity);
                    return entity;
                })
                .collect(Collectors.toList());

        pullRequestEntity.setModifiedFiles(modifiedFileEntities);

        PullRequestEntity savedPullRequest = pullRequestRepository.save(pullRequestEntity);

        String projectName = "project-GIT";
        String repoName = pullRequestEntity.getRepoName();
        long id = pullRequestEntity.getId();
        String pullRequestLink = String.format("https://codeshelf.com/%s/%s/pull_request/%d", projectName, repoName, id);

        savedPullRequest.setPullRequestLink(pullRequestLink);
        pullRequestRepository.save(savedPullRequest);
    }

    private String generateResponse(PullRequestDto pullRequestDto, List<ModifiedFileDto> modifiedFiles,
                                    List<String> conflictFiles, boolean isUpToDate, int commitCount,
                                    int overallChangesCount) {

        StringBuilder response = new StringBuilder();

        response.append("Title: ").append(pullRequestDto.getTitle()).append("\n");
        response.append("Description: ").append(pullRequestDto.getDescription()).append("\n");
        response.append("Source Branch: ").append(pullRequestDto.getSourceBranch()).append("\n");
        response.append("Target Branch: ").append(pullRequestDto.getTargetBranch()).append("\n\n");

        if (!conflictFiles.isEmpty()) {
            response.append("Conflicting files:").append("\n");
            for (String file : conflictFiles) {
                response.append("--").append(file).append("\n\n");
            }
        } else {
            response.append("No conflict in files - Ready to merge").append("\n");
        }
        if (isUpToDate) {
            response.append("Source branch is already up-to-date").append("\n\n");
        }

        response.append("Modified file links:").append("\n");
        for (ModifiedFileDto file : modifiedFiles) {
            response.append(file.getFileUrl()).append("\n");
        }
        response.append("\n--------------- ").append("\n");
        response.append("Number of commits in source branch: ").append(commitCount).append("\n");
        response.append("Overall file changes count: ").append(overallChangesCount).append("\n");

        return response.toString();
    }

    private int getOverallFileChangesCount(List<DiffEntry> diffs) {
        return diffs.size();
    }

    private int getCommitCount(String sourceBranch, Repository repository) throws GitAPIException, IOException {
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> commits = git.log().add(repository.resolve(sourceBranch)).call();
            int count = 0;
            for (RevCommit commit : commits) {
                count++;
            }
            return count;
        }
    }

    private boolean isUpToDate(ObjectId sourceCommit, ObjectId targetCommit, Repository repository) throws GitAPIException,
            IncorrectObjectTypeException, MissingObjectException {
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> commits = git.log().addRange(targetCommit, sourceCommit).call();
            return !commits.iterator().hasNext();
        }
    }

    private List<String> getConflictingFiles(List<DiffEntry> diffs) {
        List<String> conflictFiles = new ArrayList<>();
        for (DiffEntry diff : diffs) {
            if (diff.getChangeType() == DiffEntry.ChangeType.MODIFY ||
                    diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
                conflictFiles.add(diff.getNewPath());
            }
        }
        return conflictFiles;
    }

    public List<PullRequestUrlDto> getAllPullRequestUrls() {
        List<PullRequestEntity> pullRequestEntities = pullRequestRepository.findAll();
        List<PullRequestUrlDto> pullRequestUrlDtos = new ArrayList<>();

/*        int overallPRCount = pullRequestEntities.size();
        int overallOpenCount = 0;
        int overallClosedCount = 0;*/

        int overallPRCount = (int) pullRequestRepository.countPullRequestLinks();
        int overallOpenCount = (int) pullRequestRepository.countOpenPullRequests();
        int overallClosedCount = (int) pullRequestRepository.countClosedPullRequests();

        for (PullRequestEntity pullRequestEntity : pullRequestEntities) {
            PullRequestUrlDto pullRequestUrlDto = new PullRequestUrlDto();
            pullRequestUrlDto.setPullRequestLink(pullRequestEntity.getPullRequestLink());
            pullRequestUrlDto.setStatus(pullRequestEntity.getStatus());
            pullRequestUrlDto.setAuthorName(pullRequestEntity.getAuthorName());

            long hoursSinceCreation = Duration.between(pullRequestEntity.getCreatedAt().toInstant(), Instant.now()).toHours();
            pullRequestUrlDto.setCreatedHours(hoursSinceCreation);

/*            if ("Open".equalsIgnoreCase(pullRequestEntity.getStatus())) {
                overallOpenCount++;
            } else if ("Closed".equalsIgnoreCase(pullRequestEntity.getStatus())) {
                overallClosedCount++;
            }*/

            pullRequestUrlDto.setOverallOpenCount(overallOpenCount);
            pullRequestUrlDto.setOverallClosedCount(overallClosedCount);
            pullRequestUrlDto.setOverallPRCount(overallPRCount);

            pullRequestUrlDtos.add(pullRequestUrlDto);
        }
        return pullRequestUrlDtos;
    }
}

