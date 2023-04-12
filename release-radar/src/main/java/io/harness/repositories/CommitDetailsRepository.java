package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.releaseradar.entities.CommitDetails;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface CommitDetailsRepository extends PagingAndSortingRepository<CommitDetails, String> {}
