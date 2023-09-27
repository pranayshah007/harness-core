/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.execution.CIExecutionMetadata;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface CIExecutionRepository
    extends CrudRepository<CIExecutionMetadata, String>, CIExecutionRepositoryCustom {
  long countByAccountId(String accountID);
  int countByAccountIdAndStatusIn(String accountId, List<String> status);
  long countByAccountIdAndStatus(String accountID, String status);
  long countByAccountIdAndBuildTypeAndStatusIn(String accountID, OSType buildType, List<String> status);
  CIExecutionMetadata findByStageExecutionId(String stageExecutionId);
  void deleteByStageExecutionId(String stageExecutionId);
  void deleteAllByAccountId(String accountId);
}
