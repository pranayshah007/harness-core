package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.concurrency.ConcurrentChildInstance;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
public interface ConcurrentChildrenInstanceRepository extends CrudRepository<ConcurrentChildInstance, String> {
  Optional<ConcurrentChildInstance> findByParentNodeExecutionId(String nodeExecutionId);
}
