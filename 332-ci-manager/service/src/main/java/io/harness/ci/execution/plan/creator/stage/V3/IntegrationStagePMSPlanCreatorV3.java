/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.stage.V3;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.utils.CIStagePlanCreationUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.TriggerPayload;
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
import io.harness.pms.utils.IdentifierGeneratorUtils;
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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class IntegrationStagePMSPlanCreatorV3 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  private static final String BRANCH_EXPRESSION = "<+trigger.branch>";
  public static final String PR_EXPRESSION = "<+trigger.prNumber>";

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    Infrastructure infrastructure = IntegrationStageUtils.getInfrastructureV2();
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));
    ExecutionElementConfig executionElementConfig =
        IntegrationStageUtils.getExecutionElementConfigFromSteps(stepsField);
    boolean shouldClone = IntegrationStageUtils.shouldClone(specField);
    IntegrationStageNode stageNode = IntegrationStageUtils.getIntegrationStageNode(
        config.getNode(), executionElementConfig, infrastructure, shouldClone);

    StageElementParametersBuilder stageParameters = CIStagePlanCreationUtils.getStageParameters(stageNode);
    stageParameters
        .specConfig(IntegrationStageStepParametersPMS.getStepParameters(stageNode, childrenNodeIds.get(0), null, ctx))
        .build();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, config.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, config.getNodeName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getIdentifier()))
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(stageParameters.build())
            .stepType(IntegrationStageStepPMS.STEP_TYPE)
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false);
    // If strategy present then don't add advisers. Strategy node will take care of running the stage nodes.
    if (config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STRATEGY)
        == null) {
      builder.adviserObtainments(getBuild(ctx.getDependency()));
    }
    return builder.build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, YamlField config) {
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

  private List<AdviserObtainment> getBuild(Dependency dependency) {
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
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, ByteString> strategyMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    boolean cloneCodebase = IntegrationStageUtils.shouldClone(specField);
    CodeBase codebase = getCodebase(ctx);
    ExecutionSource executionSource = IntegrationStageUtils.buildExecutionSourceV2(
        ctx, codebase, connectorUtils, IdentifierGeneratorUtils.getId(config.getNodeName()));
    if (codebase != null) {
      createCodeBasePlanCreator(planCreationResponseMap, cloneCodebase, codebase, executionSource, specField.getUuid());
      metadataMap.put("codebase", ByteString.copyFrom(kryoSerializer.asBytes(codebase)));
    }
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(
        kryoSerializer, ctx, config.getUuid(), dependenciesNodeMap, strategyMetadataMap, getBuild(ctx.getDependency()));
    dependenciesNodeMap.put(specField.getUuid(), specField);
    planCreationResponseMap.put(specField.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                              .toBuilder()
                              .putDependencyMetadata(
                                  specField.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                              .putDependencyMetadata(
                                  config.getUuid(), Dependency.newBuilder().putAllMetadata(strategyMetadataMap).build())
                              .build())
            .build());
    log.info("Successfully created plan for integration stage {}", config.getNodeName());
    return planCreationResponseMap;
  }

  private void createCodeBasePlanCreator(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      boolean cloneCodebase, CodeBase codeBase, ExecutionSource executionSource, String childNode) {
    if (cloneCodebase) {
      List<PlanNode> codeBasePlanNodeList =
          CodebasePlanCreator.createPlanForCodeBaseV2(codeBase, kryoSerializer, executionSource, childNode);
      Collections.reverse(codeBasePlanNodeList);
      if (isNotEmpty(codeBasePlanNodeList)) {
        codeBasePlanNodeList.forEach(planNode
            -> planCreationResponseMap.put(
                planNode.getUuid(), PlanCreationResponse.builder().planNode(planNode).build()));
      }
    }
  }

  private CodeBase getCodebase(PlanCreationContext ctx) {
    PipelineStoreType pipelineStoreType = ctx.getPipelineStoreType();
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
    switch (pipelineStoreType) {
      case REMOTE:
        return CodeBase.builder()
            .uuid(generateUuid())
            .connectorRef(ParameterField.createValueField(ctx.getMetadata().getMetadata().getPipelineConnectorRef()))
            .repoName(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getRepoName()))
            .build(ParameterField.createValueField(getBuild(ctx)))
            .depth(ParameterField.createValueField(GIT_CLONE_MANUAL_DEPTH))
            .prCloneStrategy(ParameterField.createValueField(null))
            .sslVerify(ParameterField.createValueField(null))
            .build();
      default:
        return null;
    }
  }

  private GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  // TODO: do it properly
  private Build getBuild(PlanCreationContext ctx) {
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    ExecutionTriggerInfo triggerInfo = planCreationContextValue.getMetadata().getTriggerInfo();
    TriggerPayload triggerPayload = planCreationContextValue.getTriggerPayload();
    BuildBuilder builder =
        Build.builder()
            .type(BuildType.BRANCH)
            .spec(BranchBuildSpec.builder()
                      .branch(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getBranch()))
                      .build());
    if (triggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
      if (triggerPayload.getParsedPayload().hasPr()) {
        builder = builder.type(BuildType.PR)
                      .spec(PRBuildSpec.builder().number(ParameterField.createValueField(PR_EXPRESSION)).build());
      } else if (triggerPayload.getParsedPayload().hasPush()) {
        builder =
            builder.type(BuildType.BRANCH)
                .spec(BranchBuildSpec.builder().branch(ParameterField.createValueField(BRANCH_EXPRESSION)).build());
      }
    }
    return builder.build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
