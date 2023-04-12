package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.releaseradar.entities.CommitDetails;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

@HarnessRepo
public interface CommitDetailsRepository extends PagingAndSortingRepository<CommitDetails, String> {
    List<CommitDetails> findByJiraIdOrderByCreatedAt(String jiraId);
}
