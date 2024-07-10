package com.gitrepository.gitrepository.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "modified_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifiedFileEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String fileName;

  @Lob
  private String changes;
  private String fileUrl;

  @ManyToOne
  @JoinColumn(name = "pull_request_id", nullable = false)
  private PullRequestEntity pullRequest;

}
