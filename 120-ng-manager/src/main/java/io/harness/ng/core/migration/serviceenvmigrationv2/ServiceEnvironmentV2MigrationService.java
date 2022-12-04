/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageMigrationFailureResponse;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationProjectWrapperRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationProjectWrapperResponseDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationResponseDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.TemplateObject;
import io.harness.ng.core.refresh.service.EntityRefreshService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
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
  private static class StageSchema {
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
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Integer PIPELINE_SIZE = 25;

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

  public SvcEnvMigrationProjectWrapperResponseDto migrateProject(
      @NonNull SvcEnvMigrationProjectWrapperRequestDto requestDto, @NonNull String accountId) {
    int currentPage = 0;
    int currentSize = 0;
    List<StageMigrationFailureResponse> failures = new ArrayList<>();
    List<String> migratedPipelines = new ArrayList<>();
    do {
      List<PMSPipelineSummaryResponseDTO> pipelines =
          NGRestUtils
              .getResponse(pipelineServiceClient.listPipelines(accountId, requestDto.getOrgIdentifier(),
                  requestDto.getProjectIdentifier(), currentPage, PIPELINE_SIZE, null, null, null, null,
                  PipelineFilterPropertiesDto.builder().build()))
              .getContent();
      currentPage++;
      if (pipelines == null || pipelines.size() == 0) {
        break;
      }
      currentSize = pipelines.size();

      for (PMSPipelineSummaryResponseDTO pipeline : pipelines) {
        if (isNotEmpty(requestDto.getSkipPipelines())
            && requestDto.getSkipPipelines().contains(pipeline.getIdentifier())) {
          continue;
        }
        SvcEnvMigrationResponseDto pipelineResponse =
            migratePipeline(SvcEnvMigrationRequestDto.builder()
                                .orgIdentifier(requestDto.getOrgIdentifier())
                                .projectIdentifier(requestDto.getProjectIdentifier())
                                .pipelineIdentifier(pipeline.getIdentifier())
                                .isUpdatePipeline(requestDto.isUpdatePipeline())
                                .skipServices(requestDto.getSkipServices())
                                .skipInfras(requestDto.getSkipInfras())
                                .infraIdentifierFormat(requestDto.getInfraIdentifierFormat())
                                .templateMap(requestDto.getTemplateMap())
                                .build(),
                accountId);
        failures.addAll(pipelineResponse.getFailures());
        if (pipelineResponse.isMigrated()) {
          migratedPipelines.add(pipeline.getIdentifier());
        }
      }
    } while (currentSize == PIPELINE_SIZE);
    return SvcEnvMigrationProjectWrapperResponseDto.builder()
        .failures(failures)
        .migratedPipelines(migratedPipelines)
        .build();
  }

  public SvcEnvMigrationResponseDto migratePipeline(
      @NonNull SvcEnvMigrationRequestDto requestDto, @NonNull String accountId) {
    final PMSPipelineResponseDTO existingPipeline =
        NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(requestDto.getPipelineIdentifier(),
            accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), null, null, null));
    if (existingPipeline == null || isEmpty(existingPipeline.getYamlPipeline())) {
      throw new InvalidRequestException(
          format("pipeline doesn't exist with this identifier: %s", requestDto.getPipelineIdentifier()));
    }
    String pipelineYaml = existingPipeline.getYamlPipeline();
    YamlField pipelineYamlField = getYamlField(pipelineYaml, "pipeline");
    ArrayNode stageArrayNode = (ArrayNode) pipelineYamlField.getNode().getField("stages").getNode().getCurrJsonNode();
    if (stageArrayNode.size() < 1) {
      log.error(String.format(
          "No stages found in pipeline %s. Aborting migration request", requestDto.getPipelineIdentifier()));
      return SvcEnvMigrationResponseDto.builder().build();
    }
    List<StageMigrationFailureResponse> failures = new ArrayList<>();
    boolean updatePipelineRequired = false;

    // Loop over each stage and update each stage yaml
    for (int currentIndex = 0; currentIndex < stageArrayNode.size(); currentIndex++) {
      JsonNode stageNode = stageArrayNode.get(currentIndex);
      YamlNode stageYamlNode = new YamlNode(stageNode);
      if (!"Deployment".equals(getStageType(stageYamlNode))) {
        continue;
      }
      Optional<JsonNode> migratedStageNode = createMigratedYaml(accountId, stageYamlNode, requestDto, failures);
      if (migratedStageNode.isPresent()) {
        stageArrayNode.set(currentIndex, migratedStageNode.get());
        updatePipelineRequired = true;
      }
    }

    ObjectNode pipelineParentNode = objectMapper.createObjectNode();
    pipelineParentNode.set("pipeline", pipelineYamlField.getNode().getCurrJsonNode());
    String migratedPipelineYaml = YamlPipelineUtils.writeYamlString(pipelineParentNode);
    if (requestDto.isUpdatePipeline() && updatePipelineRequired) {
      checkPipelineAccess(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
          requestDto.getPipelineIdentifier());
      updatePipeline(existingPipeline, migratedPipelineYaml, requestDto, accountId);
    }
    return SvcEnvMigrationResponseDto.builder()
        .pipelineYaml(migratedPipelineYaml)
        .migrated(updatePipelineRequired)
        .failures(failures)
        .build();
  }

  private void updatePipeline(PMSPipelineResponseDTO existingPipeline, String migratedPipelineYaml,
      SvcEnvMigrationRequestDto requestDto, String accountId) {
    EntityGitDetails gitDetails = existingPipeline.getGitDetails();
    if (gitDetails == null) {
      gitDetails = EntityGitDetails.builder().build();
    }
    NGRestUtils.getResponse(pipelineServiceClient.updatePipeline(null, requestDto.getPipelineIdentifier(), accountId,
        requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), null, null, null,
        RequestBody.create(MediaType.parse("application/yaml"), migratedPipelineYaml), gitDetails.getBranch(),
        gitDetails.getRootFolder(), gitDetails.getFilePath(), "migrate pipeline", gitDetails.getObjectId(), null,
        StoreType.REMOTE, gitDetails.getCommitId()));
  }

  private Optional<JsonNode> createMigratedYaml(String accountId, YamlNode stageNode,
      SvcEnvMigrationRequestDto requestDto, List<StageMigrationFailureResponse> failures) {
    try {
      boolean isStageTemplatePresent = isStageContainStageTemplate(stageNode);
      JsonNode stageJsonNode;
      if (isStageTemplatePresent) {
        stageJsonNode = migrateStageWithTemplate(stageNode, accountId, requestDto);
      } else {
        stageJsonNode = migrateStage(stageNode, accountId, requestDto);
      }
      ObjectNode stageParentNode = objectMapper.createObjectNode();
      stageParentNode.set("stage", stageJsonNode);
      return Optional.of(refreshInputsInStageYaml(accountId, requestDto, stageParentNode));
    } catch (Exception ex) {
      failures.add(
          StageMigrationFailureResponse.builder()
              .pipelineIdentifier(requestDto.getPipelineIdentifier())
              .orgIdentifier(requestDto.getOrgIdentifier())
              .projectIdentifier(requestDto.getProjectIdentifier())
              .stageIdentifier(
                  stageNode.getField("stage").getNode().getField("identifier").getNode().getCurrJsonNode().textValue())
              .failureReason(ex.getMessage())
              .build());
      return Optional.empty();
    }
  }

  private JsonNode refreshInputsInStageYaml(
      String accountId, SvcEnvMigrationRequestDto requestDto, ObjectNode stageNode) {
    String migratedStageYaml = YamlPipelineUtils.writeYamlString(stageNode);
    migratedStageYaml = entityRefreshService.refreshLinkedInputs(
        accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), migratedStageYaml, null);
    YamlField migratedStageYamlField = getYamlField(migratedStageYaml, "stage");
    ObjectNode stageParentNode = objectMapper.createObjectNode();
    stageParentNode.set("stage", migratedStageYamlField.getNode().getCurrJsonNode());
    return stageParentNode;
  }

  private JsonNode migrateStageWithTemplate(
      YamlNode stageNode, String accountId, SvcEnvMigrationRequestDto requestDto) {
    String stageYaml = YamlPipelineUtils.writeYamlString(stageNode.getCurrJsonNode());
    String resolvedStageYaml =
        NGRestUtils
            .getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, requestDto.getOrgIdentifier(),
                requestDto.getProjectIdentifier(), null, null, null, null, null, null, null, null, null,
                TemplateApplyRequestDTO.builder().originalEntityYaml(stageYaml).checkForAccess(true).build()))
            .getMergedPipelineYaml();
    YamlField stageField = getYamlField(stageYaml, "stage");
    YamlField resolvedStageField = getYamlField(resolvedStageYaml, "stage");
    DeploymentStageConfig deploymentStageConfig = getDeploymentStageConfig(resolvedStageYaml);
    YamlNode templateStageYamlNode = stageField.getNode().getField("template").getNode();
    ObjectNode specNode = (ObjectNode) templateStageYamlNode.getField("templateInputs")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getCurrJsonNode();

    migrateService(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), deploymentStageConfig,
        resolvedStageField, requestDto.getSkipServices(), specNode);

    migrateEnv(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), deploymentStageConfig,
        resolvedStageField, requestDto.getInfraIdentifierFormat(), requestDto.getSkipInfras(), specNode,
        requestDto.getPipelineIdentifier());

    String templateKey = templateStageYamlNode.getField("templateRef").getNode().getCurrJsonNode().textValue() + "@ "
        + templateStageYamlNode.getField("versionLabel").getNode().getCurrJsonNode().textValue();
    if (requestDto.getTemplateMap().isEmpty() || !requestDto.getTemplateMap().containsKey(templateKey)) {
      throw new InvalidRequestException(
          format("doesn't found target template mapping for following source template: %s", templateKey));
    }
    TemplateObject targetTemplateObject = requestDto.getTemplateMap().get(templateKey);
    checkStageTemplateExistence(accountId, requestDto, templateKey, targetTemplateObject);
    ObjectNode stageTemplateNode = (ObjectNode) templateStageYamlNode.getCurrJsonNode();
    stageTemplateNode.put("templateRef", targetTemplateObject.getTemplateRef());
    stageTemplateNode.put("versionLabel", targetTemplateObject.getVersionLabel());
    return stageField.getNode().getCurrJsonNode();
  }

  public JsonNode migrateStage(YamlNode stageNode, String accountId, SvcEnvMigrationRequestDto requestDto) {
    String stageYaml = YamlPipelineUtils.writeYamlString(stageNode.getCurrJsonNode());
    DeploymentStageConfig deploymentStageConfig = getDeploymentStageConfig(stageYaml);
    YamlField stageField = getYamlField(stageYaml, "stage");
    ObjectNode specNode = (ObjectNode) stageField.getNode().getField("spec").getNode().getCurrJsonNode();

    ServiceEntity service = migrateService(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(),
        deploymentStageConfig, stageField, requestDto.getSkipServices(), specNode);
    if (service != null) {
      specNode.put("deploymentType", service.getType().getYamlName());
    }

    migrateEnv(accountId, requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), deploymentStageConfig,
        stageField, requestDto.getInfraIdentifierFormat(), requestDto.getSkipInfras(), specNode,
        requestDto.getPipelineIdentifier());
    addDeployToAllFieldInEnv(specNode);

    return stageField.getNode().getCurrJsonNode();
  }

  private void migrateStageYaml(ServiceEntity service, InfrastructureEntity infrastructure, ObjectNode specNode) {
    ObjectMapper objectMapper = new ObjectMapper();
    if (service != null) {
      specNode.remove("serviceConfig");
      specNode.set("service", getServiceV2Node(objectMapper, service));
    }
    if (infrastructure != null) {
      specNode.remove("infrastructure");
      specNode.set("environment", getEnvironmentV2Node(objectMapper, infrastructure));
    }
  }

  private void addDeployToAllFieldInEnv(ObjectNode specNode) {
    ObjectNode envNode = (ObjectNode) specNode.get("environment");
    envNode.put("deployToAll", false);
  }

  private ServiceEntity migrateService(String accountId, String orgIdentifier, String projectIdentifier,
      DeploymentStageConfig deploymentStageConfig, YamlField stageField, List<String> skipServices,
      ObjectNode resultantSpecNode) {
    ServiceEntity migratedServiceEntity;
    String serviceRef = getServiceRefInStage(deploymentStageConfig);
    checkServiceAccess(accountId, orgIdentifier, projectIdentifier, serviceRef);
    if (isSkipEntityUpdation(serviceRef, skipServices)) {
      migratedServiceEntity = getServiceEntity(accountId, orgIdentifier, projectIdentifier,
          deploymentStageConfig.getServiceConfig().getServiceRef().getValue(), "v2");
      migrateStageYaml(migratedServiceEntity, null, resultantSpecNode);
      return migratedServiceEntity;
    }
    ServiceEntity existedServiceEntity = getServiceEntity(accountId, orgIdentifier, projectIdentifier,
        deploymentStageConfig.getServiceConfig().getServiceRef().getValue(), "v1");
    addServiceV2YamlInServiceEntity(deploymentStageConfig.getServiceConfig(), stageField, existedServiceEntity);
    migratedServiceEntity = serviceEntityService.update(existedServiceEntity);
    migrateStageYaml(migratedServiceEntity, null, resultantSpecNode);
    return migratedServiceEntity;
  }

  private void migrateEnv(String accountId, String orgIdentifier, String projectIdentifier,
      DeploymentStageConfig deploymentStageConfig, YamlField stageField, String infraIdentifier,
      List<String> skipInfras, ObjectNode resultantSpecNode, String pipelineIdentifier) {
    InfrastructureEntity migratedInfrastructureEntity;
    String environmentRef = getEnvironmentRefInStage(deploymentStageConfig);
    checkEnvironmentAccess(accountId, orgIdentifier, projectIdentifier, environmentRef);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentRef);
    infraIdentifier = infraIdentifier.replace("<+org.identifier>", orgIdentifier);
    infraIdentifier = infraIdentifier.replace("<+project.identifier>", projectIdentifier);
    infraIdentifier = infraIdentifier.replace(
        "<+stage.identifier>", stageField.getNode().getField("identifier").getNode().getCurrJsonNode().textValue());
    infraIdentifier = infraIdentifier.replace("<+pipeline.identifier>", pipelineIdentifier);
    infraIdentifier = infraIdentifier.replace("<+environment.identifier>", environmentRef);
    if (deploymentStageConfig.getInfrastructure()
            .getInfrastructureDefinition()
            .getSpec()
            .getConnectorReference()
            .getValue()
        != null) {
      infraIdentifier = infraIdentifier.replace("<+infra.connectorRef>",
          deploymentStageConfig.getInfrastructure()
              .getInfrastructureDefinition()
              .getSpec()
              .getConnectorReference()
              .getValue());
    }
    infraIdentifier = infraIdentifier.replace("<+infra.type>",
        deploymentStageConfig.getInfrastructure().getInfrastructureDefinition().getType().getDisplayName());
    if (infraIdentifier.contains("<+")) {
      throw new InvalidRequestException(format(
          "infraIdentifier after resolving expressions: %s is invalid, pls provide correct infra identifier format.",
          infraIdentifier));
    }
    if (isSkipEntityUpdation(infraIdentifier, skipInfras)) {
      Optional<InfrastructureEntity> optionalInfra =
          infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, environmentRef, infraIdentifier);
      if (optionalInfra.isPresent()) {
        migratedInfrastructureEntity = optionalInfra.get();
        migrateStageYaml(null, migratedInfrastructureEntity, resultantSpecNode);
        return;
      }
      throw new InvalidRequestException(
          format("an infra (of skipInfras list) doesn't exist with identifier: %s", infraIdentifier));
    }
    InfrastructureEntity infrastructureEntity =
        createInfraEntity(deploymentStageConfig.getInfrastructure(), orgIdentifier, projectIdentifier, infraIdentifier,
            deploymentStageConfig.getServiceConfig(), accountId, stageField);
    migratedInfrastructureEntity = infrastructureEntityService.create(infrastructureEntity);
    migrateStageYaml(null, migratedInfrastructureEntity, resultantSpecNode);
  }

  private void checkStageTemplateExistence(
      String accountId, SvcEnvMigrationRequestDto requestDto, String templateKey, TemplateObject targetTemplateObject) {
    TemplateResponseDTO targetTemplateResponse;
    String orgIdentifier = null;
    String projectIdentifier = null;
    String templateRef = targetTemplateObject.getTemplateRef();
    if (targetTemplateObject.getTemplateRef().startsWith("org.")) {
      orgIdentifier = requestDto.getOrgIdentifier();
      templateRef = templateRef.replace("org.", "");
    } else if (targetTemplateObject.getTemplateRef().startsWith("account.")) {
      templateRef = templateRef.replace("account.", "");
    } else {
      projectIdentifier = requestDto.getProjectIdentifier();
      orgIdentifier = requestDto.getOrgIdentifier();
    }
    targetTemplateResponse = NGRestUtils.getResponse(templateResourceClient.get(
        templateRef, accountId, orgIdentifier, projectIdentifier, targetTemplateObject.getVersionLabel(), false));

    if (targetTemplateResponse == null) {
      throw new InvalidRequestException(format("target template: %s corresponding to source template doesn't"
              + "exist: %s",
          targetTemplateObject.getTemplateRef(), templateKey));
    }
  }

  private boolean isSkipEntityUpdation(String entityRef, List<String> skipEntities) {
    if (isNotEmpty(skipEntities) && skipEntities.contains(entityRef)) {
      return true;
    }
    return false;
  }

  private String getStageType(YamlNode stageParentNode) {
    YamlNode stageNode = stageParentNode.getField("stage").getNode();
    boolean isStageTemplatePresent = isStageContainStageTemplate(stageParentNode);
    if (isStageTemplatePresent) {
      return stageNode.getField("template")
          .getNode()
          .getField("templateInputs")
          .getNode()
          .getField("type")
          .getNode()
          .getCurrJsonNode()
          .textValue();
    }
    return stageNode.getField("type").getNode().getCurrJsonNode().textValue();
  }

  private void checkServiceAccess(String accountId, String orgIdentifier, String projectIdentifier, String serviceRef) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, serviceRef), SERVICE_UPDATE_PERMISSION,
        "unable to update service because of permission");
  }

  private void checkPipelineAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.PIPELINE, pipelineIdentifier), "core_pipeline_edit");
  }

  private void checkEnvironmentAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
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
      throw new InvalidRequestException(
          format("not able to parse %s yaml because of error: %s", fieldName, e.getMessage()));
    }
  }

  private boolean isStageContainStageTemplate(YamlNode stageNode) {
    YamlField templateField = stageNode.getField("stage").getNode().getField("template");
    return templateField != null;
  }

  private InfrastructureEntity createInfraEntity(PipelineInfrastructure infrastructure, String orgIdentifier,
      String projectIdentifier, String infraIdentifier, ServiceConfig serviceConfig, String accountId,
      YamlField stageField) {
    checkInfrastructureEntityExistence(
        accountId, orgIdentifier, projectIdentifier, infrastructure.getEnvironmentRef().getValue(), infraIdentifier);

    YamlField infrastructureField = stageField.getNode().getField("spec").getNode().getField("infrastructure");
    YamlField infrastructureSpecField =
        infrastructureField.getNode().getField("infrastructureDefinition").getNode().getField("spec");

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
    return InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
  }

  private ServiceEntity addServiceV2YamlInServiceEntity(
      ServiceConfig serviceConfig, YamlField stageField, ServiceEntity existedServiceEntity) {
    YamlField serviceConfigField = stageField.getNode().getField("spec").getNode().getField("serviceConfig");
    YamlField serviceDefinitionField = serviceConfigField.getNode().getField("serviceDefinition");

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

  private ServiceEntity getServiceEntity(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, String serviceType) {
    Optional<ServiceEntity> optionalService =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (optionalService.isPresent()) {
      ServiceEntity serviceEntity = optionalService.get();
      try {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        boolean isServiceV2Flag = ngServiceConfig != null
            && (isGitOpsEnabled(ngServiceConfig.getNgServiceV2InfoConfig())
                || ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition() != null);
        if ("v2".equals(serviceType)) {
          return getV2ServiceEntity(serviceEntity, isServiceV2Flag, serviceIdentifier);
        } else {
          return getV1ServiceEntity(serviceEntity, isServiceV2Flag, serviceIdentifier);
        }
      } catch (Exception e) {
        throw new InvalidRequestException(e.getMessage());
      }
    }
    throw new InvalidRequestException(format("service doesn't exist with identifier: %s", serviceIdentifier));
  }

  private ServiceEntity getV2ServiceEntity(ServiceEntity serviceEntity, boolean isServiceV2Flag, String serviceRef) {
    if (isServiceV2Flag) {
      return serviceEntity;
    }
    throw new InvalidRequestException(
        format("a service (in skipServices list) of type v2 doesn't exist with identifier: %s", serviceRef));
  }

  private ServiceEntity getV1ServiceEntity(ServiceEntity serviceEntity, boolean isServiceV2Flag, String serviceRef) {
    if (!isServiceV2Flag) {
      return serviceEntity;
    }
    throw new InvalidRequestException(format("a service of type v1 doesn't exist with identifier: %s", serviceRef));
  }

  private void checkInfrastructureEntityExistence(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String infraIdentifier) {
    Optional<InfrastructureEntity> optionalInfra =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    if (optionalInfra.isPresent()) {
      throw new InvalidRequestException(format("an infra already exists with identifier: %s", infraIdentifier));
    }
  }

  private boolean isAllowSimultaneousDeployments(ParameterField<Boolean> allowSimultaneousDeployments) {
    if (allowSimultaneousDeployments.getValue() != null) {
      return allowSimultaneousDeployments.getValue();
    }
    return false;
  }

  private boolean isGitOpsEnabled(NGServiceV2InfoConfig ngServiceV2InfoConfig) {
    return ngServiceV2InfoConfig != null && ngServiceV2InfoConfig.getGitOpsEnabled() != null
        && ngServiceV2InfoConfig.getGitOpsEnabled();
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
