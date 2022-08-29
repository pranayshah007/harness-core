package io.harness.cdng.creator.plan.service;

import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.azure.webapp.AzureServiceSettingsStep;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceAllInOnePlanCreatorUtils {
  /**
   * Add the following plan nodes
   * ServiceStepV3 ( 3 children )
   *      artifactsV2
   *      manifests
   *      config files
   *      azure settings
   */
  public LinkedHashMap<String, PlanCreationResponse> addServiceNode(KryoSerializer kryoSerializer,
      ServiceYamlV2 serviceYamlV2, EnvironmentYamlV2 environmentYamlV2, String serviceNodeId, String nextNodeId,
      ServiceDefinitionType serviceType) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType);
    final ServiceStepV3Parameters stepParameters = ServiceStepV3Parameters.builder()
                                                       .serviceRef(serviceYamlV2.getServiceRef())
                                                       .inputs(serviceYamlV2.getServiceInputs())
                                                       .envRef(environmentYamlV2.getEnvironmentRef())
                                                       .envInputs(environmentYamlV2.getEnvironmentInputs())
                                                       .childrenNodeIds(childrenNodeIds)
                                                       .build();

    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceNodeId)
            .stepType(ServiceStepV3.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_NODE_NAME)
            .identifier(YamlTypes.SERVICE_ENTITY)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeId).build())))
                    .build())
            .skipExpressionChain(true)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());

    return planCreationResponseMap;
  }

  private List<String> addChildrenNodes(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, ServiceDefinitionType serviceType) {
    final List<String> nodeIds = new ArrayList<>();

    // Add artifacts node
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
    nodeIds.add(artifactsNode.getUuid());
    planCreationResponseMap.put(
        artifactsNode.getUuid(), PlanCreationResponse.builder().planNode(artifactsNode).build());

    // Add manifests node
    final PlanNode manifestsNode =
        PlanNode.builder()
            .uuid("manifests-" + UUIDGenerator.generateUuid())
            .stepType(ManifestsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.MANIFESTS_NODE_NAME)
            .identifier(YamlTypes.MANIFEST_LIST_CONFIG)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    nodeIds.add(manifestsNode.getUuid());
    planCreationResponseMap.put(
        manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());

    // Add configFiles node
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
    nodeIds.add(configFilesNode.getUuid());
    planCreationResponseMap.put(
        configFilesNode.getUuid(), PlanCreationResponse.builder().planNode(configFilesNode).build());

    // Add Azure settings node
    if (serviceType == ServiceDefinitionType.AZURE_WEBAPP) {
      PlanNode azureSettingsNode =
          PlanNode.builder()
              .uuid("azure-settings-" + UUIDGenerator.generateUuid())
              .stepType(AzureServiceSettingsStep.STEP_TYPE)
              .name(PlanCreatorConstants.CONNECTION_STRINGS)
              .identifier(YamlTypes.AZURE_SERVICE_SETTINGS_STEP)
              .stepParameters(new EmptyStepParameters())
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                      .build())
              .skipExpressionChain(true)
              .build();
      nodeIds.add(configFilesNode.getUuid());
      planCreationResponseMap.put(
          azureSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(azureSettingsNode).build());
    }

    return nodeIds;
  }
}
