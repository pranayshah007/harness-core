/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.pms.sdk.core.steps.io.PipelineViewObject.DEFAULT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdvisorObtainmentList;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.sdk.PmsSdkModuleUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.contracts.TimeoutObtainment;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanNodeProtoMapper {
  @Inject @Named(PmsSdkModuleUtils.SDK_SERVICE_NAME) String serviceName;
  @Inject private KryoSerializer kryoSerializer;

  public PlanNodeProto toPlanNodeProtoWithDecoratedFields(PlanNode node) {
    PlanNodeProto.Builder builder =
        PlanNodeProto.newBuilder()
            .setUuid(node.getUuid())
            .setName(isEmpty(node.getName()) ? "" : node.getName())
            .setStepType(node.getStepType())
            .setStageFqn(emptyIfNull(node.getStageFqn()))
            .setIdentifier(isEmpty(node.getIdentifier()) ? "" : node.getIdentifier())
            .setStepParameters(
                node.getStepParameters() == null ? "" : RecastOrchestrationUtils.toJson(node.getStepParameters()))
            .addAllRebObjects(CollectionUtils.emptyIfNull(node.getRefObjects()))
            .addAllAdviserObtainments(CollectionUtils.emptyIfNull(node.getAdviserObtainments()))
            .putAllAdviserObtainmentsForExecutionMode(
                buildAdvisorObtainmentsForExecutionMode(node.getAdvisorObtainmentsForExecutionMode()))
            .addAllFacilitatorObtainments(CollectionUtils.emptyIfNull(node.getFacilitatorObtainments()))
            .setSkipExpressionChain(node.isSkipExpressionChain())
            .setExpressionMode(node.getExpressionMode())
            .setSkipType(node.getSkipGraphType())
            .setServiceName(serviceName)
            .addAllTimeoutObtainments(toTimeoutObtainments(node.getTimeoutObtainments()))
            .setSkipUnresolvedExpressionsCheck(node.isSkipUnresolvedExpressionsCheck());
    if (node.getWhenCondition() != null) {
      builder.setWhenCondition(node.getWhenCondition());
    }
    if (node.getSkipCondition() != null) {
      builder.setSkipCondition(node.getSkipCondition());
    }
    if (node.getGroup() != null) {
      builder.setGroup(node.getGroup());
    }
    if (node.getStepParameters() != null && node.getStepParameters().toViewJson() != null
        && node.getStepParameters().toViewJson() != DEFAULT) {
      builder.setStepInputs(node.getStepParameters().toViewJson());
    }
    if (node.getExecutionInputTemplate() != null) {
      builder.setExecutionInputTemplate(node.getExecutionInputTemplate());
    }
    return builder.build();
  }

  Map<String, AdvisorObtainmentList> buildAdvisorObtainmentsForExecutionMode(
      Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode) {
    Map<String, AdvisorObtainmentList> result = new HashMap<>();
    for (Map.Entry<ExecutionMode, List<AdviserObtainment>> entry : advisorObtainmentsForExecutionMode.entrySet()) {
      ExecutionMode executionMode = entry.getKey();
      List<AdviserObtainment> adviserObtainmentList = entry.getValue();
      result.put(executionMode.name(),
          AdvisorObtainmentList.newBuilder().addAllAdviserObtainments(adviserObtainmentList).build());
    }
    return result;
  }

  private List<TimeoutObtainment> toTimeoutObtainments(List<SdkTimeoutObtainment> sdkTimeoutObtainments) {
    if (sdkTimeoutObtainments == null) {
      return new ArrayList<>();
    }
    return sdkTimeoutObtainments.stream()
        .map(entry
            -> TimeoutObtainment.newBuilder()
                   .setDimension(entry.getDimension())
                   .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(entry.getParameters())))
                   .build())
        .collect(Collectors.toList());
  }
}
