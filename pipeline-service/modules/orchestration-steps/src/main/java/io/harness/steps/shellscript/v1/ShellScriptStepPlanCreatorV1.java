/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.plancreator.DependencyMetadata;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.shellscript.ShellScriptStepNode;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;

public class ShellScriptStepPlanCreatorV1 implements PartialPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Sets.newHashSet(StepSpecTypeConstants.SHELL_SCRIPT));
  }

  @SneakyThrows
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, YamlField field) {
    ShellScriptStepNode stepNode = YamlUtils.read(field.getNode().toString(), ShellScriptStepNode.class);
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    StepParameters stepParameters = ((PMSStepInfo) stepNode.getStepSpecType()).getStepParameters(stepNode, null, ctx);
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stepNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getNodeName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, field.getId()))
            .stepType(stepNode.getStepSpecType().getStepType())
            .group(StepOutcomeGroup.STEP.name())
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(
                        FacilitatorType.newBuilder().setType(stepNode.getStepSpecType().getFacilitatorType()).build())
                    .build())
            .whenCondition(RunInfoUtils.getRunConditionForStep(stepNode.getWhen()))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                    .timeout(TimeoutUtils.getTimeoutParameterFieldString(stepNode.getTimeout()))
                                    .build())
                    .build())
            .skipUnresolvedExpressionsCheck(stepNode.getStepSpecType().skipUnresolvedExpressionsCheck())
            .expressionMode(stepNode.getStepSpecType().getExpressionMode());

    if (field.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField("strategy") == null) {
      builder.adviserObtainments(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency()));
    }
    DependencyMetadata dependencyMetadata =
        StrategyUtilsV1.getStrategyFieldDependencyMetadataIfPresent(kryoSerializer, ctx, field.getUuid(),
            dependenciesNodeMap, PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency()));

    // Both metadata and nodeMetadata contain the same metadata, the first one's value will be kryo serialized bytes
    // while second one can have values in their primitive form like strings, int, etc. and will have kryo serialized
    // bytes for complex objects. We will deprecate the first one in v1
    return PlanCreationResponse.builder()
        .planNode(builder.build())
        .dependencies(
            DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                .toBuilder()
                .putDependencyMetadata(field.getUuid(),
                    Dependency.newBuilder()
                        .putAllMetadata(dependencyMetadata.getMetadataMap())
                        .setNodeMetadata(
                            HarnessStruct.newBuilder().putAllFields(dependencyMetadata.getNodeMetadataMap()).build())
                        .build())
                .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
