package com.gitrepository.gitrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PullRequestDto {
  private String title;
  private String description;
  private String authorName;
  private String repoName;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private String sourceBranch;
  private String targetBranch;
  private String status;
  private String pullRequestLink;
  private List<ModifiedFileDto> modifiedFileDtos = new ArrayList<>();

  public PullRequestDto(String title, String description, String repoName, String sourceBranch, String targetBranch, List<ModifiedFileDto> modifiedFileDtos) {
    this.title = title;
    this.description = description;
    this.repoName = repoName;
    this.sourceBranch = sourceBranch;
    this.targetBranch = targetBranch;
    this.modifiedFileDtos = modifiedFileDtos;
  }
}
