/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.repositories.CIExecutionRepository;

import com.google.inject.Inject;

public class QueueExecutionUtils {
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private RunTimeInputHandler runTimeInputHandler;

  public void addActiveExecutionBuild(OrchestrationEvent event, String accountID, String runtimeID) {
    int count = 0;
    if (event.getResolvedStepParameters() != null && event.getStatus() == RUNNING
        && event.getResolvedStepParameters() instanceof StageElementParameters) {
      IntegrationStageStepParametersPMS specConfig =
          (IntegrationStageStepParametersPMS) ((StageElementParameters) event.getResolvedStepParameters())
              .getSpecConfig();

      Infrastructure.Type infraType = specConfig.getInfrastructure().getType();
      OSType buildType = getBuildType(specConfig);
      CIExecutionMetadata ciAccountBuildMetadata = CIExecutionMetadata.builder()
                                                       .accountId(accountID)
                                                       .buildType(buildType)
                                                       .runtimeId(runtimeID)
                                                       .infraType(infraType)
                                                       .build();
      ciExecutionRepository.save(ciAccountBuildMetadata);
    }
  }

  private OSType getBuildType(IntegrationStageStepParametersPMS specConfig) {
    String os = "Linux";
    if (specConfig.getInfrastructure() instanceof VmInfraYaml) {
      VmInfraYaml infrastructure = (VmInfraYaml) specConfig.getInfrastructure();
      return RunTimeInputHandler.resolveOSType(((VmPoolYaml) infrastructure.getSpec()).getSpec().getOs());
    } else if (specConfig.getInfrastructure() instanceof DockerInfraYaml) {
      DockerInfraYaml infrastructure = (DockerInfraYaml) specConfig.getInfrastructure();
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getPlatform().getValue().getOs());
    } else if (specConfig.getInfrastructure() instanceof K8sDirectInfraYaml) {
      K8sDirectInfraYaml infrastructure = (K8sDirectInfraYaml) specConfig.getInfrastructure();
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getOs());
    } else if (specConfig.getInfrastructure() instanceof HostedVmInfraYaml) {
      HostedVmInfraYaml infrastructure = (HostedVmInfraYaml) specConfig.getInfrastructure();
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getPlatform().getValue().getOs());
    } else {
      throw new CIStageExecutionException("unexptected type of infra received");
    }
  }

  public long getActiveExecutionsCount(String accountID) {
    return ciExecutionRepository.countByAccountId(accountID);
  }

  public long getActiveMacExecutionsCount(String accountID) {
    return ciExecutionRepository.countByAccountIdAndBuildType(accountID, OSType.MacOS);
  }

  public void deleteActiveExecutionRecord(String runtimeID) {
    ciExecutionRepository.deleteByRuntimeId(runtimeID);
  }
}
