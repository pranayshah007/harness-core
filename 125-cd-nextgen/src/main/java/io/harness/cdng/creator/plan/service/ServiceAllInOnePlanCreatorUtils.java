/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerUtils.SERVICE_OVERRIDE_INPUTS_EXPRESSION;
import static io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerUtils.SERVICE_REF_EXPRESSION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepV2Constants;
import io.harness.cdng.aws.asg.AsgServiceSettingsStep;
import io.harness.cdng.azure.webapp.AzureServiceSettingsStep;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.elastigroup.ElastigroupServiceSettingsStep;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterUtils;
import io.harness.cdng.environment.yaml.EnvironmentInfraUseFromStage;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.hooks.steps.ServiceHooksStep;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters.ServiceStepV3ParametersBuilder;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
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
  public LinkedHashMap<String, PlanCreationResponse> addServiceNode(YamlField specField, KryoSerializer kryoSerializer,
      ServiceYamlV2 serviceYamlV2, EnvironmentYamlV2 environmentYamlV2, String serviceNodeId, String nextNodeId,
      ServiceDefinitionType serviceType, ParameterField<String> envGroupRef, PlanCreationContext ctx) {
    if (isConcreteServiceRefUnavailable(serviceYamlV2) && serviceYamlV2.getUseFromStage() == null) {
      throw new InvalidRequestException("At least one of serviceRef and useFromStage fields is required.");
    }

    if (serviceYamlV2.getServiceRef() != null && isNotBlank(serviceYamlV2.getServiceRef().getValue())
        && serviceYamlV2.getUseFromStage() != null) {
      throw new InvalidRequestException("Only one of serviceRef and useFromStage fields are allowed.");
    }
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final EnvironmentYamlV2 finalEnvironmentYamlV2 = useFromStage(environmentYamlV2)
        ? useEnvironmentYamlFromStage(environmentYamlV2.getUseFromStage(), specField)
        : environmentYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType, ctx);
    ParameterField<Map<String, Object>> serviceOverrideInputs = finalEnvironmentYamlV2.getServiceOverrideInputs();
    if (finalServiceYaml.getServiceRef().isExpression()
        && finalServiceYaml.getServiceRef().getExpressionValue().equals(SERVICE_REF_EXPRESSION)) {
      serviceOverrideInputs =
          ParameterField.createExpressionField(true, SERVICE_OVERRIDE_INPUTS_EXPRESSION, null, false);
    }

    ParameterField<String> infraRef = ParameterField.createValueField(null);
    if (ParameterField.isNotNull(finalEnvironmentYamlV2.getInfrastructureDefinitions())
        && isNotEmpty(finalEnvironmentYamlV2.getInfrastructureDefinitions().getValue())) {
      infraRef = finalEnvironmentYamlV2.getInfrastructureDefinitions().getValue().get(0).getIdentifier();
    } else if (ParameterField.isNotNull(finalEnvironmentYamlV2.getInfrastructureDefinition())) {
      infraRef = finalEnvironmentYamlV2.getInfrastructureDefinition().getValue().getIdentifier();
    }
    final ServiceStepV3ParametersBuilder stepParameters = ServiceStepV3Parameters.builder()
                                                              .serviceRef(finalServiceYaml.getServiceRef())
                                                              .serviceGitBranch(finalServiceYaml.getGitBranch())
                                                              .inputs(finalServiceYaml.getServiceInputs())
                                                              .infraId(infraRef)
                                                              .childrenNodeIds(childrenNodeIds)
                                                              .serviceOverrideInputs(serviceOverrideInputs)
                                                              .deploymentType(serviceType)
                                                              .envRef(finalEnvironmentYamlV2.getEnvironmentRef())
                                                              .envGitBranch(finalEnvironmentYamlV2.getGitBranch())
                                                              .envInputs(finalEnvironmentYamlV2.getEnvironmentInputs())
                                                              .envGroupRef(envGroupRef);

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters.build());
  }

  public LinkedHashMap<String, PlanCreationResponse> addServiceNodeForGitOpsEnvGroup(YamlField specField,
      KryoSerializer kryoSerializer, ServiceYamlV2 serviceYamlV2, EnvironmentGroupYaml environmentGroupYaml,
      String serviceNodeId, String nextNodeId, ServiceDefinitionType serviceType, PlanCreationContext ctx) {
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType, ctx);
    ServiceStepV3ParametersBuilder stepParameters =
        ServiceStepV3Parameters.builder()
            .serviceRef(finalServiceYaml.getServiceRef())
            .serviceGitBranch(finalServiceYaml.getGitBranch())
            .inputs(finalServiceYaml.getServiceInputs())
            .childrenNodeIds(childrenNodeIds)
            .deploymentType(serviceType)
            .gitOpsMultiSvcEnvEnabled(ParameterField.<Boolean>builder().value(true).build())
            .envGroupRef(environmentGroupYaml.getEnvGroupRef());

    if (EnvironmentInfraFilterUtils.areFiltersPresent(environmentGroupYaml)) {
      stepParameters.environmentGroupYaml(environmentGroupYaml)
          .envToEnvInputs(new HashMap<>())
          .envToSvcOverrideInputs(new HashMap<>());
    } else {
      stepParameters
          .envRefs(environmentGroupYaml.getEnvironments()
                       .getValue()
                       .stream()
                       .map(EnvironmentYamlV2::getEnvironmentRef)
                       .collect(Collectors.toList()))
          .envToEnvInputs(getMergedEnvironmentRuntimeInputs(environmentGroupYaml.getEnvironments().getValue()))
          .envToSvcOverrideInputs(getMergedServiceOverrideInputs(environmentGroupYaml.getEnvironments().getValue()));
    }

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters.build());
  }

  public LinkedHashMap<String, PlanCreationResponse> addServiceNodeForGitOpsEnvironments(YamlField specField,
      KryoSerializer kryoSerializer, ServiceYamlV2 serviceYamlV2, EnvironmentsYaml environmentsYaml,
      String serviceNodeId, String nextNodeId, ServiceDefinitionType serviceType, PlanCreationContext ctx) {
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType, ctx);
    final ServiceStepV3ParametersBuilder stepParameters =
        ServiceStepV3Parameters.builder()
            .serviceRef(finalServiceYaml.getServiceRef())
            .serviceGitBranch(finalServiceYaml.getGitBranch())
            .inputs(finalServiceYaml.getServiceInputs())
            .childrenNodeIds(childrenNodeIds)
            .deploymentType(serviceType)
            .gitOpsMultiSvcEnvEnabled(ParameterField.<Boolean>builder().value(true).build());

    if (EnvironmentInfraFilterUtils.areFiltersPresent(environmentsYaml)) {
      stepParameters.environmentsYaml(environmentsYaml)
          .envToEnvInputs(new HashMap<>())
          .envToSvcOverrideInputs(new HashMap<>());
    } else {
      stepParameters
          .envRefs(environmentsYaml.getValues()
                       .getValue()
                       .stream()
                       .map(EnvironmentYamlV2::getEnvironmentRef)
                       .collect(Collectors.toList()))
          .envToEnvInputs(getMergedEnvironmentRuntimeInputs(environmentsYaml.getValues().getValue()))
          .envToSvcOverrideInputs(getMergedServiceOverrideInputs(environmentsYaml.getValues().getValue()));
    }

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters.build());
  }

  private static LinkedHashMap<String, PlanCreationResponse> createPlanNode(KryoSerializer kryoSerializer,
      String serviceNodeId, String nextNodeId, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      ServiceStepV3Parameters stepParameters) {
    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceNodeId)
            .stepType(ServiceStepV3Constants.STEP_TYPE)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
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

  public static boolean useFromStage(ServiceYamlV2 serviceYamlV2) {
    return serviceYamlV2.getUseFromStage() != null && serviceYamlV2.getUseFromStage().getStage() != null;
  }

  private List<String> addChildrenNodes(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      ServiceDefinitionType serviceType, PlanCreationContext ctx) {
    final List<String> nodeIds = new ArrayList<>();
    // Add artifacts node
    EmptyStepParameters emptyStepParameters = new EmptyStepParameters();
    StepUtils.appendDelegateSelectors(emptyStepParameters, ctx);
    final PlanNode artifactsNode =
        PlanNode.builder()
            .uuid("artifacts-" + UUIDGenerator.generateUuid())
            .stepType(ArtifactsStepV2Constants.STEP_TYPE)
            .name(PlanCreatorConstants.ARTIFACTS_NODE_NAME)
            .identifier(YamlTypes.ARTIFACT_LIST_CONFIG)
            .stepParameters(emptyStepParameters)
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
    PlanNode manifestsNode = getManifestsNode();
    planCreationResponseMap.put(
        manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());
    nodeIds.add(manifestsNode.getUuid());

    // Add configFiles node
    PlanNode configFilesNode = getConfigFilesNode();
    planCreationResponseMap.put(
        configFilesNode.getUuid(), PlanCreationResponse.builder().planNode(configFilesNode).build());
    nodeIds.add(configFilesNode.getUuid());

    // Add serviceHooks node
    final PlanNode serviceHooksNode =
        PlanNode.builder()
            .uuid("hooks-" + UUIDGenerator.generateUuid())
            .stepType(ServiceHooksStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_HOOKS_NODE_NAME)
            .identifier(YamlTypes.SERVICE_HOOKS)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    nodeIds.add(serviceHooksNode.getUuid());
    planCreationResponseMap.put(
        serviceHooksNode.getUuid(), PlanCreationResponse.builder().planNode(serviceHooksNode).build());

    // Add Azure settings node
    if (ServiceDefinitionType.AZURE_WEBAPP == serviceType) {
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
      nodeIds.add(azureSettingsNode.getUuid());
      planCreationResponseMap.put(
          azureSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(azureSettingsNode).build());
    }

    // Add Elastigroup settings node
    if (ServiceDefinitionType.ELASTIGROUP == serviceType) {
      PlanNode elastigroupSettingsNode =
          PlanNode.builder()
              .uuid("elastigroup-settings-" + UUIDGenerator.generateUuid())
              .stepType(ElastigroupServiceSettingsStep.STEP_TYPE)
              .name(PlanCreatorConstants.ELASTIGROUP_SERVICE_SETTINGS_NODE)
              .identifier(YamlTypes.ELASTIGROUP_SERVICE_SETTINGS_STEP)
              .stepParameters(new EmptyStepParameters())
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                      .build())
              .skipExpressionChain(true)
              .build();
      nodeIds.add(elastigroupSettingsNode.getUuid());
      planCreationResponseMap.put(
          elastigroupSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(elastigroupSettingsNode).build());
    }

    // Add ASG settings node
    if (ServiceDefinitionType.ASG == serviceType) {
      PlanNode asgSettingsNode =
          PlanNode.builder()
              .uuid("asg-settings-" + UUIDGenerator.generateUuid())
              .stepType(AsgServiceSettingsStep.STEP_TYPE)
              .name(PlanCreatorConstants.ASG_SERVICE_SETTINGS_NODE)
              .identifier(YamlTypes.ASG_SERVICE_SETTINGS_STEP)
              .stepParameters(new EmptyStepParameters())
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                      .build())
              .skipExpressionChain(true)
              .build();
      nodeIds.add(asgSettingsNode.getUuid());
      planCreationResponseMap.put(
          asgSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(asgSettingsNode).build());
    }

    return nodeIds;
  }

  private Map<String, ParameterField<Map<String, Object>>> getMergedEnvironmentRuntimeInputs(
      List<EnvironmentYamlV2> envYamlV2List) {
    Map<String, ParameterField<Map<String, Object>>> mergedEnvironmentInputs = new HashMap<>();
    for (EnvironmentYamlV2 environmentYamlV2 : envYamlV2List) {
      ParameterField<Map<String, Object>> environmentInputs = environmentYamlV2.getEnvironmentInputs();
      if (environmentInputs != null) {
        mergedEnvironmentInputs.put(environmentYamlV2.getEnvironmentRef().getValue(), environmentInputs);
      }
    }
    return mergedEnvironmentInputs;
  }

  private Map<String, ParameterField<Map<String, Object>>> getMergedServiceOverrideInputs(
      List<EnvironmentYamlV2> envYamlV2List) {
    Map<String, ParameterField<Map<String, Object>>> mergedServiceOverrideInputs = new HashMap<>();
    for (EnvironmentYamlV2 environmentYamlV2 : envYamlV2List) {
      ParameterField<Map<String, Object>> serviceOverrideInputs = environmentYamlV2.getServiceOverrideInputs();
      if (serviceOverrideInputs != null) {
        mergedServiceOverrideInputs.put(environmentYamlV2.getEnvironmentRef().getValue(), serviceOverrideInputs);
      }
    }
    return mergedServiceOverrideInputs;
  }

  public static ServiceYamlV2 useServiceYamlFromStage(
      @NotNull ServiceUseFromStageV2 useFromStage, YamlField specField) {
    final YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    String stage = useFromStage.getStage();
    if (stage.isBlank()) {
      throw new InvalidRequestException("Stage identifier is empty in useFromStage");
    }

    try {
      YamlField propagatedFromStageConfig = PlanCreatorUtils.getStageConfig(serviceField, stage);
      if (propagatedFromStageConfig == null) {
        throw new InvalidArgumentsException(
            "Stage with identifier [" + stage + "] given for service propagation does not exist.");
      }

      DeploymentStageNode stageElementConfig =
          YamlUtils.read(propagatedFromStageConfig.getNode().toString(), DeploymentStageNode.class);
      DeploymentStageConfig deploymentStage = stageElementConfig.getDeploymentStageConfig();
      if (deploymentStage != null) {
        if (deploymentStage.getService() != null && useFromStage(deploymentStage.getService())) {
          throw new InvalidArgumentsException("Invalid identifier [" + stage
              + "] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
        }

        if (deploymentStage.getService() == null) {
          if (deploymentStage.getServices() != null) {
            throw new InvalidRequestException(
                "Propagate from stage is not supported with multi service deployments, hence not possible to propagate service from that stage");
          }
          throw new InvalidRequestException(String.format(
              "Could not find service in stage [%s], hence not possible to propagate service from that stage", stage));
        }

        if (deploymentStage.getDeploymentType() != null && isNotBlank(deploymentStage.getDeploymentType().getYamlName())
            && specField.getNode().getField("deploymentType").getNode() != null) {
          if (!deploymentStage.getDeploymentType().getYamlName().equals(
                  specField.getNode().getField("deploymentType").getNode().asText())) {
            throw new InvalidRequestException(String.format(
                "Deployment type: [%s] of stage: [%s] does not match with deployment type: [%s] of stage: [%s] from which service propagation is configured",
                specField.getNode().getField("deploymentType").getNode().asText(),
                specField.getNode().getParentNode().getIdentifier(), deploymentStage.getDeploymentType().getYamlName(),
                stage));
          }
        }

        return deploymentStage.getService();
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }

  private boolean isConcreteServiceRefUnavailable(@NonNull ServiceYamlV2 serviceYamlV2) {
    if (serviceYamlV2.getServiceRef() != null) {
      if (serviceYamlV2.getServiceRef().isExpression()) {
        return NGExpressionUtils.matchesRawInputSetPatternV2(serviceYamlV2.getServiceRef().getExpressionValue());
      } else {
        return isBlank(serviceYamlV2.getServiceRef().getValue());
      }
    }
    return true;
  }

  public static boolean useFromStage(EnvironmentYamlV2 environmentYamlV2) {
    return environmentYamlV2.getUseFromStage() != null && environmentYamlV2.getUseFromStage().getStage() != null;
  }

  public static EnvironmentYamlV2 useEnvironmentYamlFromStage(
      @NotNull EnvironmentInfraUseFromStage useFromStage, YamlField specField) {
    final YamlField environmentField = specField.getNode().getField(YamlTypes.ENVIRONMENT_YAML);
    String stage = useFromStage.getStage();
    if (stage.isBlank()) {
      throw new InvalidRequestException("Stage identifier is empty in useFromStage");
    }

    try {
      YamlField propagatedFromStageConfig = PlanCreatorUtils.getStageConfig(environmentField, stage);
      if (propagatedFromStageConfig == null) {
        throw new InvalidArgumentsException(
            "Stage with identifier [" + stage + "] given for environment propagation does not exist.");
      }

      DeploymentStageNode stageElementConfig =
          YamlUtils.read(propagatedFromStageConfig.getNode().toString(), DeploymentStageNode.class);
      DeploymentStageConfig deploymentStage = stageElementConfig.getDeploymentStageConfig();
      if (deploymentStage != null) {
        validateEnvironmentInDeploymentStageConfig(deploymentStage, stage, specField);
        return deploymentStage.getEnvironment();
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }

  private void validateEnvironmentInDeploymentStageConfig(
      DeploymentStageConfig deploymentStage, String stage, YamlField specField) {
    if (deploymentStage.getEnvironment() != null && useFromStage(deploymentStage.getEnvironment())) {
      throw new InvalidArgumentsException("Invalid identifier [" + stage
          + "] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
    }

    if (deploymentStage.getEnvironment() == null) {
      if (deploymentStage.getEnvironments() != null) {
        throw new InvalidRequestException(
            "Propagate from stage is not supported with multi environment deployments, hence not possible to propagate environment from that stage");
      }
      throw new InvalidRequestException(String.format(
          "Could not find environment in stage [%s], hence not possible to propagate environment from that stage",
          stage));
    }

    if (deploymentStage.getEnvironmentGroup() != null) {
      throw new InvalidRequestException(
          "Propagate from stage is not supported with environment group deployments, hence not possible to propagate environment from that stage");
    }

    if (deploymentStage.getDeploymentType() != null && isNotBlank(deploymentStage.getDeploymentType().getYamlName())
        && specField.getNode().getField("deploymentType").getNode() != null) {
      if (!deploymentStage.getDeploymentType().getYamlName().equals(
              specField.getNode().getField("deploymentType").getNode().asText())) {
        throw new InvalidRequestException(String.format(
            "Deployment type: [%s] of stage: [%s] does not match with deployment type: [%s] of stage: [%s] from which environment propagation is configured",
            specField.getNode().getField("deploymentType").getNode().asText(),
            specField.getNode().getParentNode().getIdentifier(), deploymentStage.getDeploymentType().getYamlName(),
            stage));
      }
    }
  }

  public PlanNode getManifestsNode() {
    return PlanNode.builder()
        .uuid("manifests-" + UUIDGenerator.generateUuid())
        .stepType(ManifestsStepV2.STEP_TYPE)
        .name(PlanCreatorConstants.MANIFESTS_NODE_NAME)
        .identifier(YamlTypes.MANIFEST_LIST_CONFIG)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  public PlanNode getConfigFilesNode() {
    return PlanNode.builder()
        .uuid("configFiles-" + UUIDGenerator.generateUuid())
        .stepType(ConfigFilesStepV2.STEP_TYPE)
        .name(PlanCreatorConstants.CONFIG_FILES_NODE_NAME)
        .identifier(YamlTypes.CONFIG_FILES)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }
}
