/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.stage;

import static io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import static io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import static io.harness.yaml.extended.ci.codebase.Build.builder;
import static io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.Workspace;
import io.harness.beans.stages.IACMStageConfigImplV1;
import io.harness.beans.stages.IACMStageNodeV1;
import io.harness.beans.steps.IACMStepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.VMRuntimeV1;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacm.execution.IACMIntegrationStageStepPMS;
import io.harness.iacm.execution.IACMIntegrationStageStepParametersPMS;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.plancreator.DependencyMetadata;
import io.harness.plancreator.PlanCreatorUtilsV1;
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
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.clone.Clone;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMStagePMSPlanCreatorV1 extends ChildrenPlanCreator<IACMStageNodeV1> {
  @Inject private KryoSerializer kryoSerializer;
  public static final String workspaceID = "workspaceId";
  public static final String STAGE_NODE = "stageNode";
  public static final String INFRASTRUCTURE = "infrastructure";
  public static final String CODEBASE = "codebase";

  @Inject private IACMServiceUtils serviceUtils;

  @Override
  public Class<IACMStageNodeV1> getFieldClass() {
    return IACMStageNodeV1.class;
  }

  // TODO: We may not need this as our infra is going to be always cloud
  public Infrastructure getInfrastructure(RuntimeV1 runtime, PlatformV1 platformV1) {
    Platform platform = platformV1.toPlatform();
    switch (runtime.getType()) {
      case CLOUD:
        return HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(platform))
                      .build())
            .build();
      case MACHINE:
        return DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder().platform(ParameterField.createValueField(platform)).build())
            .build();
      case VM:
        VMRuntimeV1 vmRuntime = (VMRuntimeV1) runtime;
        return VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().poolName(vmRuntime.getSpec().getPool()).build())
                      .build())
            .build();
      default:
        throw new InvalidRequestException("Invalid Runtime - " + runtime.getType());
    }
  }

  private CodeBase getIACMCodebase(PlanCreationContext ctx, String workspaceId) {
    try {
      CodeBaseBuilder iacmCodeBase = CodeBase.builder();
      Workspace workspace = serviceUtils.getIACMWorkspaceInfo(
          ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspaceId);
      // If the repository name is empty, it means that the connector is an account connector and the repo needs to be
      // defined
      if (!Objects.equals(workspace.getRepository(), "") && workspace.getRepository() != null) {
        iacmCodeBase.repoName(ParameterField.<String>builder().value(workspace.getRepository()).build());
      } else {
        iacmCodeBase.repoName(ParameterField.<String>builder().value(null).build());
      }

      iacmCodeBase.connectorRef(ParameterField.<String>builder().value(workspace.getRepository_connector()).build());
      iacmCodeBase.depth(ParameterField.<Integer>builder().value(50).build());
      iacmCodeBase.prCloneStrategy(ParameterField.<PRCloneStrategy>builder().value(null).build());
      iacmCodeBase.sslVerify(ParameterField.<Boolean>builder().value(null).build());
      iacmCodeBase.uuid(generateUuid());

      // Now we need to build the Build type for the Codebase.
      // We support 2,

      BuildBuilder buildObject = builder();
      if (!Objects.equals(workspace.getRepository_branch(), "") && workspace.getRepository_branch() != null) {
        buildObject.type(BuildType.BRANCH);
        buildObject.spec(BranchBuildSpec.builder()
                             .branch(ParameterField.<String>builder().value(workspace.getRepository_branch()).build())
                             .build());
      } else if (!Objects.equals(workspace.getRepository_commit(), "") && workspace.getRepository_commit() != null) {
        buildObject.type(BuildType.TAG);
        buildObject.spec(TagBuildSpec.builder()
                             .tag(ParameterField.<String>builder().value(workspace.getRepository_commit()).build())
                             .build());
      } else {
        throw new IACMStageExecutionException(
            "Unexpected connector information while writing the CodeBase block. There was not repository branch nor commit id defined in the workspace "
            + workspaceId);
      }

      return iacmCodeBase.build(ParameterField.<Build>builder().value(buildObject.build()).build()).build();

    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve iacmCodeBase from pipeline");
      throw new IACMStageExecutionException("Unexpected error building the connector information from the workspace: "
          + workspaceId + " ." + ex.getMessage());
    }
  }

  // TODO ???
  public Optional<Object> getDeserializedObjectFromDependency(Dependency dependency, String key) {
    return PlanCreatorUtilsV1.getDeserializedObjectFromDependency(dependency, kryoSerializer, key, false);
  }

  // TODO: We may not this this
  public boolean shouldCloneManually(PlanCreationContext ctx, CodeBase codeBase) {
    if (codeBase == null) {
      return false;
    }

    switch (ctx.getTriggerInfo().getTriggerType()) {
      case WEBHOOK:
        Dependency globalDependency = ctx.getMetadata().getGlobalDependency();
        Optional<Object> optionalOptions =
            getDeserializedObjectFromDependency(globalDependency, YAMLFieldNameConstants.OPTIONS);
        Options options = (Options) optionalOptions.orElse(Options.builder().build());
        Clone clone = options.getClone();
        if (clone == null || ParameterField.isNull(clone.getRef())) {
          return false;
        }
        break;
      default:
    }
    return true;
  }

  /*
   * This method creates a plan to follow for the Parent node, which is the stage. If I get this right, because the
   * stage is treated as another step, this follows the same procedure where stages are defined in what order need to be
   * executed and then for each step a Plan for the child nodes (steps?) will be executed
   * */
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, IACMStageNodeV1 stageNode, List<String> childrenNodeIds) {
    YamlField field = ctx.getCurrentField();
    IACMStageConfigImplV1 stageConfig = stageNode.getStageConfig();
    Infrastructure infrastructure = getInfrastructure(stageConfig.getRuntime(), stageConfig.getPlatform());
    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    String workspaceId = specField.getNode().getFieldOrThrow("workspace").getNode().getCurrJsonNode().asText();

    CodeBase codeBase = getIACMCodebase(ctx, workspaceId);
    Optional<Object> optionalOptions =
        getDeserializedObjectFromDependency(ctx.getMetadata().getGlobalDependency(), YAMLFieldNameConstants.OPTIONS);
    Options options = (Options) optionalOptions.orElse(Options.builder().build());
    Registry registry = options.getRegistry() == null ? Registry.builder().build() : options.getRegistry();
    IACMIntegrationStageStepParametersPMS params = IACMIntegrationStageStepParametersPMS.builder()
                                                       .infrastructure(infrastructure)
                                                       .childNodeID(childrenNodeIds.get(0))
                                                       .codeBase(codeBase)
                                                       .triggerPayload(ctx.getTriggerPayload())
                                                       .registry(registry)
                                                       .cloneManually(shouldCloneManually(ctx, codeBase))
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
            .stepType(IACMIntegrationStageStepPMS.STEP_TYPE)
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
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, IACMStageNodeV1 stageNode) {
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
        YAMLFieldNameConstants.STAGE, Collections.singleton(IACMStepSpecTypeConstants.IACM_STAGE_V1));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, IACMStageNodeV1 stageNode) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField field = ctx.getCurrentField();
    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    String workspaceId = specField.getNode().getFieldOrThrow("workspace").getNode().getCurrJsonNode().asText();
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));

    IACMStageConfigImplV1 stageConfigImpl = stageNode.getStageConfig();
    Infrastructure infrastructure = getInfrastructure(stageConfigImpl.getRuntime(), stageConfigImpl.getPlatform());
    CodeBase codeBase = createPlanForCodebase(ctx, planCreationResponseMap, stepsField.getUuid(), workspaceId);
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
                    .putDependencyMetadata(stepsField.getUuid(),
                        getDependencyMetadataForStepsField(infrastructure, codeBase, workspaceId, stageNode))
                    .build())
            .build());
    log.info("Successfully created plan for integration stage {}", stageNode.getName());
    return planCreationResponseMap;
  }

  private CodeBase createPlanForCodebase(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String childNodeID, String workspaceId) {
    CodeBase codeBase = getIACMCodebase(ctx, workspaceId);
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

  Dependency getDependencyMetadataForStepsField(
      Infrastructure infrastructure, CodeBase codeBase, String workspaceId, IACMStageNodeV1 stageNode) {
    Map<String, HarnessValue> nodeMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    ByteString stageNodeBytes = ByteString.copyFrom(kryoSerializer.asBytes(stageNode));
    ByteString infrastructureBytes = ByteString.copyFrom(kryoSerializer.asBytes(infrastructure));
    ByteString codebaseBytes = ByteString.copyFrom(kryoSerializer.asBytes(codeBase));
    metadataMap.put(STAGE_NODE, stageNodeBytes);
    metadataMap.put(INFRASTRUCTURE, infrastructureBytes);
    metadataMap.put(workspaceID, ByteString.copyFrom(kryoSerializer.asBytes(workspaceId)));
    metadataMap.put(CODEBASE, codebaseBytes);

    nodeMetadataMap.put(STAGE_NODE, HarnessValue.newBuilder().setBytesValue(stageNodeBytes).build());
    nodeMetadataMap.put(INFRASTRUCTURE, HarnessValue.newBuilder().setBytesValue(infrastructureBytes).build());
    nodeMetadataMap.put(workspaceID, HarnessValue.newBuilder().setStringValue(workspaceId).build());
    nodeMetadataMap.put(CODEBASE, HarnessValue.newBuilder().setBytesValue(codebaseBytes).build());
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
    return Set.of(HarnessYamlVersion.V1);
  }
}
