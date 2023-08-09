/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.http.HttpStepNodeV1;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;

public class HttpStepPlanCreatorV1 implements PartialPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Sets.newHashSet(StepSpecTypeConstants.HTTP));
  }

  @SneakyThrows
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, YamlField field) {
    final boolean isStepInsideRollback = isStepInsideRollback(ctx.getDependency());
    HttpStepNodeV1 stepNode = YamlUtils.read(field.getNode().toString(), HttpStepNodeV1.class);
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();

    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(
        kryoSerializer, ctx, field.getUuid(), dependenciesNodeMap, metadataMap, buildAdviser(ctx.getDependency()));

    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stepNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getNodeName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getId()))
            .stepType(StepSpecTypeConstants.HTTP_STEP_TYPE)
            .group(StepOutcomeGroup.STEP.name())
            .stepParameters(
                stepNode.getStepParameters(PlanCreatorUtilsCommon.getRollbackParameters(
                                               ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN),
                    ctx))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK).build())
                    .build())
            .whenCondition(RunInfoUtils.getStepWhenCondition(stepNode.getWhen(), isStepInsideRollback))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                    .timeout(TimeoutUtils.getTimeoutParameterFieldString(stepNode.getTimeout()))
                                    .build())
                    .build())
            .skipUnresolvedExpressionsCheck(true)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    if (field.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField("strategy") == null) {
      builder.adviserObtainments(buildAdviser(ctx.getDependency()));
    }

    return PlanCreationResponse.builder()
        .planNode(builder.build())
        .dependencies(
            DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                .toBuilder()
                .putDependencyMetadata(field.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }

  private List<AdviserObtainment> buildAdviser(Dependency dependency) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("nextId")) {
      return adviserObtainments;
    }

    String nextId = (String) kryoSerializer.asObject(dependency.getMetadataMap().get("nextId").toByteArray());
    adviserObtainments.add(
        AdviserObtainment.newBuilder()
            .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
            .setParameters(ByteString.copyFrom(
                kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(nextId).build())))
            .build());
    return adviserObtainments;
  }

  private boolean isStepInsideRollback(Dependency dependency) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("isStepInsideRollback")) {
      return false;
    }
    return (boolean) kryoSerializer.asObject(dependency.getMetadataMap().get("isStepInsideRollback").toByteArray());
  }
}
