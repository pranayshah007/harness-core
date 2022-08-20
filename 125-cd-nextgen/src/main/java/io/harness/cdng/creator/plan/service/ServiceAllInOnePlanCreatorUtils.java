package io.harness.cdng.creator.plan.service;

import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceDefinitionStepParameters;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceSpecStepParameters;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceStepV3Parameters;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceAllInOnePlanCreatorUtils {
  /**
   * Add the following plan nodes
   * ServiceStepV3
   *  ServiceDefinition
   *    serviceSpec (child = artifactsV2 )
   *      artifactsV2
   *
   */
  LinkedHashMap<String, PlanCreationResponse> addServiceNodeWithExpression(
      KryoSerializer kryoSerializer, ServicePlanCreatorV2Config config, String envNodeId, String service_spec_node_id) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    final ServiceStepV3Parameters stepParameters =
        ServiceStepV3Parameters.builder().serviceRef(config.getIdentifier()).inputs(config.getInputs()).build();
    final String serviceDefinitionNodeUuid = UUIDGenerator.generateUuid();
    final String serviceStepUuid = "service-" + config.getUuid();

    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceStepUuid)
            .stepType(ServiceStepV3.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_NODE_NAME)
            .identifier(YamlTypes.SERVICE_ENTITY)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                        OnSuccessAdviserParameters.builder().nextNodeId(serviceDefinitionNodeUuid).build())))
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());

    addServiceDefinitionNode(planCreationResponseMap, serviceDefinitionNodeUuid, envNodeId, service_spec_node_id);

    return planCreationResponseMap;
  }

  private void addServiceDefinitionNode(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      String serviceDefinitionNodeId, String envNodeId, String service_spec_node_id) {
    // Add artifact node
    final PlanNode artifactsNode =
        PlanNode.builder()
            .uuid("artifacts-" + UUIDGenerator.generateUuid())
            .stepType(ArtifactsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.ARTIFACT_NODE_NAME)
            .identifier(YamlTypes.ARTIFACT_LIST_CONFIG)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        artifactsNode.getUuid(), PlanCreationResponse.builder().planNode(artifactsNode).build());

    // Add manifests node
    final PlanNode manifestsNode =
        PlanNode.builder()
            .uuid("manifests-" + UUIDGenerator.generateUuid())
            .stepType(ManifestsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.MANIFEST_NODE_NAME)
            .identifier(YamlTypes.MANIFEST_CONFIG)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());

    // Add config files node
    final PlanNode configFilesNode =
        PlanNode.builder()
            .uuid("configFiles-" + UUIDGenerator.generateUuid())
            .stepType(ConfigFilesStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.CONFIG_FILES_NODE_NAME)
            .identifier(YamlTypes.CONFIG_FILES)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        configFilesNode.getUuid(), PlanCreationResponse.builder().planNode(configFilesNode).build());

    // Add service definition node
    final ServiceDefinitionStepParameters stepParameters =
        ServiceDefinitionStepParameters.builder().childNodeId(envNodeId).build();
    PlanNode serviceDefNode =
        PlanNode.builder()
            .uuid(serviceDefinitionNodeId)
            .stepType(ServiceDefinitionStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_DEFINITION_NODE_NAME)
            .identifier(YamlTypes.SERVICE_DEFINITION)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        serviceDefinitionNodeId, PlanCreationResponse.builder().planNode(serviceDefNode).build());

    // Add service spec node
    final ServiceSpecStepParameters serviceSpecStepParameters =
        ServiceSpecStepParameters.builder()
            .childrenNodeIds(List.of(artifactsNode.getUuid(), manifestsNode.getUuid(), configFilesNode.getUuid()))
            .build();
    final PlanNode serviceSpecNode =
        PlanNode.builder()
            .uuid(service_spec_node_id)
            .stepType(ServiceSpecStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_SPEC_NODE_NAME)
            .identifier(YamlTypes.SERVICE_SPEC)
            .stepParameters(serviceSpecStepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(false)
            .build();
    planCreationResponseMap.put(
        serviceSpecNode.getUuid(), PlanCreationResponse.builder().planNode(serviceSpecNode).build());
  }
}
