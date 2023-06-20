/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.iacm.execution;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;

import java.util.List;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@SuperBuilder
@TypeAlias("iacmintegrationStageStepParameters")
@OwnedBy(HarnessTeam.IACM)
@RecasterAlias("io.harness.iacm.execution.IACMIntegrationStageStepParametersPMS")
public class IACMIntegrationStageStepParametersPMS extends IntegrationStageStepParametersPMS {
  String workspace;

  public static IACMIntegrationStageStepParametersPMS getStepParameters(IntegrationStageNode stageNode,
      String childNodeID, BuildStatusUpdateParameter buildStatusUpdateParameter, PlanCreationContext ctx) {
    if (stageNode == null) {
      return IACMIntegrationStageStepParametersPMS.builder().childNodeID(childNodeID).build();
    }
    IntegrationStageConfig integrationStageConfig = stageNode.getIntegrationStageConfig();

    Infrastructure infrastructure = getInfrastructure(stageNode, ctx);

    List<String> stepIdentifiers =
        IntegrationStageUtils.getStepIdentifiers(integrationStageConfig.getExecution().getSteps());

    return IACMIntegrationStageStepParametersPMS.builder()
        .buildStatusUpdateParameter(buildStatusUpdateParameter)
        .infrastructure(infrastructure)
        .dependencies(integrationStageConfig.getServiceDependencies().getValue())
        .childNodeID(childNodeID)
        .sharedPaths(integrationStageConfig.getSharedPaths())
        .enableCloneRepo(integrationStageConfig.getCloneCodebase())
        .stepIdentifiers(stepIdentifiers)
        .caching(getCaching(stageNode))
        .build();
  }
}
