package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.dto.CITaskDetails;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface CITaskDetailsRepository extends CrudRepository<CITaskDetails, String> {
  Optional<CITaskDetails> findFirstByStageExecutionId(String stageExecutionId);
  void deleteFirstByStageExecutionId(String stageExecutionId);
}
