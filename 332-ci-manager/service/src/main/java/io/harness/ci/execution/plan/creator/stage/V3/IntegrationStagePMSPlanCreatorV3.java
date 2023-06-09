/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.stage.V3;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.cimanager.stages.V1.IntegrationStageConfigImplV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutNode;
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
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.clone.Clone;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.options.Options;
import io.harness.yaml.registry.Registry;

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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class IntegrationStagePMSPlanCreatorV3 extends ChildrenPlanCreator<IntegrationStageNodeV1> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private CIPlanCreatorUtils ciPlanCreatorUtils;

  @Override
  public Class<IntegrationStageNodeV1> getFieldClass() {
    return IntegrationStageNodeV1.class;
  }

  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, IntegrationStageNodeV1 stageNode, List<String> childrenNodeIds) {
    YamlField field = ctx.getCurrentField();
    IntegrationStageConfigImplV1 stageConfig = stageNode.getStageConfig();
    Infrastructure infrastructure =
        ciPlanCreatorUtils.getInfrastructure(stageConfig.getRuntime(), stageConfig.getPlatform());
    CodeBase codeBase = ciPlanCreatorUtils.getCodebase(ctx, stageConfig.getClone()).orElse(null);
    Optional<Options> optionalOptions =
        ciPlanCreatorUtils.getDeserializedOptions(ctx.getMetadata().getGlobalDependency());
    Options options = optionalOptions.orElse(Options.builder().build());
    Registry registry = options.getRegistry() == null ? Registry.builder().build() : options.getRegistry();

    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));
    List<YamlField> steps = CIPlanCreatorUtils.getStepYamlFields(stepsField);
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        steps.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    IntegrationStageStepParametersPMS params =
        IntegrationStageStepParametersPMS.builder()
            .stepIdentifiers(IntegrationStageUtils.getStepIdentifiers(executionWrapperConfigs))
            .infrastructure(infrastructure)
            .childNodeID(childrenNodeIds.get(0))
            .codeBase(codeBase)
            .triggerPayload(ctx.getTriggerPayload())
            .registry(registry)
            .cloneManually(ciPlanCreatorUtils.shouldCloneManually(ctx, codeBase))
            .build();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getIdentifier()))
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(StageElementParameters.builder()
                                .identifier(stageNode.getIdentifier())
                                .name(stageNode.getName())
                                .specConfig(params)
                                .build())
            .stepType(IntegrationStageStepPMS.STEP_TYPE)
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen()))
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
    IntegrationStageConfigImplV1 stageConfigImpl = stageNode.getStageConfig();
    Infrastructure infrastructure =
        ciPlanCreatorUtils.getInfrastructure(stageConfigImpl.getRuntime(), stageConfigImpl.getPlatform());
    createPlanForCodebase(ctx, stageConfigImpl.getClone(), planCreationResponseMap, metadataMap, stepsField.getUuid());
    dependenciesNodeMap.put(stepsField.getUuid(), stepsField);
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), dependenciesNodeMap,
        strategyMetadataMap, getAdvisorObtainments(ctx.getDependency()));
    metadataMap.put("stageNode", ByteString.copyFrom(kryoSerializer.asBytes(stageNode)));
    metadataMap.put("infrastructure", ByteString.copyFrom(kryoSerializer.asBytes(infrastructure)));
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

  private void createPlanForCodebase(PlanCreationContext ctx, Clone stageClone,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, Map<String, ByteString> metadataMap,
      String childNodeID) {
    Optional<CodeBase> optionalCodeBase = ciPlanCreatorUtils.getCodebase(ctx, stageClone);
    if (optionalCodeBase.isPresent()) {
      CodeBase codeBase = optionalCodeBase.get();
      List<PlanNode> codebasePlanNodes =
          CodebasePlanCreator.buildCodebasePlanNodes(generateUuid(), childNodeID, kryoSerializer, codeBase, null);
      if (isNotEmpty(codebasePlanNodes)) {
        Collections.reverse(codebasePlanNodes);
        for (PlanNode planNode : codebasePlanNodes) {
          planCreationResponseMap.put(planNode.getUuid(), PlanCreationResponse.builder().planNode(planNode).build());
        }
      }
      metadataMap.put("codebase", ByteString.copyFrom(kryoSerializer.asBytes(codeBase)));
    }
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
