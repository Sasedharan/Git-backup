package com.gitrepository.gitrepository.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gitrepository.gitrepository.dto.CommitDto;
import com.gitrepository.gitrepository.dto.ConsolidatedStatusDto;
import com.gitrepository.gitrepository.dto.FileDto;
import com.gitrepository.gitrepository.dto.FileStructureResponse;
import com.gitrepository.gitrepository.dto.MergePullRequestDto;
import com.gitrepository.gitrepository.dto.PullRequestDto;
import com.gitrepository.gitrepository.dto.PullRequestUrlDto;
import com.gitrepository.gitrepository.entity.PullRequestEntity;
import com.gitrepository.gitrepository.service.GitService;
import com.gitrepository.gitrepository.service.PullRequestService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/git")
public class GitController {

    @Autowired
    private GitService gitService;

    @Value("${git.base.directory}")
    private String baseDirectory;

    @PostMapping("/create")
    public String createRepository(@RequestParam String repoName,
                                   @RequestParam(required = false, defaultValue = "")
                                   String description) {
        try {
            System.out.println("This request have reached to repository");
            gitService.createRepository(repoName, description);
            return "Repository created: " + repoName;
        } catch (Exception e) {
            return e.getMessage() == null ? String.valueOf(e) : e.getMessage();
        }
    }

    @PostMapping("/add")
    public ResponseEntity<ConsolidatedStatusDto> CreateAndAddFile(@RequestParam String repoName,
                                                                  @RequestParam String branchName,
                                                                  @RequestParam(required = false) String fileName,
                                                                  @RequestParam(required = false) String fileContent,
                                                                  @RequestParam(required = false) List<MultipartFile> files,
                                                                  @RequestParam String commitMessage) {
        try {
            ConsolidatedStatusDto statusDto = gitService.addFile(repoName, branchName, fileName, fileContent, files, commitMessage);
            return ResponseEntity.ok(statusDto);
        } catch (Exception e) {
            throw new RuntimeException("Error adding file: " + e.getMessage());
        }
    }

    @GetMapping("/commit/log")
    public List<CommitDto> commitLog(@RequestParam String repoName,
                                     @RequestParam String branchName) {
        try {
            return gitService.commitLog(repoName, branchName);
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Error Fetching Commit_ID : " + e.getMessage());
        }
    }

    @PostMapping("/pull")
    public String pullChanges(@RequestParam String repoName, @RequestParam String branch) {
        try {
            gitService.pullChanges(repoName, branch);
            return "Changes pulled from branch: " + branch;
        } catch (IOException | GitAPIException e) {
            return "Error pulling changes: " + e.getMessage();
        }
    }

    @PostMapping("/branch")
    public String createBranch(@RequestParam String repoName, @RequestParam String branchName) {
        try {
            gitService.createBranch(repoName, branchName);
            return "Branch created: " + branchName;
        } catch (Exception e) {
            return e.getMessage() == null ? String.valueOf(e) : e.getMessage();
        }
    }

    @GetMapping("/url")
    public String getRepositoryUrl(@RequestParam String repoName) {
        System.out.println("This request have reached to repository");

        return gitService.getRepositoryUrl(repoName);
    }

    @GetMapping("/files")
    public List<FileDto> getRepositoryFiles(@RequestParam String repoName, @RequestParam String branchName) {
        try {
            return gitService.getRepositoryFiles(repoName, branchName);
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Error reading repository: " + e.getMessage(), e);
        }
    }

    @GetMapping("/branches")
    public List<String> listBranches(@RequestParam String repoName) {
        try {
            return gitService.listBranches(repoName);
        } catch (Exception e) {
            return Collections.singletonList(e.getMessage() == null ? String.valueOf(e) : e.getMessage());
        }
    }

    @GetMapping("/allRepository")
    public List<String> getRepositoryName() {
        try {
            System.out.println("This request for List of Repository in Project");
            return gitService.getRepositoryNames();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", e);
        }
    }

    @GetMapping("/folder-structure")
    public List<FileStructureResponse> getFolderStructure(@RequestParam(value = "path",
            required = false, defaultValue = "") String path) {
        return gitService.getFolderStructure(path);
    }

    @GetMapping("/file-content")
    public String getFileContent(@RequestParam(value = "path") String path) {
        return gitService.getFileContent(path);
    }

    @PostMapping("/checkoutBranch")
    public String checkoutBranch(@RequestParam String repoName, @RequestParam String branchName) throws GitAPIException {
        gitService.checkoutBranch(repoName,branchName);
        return "Switched to " + branchName + "branch";
    }

    @PostMapping("/pullRequest")
    public ResponseEntity<String> createPullRequest(@RequestParam String repoName,
                                                    @RequestParam String title,
                                                    @RequestParam String description,
                                                    @RequestParam String sourceBranch,
                                                    @RequestParam String targetBranch) {
        try {
            String response = gitService.createPullRequest(repoName, title, description, sourceBranch, targetBranch);
            return ResponseEntity.ok(response);
        } catch (IOException | GitAPIException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating pull request: " + e.getMessage());
        }
    }

    @GetMapping("/pullRequest/urls")
    public List<PullRequestUrlDto> getAllPullRequestUrls() {
        try {
            return gitService.getAllPullRequestUrls();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve pull request URLs");
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<String> mergePullRequest(@RequestParam Long id) {
        try {
            String result = gitService.mergePullRequest(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (GitAPIException e) {
            return ResponseEntity.status(500).body("Error merging branches: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    @GetMapping("/conflictContent")
    public ResponseEntity<Map<String, Object>> getConflictContent(
            @RequestParam String repoName,
            @RequestParam String sourceBranch,
            @RequestParam String targetBranch
    ) {
        try {
            Map<String, Object> response = gitService.getConflictContent(repoName, sourceBranch, targetBranch);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "Failed to retrieve conflict content."));
        }
    }

    @PostMapping("/resolvedChanges/commits")
    public ResponseEntity<Map<String, Object>> commitResolvedChanges( @RequestParam String repoName,
                                                                      @RequestParam String sourceBranch,
                                                                      @RequestParam String targetBranch) {
        try {
            Map<String, Object> response = gitService.commitResolvedChanges(repoName, sourceBranch, targetBranch);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "Failed to commit changes."));
        }
    }

    @GetMapping("/fileChanges")
    public ResponseEntity<Map<String, String>> getFileChanged(@RequestParam("repoName") String repoName,
                                                              @RequestParam("sourceBranch") String sourceBranch,
                                                              @RequestParam("targetBranch") String targetBranch) {
        try {
            List<String> differences = gitService.compareBranches(repoName, sourceBranch, targetBranch);

            Map<String, String> response = new HashMap<>();
            for (String diff : differences) {
                response.put("file-difference-" + diff.hashCode(), diff);
            }

            return ResponseEntity.ok(response);
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}