package com.gitrepository.gitrepository.repository;

import com.gitrepository.gitrepository.entity.PullRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long> {

  @Query("SELECT COUNT(pr) FROM PullRequestEntity pr WHERE pr.status = 'Open'")
  long countOpenPullRequests();

  @Query("SELECT COUNT(pr) FROM PullRequestEntity pr WHERE pr.status = 'Closed'")
  long countClosedPullRequests();

   @Query("SELECT COUNT(pr) FROM PullRequestEntity pr WHERE pr.pullRequestLink IS NOT NULL")
   long countPullRequestLinks();

}