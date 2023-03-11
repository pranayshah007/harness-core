/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.repositories.CIExecutionRepository;

import com.google.inject.Inject;

public class QueueExecutionUtils {
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  public void addActiveExecutionBuild(InitializeStepInfo initializeStepInfo, String accountID, String stagExecutionID) {
    if (ciExecutionRepository.findByStageExecutionId(stagExecutionID) == null) {
      IntegrationStageConfig stageConfig = initializeStepInfo.getStageElementConfig();

      Infrastructure.Type infraType = initializeStepInfo.getInfrastructure().getType();
      OSType buildType = IntegrationStageUtils.getBuildType(stageConfig.getInfrastructure());
      CIExecutionMetadata ciAccountBuildMetadata = CIExecutionMetadata.builder()
                                                       .accountId(accountID)
                                                       .buildType(buildType)
                                                       .stageExecutionId(stagExecutionID)
                                                       .infraType(infraType)
                                                       .build();
      ciExecutionRepository.save(ciAccountBuildMetadata);
    }
  }

  public long getActiveExecutionsCount(String accountID) {
    return ciExecutionRepository.countByAccountId(accountID);
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
