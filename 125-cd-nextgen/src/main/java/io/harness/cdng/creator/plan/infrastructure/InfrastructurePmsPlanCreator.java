/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.infrastructure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.advisers.RollbackCustomAdviser;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.gitops.ClusterPlanCreatorUtils;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.infra.steps.InfraSectionStepParameters;
import io.harness.cdng.infra.steps.InfrastructureSectionStep;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.rollback.steps.InfrastructureDefinitionStep;
import io.harness.cdng.rollback.steps.InfrastructureProvisionerStep;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.utilities.ResourceConstraintUtility;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStepParameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class InfrastructurePmsPlanCreator {
  public PlanNode getInfraStepPlanNode(Infrastructure pipelineInfrastructure) {
    return PlanNode.builder()
        .uuid(UUIDGenerator.generateUuid())
        .name(PlanCreatorConstants.INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.SPEC_IDENTIFIER)
        .stepType(InfrastructureStep.STEP_TYPE)
        .stepParameters(pipelineInfrastructure)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .build();
  }

  public static LinkedHashMap<String, PlanCreationResponse> createPlanForInfraSectionV2(YamlNode infraSectionNode,
      String infraStepNodeUuid, InfrastructureDefinitionConfig infrastructureDefinitionConfig,
      KryoSerializer kryoSerializer, String infraSectionUuid) {
    InfraSectionStepParameters infraSectionStepParameters =
        getInfraSectionStepParamsFromConfig(infrastructureDefinitionConfig, infraStepNodeUuid);
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    PlanNodeBuilder planNodeBuilder = planBuilderForInfraSection(infraSectionNode, infraSectionUuid);
    planNodeBuilder = planNodeBuilder.stepParameters(infraSectionStepParameters);

    List<AdviserObtainment> adviserObtainments =
        getAdviserObtainmentFromMetaDataToExecution(infraSectionNode.getParentNode(), kryoSerializer);

    // adding RC dependency
    boolean allowSimultaneousDeployments = infrastructureDefinitionConfig.isAllowSimultaneousDeployments();

    if (!allowSimultaneousDeployments) {
      // Passing infra section parent node since rbac will be created parallel to environment node
      YamlField rcYamlField =
          addResourceConstraintDependency(infraSectionNode.getParentNode(), planCreationResponseMap);
      adviserObtainments = getAdviserObtainmentFromMetaDataToResourceConstraint(rcYamlField, kryoSerializer);
    }

    PlanNode infraSectionPlanNode = planNodeBuilder.adviserObtainments(adviserObtainments).build();

    // adding infraSection
    planCreationResponseMap.put(infraSectionPlanNode.getUuid(),
        PlanCreationResponse.builder().node(infraSectionNode.getUuid(), infraSectionPlanNode).build());

    return planCreationResponseMap;
  }

  public static PlanNode createPlanForGitopsClusters(YamlField envField, String infraSectionUuid,
      EnvironmentPlanCreatorConfig envConfig, KryoSerializer kryoSerializer) {
    List<AdviserObtainment> adviserObtainmentFromMetaDataToExecution =
        getAdviserObtainmentFromMetaDataToExecution(envField.getNode(), kryoSerializer);
    PlanNodeBuilder planNodeBuilder =
        ClusterPlanCreatorUtils.getGitopsClustersStepPlanNodeBuilder(infraSectionUuid, envConfig);
    planNodeBuilder.adviserObtainments(adviserObtainmentFromMetaDataToExecution);
    return planNodeBuilder.build();
  }

  public static PlanNode createPlanForGitopsClusters(YamlField envField, String postServiceSpecUuid,
      EnvGroupPlanCreatorConfig envGroupPlanCreatorConfig, KryoSerializer kryoSerializer) {
    List<AdviserObtainment> adviserObtainmentFromMetaDataToExecution =
        getAdviserObtainmentFromMetaDataToExecution(envField.getNode(), kryoSerializer);
    PlanNodeBuilder planNodeBuilder =
        ClusterPlanCreatorUtils.getGitopsClustersStepPlanNodeBuilder(postServiceSpecUuid, envGroupPlanCreatorConfig);
    planNodeBuilder.adviserObtainments(adviserObtainmentFromMetaDataToExecution);
    return planNodeBuilder.build();
  }

  public LinkedHashMap<String, PlanCreationResponse> createPlanForInfraSectionV1(YamlNode infraSectionNode,
      String infraStepNodeUuid, PipelineInfrastructure pipelineInfrastructure, KryoSerializer kryoSerializer,
      String infraSectionUuid) {
    InfraSectionStepParameters infraSectionStepParameters =
        getInfraSectionStepParams(pipelineInfrastructure, infraStepNodeUuid);
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    PlanNodeBuilder planNodeBuilder = planBuilderForInfraSection(infraSectionNode, infraSectionUuid);
    planNodeBuilder = planNodeBuilder.stepParameters(infraSectionStepParameters);

    if (!isProvisionerConfigured(pipelineInfrastructure)) {
      planNodeBuilder.skipGraphType(SkipType.SKIP_NODE);
    }

    List<AdviserObtainment> adviserObtainments =
        getAdviserObtainmentFromMetaDataToExecution(infraSectionNode, kryoSerializer);

    // adding RC dependency
    boolean allowSimultaneousDeployments = ResourceConstraintUtility.isSimultaneousDeploymentsAllowed(
        pipelineInfrastructure.getAllowSimultaneousDeployments());

    if (!allowSimultaneousDeployments) {
      YamlField rcYamlField = addResourceConstraintDependency(infraSectionNode, planCreationResponseMap);
      adviserObtainments = getAdviserObtainmentFromMetaDataToResourceConstraint(rcYamlField, kryoSerializer);
    }

    PlanNode infraSectionPlanNode = planNodeBuilder.adviserObtainments(adviserObtainments).build();

    // adding infraSection
    planCreationResponseMap.put(infraSectionPlanNode.getUuid(),
        PlanCreationResponse.builder().node(infraSectionNode.getUuid(), infraSectionPlanNode).build());

    return planCreationResponseMap;
  }

  public YamlField addResourceConstraintDependency(
      YamlNode rcStepSibilingNode, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField rcYamlField = constructResourceConstraintYamlField(rcStepSibilingNode);

    try {
      YamlUpdates yamlUpdates =
          YamlUpdates.newBuilder()
              .putFqnToYaml(rcYamlField.getYamlPath(), YamlUtils.writeYamlString(rcYamlField).replace("---\n", ""))
              .build();
      planCreationResponseMap.put(rcYamlField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  DependenciesUtils.toDependenciesProto(ImmutableMap.of(rcYamlField.getNode().getUuid(), rcYamlField)))
              .yamlUpdates(yamlUpdates)
              .build());
    } catch (IOException e) {
      throw new YamlException("Yaml created for resource constraint at " + rcYamlField.getYamlPath()
          + " could not be converted into a yaml string");
    }
    return rcYamlField;
  }

  public PlanNodeBuilder planBuilderForInfraSection(YamlNode infraSectionNode, String infraSectionUuid) {
    return PlanNode.builder()
        .uuid(infraSectionUuid)
        .name(PlanCreatorConstants.INFRA_SECTION_NODE_NAME)
        .identifier(PlanCreatorConstants.INFRA_SECTION_NODE_IDENTIFIER)
        .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
        .stepType(InfrastructureSectionStep.STEP_TYPE)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build());
  }

  public InfraSectionStepParameters getInfraSectionStepParamsFromConfig(
      InfrastructureDefinitionConfig infrastructure, String infraStepNodeUuid) {
    return InfraSectionStepParameters.builder()
        .childNodeID(infraStepNodeUuid)
        .environmentRef(ParameterField.createValueField(infrastructure.getEnvironmentRef()))
        .ref(ParameterField.createValueField(infrastructure.getIdentifier()))
        .build();
  }

  private YamlField constructResourceConstraintYamlField(YamlNode infraNode) {
    final String resourceUnit = "<+INFRA_KEY>";
    JsonNode resourceConstraintJsonNode = ResourceConstraintUtility.getResourceConstraintJsonNode(resourceUnit);
    return new YamlField("step", new YamlNode("step", resourceConstraintJsonNode, infraNode.getParentNode()));
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaDataToExecution(
      YamlNode currentNode, KryoSerializer kryoSerializer) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (currentNode != null) {
      YamlField siblingField = currentNode.nextSiblingNodeFromParentObject("execution");
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        adviserObtainments.add(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    OnSuccessAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
                .build());
      }
    }
    adviserObtainments.add(AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build());
    return adviserObtainments;
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaDataToResourceConstraint(
      YamlField resourceConstraintField, KryoSerializer kryoSerializer) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    adviserObtainments.add(
        AdviserObtainment.newBuilder()
            .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                OnSuccessAdviserParameters.builder().nextNodeId(resourceConstraintField.getNode().getUuid()).build())))
            .build());
    adviserObtainments.add(AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build());
    return adviserObtainments;
  }

  /**
   * @param actualInfraConfig infrastructure after resolving useFromStage.
   * @param infraStepNodeUuid infraStep node uuid
   * @return step params
   */
  public InfraSectionStepParameters getInfraSectionStepParams(
      PipelineInfrastructure actualInfraConfig, String infraStepNodeUuid) {
    return InfraSectionStepParameters.builder()
        .environmentRef(actualInfraConfig.getEnvironmentRef())
        .environment(actualInfraConfig.getEnvironment())
        .childNodeID(infraStepNodeUuid)
        .build();
  }

  /**
   * Method returns actual InfraStructure object by resolving useFromStage if present.
   */

  public LinkedHashMap<String, PlanCreationResponse> createPlanForProvisioner(PipelineInfrastructure actualInfraConfig,
      YamlField infraField, String infraStepNodeId, KryoSerializer kryoSerializer) {
    if (!isProvisionerConfigured(actualInfraConfig)) {
      return new LinkedHashMap<>();
    }
    validateProvisionerConfig(actualInfraConfig.getInfrastructureDefinition().getProvisioner());

    YamlField infraDefField = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
    YamlField provisionerYamlField = infraDefField.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
    YamlField stepsYamlField = provisionerYamlField.getNode().getField(YAMLFieldNameConstants.STEPS);

    // Add each step dependency
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
    stepsYamlFieldMap.put(stepsYamlField.getNode().getUuid(), stepsYamlField);
    responseMap.put(stepsYamlField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(stepsYamlFieldMap)).build());

    // Add provisioner Node
    PlanNode provisionerPlanNode = getProvisionerPlanNode(
        provisionerYamlField, stepsYamlField.getNode().getUuid(), infraStepNodeId, kryoSerializer);
    responseMap.put(provisionerPlanNode.getUuid(),
        PlanCreationResponse.builder().node(provisionerPlanNode.getUuid(), provisionerPlanNode).build());

    return responseMap;
  }

  private static void validateProvisionerConfig(@NotNull ExecutionElementConfig provisioner) {
    if (EmptyPredicate.isEmpty(provisioner.getSteps())) {
      throw new InvalidRequestException("Steps under Provisioner Section can't be empty");
    }
  }

  public boolean isProvisionerConfigured(PipelineInfrastructure actualInfraConfig) {
    if (actualInfraConfig.getInfrastructureDefinition() == null) {
      throw new InvalidRequestException("Infrastructure Definition can not be empty, please add it and try again");
    }
    return actualInfraConfig.getInfrastructureDefinition().getProvisioner() != null;
  }

  private PlanNode getProvisionerPlanNode(
      YamlField provisionerYamlField, String childNodeId, String infraStepNodeId, KryoSerializer kryoSerializer) {
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childNodeId).logMessage("Provisioner Section").build();
    return PlanNode.builder()
        .uuid(provisionerYamlField.getNode().getUuid())
        .identifier(YAMLFieldNameConstants.PROVISIONER)
        .stepType(InfrastructureProvisionerStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.PROVISIONER)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(
                    kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(infraStepNodeId).build())))
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  public String getProvisionerNodeId(YamlField infraField) {
    YamlField infraDefField = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
    YamlField provisionerYamlField = infraDefField.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
    return provisionerYamlField.getNode().getUuid();
  }

  public boolean areSimultaneousDeploymentsAllowed(boolean allowSimultaneousDeployments, YamlField rcField) {
    return !(allowSimultaneousDeployments || rcField == null);
  }

  public static PlanNode getInfraDefPlanNode(YamlField infrastructureDefField, String childNodeId) {
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childNodeId).logMessage("Infra Definition").build();
    return PlanNode.builder()
        .uuid(infrastructureDefField.getNode().getUuid())
        .identifier(YamlTypes.INFRASTRUCTURE_DEF)
        .stepType(InfrastructureDefinitionStep.STEP_TYPE)
        .name(YamlTypes.INFRASTRUCTURE_DEF)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }
}
