package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.CIExecutionMetadata;

import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface CIExecutionRepository extends CrudRepository<CIExecutionMetadata, String> {
  long countByAccountId(String AccountID);
  long countByAccountIdAndBuildType(String AccountID, OSType BuildType);

  void deleteByRuntimeId(String runtimeId);
}
