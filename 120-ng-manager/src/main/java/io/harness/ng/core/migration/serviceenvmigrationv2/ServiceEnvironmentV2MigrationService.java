package io.harness.ng.core.migration.serviceenvmigrationv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;

import static java.lang.String.format;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageRequestDto;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ServiceEnvironmentV2MigrationService {
  @OwnedBy(CDP)
  @Data
  @Builder
  public static class StageSchema {
    @JsonProperty("stage") private DeploymentStageNode stageNode;
  }

  @Inject private ServiceEntityService serviceEntityService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Inject private EnvironmentValidationHelper environmentValidationHelper;
  @Inject private AccessControlClient accessControlClient;

  private StageSchema getStageSchema(String yaml) {
    try {
      return YamlPipelineUtils.read(yaml, StageSchema.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("not able to parse stage yaml due to " + ex.getMessage());
    }
  }

  private String toYaml(StageSchema stageSchema) {
    try {
      return YamlPipelineUtils.getYamlString(stageSchema);
    } catch (IOException e) {
      throw new InvalidRequestException("not able to parse stage yaml due to " + e.getMessage());
    }
  }

  public String createServiceInfraV2(StageRequestDto stageRequestDto, String accountId) {
    if (isEmpty(stageRequestDto.getYaml())) {
      throw new InvalidRequestException("stage yaml can't be empty");
    }
    StageSchema stageSchema = getStageSchema(stageRequestDto.getYaml());
    DeploymentStageConfig deploymentStageConfig = stageSchema.getStageNode().getDeploymentStageConfig();
    YamlField stageField = getStageYamlField(stageRequestDto.getYaml());

    validateOldService(deploymentStageConfig);
    validateOldInfra(deploymentStageConfig);

    ServiceConfig serviceConfig = deploymentStageConfig.getServiceConfig();
    validateParameterRef(serviceConfig.getServiceRef(), "serviceRef");
    String serviceRef = serviceConfig.getServiceRef().getValue();

    PipelineInfrastructure infrastructure = deploymentStageConfig.getInfrastructure();
    validateParameterRef(infrastructure.getEnvironmentRef(), "environmentRef");
    String environmentRef = infrastructure.getEnvironmentRef().getValue();

    environmentValidationHelper.checkThatEnvExists(
        accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier(), environmentRef);

    ServiceEntity existedServiceEntity = getServiceV1Entity(
        accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier(), serviceRef);

    addServiceV2YamlInServiceEntity(existedServiceEntity, serviceConfig, stageField);

    checkInfrastructureEntityExistence(accountId, stageRequestDto.getOrgIdentifier(),
        stageRequestDto.getProjectIdentifier(), environmentRef, stageRequestDto.getInfraIdentifier());
    InfrastructureEntity infrastructureEntity =
        createInfraEntity(infrastructure, stageRequestDto, serviceConfig, accountId, stageField);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier()),
        Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier()),
        Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_UPDATE_PERMISSION,
        "unable to create infrastructure because of permission");

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_UPDATE_PERMISSION,
        "unable to update service because of permission");

    InfrastructureEntity createdInfrastructure = infrastructureEntityService.create(infrastructureEntity);
    ServiceEntity updatedService = serviceEntityService.update(existedServiceEntity);

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode serviceNode = objectMapper.createObjectNode();
    serviceNode.put("serviceRef", updatedService.getIdentifier());

    JsonNode infraNode = objectMapper.createObjectNode().put("identifier", createdInfrastructure.getIdentifier());
    ArrayNode infraArrayNode = objectMapper.createArrayNode().add(infraNode);
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("environmentRef", createdInfrastructure.getEnvIdentifier());
    envNode.put("deployToAll", false);
    envNode.set("infrastructureDefinitions", infraArrayNode);

    ObjectNode stageSpecNode = (ObjectNode) stageField.getNode().getField("spec").getNode().getCurrJsonNode();
    stageSpecNode.remove("serviceConfig");
    stageSpecNode.remove("infrastructure");
    stageSpecNode.put("deploymentType", updatedService.getType().getYamlName());
    stageSpecNode.set("service", serviceNode);
    stageSpecNode.set("environment", envNode);

    return YamlPipelineUtils.writeYamlString(stageField.getNode().getCurrJsonNode());
  }

  private YamlField getStageYamlField(String yaml) {
    try {
      return YamlUtils.readTree(yaml).getNode().getField("stage");
    } catch (Exception e) {
      throw new InvalidRequestException(format("not able to parse stage yaml because of error: %s", e.getMessage()));
    }
  }

  private InfrastructureEntity createInfraEntity(PipelineInfrastructure infrastructure, StageRequestDto stageRequestDto,
      ServiceConfig serviceConfig, String accountId, YamlField stageField) {
    YamlField infrastructureField = stageField.getNode().getField("spec").getNode().getField("infrastructure");
    YamlField infrastructureSpecField =
        infrastructureField.getNode().getField("infrastructureDefinition").getNode().getField("spec");

    ObjectMapper objectMapper = new ObjectMapper();

    ObjectNode parentInfraNode =
        objectMapper.createObjectNode().set("infrastructureDefinition", objectMapper.createObjectNode());
    ObjectNode infraNode = (ObjectNode) parentInfraNode.get("infrastructureDefinition");
    infraNode.put("identifier", stageRequestDto.getInfraIdentifier());
    infraNode.put("name", stageRequestDto.getInfraIdentifier()); // name is same as identifier as of now
    infraNode.put("orgIdentifier", stageRequestDto.getOrgIdentifier());
    infraNode.put("projectIdentifier", stageRequestDto.getProjectIdentifier());
    infraNode.put("environmentRef", infrastructure.getEnvironmentRef().getValue());
    infraNode.put("deploymentType", serviceConfig.getServiceDefinition().getType().getYamlName());
    infraNode.put("type", infrastructure.getInfrastructureDefinition().getType().getDisplayName());
    infraNode.put("allowSimultaneousDeployments",
        isAllowSimultaneousDeployments(infrastructure.getAllowSimultaneousDeployments()));
    infraNode.set("spec", infrastructureSpecField.getNode().getCurrJsonNode());

    InfrastructureRequestDTO infrastructureRequestDTO =
        InfrastructureRequestDTO.builder()
            .identifier(stageRequestDto.getInfraIdentifier())
            .name(stageRequestDto.getInfraIdentifier())
            .orgIdentifier(stageRequestDto.getOrgIdentifier())
            .projectIdentifier(stageRequestDto.getProjectIdentifier())
            .environmentRef(infrastructure.getEnvironmentRef().getValue())
            .type(infrastructure.getInfrastructureDefinition().getType())
            .yaml(YamlPipelineUtils.writeYamlString(parentInfraNode))
            .build();
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    if (infrastructureEntity.getDeploymentType() == ServiceDefinitionType.CUSTOM_DEPLOYMENT
        && infrastructureEntity.getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
      if (customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)) {
        throw new InvalidRequestException(
            "Infrastructure yaml is not valid, template variables and infra variables doesn't match");
      }
    }
    return infrastructureEntity;
  }

  private ServiceEntity addServiceV2YamlInServiceEntity(
      ServiceEntity serviceEntity, ServiceConfig serviceConfig, YamlField stageField) {
    YamlField serviceConfigField = stageField.getNode().getField("spec").getNode().getField("serviceConfig");
    YamlField serviceDefinitionField = serviceConfigField.getNode().getField("serviceDefinition");

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode parentServiceNode = objectMapper.createObjectNode().set("service", objectMapper.createObjectNode());
    ObjectNode serviceNode = (ObjectNode) parentServiceNode.get("service");
    serviceNode.put("name", serviceEntity.getName());
    serviceNode.put("identifier", serviceEntity.getIdentifier());
    serviceNode.put("description", serviceEntity.getDescription());
    serviceNode.put("name", serviceEntity.getName());
    serviceNode.putPOJO("tags", TagMapper.convertToMap(serviceEntity.getTags()));
    serviceNode.set("serviceDefinition", serviceDefinitionField.getNode().getCurrJsonNode());

    serviceEntity.setYaml(YamlPipelineUtils.writeYamlString(parentServiceNode));
    serviceEntity.setType(serviceConfig.getServiceDefinition().getType());
    // gitops is not considered here as of now

    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
    if (ngServiceConfig == null) {
      throw new InvalidRequestException("not able to parse generated yaml for service of type v2");
    }
    return serviceEntity;
  }

  private ServiceEntity getServiceV1Entity(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    Optional<ServiceEntity> optionalService =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (optionalService.isPresent()) {
      ServiceEntity serviceEntity = optionalService.get();
      try {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        if (ngServiceConfig != null
            && (isGitOpsEnabled(ngServiceConfig.getNgServiceV2InfoConfig())
                || ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition() != null)) {
          throw new InvalidRequestException(
              format("a service of type v2 already exists with identifier: %s", serviceIdentifier));
        }
      } catch (Exception e) {
        throw new InvalidRequestException(format("not able to parse service due to %s", e.getMessage()));
      }
      return serviceEntity;
    }
    throw new InvalidRequestException(
        format("a service of type v1 doesn't exist with identifier: %s", serviceIdentifier));
  }

  private void checkInfrastructureEntityExistence(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String infraIdentifier) {
    Optional<InfrastructureEntity> optionalInfra =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    if (optionalInfra.isPresent()) {
      throw new InvalidRequestException(
          format("an infra of type v2 already exists with identifier: %s", infraIdentifier));
    }
  }

  private boolean isAllowSimultaneousDeployments(ParameterField<Boolean> allowSimultaneousDeployments) {
    if (allowSimultaneousDeployments.getValue() != null) {
      return allowSimultaneousDeployments.getValue();
    }
    return false;
  }

  private boolean isGitOpsEnabled(NGServiceV2InfoConfig ngServiceV2InfoConfig) {
    if (ngServiceV2InfoConfig != null && ngServiceV2InfoConfig.getGitOpsEnabled() != null
        && ngServiceV2InfoConfig.getGitOpsEnabled()) {
      return true;
    }
    return false;
  }

  private void validateParameterRef(ParameterField<String> parameterRef, String parameter) {
    if (parameterRef == null || parameterRef.isExpression() || isEmpty(parameterRef.getValue())) {
      throw new InvalidRequestException(format("either %s is having expressions or it is not present in"
              + "stage yaml or its value is an empty string",
          parameter));
    }
  }

  private void validateOldService(DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getServiceConfig() == null) {
      throw new InvalidRequestException("service of type v1 doesn't exist in stage yaml");
    }
  }

  private void validateOldInfra(DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getInfrastructure() == null) {
      throw new InvalidRequestException("infra of type v1 doesn't exist in stage yaml");
    }
  }
}
