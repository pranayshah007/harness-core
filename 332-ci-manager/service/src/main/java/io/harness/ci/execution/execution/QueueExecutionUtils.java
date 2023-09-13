/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.CIExecutionRepository;

import com.google.inject.Inject;
import java.util.List;

public class QueueExecutionUtils {
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  public void addActiveExecutionBuild(InitializeStepInfo initializeStepInfo, String accountID, String stagExecutionID) {
    if (ciExecutionRepository.findByStageExecutionId(stagExecutionID) == null) {
      Infrastructure.Type infraType = initializeStepInfo.getInfrastructure().getType();
      OSType buildType = IntegrationStageUtils.getBuildType(initializeStepInfo.getInfrastructure());
      CIExecutionMetadata ciAccountBuildMetadata = CIExecutionMetadata.builder()
                                                       .accountId(accountID)
                                                       .status(Status.QUEUED.toString())
                                                       .buildType(buildType)
                                                       .stageExecutionId(stagExecutionID)
                                                       .infraType(infraType)
                                                       .build();
      ciExecutionRepository.save(ciAccountBuildMetadata);
    }
  }

  public long getActiveExecutionsCount(String accountID, List<String> status) {
    return ciExecutionRepository.countByAccountIdAndStatusIn(accountID, status);
  }

  public long getActiveMacExecutionsCount(String accountID) {
    return ciExecutionRepository.countByAccountIdAndBuildType(accountID, OSType.MacOS);
  }

  public CIExecutionMetadata deleteActiveExecutionRecord(String stageExecutionID) {
    CIExecutionMetadata executionMetadata = ciExecutionRepository.findByStageExecutionId(stageExecutionID);
    ciExecutionRepository.deleteByStageExecutionId(stageExecutionID);
    return executionMetadata;
  }
}
