/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.states.codebase.CodeBaseStep;
import io.harness.states.codebase.CodeBaseStepParameters;
import io.harness.states.codebase.CodeBaseTaskStep;
import io.harness.states.codebase.CodeBaseTaskStepParameters;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CodebasePlanCreator {
  public List<PlanNode> createPlanForCodeBase(YamlField ciCodeBaseField, String childNodeId,
      KryoSerializer kryoSerializer, String codeBaseNodeUUID, ExecutionSource executionSource) {
    CodeBase ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseField.getNode());

    return buildCodebasePlanNodes(codeBaseNodeUUID, childNodeId, kryoSerializer, ciCodeBase, executionSource);
  }

  @NotNull
  @VisibleForTesting
  List<PlanNode> buildCodebasePlanNodes(String ciCodeBaseFieldUuid, String childNodeId, KryoSerializer kryoSerializer,
      CodeBase ciCodeBase, ExecutionSource executionSource) {
    List<PlanNode> planNodeList = new ArrayList<>();
    PlanNode codeBaseDelegateTask =
        createPlanForCodeBaseTask(ciCodeBase, executionSource, OrchestrationFacilitatorType.TASK, ciCodeBaseFieldUuid);
    planNodeList.add(codeBaseDelegateTask);
    PlanNode codeBaseSyncTask =
        createPlanForCodeBaseTask(ciCodeBase, executionSource, OrchestrationFacilitatorType.SYNC, ciCodeBaseFieldUuid);
    planNodeList.add(codeBaseSyncTask);

    planNodeList.add(
        PlanNode.builder()
            .uuid(ciCodeBaseFieldUuid)
            .stepType(CodeBaseStep.STEP_TYPE)
            .name("codebase_node")
            .identifier("codebase_node")
            .stepParameters(CodeBaseStepParameters.builder()
                                .codeBaseSyncTaskId(codeBaseSyncTask.getUuid())
                                .codeBaseDelegateTaskId(codeBaseDelegateTask.getUuid())
                                .connectorRef(ciCodeBase.getConnectorRef().getValue())
                                .executionSource(executionSource)
                                .build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(childNodeId).build())))
                    .build())
            .skipGraphType(SkipType.SKIP_NODE)
            .build());

    return planNodeList;
  }

  @NotNull
  @VisibleForTesting
  PlanNode createPlanForCodeBaseTask(
      CodeBase ciCodeBase, ExecutionSource executionSource, String facilitatorType, String codeBaseId) {
    CodeBaseTaskStepParameters codeBaseTaskStepParameters = CodeBaseTaskStepParameters.builder()
                                                                .connectorRef(ciCodeBase.getConnectorRef().getValue())
                                                                .repoName(ciCodeBase.getRepoName().getValue())
                                                                .projectName(ciCodeBase.getProjectName().getValue())
                                                                .executionSource(executionSource)
                                                                .build();

    return PlanNode.builder()
        .uuid(codeBaseId + "-" + facilitatorType.toLowerCase())
        .stepType(CodeBaseTaskStep.STEP_TYPE)
        .identifier("codebase"
            + "-" + facilitatorType.toLowerCase())
        .stepParameters(codeBaseTaskStepParameters)
        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                   .setType(FacilitatorType.newBuilder().setType(facilitatorType).build())
                                   .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }
}
