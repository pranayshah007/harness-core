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
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.utils.CIStagePlanCreationUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
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
            .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
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
    AdviserObtainment adviser = getBuild(ctx.getDependency());
    if (adviser != null) {
      builder.adviserObtainment(adviser);
    }
    return builder.build();
  }

  private AdviserObtainment getBuild(Dependency dependency) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("nextId")) {
      return null;
    }

    String nextId = (String) kryoSerializer.asObject(dependency.getMetadataMap().get("nextId").toByteArray());
    return AdviserObtainment.newBuilder()
        .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
        .setParameters(
            ByteString.copyFrom(kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(nextId).build())))
        .build();
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
    Map<String, ByteString> metadataMap = new HashMap<>();
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    boolean cloneCodebase = IntegrationStageUtils.shouldClone(specField);
    CodeBase codebase = getCodebase(ctx);
    if (codebase != null) {
      createCodeBasePlanCreator(planCreationResponseMap, cloneCodebase, codebase, specField.getUuid());
      metadataMap.put("codebase", ByteString.copyFrom(kryoSerializer.asBytes(codebase)));
    }
    planCreationResponseMap.put(specField.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder()
                              .putDependencies(specField.getUuid(), specField.getYamlPath())
                              .putDependencyMetadata(
                                  specField.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                              .build())
            .build());
    log.info("Successfully created plan for integration stage {}", config.getNodeName());
    return planCreationResponseMap;
  }

  private void createCodeBasePlanCreator(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      boolean cloneCodebase, CodeBase codeBase, String childNode) {
    if (cloneCodebase) {
      String branchName = null;
      Build build = RunTimeInputHandler.resolveBuild(codeBase.getBuild());
      if (build.getType() == BuildType.BRANCH) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        branchName = RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", "identifier", branch, false);
      }
      ExecutionSource executionSource = ManualExecutionSource.builder().branch(branchName).build();
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
            .build(
                ParameterField.createValueField(Build.builder()
                                                    .type(BuildType.BRANCH)
                                                    .spec(BranchBuildSpec.builder()
                                                              .branch(ParameterField.createValueField(
                                                                  gitSyncBranchContext.getGitBranchInfo().getBranch()))
                                                              .build())
                                                    .build()))
            .depth(ParameterField.createValueField(GIT_CLONE_MANUAL_DEPTH))
            .prCloneStrategy(ParameterField.createValueField(null))
            .sslVerify(ParameterField.createValueField(null))
            .build();
      default:
        return null;
    }
  }

  public GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
