package com.gitrepository.gitrepository.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pull_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String title;
  private String description;
  private String repoName;
  private String authorName;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private String sourceBranch;
  private String targetBranch;
  private String status;
  private String pullRequestLink;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "pullRequest")
  private List<ModifiedFileEntity> modifiedFiles = new ArrayList<>();

}

