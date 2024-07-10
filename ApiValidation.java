package com.gitrepository.gitrepository.validation;

import io.netty.util.internal.StringUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ApiValidation {

  public void CreateRepoValid(String repoName, File repoDir) throws Exception {
    if (StringUtil.isNullOrEmpty(repoName)){
        throw new Exception("Repository is Empty .");
    } else if (repoDir.exists()) {
        throw new Exception("Repository " + repoName + " already exists.");
    }
  }

  public boolean branchExists(Git git, String branchName) throws GitAPIException {
    return git.branchList()
        .call()
        .stream()
        .anyMatch(ref -> ref.getName().endsWith("/" + branchName));
  }

  public void checkRepoNotNull(String repoName) throws Exception {
    if (StringUtil.isNullOrEmpty(repoName)) {
    throw new Exception("Repository is Empty .");
    }
  }
  public void checkBranchNotNull(String branch) throws Exception {
    if (StringUtil.isNullOrEmpty(branch)) {
      throw new Exception("Branch is Empty .");
    }
  }
}
