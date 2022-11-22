/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase.V1;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.ci.states.codebase.CodeBaseTaskStepParameters;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.ng.core.BaseNGAccess;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CodebasePlanCreatorV1 {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;

  public static PlanNode createPlanForCodeBase(PlanCreationContext ctx, KryoSerializer kryoSerializer,
      CodeBase codeBase, ConnectorUtils connectorUtils, ExecutionSource executionSource, String childNodeID) {
    boolean delegateTask = isDelegateTask(ctx, codeBase, executionSource, connectorUtils);
    String facilitatorType = delegateTask ? OrchestrationFacilitatorType.TASK : OrchestrationFacilitatorType.SYNC;
    return createPlanForCodeBaseTask(kryoSerializer, codeBase, executionSource, facilitatorType, childNodeID);
  }

  private static PlanNode createPlanForCodeBaseTask(KryoSerializer kryoSerializer, CodeBase codeBase,
      ExecutionSource executionSource, String facilitatorType, String childNodeID) {
    CodeBaseTaskStepParameters codeBaseTaskStepParameters = CodeBaseTaskStepParameters.builder()
                                                                .connectorRef(codeBase.getConnectorRef().getValue())
                                                                .repoName(codeBase.getRepoName().getValue())
                                                                .executionSource(executionSource)
                                                                .build();
    return PlanNode.builder()
        .uuid(codeBase.getUuid() + "-" + facilitatorType.toLowerCase())
        .stepType(CodeBaseTaskStep.STEP_TYPE)
        .name("codebase"
            + "-" + facilitatorType.toLowerCase())
        .identifier("codebase"
            + "-" + facilitatorType.toLowerCase())
        .stepParameters(codeBaseTaskStepParameters)
        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                   .setType(FacilitatorType.newBuilder().setType(facilitatorType).build())
                                   .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(
                    kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(childNodeID).build())))
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  private static boolean isDelegateTask(
      PlanCreationContext ctx, CodeBase codeBase, ExecutionSource executionSource, ConnectorUtils connectorUtils) {
    BaseNGAccess ambiance = IntegrationStageUtils.getBaseNGAccess(
        ctx.getAccountIdentifier(), ctx.getOrgIdentifier(), ctx.getProjectIdentifier());
    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL) {
      ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
      if (isNotEmpty(manualExecutionSource.getPrNumber()) || isNotEmpty(manualExecutionSource.getBranch())
          || isNotEmpty(manualExecutionSource.getTag())) {
        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(ambiance, codeBase.getConnectorRef().getValue());
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        return connectorUtils.hasApiAccess(connectorDetails) && executeOnDelegate;
      }
    }
    return false;
  }
}
