/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
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
import io.harness.steps.http.v1.HttpStepNodeV1;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
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
    final boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
    HttpStepNodeV1 stepNode = YamlUtils.read(field.getNode().toString(), HttpStepNodeV1.class);
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();

    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, field.getUuid(), dependenciesNodeMap,
        metadataMap, PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency()));

    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stepNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getNodeName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getId()))
            .stepType(StepSpecTypeConstants.HTTP_STEP_TYPE)
            .group(StepOutcomeGroup.STEP.name())
            // TODO: send rollback parameters to this method which can be extracted from dependency
            .stepParameters(stepNode.getStepParameters(ctx))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK).build())
                    .build())
            .whenCondition(RunInfoUtilsV1.getStepWhenCondition(stepNode.getWhen(), isStepInsideRollback))
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
      builder.adviserObtainments(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency()));
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
}
