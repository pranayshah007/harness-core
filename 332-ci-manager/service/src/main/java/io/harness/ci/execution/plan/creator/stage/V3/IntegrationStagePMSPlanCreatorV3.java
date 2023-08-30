/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.cimanager.stages.V1.IntegrationStageConfigImplV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.plancreator.DependencyMetadata;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
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
  public static final String STAGE_NODE = "stageNode";
  public static final String INFRASTRUCTURE = "infrastructure";
  public static final String CODEBASE = "codebase";

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
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, context.getDependency());
    if (StrategyUtilsV1.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtilsV1.modifyStageLayoutNodeGraph(stageYamlField, nextNodeUuid);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  private List<AdviserObtainment> getAdvisorObtainments(Dependency dependency) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    AdviserObtainment nextStepAdviser = PlanCreatorUtilsV1.getNextStepAdviser(kryoSerializer, dependency);
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
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
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField field = ctx.getCurrentField();
    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));
    IntegrationStageConfigImplV1 stageConfigImpl = stageNode.getStageConfig();
    Infrastructure infrastructure =
        ciPlanCreatorUtils.getInfrastructure(stageConfigImpl.getRuntime(), stageConfigImpl.getPlatform());
    CodeBase codeBase =
        createPlanForCodebase(ctx, stageConfigImpl.getClone(), planCreationResponseMap, stepsField.getUuid());
    dependenciesNodeMap.put(stepsField.getUuid(), stepsField);
    DependencyMetadata dependencyMetadata = StrategyUtilsV1.getStrategyFieldDependencyMetadataIfPresent(
        kryoSerializer, ctx, stageNode.getUuid(), dependenciesNodeMap, getAdvisorObtainments(ctx.getDependency()));

    // Both metadata and nodeMetadata contain the same metadata, the first one's value will be kryo serialized bytes
    // while second one can have values in their primitive form like strings, int, etc. and will have kryo serialized
    // bytes for complex objects. We will deprecate the first one in v1
    planCreationResponseMap.put(stepsField.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(
                DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                    .toBuilder()
                    .putDependencyMetadata(field.getUuid(),
                        Dependency.newBuilder()
                            .putAllMetadata(dependencyMetadata.getMetadataMap())
                            .setNodeMetadata(
                                HarnessStruct.newBuilder().putAllData(dependencyMetadata.getNodeMetadataMap()).build())
                            .build())
                    .putDependencyMetadata(
                        stepsField.getUuid(), getDependencyMetadataForStepsField(infrastructure, codeBase, stageNode))
                    .build())
            .build());
    log.info("Successfully created plan for integration stage {}", stageNode.getName());
    return planCreationResponseMap;
  }

  private CodeBase createPlanForCodebase(PlanCreationContext ctx, Clone stageClone,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String childNodeID) {
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
      return codeBase;
    }
    return null;
  }

  Dependency getDependencyMetadataForStepsField(
      Infrastructure infrastructure, CodeBase codeBase, IntegrationStageNodeV1 stageNode) {
    Map<String, HarnessValue> nodeMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    ByteString stageNodeBytes = ByteString.copyFrom(kryoSerializer.asBytes(stageNode));
    ByteString infrastructureBytes = ByteString.copyFrom(kryoSerializer.asBytes(infrastructure));
    metadataMap.put(STAGE_NODE, stageNodeBytes);
    metadataMap.put(INFRASTRUCTURE, infrastructureBytes);

    nodeMetadataMap.put(STAGE_NODE, HarnessValue.newBuilder().setBytesValue(stageNodeBytes).build());
    nodeMetadataMap.put(INFRASTRUCTURE, HarnessValue.newBuilder().setBytesValue(infrastructureBytes).build());
    if (codeBase != null) {
      ByteString codebaseBytes = ByteString.copyFrom(kryoSerializer.asBytes(codeBase));
      metadataMap.put(CODEBASE, codebaseBytes);
      nodeMetadataMap.put(CODEBASE, HarnessValue.newBuilder().setBytesValue(codebaseBytes).build());
    }
    // Both metadata and nodeMetadata contain the same metadata, the first one's value will be kryo serialized bytes
    // while second one can have values in their primitive form like strings, int, etc. and will have kryo serialized
    // bytes for complex objects. We will deprecate the first one in v1
    return Dependency.newBuilder()
        .putAllMetadata(metadataMap)
        .setNodeMetadata(HarnessStruct.newBuilder().putAllData(nodeMetadataMap).build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
