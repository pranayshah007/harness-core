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

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.Workspace;
import io.harness.beans.stages.IACMStageConfigImplV1;
import io.harness.beans.stages.IACMStageNodeV1;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
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
import io.harness.ci.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacmserviceclient.IACMServiceUtils;
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
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey(key)) {
      return Optional.empty();
    }
    byte[] bytes = dependency.getMetadataMap().get(key).toByteArray();
    return EmptyPredicate.isEmpty(bytes) ? Optional.empty() : Optional.of(kryoSerializer.asObject(bytes));
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
    String workspaceId = specField.getNode().getField("workspace").getNode().getCurrJsonNode().asText();

    CodeBase codeBase = getIACMCodebase(ctx, workspaceId);
    Optional<Object> optionalOptions =
        getDeserializedObjectFromDependency(ctx.getMetadata().getGlobalDependency(), YAMLFieldNameConstants.OPTIONS);
    Options options = (Options) optionalOptions.orElse(Options.builder().build());
    Registry registry = options.getRegistry() == null ? Registry.builder().build() : options.getRegistry();
    IntegrationStageStepParametersPMS params = IntegrationStageStepParametersPMS.builder()
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
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, IACMStageNodeV1 stageNode) {
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
        YAMLFieldNameConstants.STAGE, Collections.singleton(IACMStepSpecTypeConstants.IACM_STAGE_V1));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, IACMStageNodeV1 stageNode) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, ByteString> strategyMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField field = ctx.getCurrentField();
    YamlField specField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.SPEC));
    String workspaceId = specField.getNode().getField("workspace").getNode().getCurrJsonNode().asText();
    YamlField stepsField = Preconditions.checkNotNull(specField.getNode().getField(YAMLFieldNameConstants.STEPS));

    IACMStageConfigImplV1 stageConfigImpl = stageNode.getStageConfig();
    Infrastructure infrastructure = getInfrastructure(stageConfigImpl.getRuntime(), stageConfigImpl.getPlatform());
    createPlanForCodebase(ctx, planCreationResponseMap, metadataMap, stepsField.getUuid(), workspaceId);
    dependenciesNodeMap.put(stepsField.getUuid(), stepsField);
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), dependenciesNodeMap,
        strategyMetadataMap, getAdvisorObtainments(ctx.getDependency()));

    metadataMap.put("stageNode", ByteString.copyFrom(kryoSerializer.asBytes(stageNode)));
    metadataMap.put("infrastructure", ByteString.copyFrom(kryoSerializer.asBytes(infrastructure)));
    metadataMap.put("workspaceId", ByteString.copyFrom(kryoSerializer.asBytes(workspaceId)));

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

  private void createPlanForCodebase(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, Map<String, ByteString> metadataMap,
      String childNodeID, String workspaceId) {
    CodeBase codeBase = getIACMCodebase(ctx, workspaceId);
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

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
