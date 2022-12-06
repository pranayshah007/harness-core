/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.ParameterField.isNotNull;

import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.strategy.StrategyValidationUtils;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class PipelineStageFilterCreator extends GenericStageFilterJsonCreatorV2<PipelineStageNode> {
  @Inject private PMSPipelineServiceImpl pmsPipelineService;
  @Inject private PipelineStageHelper pipelineStageHelper;
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, PipelineStageNode stageNode) {
    // No filter required for pipeline stage, will be shown in all modules CI, CD etc.
    return null;
  }

  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, PipelineStageNode stageNode) {
    if (stageNode.getStrategy() != null) {
      StrategyValidationUtils.validateStrategyNode(stageNode.getStrategy());
    }

    YamlField variablesField =
        filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      FilterCreatorHelper.checkIfVariableNamesAreValid(variablesField);
    }

    PipelineStageConfig pipelineStageConfig = stageNode.getPipelineStageConfig();
    if (pipelineStageConfig == null) {
      throw new InvalidRequestException("Pipeline Stage Yaml is empty");
    }

    if (isNotNull(pipelineStageConfig.getInputs()) && isNotEmpty(pipelineStageConfig.getInputSetReferences())) {
      throw new InvalidRequestException("Pipeline Inputs and Pipeline Input Set references are not allowed together");
    }

    Optional<PipelineEntity> childPipelineEntity = pmsPipelineService.getPipeline(
        filterCreationContext.getSetupMetadata().getAccountId(), pipelineStageConfig.getOrg(),
        pipelineStageConfig.getProject(), pipelineStageConfig.getPipeline(), false, false);

    if (!childPipelineEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Child pipeline does not exists %s ", pipelineStageConfig.getPipeline()));
    }
    pipelineStageHelper.validateNestedChainedPipeline(childPipelineEntity.get());

    EntityDetailProtoDTO entityDetailProtoDTO =
        getEntityDetailOfChildPipeline(filterCreationContext.getSetupMetadata().getAccountId(), pipelineStageConfig);
    return FilterCreationResponse.builder().referredEntities(Collections.singletonList(entityDetailProtoDTO)).build();
  }

  private EntityDetailProtoDTO getEntityDetailOfChildPipeline(
      String accountId, PipelineStageConfig pipelineStageConfig) {
    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                              .setAccountIdentifier(StringValue.of(accountId))
                              .setOrgIdentifier(StringValue.of(pipelineStageConfig.getOrg()))
                              .setProjectIdentifier(StringValue.of(pipelineStageConfig.getProject()))
                              .setIdentifier(StringValue.of(pipelineStageConfig.getPipeline()))
                              .build())
        .setType(EntityTypeProtoEnum.PIPELINES)
        .build();
  }

  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }
}
