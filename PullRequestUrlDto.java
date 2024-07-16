package com.gitrepository.gitrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PullRequestUrlDto {
  private String pullRequestLink;
  private String authorName;
  private String status;
  private String createdHours;
}
