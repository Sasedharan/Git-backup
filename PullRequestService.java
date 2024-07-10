package com.gitrepository.gitrepository.service;

import com.gitrepository.gitrepository.dto.PullRequestDto;
import com.gitrepository.gitrepository.entity.PullRequestEntity;
import com.gitrepository.gitrepository.repository.PullRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PullRequestService {

  @Autowired
  PullRequestRepository pullRequestRepository;

  public List<PullRequestEntity> getAllPullRequests() {
    return pullRequestRepository.findAll();
  }

  public Optional<PullRequestEntity> getPullRequestById(Long id) {
    return pullRequestRepository.findById(id);
  }

  public List<PullRequestDto> getAllPullRequestUrls() {
    List<PullRequestEntity> pullRequestEntities = pullRequestRepository.findAll();
    List<PullRequestDto> pullRequestDtos = new ArrayList<>();
    for (PullRequestEntity pullRequestEntity : pullRequestEntities) {
      PullRequestDto pullRequestDto = new PullRequestDto();
      pullRequestDto.setPullRequestLink(pullRequestEntity.getPullRequestLink());
      pullRequestDtos.add(pullRequestDto);
    }
    return pullRequestDtos;
  }

  public PullRequestEntity updatePullRequest(Long id, PullRequestEntity pullRequestDetails) {
    PullRequestEntity pullRequest = pullRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("PullRequest not found for this id :: " + id));

    pullRequest.setRepoName(pullRequestDetails.getRepoName());
    pullRequest.setAuthorName(pullRequestDetails.getAuthorName());
    pullRequest.setTitle(pullRequestDetails.getTitle());
    pullRequest.setDescription(pullRequestDetails.getDescription());
    pullRequest.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
    pullRequest.setStatus(pullRequestDetails.getStatus());
    pullRequest.setSourceBranch(pullRequestDetails.getSourceBranch());
    pullRequest.setTargetBranch(pullRequestDetails.getTargetBranch());

    final PullRequestEntity updatedPullRequest = pullRequestRepository.save(pullRequest);
    return updatedPullRequest;
  }

  public void deletePullRequest(Long id) {
    PullRequestEntity pullRequest = pullRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("PullRequest not found for this id :: " + id));
    pullRequestRepository.delete(pullRequest);
  }
 }

