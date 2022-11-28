package io.harness.ng.core.migration.serviceenvmigrationv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.ServiceEnvironmentRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.TemplateObject;
import io.harness.ng.core.refresh.service.EntityRefreshService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
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
import okhttp3.MediaType;
import okhttp3.RequestBody;

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
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private EntityRefreshService entityRefreshService;
  @Inject private TemplateResourceClient templateResourceClient;

  private DeploymentStageConfig getDeploymentStageConfig(String stageYaml) {
    if (isEmpty(stageYaml)) {
      throw new InvalidRequestException("stage yaml can't be empty");
    }
    try {
      return YamlPipelineUtils.read(stageYaml, StageSchema.class).getStageNode().getDeploymentStageConfig();
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

  public String migratePipelineWithServiceEnvV2(ServiceEnvironmentRequestDto requestDto, String accountId) {
    PMSPipelineResponseDTO existingPipeline =  NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(
            requestDto.getPipelineIdentifier(), accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
            null, null, null));
    String pipelineYaml = existingPipeline.getYamlPipeline();
    if(isEmpty(pipelineYaml)) {

    }
    YamlField pipelineYamlField = getYamlField(pipelineYaml, "pipeline");
    ArrayNode stageArrayNode = (ArrayNode) pipelineYamlField.getNode().getField("stages").getNode().getCurrJsonNode();
    if(stageArrayNode.size()<1) {

    }
    for(int currentIndex=0; currentIndex< stageArrayNode.size(); currentIndex++) {
      JsonNode stageNode = stageArrayNode.get(currentIndex);
      YamlNode stageYamlNode = new YamlNode(stageNode);
      Optional<JsonNode> migratedStageNode = migrateStageWithServiceEnvV2(stageYamlNode, accountId, requestDto);
      if(migratedStageNode.isPresent()) {
        stageArrayNode.set(currentIndex, migratedStageNode.get());
      }
    }
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode pipelineParentNode = objectMapper.createObjectNode();
    pipelineParentNode.set("pipeline", pipelineYamlField.getNode().getCurrJsonNode());
    String migratedPipelineYaml = YamlPipelineUtils.writeYamlString(pipelineParentNode);
    migratedPipelineYaml = entityRefreshService.refreshLinkedInputs(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
            migratedPipelineYaml, null);
    if(requestDto.isUpdatePipeline()) {
      NGRestUtils.getResponse(pipelineServiceClient.updatePipeline(null,
              requestDto.getPipelineIdentifier(), accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
               null,null, null, RequestBody.create(MediaType.parse("application/yaml"),migratedPipelineYaml)));
    }
    return migratedPipelineYaml;
  }

  private Optional<JsonNode> migrateStageWithServiceEnvV2(YamlNode stageNode, String accountId, ServiceEnvironmentRequestDto requestDto) {
    boolean isStageTemplatePresent = isStageContainStageTemplate(stageNode);
    try {
      JsonNode stageJsonNode;
      if(isStageTemplatePresent) {
        stageJsonNode = migrateStageWithTemplate(stageNode, accountId, requestDto);
      }
      else {
        stageJsonNode = migrateStage(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
                requestDto.getInfraIdentifier(), stageNode);
      }
      ObjectMapper objectMapper = new ObjectMapper();
      ObjectNode stageParentNode = objectMapper.createObjectNode();
      stageParentNode.set("stage", stageJsonNode);
      return Optional.of(stageParentNode);
    }
    catch(Exception ex ){
      return Optional.empty();
    }
  }


  private JsonNode migrateStageWithTemplate(YamlNode stageNode, String accountId,  ServiceEnvironmentRequestDto requestDto) {
    String stageYaml = YamlPipelineUtils.writeYamlString(stageNode.getCurrJsonNode());
    String resolvedStageYaml = NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId,
            requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
            null, null, null, null, null,
            null, null, null,
            TemplateApplyRequestDTO.builder()
                    .originalEntityYaml(stageYaml)
                    .checkForAccess(true).build())).getMergedPipelineYaml();
    YamlField stageField = getYamlField(stageYaml, "stage");
    YamlField resolvedStageField = getYamlField(resolvedStageYaml, "stage");
    DeploymentStageConfig deploymentStageConfig = getDeploymentStageConfig(resolvedStageYaml);

    ServiceEntity service = migrateService(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
            deploymentStageConfig, resolvedStageField);

    InfrastructureEntity infrastructure = migrateEnv(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
            deploymentStageConfig, resolvedStageField, requestDto.getInfraIdentifier());

    YamlNode templateStageYamlNode = stageField.getNode().getField("template").getNode();
    String templateKey = templateStageYamlNode.getField("templateRef").getNode().getCurrJsonNode().textValue()+"@ "+
            templateStageYamlNode.getField("versionLabel").getNode().getCurrJsonNode().textValue();
    if(!requestDto.getTemplateMap().containsKey(templateKey)) {

    }
    TemplateObject templateObject = requestDto.getTemplateMap().get(templateKey);
    //todo: validateNewTemplate
    ObjectNode specNode = (ObjectNode) templateStageYamlNode.getField("templateInputs").getNode().getField("spec").getNode()
            .getCurrJsonNode();
    ObjectNode stageTemplateNode = (ObjectNode) templateStageYamlNode.getCurrJsonNode();
    stageTemplateNode.put("templateRef", templateObject.getTemplateRef());
    stageTemplateNode.put("versionLabel", templateObject.getVersionLabel());
    migrateStageYaml(service, infrastructure, specNode);
    return stageField.getNode().getCurrJsonNode();
  }

  public JsonNode migrateStage(String accountId, String orgIdentifier,
                             String projectIdentifier, String infraIdentifier, YamlNode stageNode) {
    String stageYaml = YamlPipelineUtils.writeYamlString(stageNode.getCurrJsonNode());
    DeploymentStageConfig deploymentStageConfig = getDeploymentStageConfig(stageYaml);
    YamlField stageField = getYamlField(stageYaml, "stage");

    ServiceEntity service = migrateService(accountId, orgIdentifier, projectIdentifier, deploymentStageConfig,
            stageField);

    InfrastructureEntity infrastructure = migrateEnv(accountId, orgIdentifier, projectIdentifier, deploymentStageConfig,
            stageField, infraIdentifier);

    ObjectNode specNode = (ObjectNode) stageField.getNode().getField("spec").getNode().getCurrJsonNode();
    specNode.put("deploymentType", service.getType().getYamlName());
    migrateStageYaml(service, infrastructure, specNode);

    return stageField.getNode().getCurrJsonNode();
  }

  private void migrateStageYaml(ServiceEntity service, InfrastructureEntity infrastructure, ObjectNode specNode) {
    ObjectMapper objectMapper = new ObjectMapper();
    specNode.remove("serviceConfig");
    specNode.remove("infrastructure");
    specNode.set("service", getServiceV2Node(objectMapper, service));
    specNode.set("environment", getEnvironmentV2Node(objectMapper, infrastructure));
  }

  private ServiceEntity migrateService(String accountId, String orgIdentifier, String projectIdentifier,
                                       DeploymentStageConfig deploymentStageConfig, YamlField stageField) {
    String serviceRef = getServiceRefInStage(deploymentStageConfig);
    checkServiceAccess(accountId, orgIdentifier, projectIdentifier, serviceRef);
    ServiceEntity existedServiceEntity = getServiceV1Entity(accountId, orgIdentifier, projectIdentifier,
            deploymentStageConfig.getServiceConfig().getServiceRef().getValue());
    addServiceV2YamlInServiceEntity(deploymentStageConfig.getServiceConfig(), stageField, existedServiceEntity);
    return serviceEntityService.update(existedServiceEntity);
  }

  private InfrastructureEntity migrateEnv(String accountId, String orgIdentifier, String projectIdentifier,
                                          DeploymentStageConfig deploymentStageConfig, YamlField stageField, String infraIdentifier) {
    String environmentRef = getEnvironmentRefInStage(deploymentStageConfig);
    checkEnvironmentAccess(accountId, orgIdentifier, projectIdentifier, environmentRef);
    environmentValidationHelper.checkThatEnvExists(
            accountId, orgIdentifier, projectIdentifier, environmentRef);
    InfrastructureEntity infrastructureEntity = createInfraEntity(deploymentStageConfig.getInfrastructure(), orgIdentifier, projectIdentifier,
            infraIdentifier, deploymentStageConfig.getServiceConfig(), accountId, stageField);
    return infrastructureEntityService.create(infrastructureEntity);

  }

  private void checkServiceAccess(String accountId, String orgIdentifier,
                                  String projectIdentifier, String serviceRef) {
    accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
            Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
            Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_UPDATE_PERMISSION,
            "unable to update service because of permission");
  }

  private void checkEnvironmentAccess(String accountId, String orgIdentifier,
                                      String projectIdentifier, String environmentRef ) {
    accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
            Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
            Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_UPDATE_PERMISSION,
            "unable to create infrastructure because of permission");
  }

  private ObjectNode getEnvironmentV2Node(ObjectMapper objectMapper, InfrastructureEntity infrastructureEntity) {
    JsonNode infraNode = objectMapper.createObjectNode().put("identifier", infrastructureEntity.getIdentifier());
    ArrayNode infraArrayNode = objectMapper.createArrayNode().add(infraNode);
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("environmentRef", infrastructureEntity.getEnvIdentifier());
    envNode.set("infrastructureDefinitions", infraArrayNode);
    return envNode;
  }

  private ObjectNode getServiceV2Node(ObjectMapper objectMapper, ServiceEntity serviceEntity) {
    ObjectNode serviceNode = objectMapper.createObjectNode();
    serviceNode.put("serviceRef", serviceEntity.getIdentifier());
    return serviceNode;
  }

  private String getServiceRefInStage(DeploymentStageConfig deploymentStageConfig) {
    validateOldService(deploymentStageConfig);
    validateParameterRef(deploymentStageConfig.getServiceConfig().getServiceRef(), "serviceRef");
    return deploymentStageConfig.getServiceConfig().getServiceRef().getValue();
  }

  private String getEnvironmentRefInStage(DeploymentStageConfig deploymentStageConfig) {
    validateOldInfra(deploymentStageConfig);
    validateParameterRef(deploymentStageConfig.getInfrastructure().getEnvironmentRef(), "environmentRef");
    return deploymentStageConfig.getInfrastructure().getEnvironmentRef().getValue();
  }

  private YamlField getYamlField(String yaml, String fieldName) {
    try {
      return YamlUtils.readTree(yaml).getNode().getField(fieldName);
    } catch (Exception e) {
      throw new InvalidRequestException(format("not able to parse %s yaml because of error: %s", fieldName, e.getMessage()));
    }
  }

  private boolean isStageContainStageTemplate(YamlNode stageNode) {
    YamlField templateField = stageNode.getField("stage").getNode().getField("template");
    if(templateField==null) {
      return false;
    }
    return true;
  }

  private InfrastructureEntity createInfraEntity(PipelineInfrastructure infrastructure, String orgIdentifier,
                                                 String projectIdentifier, String infraIdentifier,
      ServiceConfig serviceConfig, String accountId, YamlField stageField) {

    checkInfrastructureEntityExistence(accountId, orgIdentifier, projectIdentifier,
            infrastructure.getEnvironmentRef().getValue(), infraIdentifier);

    YamlField infrastructureField = stageField.getNode().getField("spec").getNode().getField("infrastructure");
    YamlField infrastructureSpecField =
        infrastructureField.getNode().getField("infrastructureDefinition").getNode().getField("spec");

    ObjectMapper objectMapper = new ObjectMapper();

    ObjectNode parentInfraNode =
        objectMapper.createObjectNode().set("infrastructureDefinition", objectMapper.createObjectNode());
    ObjectNode infraNode = (ObjectNode) parentInfraNode.get("infrastructureDefinition");
    infraNode.put("identifier", infraIdentifier);
    infraNode.put("name", infraIdentifier); // name is same as identifier as of now
    infraNode.put("orgIdentifier", orgIdentifier);
    infraNode.put("projectIdentifier", projectIdentifier);
    infraNode.put("environmentRef", infrastructure.getEnvironmentRef().getValue());
    infraNode.put("deploymentType", serviceConfig.getServiceDefinition().getType().getYamlName());
    infraNode.put("type", infrastructure.getInfrastructureDefinition().getType().getDisplayName());
    infraNode.put("allowSimultaneousDeployments",
        isAllowSimultaneousDeployments(infrastructure.getAllowSimultaneousDeployments()));
    infraNode.set("spec", infrastructureSpecField.getNode().getCurrJsonNode());

    InfrastructureRequestDTO infrastructureRequestDTO =
        InfrastructureRequestDTO.builder()
            .identifier(infraIdentifier)
            .name(infraIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
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
    return infrastructureEntityService.create(infrastructureEntity);
  }

  private ServiceEntity addServiceV2YamlInServiceEntity(ServiceConfig serviceConfig, YamlField stageField,
                                                        ServiceEntity existedServiceEntity) {
    YamlField serviceConfigField = stageField.getNode().getField("spec").getNode().getField("serviceConfig");
    YamlField serviceDefinitionField = serviceConfigField.getNode().getField("serviceDefinition");

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode parentServiceNode = objectMapper.createObjectNode().set("service", objectMapper.createObjectNode());
    ObjectNode serviceNode = (ObjectNode) parentServiceNode.get("service");
    serviceNode.put("name", existedServiceEntity.getName());
    serviceNode.put("identifier", existedServiceEntity.getIdentifier());
    serviceNode.put("description", existedServiceEntity.getDescription());
    serviceNode.put("name", existedServiceEntity.getName());
    serviceNode.putPOJO("tags", TagMapper.convertToMap(existedServiceEntity.getTags()));
    serviceNode.set("serviceDefinition", serviceDefinitionField.getNode().getCurrJsonNode());

    existedServiceEntity.setYaml(YamlPipelineUtils.writeYamlString(parentServiceNode));
    existedServiceEntity.setType(serviceConfig.getServiceDefinition().getType());
    // gitops is not considered here as of now

    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(existedServiceEntity);
    if (ngServiceConfig == null) {
      throw new InvalidRequestException("not able to parse generated yaml for service of type v2");
    }
    return existedServiceEntity;
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
