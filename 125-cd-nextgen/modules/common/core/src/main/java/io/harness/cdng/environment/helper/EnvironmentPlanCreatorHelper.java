/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructurePlanCreatorHelper;
import io.harness.cdng.infra.steps.EnvironmentStep;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class EnvironmentPlanCreatorHelper {
  public PlanNode getPlanNode(
      String envNodeUuid, StepParameters infraSectionStepParameters, ByteString advisorParameters) {
    return PlanNode.builder()
        .uuid(envNodeUuid)
        .stepType(EnvironmentStep.STEP_TYPE)
        .name(PlanCreatorConstants.ENVIRONMENT_NODE_NAME)
        .identifier(YamlTypes.ENVIRONMENT_YAML)
        .stepParameters(infraSectionStepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(advisorParameters)
                .build())
        .skipExpressionChain(false)
        .build();
  }

  public EnvironmentPlanCreatorConfig getResolvedEnvRefs(PlanCreationContext ctx, EnvironmentYamlV2 environmentV2,
      boolean gitOpsEnabled, String serviceRef, ServiceOverrideService serviceOverrideService,
      EnvironmentService environmentService, InfrastructureEntityService infrastructure) {
    String accountIdentifier = ctx.getAccountIdentifier();
    String orgIdentifier = ctx.getOrgIdentifier();
    String projectIdentifier = ctx.getProjectIdentifier();

    // TODO: check the case when its a runtime value if its possible for it to have here
    Optional<Environment> environment = environmentService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentV2.getEnvironmentRef().getValue(), false);

    String envIdentifier = environmentV2.getEnvironmentRef().getValue();
    if (environment.isEmpty()) {
      throw new InvalidRequestException(
          String.format("No environment found with %s identifier in %s project in %s org and %s account", envIdentifier,
              projectIdentifier, orgIdentifier, accountIdentifier));
    }

    // Fetch service overrides And resolve inputs for service override
    Optional<NGServiceOverridesEntity> serviceOverridesOptional = serviceOverrideService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentV2.getEnvironmentRef().getValue(), serviceRef);
    NGServiceOverrideConfig serviceOverrideConfig = getNgServiceOverrides(environmentV2, serviceOverridesOptional);

    String originalEnvYaml = environment.get().getYaml();

    // TODO: need to remove this once we have the migration for old env
    if (EmptyPredicate.isEmpty(originalEnvYaml)) {
      try {
        originalEnvYaml = YamlPipelineUtils.getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment.get()));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Unable to convert environment to yaml");
      }
    }

    String mergedEnvYaml = originalEnvYaml;
    if (isNotEmpty(environmentV2.getEnvironmentInputs().getValue())) {
      mergedEnvYaml = mergeEnvironmentInputs(originalEnvYaml, environmentV2.getEnvironmentInputs().getValue());
    }

    if (!gitOpsEnabled) {
      List<InfrastructureConfig> infrastructureConfigs = getInfraStructureConfigList(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentV2, infrastructure);

      if (infrastructureConfigs.size() == 0) {
        throw new InvalidRequestException(String.format(
            "Infrastructure linked with environment %s does not exists", environmentV2.getEnvironmentRef().getValue()));
      }

      if (infrastructureConfigs.size() > 1) {
        throw new InvalidRequestException("Deployment to multiple infrastructures is not supported yet");
      }

      return EnvironmentPlanCreatorConfigMapper.toEnvironmentPlanCreatorConfig(
          mergedEnvYaml, infrastructureConfigs, serviceOverrideConfig);
    } else {
      if (!environmentV2.getDeployToAll().getValue() && isEmpty(environmentV2.getGitOpsClusters().getValue())) {
        throw new InvalidRequestException("List of Gitops clusters must be provided because deployToAll is false");
      }
      return EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitops(
          mergedEnvYaml, environmentV2, serviceOverrideConfig);
    }
  }

  private NGServiceOverrideConfig getNgServiceOverrides(
      EnvironmentYamlV2 environmentV2, Optional<NGServiceOverridesEntity> serviceOverridesOptional) {
    NGServiceOverrideConfig serviceOverrideConfig = NGServiceOverrideConfig.builder().build();
    if (serviceOverridesOptional.isPresent()) {
      NGServiceOverridesEntity serviceOverridesEntity = serviceOverridesOptional.get();
      String mergedYaml = serviceOverridesEntity.getYaml();
      if (isNotEmpty(environmentV2.getServiceOverrideInputs().getValue())) {
        mergedYaml = resolveServiceOverrideInputs(
            serviceOverridesEntity.getYaml(), environmentV2.getServiceOverrideInputs().getValue());
      }
      if (mergedYaml != null) {
        serviceOverrideConfig = ServiceOverridesMapper.toNGServiceOverrideConfig(mergedYaml);
      }
    }
    return serviceOverrideConfig;
  }

  public String resolveServiceOverrideInputs(
      String originalServiceOverrideYaml, Map<String, Object> serviceOverrideInputs) {
    Map<String, Object> serviceOverrideInputYaml = new HashMap<>();
    serviceOverrideInputYaml.put(YamlTypes.SERVICE_OVERRIDE, serviceOverrideInputs);
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalServiceOverrideYaml, YamlPipelineUtils.writeYamlString(serviceOverrideInputYaml), true, true);
  }

  public String mergeEnvironmentInputs(String originalEnvYaml, Map<String, Object> environmentInputs) {
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalEnvYaml, YamlPipelineUtils.writeYamlString(environmentInputYaml), true, true);
  }

  private List<InfrastructureConfig> getInfraStructureConfigList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, EnvironmentYamlV2 environmentV2, InfrastructureEntityService infrastructure) {
    List<InfrastructureEntity> infrastructureEntityList;
    Map<String, Map<String, Object>> refToInputMap = new HashMap<>();
    String envIdentifier = environmentV2.getEnvironmentRef().getValue();
    if (!environmentV2.getDeployToAll().getValue()) {
      List<String> infraIdentifierList = new ArrayList<>();

      if (ParameterField.isNotNull(environmentV2.getInfrastructureDefinitions())) {
        for (InfraStructureDefinitionYaml infraYaml : environmentV2.getInfrastructureDefinitions().getValue()) {
          String ref = infraYaml.getIdentifier().getValue();
          infraIdentifierList.add(ref);
          if (isNotEmpty(infraYaml.getInputs().getValue())) {
            refToInputMap.put(ref, infraYaml.getInputs().getValue());
          }
        }
      } else {
        InfraStructureDefinitionYaml infraYaml = environmentV2.getInfrastructureDefinition().getValue();
        String ref = infraYaml.getIdentifier().getValue();
        infraIdentifierList.add(ref);
        if (isNotEmpty(infraYaml.getInputs().getValue())) {
          refToInputMap.put(ref, infraYaml.getInputs().getValue());
        }
      }
      infrastructureEntityList = infrastructure.getAllInfrastructureFromIdentifierList(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifierList);

    } else {
      if (isNotEmpty(environmentV2.getInfrastructureDefinitions().getValue())
          || ParameterField.isBlank(environmentV2.getInfrastructureDefinition())) {
        throw new InvalidRequestException(String.format("DeployToAll is enabled along with specific Infrastructures %s",
            environmentV2.getInfrastructureDefinitions().getValue()));
      }
      infrastructureEntityList = infrastructure.getAllInfrastructureFromEnvRef(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier);
    }

    return InfrastructurePlanCreatorHelper.getResolvedInfrastructureConfig(infrastructureEntityList, refToInputMap);
  }

  public YamlField fetchEnvironmentPlanCreatorConfigYaml(
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig, YamlField originalEnvironmentField) {
    try {
      String yamlString = YamlPipelineUtils.getYamlString(environmentPlanCreatorConfig);
      YamlField yamlField = YamlUtils.injectUuidInYamlField(yamlString);
      return new YamlField(YamlTypes.ENVIRONMENT_YAML,
          new YamlNode(YamlTypes.ENVIRONMENT_YAML, yamlField.getNode().getCurrJsonNode(),
              originalEnvironmentField.getNode().getParentNode()));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid environment yaml", e);
    }
  }

  public static Map<String, ByteString> prepareMetadata(String environmentUuid, String infraSectionUuid,
      String serviceSpecNodeId, boolean gitOpsEnabled, boolean skipInstances, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();

    metadataDependency.put(YamlTypes.NEXT_UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceSpecNodeId)));
    metadataDependency.put(
        YamlTypes.INFRA_SECTION_UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(infraSectionUuid)));
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(environmentUuid)));
    metadataDependency.put(
        YAMLFieldNameConstants.GITOPS_ENABLED, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(gitOpsEnabled)));
    metadataDependency.put(
        YAMLFieldNameConstants.SKIP_INSTANCES, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(skipInstances)));

    return metadataDependency;
  }

  public void addEnvironmentV2Dependency(Map<String, PlanCreationResponse> planCreationResponseMap,
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig, YamlField originalEnvironmentField,
      boolean gitOpsEnabled, boolean skipInstances, String environmentUuid, String infraSectionUuid,
      String serviceSpecNodeUuid, KryoSerializer kryoSerializer) throws IOException {
    YamlField updatedEnvironmentYamlField =
        fetchEnvironmentPlanCreatorConfigYaml(environmentPlanCreatorConfig, originalEnvironmentField);
    Map<String, YamlField> environmentYamlFieldMap = new HashMap<>();
    environmentYamlFieldMap.put(environmentUuid, updatedEnvironmentYamlField);

    // preparing meta data
    final Dependency envDependency = Dependency.newBuilder()
                                         .putAllMetadata(prepareMetadata(environmentUuid, infraSectionUuid,
                                             serviceSpecNodeUuid, gitOpsEnabled, skipInstances, kryoSerializer))
                                         .build();

    planCreationResponseMap.put(environmentUuid,
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(environmentYamlFieldMap)
                              .toBuilder()
                              .putDependencyMetadata(environmentUuid, envDependency)
                              .build())
            .yamlUpdates(YamlUpdates.newBuilder()
                             .putFqnToYaml(updatedEnvironmentYamlField.getYamlPath(),
                                 YamlUtils.writeYamlString(updatedEnvironmentYamlField).replace("---\n", ""))
                             .build())
            .build());
  }
}