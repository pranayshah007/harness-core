/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.logging.AutoLogContext;
import io.harness.plancreator.steps.internal.PmsStepPlanCreatorUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.pipelinestage.step.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.steps.pipelinestage.PipelineStageOutputs;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.when.utils.RunInfoUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineStagePlanCreator implements PartialPlanCreator<PipelineStageNode> {
  @Inject private PipelineStageHelper pipelineStageHelper;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject KryoSerializer kryoSerializer;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  public PipelineStageStepParameters getStepParameter(PipelineStageConfig config, YamlField pipelineInputs,
      String stageNodeId, String childPipelineVersion, String accountIdentifier) {
    return PipelineStageStepParameters.builder()
        .pipeline(config.getPipeline())
        .org(config.getOrg())
        .project(config.getProject())
        .stageNodeId(stageNodeId)
        .inputSetReferences(config.getInputSetReferences())
        .outputs(ParameterField.createValueField(PipelineStageOutputs.getMapOfString(config.getOutputs())))
        .pipelineInputsJsonNode(pipelineStageHelper.getInputSetJsonNode(pipelineInputs, childPipelineVersion))
        .build();
  }

  public void setSourcePrincipal(PlanCreationContextValue executionMetadata) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipal(executionMetadata.getAccountIdentifier(),
        executionMetadata.getMetadata().getPrincipalInfo(),
        executionMetadata.getMetadata().getTriggerInfo().getTriggeredBy());
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineStageNode stageNode) {
    PipelineStageConfig config = stageNode.getPipelineStageConfig();
    if (config == null) {
      throw new InvalidRequestException("Pipeline Stage Yaml does not contain spec");
    }
    // Principal is added to fetch Git Entity. GitContext is to set GitContext for child pipeline. This was missed where
    // parent pipeline is in non-default branch. With this change, chained pipeline will be executed with same branch as
    // that of parent pipeline branch
    setSourcePrincipal(ctx.getMetadata());
    setGitContextForChildPipeline(ctx);

    try (AutoLogContext ignore = GitAwareContextHelper.autoLogContext()) {
      log.info("Retrieving nested pipeline for pipeline stage");
      Optional<PipelineEntity> childPipelineEntity = pmsPipelineService.getPipeline(ctx.getAccountIdentifier(),
          config.getOrg(), config.getProject(), config.getPipeline(), false, false, false, true);

      if (!childPipelineEntity.isPresent()) {
        throw new InvalidRequestException(String.format("Child pipeline does not exists %s ", config.getPipeline()));
      }

      String parentPipelineIdentifier = ctx.getPipelineIdentifier();

      pipelineStageHelper.validateNestedChainedPipeline(
          childPipelineEntity.get(), stageNode.getName(), parentPipelineIdentifier);
      pipelineStageHelper.validateFailureStrategy(stageNode.getFailureStrategies());

      // TODO: remove this to enable Strategy support for Pipeline Stage
      if (ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY) != null) {
        throw new InvalidRequestException(
            String.format("Strategy is not supported for Pipeline stage %s", stageNode.getIdentifier()));
      }

      Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
      Map<String, ByteString> metadataMap = new HashMap<>();

      // This will be empty till we enable strategy support for Pipeline Stage
      addDependencyForStrategy(ctx, stageNode, dependenciesNodeMap, metadataMap);

      // Here planNodeId is used to support strategy. Same node id will be passed to child execution for navigation to
      // parent execution
      String planNodeId = StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid());

      PlanNodeBuilder builder =
          PlanNode.builder()
              .uuid(planNodeId)
              .name(stageNode.getName())
              .identifier(stageNode.getIdentifier())
              .group(StepCategory.STAGE.name())
              .stepType(PipelineStageStep.STEP_TYPE)
              .expressionMode(
                  ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED) // Do not want null if expression is
              // unresolved. Used in envV2 implementation
              .stepParameters(getStepParameter(config,
                  ctx.getCurrentField()
                      .getNode()
                      .getField(YAMLFieldNameConstants.SPEC)
                      .getNode()
                      .getField(YAMLFieldNameConstants.INPUTS),
                  planNodeId, childPipelineEntity.get().getHarnessVersion(), ctx.getAccountIdentifier()))
              .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
              .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen()))
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                      .build())
              .adviserObtainments(PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(
                  kryoSerializer, ctx.getCurrentField(), true));
      if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
        builder.executionInputTemplate(ctx.getExecutionInputTemplate());
      }

      // Dependencies is added for strategy node
      return PlanCreationResponse.builder()
          .graphLayoutResponse(getLayoutNodeInfo(ctx, stageNode))
          .planNode(builder.build())
          .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                            .toBuilder()
                            .putDependencyMetadata(
                                stageNode.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                            .build())
          .build();
    }
  }

  private void setGitContextForChildPipeline(PlanCreationContext ctx) {
    EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(ctx.getGitSyncBranchContext());
    PipelineGitXHelper.setupEntityDetails(entityGitDetails);
  }

  private void addDependencyForStrategy(PlanCreationContext ctx, PipelineStageNode stageNode,
      Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(),
        stageNode.getIdentifier(), stageNode.getName(), dependenciesNodeMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false));
  }

  // This is for graph view of strategy execution
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, PipelineStageNode config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }
}
