/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy.v1;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;
import io.harness.steps.matrix.v1.StrategyStepParametersV1;
import io.harness.steps.matrix.v1.StrategyStepV1;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StrategyConfigPlanCreatorV1 extends ChildrenPlanCreator<StrategyConfigV1> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StrategyConfigV1 config) {
    return new LinkedHashMap<>();
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StrategyConfigV1 config, List<String> childrenNodeIds) {
    ByteString strategyMetadata = ctx.getDependency().getMetadataMap().get(
        StrategyConstants.STRATEGY_METADATA + ctx.getCurrentField().getNode().getUuid());
    StrategyMetadata metadata = (StrategyMetadata) kryoSerializer.asInflatedObject(strategyMetadata.toByteArray());
    String childNodeId = metadata.getChildNodeId();
    String strategyNodeId = metadata.getStrategyNodeId();
    if (EmptyPredicate.isEmpty(childNodeId) || EmptyPredicate.isEmpty(strategyNodeId)) {
      log.error("childNodeId and strategyNodeId not passed from parent. Please pass it.");
      throw new InvalidRequestException("Invalid use of strategy field. Please check");
    }
    StrategyTypeV1 strategyType = config.getType();
    ParameterField<Integer> maxConcurrency = null;
    if (strategyType == StrategyTypeV1.MATRIX) {
      if (!ParameterField.isNotNull(config.getMatrixConfig())) {
        throw new InvalidRequestException(String.format(
            "StrategyType is %s but matrix configuration is not defined. Please define valid matrix configuration and retry.",
            strategyType));
      }
      maxConcurrency = config.getMatrixConfig().getValue().getMaxConcurrency();
    } else {
      throw new InvalidRequestException(
          String.format("Strategy of type %s not supported at this moment.", strategyType));
    }

    //    StrategyValidationUtils.validateStrategyNode(config);

    StepParameters stepParameters = StrategyStepParametersV1.builder()
                                        .childNodeId(childNodeId)
                                        .strategyConfig(config)
                                        .maxConcurrency(maxConcurrency)
                                        .strategyType(strategyType)
                                        .shouldProceedIfFailed(metadata.getShouldProceedIfFailed())
                                        .build();
    return PlanNode.builder()
        .uuid(strategyNodeId)
        .identifier(metadata.getStrategyNodeIdentifier())
        .stepType(StrategyStepV1.STEP_TYPE)
        .group(StepOutcomeGroup.STRATEGY.name())
        .name(metadata.getStrategyNodeName())
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(true)
        .adviserObtainments(metadata.getAdviserObtainments())
        .build();
  }

  @Override
  public Class<StrategyConfigV1> getFieldClass() {
    return StrategyConfigV1.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STRATEGY, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
