/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.stage.V3;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.clone.Clone;
import io.harness.beans.yaml.extended.repository.Repository;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.plan.creator.codebase.V1.CodebasePlanCreatorV1;
import io.harness.ci.states.V1.IntegrationStageStepPMSV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.cimanager.stages.V1.IntegrationStageSpecParamsV1;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class IntegrationStagePMSPlanCreatorV3 extends ChildrenPlanCreator<IntegrationStageNodeV1> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;

  @Override
  public Class<IntegrationStageNodeV1> getFieldClass() {
    return IntegrationStageNodeV1.class;
  }

  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, IntegrationStageNodeV1 stageNode, List<String> childrenNodeIds) {
    YamlField field = ctx.getCurrentField();
    IntegrationStageSpecParamsV1 params =
        IntegrationStageSpecParamsV1.builder().childNodeID(childrenNodeIds.get(0)).build();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getIdentifier()))
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(
                StageElementParameters.builder().identifier(stageNode.getIdentifier()).specConfig(params).build())
            .stepType(IntegrationStageStepPMSV1.STEP_TYPE)
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false);
    // If strategy present then don't add advisers. Strategy node will take care of running the stage nodes.
    if (field.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STRATEGY)
        == null) {
      builder.adviserObtainments(getAdvisorObtainments(ctx.getDependency()));
    }
    return builder.build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, IntegrationStageNodeV1 stageNode) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    String nextNodeUuid = null;
    if (context.getDependency() != null && !EmptyPredicate.isEmpty(context.getDependency().getMetadataMap())
        && context.getDependency().getMetadataMap().containsKey("nextId")) {
      nextNodeUuid =
          (String) kryoSerializer.asObject(context.getDependency().getMetadataMap().get("nextId").toByteArray());
    }
    if (StrategyUtilsV1.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtilsV1.modifyStageLayoutNodeGraph(stageYamlField, nextNodeUuid);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  private List<AdviserObtainment> getAdvisorObtainments(Dependency dependency) {
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

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.CI_STAGE_V2));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, IntegrationStageNodeV1 stageNode) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, ByteString> strategyMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField field = ctx.getCurrentField();
    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));
    createPlanForCodebase(
        ctx, stageNode.getStageConfig().getClone(), planCreationResponseMap, metadataMap, stepsField.getUuid());
    dependenciesNodeMap.put(stepsField.getUuid(), stepsField);
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), dependenciesNodeMap,
        strategyMetadataMap, getAdvisorObtainments(ctx.getDependency()));
    metadataMap.put("stageNode", ByteString.copyFrom(kryoSerializer.asBytes(stageNode)));
    planCreationResponseMap.put(stepsField.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                              .toBuilder()
                              .putDependencyMetadata(
                                  field.getUuid(), Dependency.newBuilder().putAllMetadata(strategyMetadataMap).build())
                              .putDependencyMetadata(
                                  stepsField.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                              .build())
            .build());
    log.info("Successfully created plan for integration stage {}", stageNode.getName());
    return planCreationResponseMap;
  }

  private void createPlanForCodebase(PlanCreationContext ctx, Clone clone,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, Map<String, ByteString> metadataMap,
      String childNodeID) {
    if (!clone.getDisabled().getValue()) {
      CodeBase codeBase = getCodebase(ctx, clone);
      ExecutionSource executionSource =
          IntegrationStageUtils.buildExecutionSourceV2(ctx, codeBase, connectorUtils, ctx.getCurrentField().getId());
      PlanNode codebasePlanNode = CodebasePlanCreatorV1.createPlanForCodeBase(
          ctx, kryoSerializer, codeBase, connectorUtils, executionSource, childNodeID);
      planCreationResponseMap.put(
          codebasePlanNode.getUuid(), PlanCreationResponse.builder().planNode(codebasePlanNode).build());
      metadataMap.put("codebase", ByteString.copyFrom(kryoSerializer.asBytes(codeBase)));
    }
  }

  private CodeBase getCodebase(PlanCreationContext ctx, Clone clone) {
    PipelineStoreType pipelineStoreType = ctx.getPipelineStoreType();
    Optional<Repository> optionalRepository = RunTimeInputHandler.resolveRepository(clone.getRepository());
    switch (pipelineStoreType) {
      case REMOTE:
        GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
        return CodeBase.builder()
            .uuid(clone.getUuid())
            .connectorRef(ParameterField.createValueField(ctx.getPipelineConnectorRef()))
            .repoName(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getRepoName()))
            .build(ParameterField.createValueField(getBuild(ctx, optionalRepository)))
            .depth(ParameterField.createValueField(GIT_CLONE_MANUAL_DEPTH))
            .prCloneStrategy(ParameterField.createValueField(null))
            .sslVerify(ParameterField.createValueField(null))
            .build();
      case INLINE:
        if (optionalRepository.isEmpty()) {
          throw new InvalidRequestException("repository cannot be null for inline pipeline if clone is enabled");
        }
        Repository repository = optionalRepository.get();
        return CodeBase.builder()
            .uuid(clone.getUuid())
            .connectorRef(ParameterField.createValueField(RunTimeInputHandler.resolveStringParameter(
                "connector", "clone", "clone", repository.getConnector(), true)))
            .repoName(ParameterField.createValueField(
                RunTimeInputHandler.resolveStringParameter("name", "clone", "clone", repository.getName(), true)))
            .build(ParameterField.createValueField(getBuild(ctx, optionalRepository)))
            .depth(ParameterField.createValueField(
                RunTimeInputHandler.resolveIntegerParameter(clone.getDepth(), GIT_CLONE_MANUAL_DEPTH)))
            .prCloneStrategy(ParameterField.createValueField(null))
            .sslVerify(ParameterField.createValueField(
                RunTimeInputHandler.resolveBooleanParameter(clone.getInsecure(), false)))
            .build();
      default:
        throw new InvalidRequestException("Invalid Pipeline Store Type : " + pipelineStoreType);
    }
  }

  private GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  private Build getBuild(PlanCreationContext ctx, Optional<Repository> optionalRepository) {
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
    BuildBuilder builder = builder();
    if (optionalRepository.isEmpty()) {
      return builder.type(BuildType.BRANCH)
          .spec(BranchBuildSpec.builder()
                    .branch(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getBranch()))
                    .build())
          .build();
    }
    Repository repository = optionalRepository.get();
    switch (repository.getBuildType()) {
      case BRANCH:
        builder = builder.type(BuildType.BRANCH).spec(BranchBuildSpec.builder().branch(repository.getBranch()).build());
        break;
      case TAG:
        builder = builder.type(BuildType.TAG).spec(TagBuildSpec.builder().tag(repository.getTag()).build());
        break;
      default:
        builder = builder.type(BuildType.PR).spec(PRBuildSpec.builder().number(repository.getPr()).build());
    }
    return builder.build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
