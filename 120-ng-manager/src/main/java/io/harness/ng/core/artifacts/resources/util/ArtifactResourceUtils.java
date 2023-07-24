/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetValidatorType;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusConstant;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryRawConfig;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.evaluators.CDExpressionEvaluator;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.custom.CustomScriptInfo;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.InputSetMergeUtility;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.nexus.NexusRepositories;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactResourceUtils {
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject TemplateResourceClient templateResourceClient;
  @Inject ServiceEntityService serviceEntityService;
  @Inject EnvironmentService environmentService;
  @Inject NexusResourceService nexusResourceService;
  @Inject DockerResourceService dockerResourceService;
  @Inject GARResourceService garResourceService;
  @Inject GcrResourceService gcrResourceService;
  @Inject EcrResourceService ecrResourceService;
  @Inject AcrResourceService acrResourceService;
  @Inject AzureResourceService azureResourceService;
  @Inject ArtifactoryResourceService artifactoryResourceService;
  @Inject AccessControlClient accessControlClient;
  @Inject CustomResourceService customResourceService;

  // Checks whether field is fixed value or not, if empty then also we return false for fixed value.
  public static boolean isFieldFixedValue(String fieldValue) {
    return !isEmpty(fieldValue) && !NGExpressionUtils.isRuntimeOrExpressionField(fieldValue);
  }

  private String getMergedCompleteYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isEmpty(pipelineIdentifier)) {
      return runtimeInputYaml;
    }

    if (gitEntityBasicInfo == null) {
      gitEntityBasicInfo = new GitEntityFindInfoDTO();
    }

    MergeInputSetResponseDTOPMS response =
        NGRestUtils.getResponse(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, gitEntityBasicInfo.getBranch(),
            gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getBranch(),
            gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getDefaultFromOtherRepo(),
            MergeInputSetTemplateRequestDTO.builder().runtimeInputYaml(runtimeInputYaml).build()));
    if (response.isErrorResponse()) {
      log.error("Failed to get Merged Pipeline Yaml with error yaml - \n "
          + response.getInputSetErrorWrapper().getErrorPipelineYaml());
      log.error("Error map to identify the errors - \n"
          + response.getInputSetErrorWrapper().getUuidToErrorResponseMap().toString());
      throw new InvalidRequestException("Failed to get Merged Pipeline yaml.");
    }
    return response.getCompletePipelineYaml();
  }

  private String applyTemplatesOnGivenYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String yaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    TemplateMergeResponseDTO response = NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYaml(
        accountId, orgIdentifier, projectIdentifier, gitEntityBasicInfo.getBranch(),
        gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getDefaultFromOtherRepo(), BOOLEAN_FALSE_VALUE,
        TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build(), false));
    return response.getMergedPipelineYaml();
  }

  public boolean checkValidRegexType(ParameterField<String> artifactConfig) {
    return artifactConfig.getExpressionValue() != null && artifactConfig.getInputSetValidator() != null
        && artifactConfig.getInputSetValidator().getValidatorType() == InputSetValidatorType.REGEX;
  }

  @Nullable
  public String getResolvedFieldValue(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String imagePath, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
    final ParameterField<String> imageParameterField = RuntimeInputValuesValidator.getInputSetParameterField(imagePath);
    if (imageParameterField == null) {
      return imagePath;
    } else if (!imageParameterField.isExpression()) {
      return imageParameterField.getValue();
    } else {
      // this check assumes ui sends -1 as pipeline identifier when pipeline is under construction
      if ("-1".equals(pipelineIdentifier)) {
        throw new InvalidRequestException(String.format(
            "Couldn't resolve artifact image path expression %s, as pipeline has not been saved yet.", imagePath));
      }
      String mergedCompleteYaml = getMergedCompleteYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
      if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
        mergedCompleteYaml = applyTemplatesOnGivenYaml(
            accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo);
      }
      String[] split = fqnPath.split("\\.");
      String stageIdentifier = split[2];
      YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
      Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();

      EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
      if (isEmpty(serviceId)) {
        // pipelines with inline service definitions
        serviceId = serviceRefAndFQN.getEntityRef();
      }
      serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);

      // get environment ref
      String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);
      List<YamlField> aliasYamlField =
          getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId);
      CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
          new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
      String resolvedImagePath = CDYamlExpressionEvaluator.renderExpression(
          imagePath, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      final ParameterField<String> imageParameter =
          RuntimeInputValuesValidator.getInputSetParameterField(resolvedImagePath);
      if (imageParameter == null || imageParameter.isExpression()) {
        return null;
      } else {
        return imageParameter.getValue();
      }
    }
  }

  @Nullable
  private String resolveEntityIdIfExpression(
      String entityId, String mergedCompleteYaml, EntityRefAndFQN entityRefAndFQN) {
    if (isNotEmpty(entityId) && EngineExpressionEvaluator.hasExpressions(entityId)) {
      CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
          new CDYamlExpressionEvaluator(mergedCompleteYaml, entityRefAndFQN.getEntityFQN(), new ArrayList<>());
      return CDYamlExpressionEvaluator.renderExpression(entityRefAndFQN.getEntityRef());
    }
    return entityId;
  }

  @Nullable
  private String getResolvedEnvironmentId(
      String mergedCompleteYaml, String stageIdentifier, Map<FQN, Object> fqnObjectMap) {
    EntityRefAndFQN environmentRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.ENVIRONMENT_REF);
    return resolveEntityIdIfExpression(environmentRefAndFQN.getEntityRef(), mergedCompleteYaml, environmentRefAndFQN);
  }

  public void resolveParameterFieldValues(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, List<ParameterField<String>> parameterFields, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
    boolean shouldResolveExpression = false;
    for (ParameterField<String> param : parameterFields) {
      if (isResolvableParameterField(param)) {
        shouldResolveExpression = true;
        break;
      }
    }
    if (!shouldResolveExpression) {
      return;
    }
    String mergedCompleteYaml = "";

    try {
      mergedCompleteYaml = getMergedCompleteYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    } catch (InvalidRequestException invalidRequestException) {
      if (invalidRequestException.getMessage().contains("doesn't exist or has been deleted")) {
        return;
      }
      throw invalidRequestException;
    }
    if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
      mergedCompleteYaml = applyTemplatesOnGivenYaml(
          accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo);
    }
    String[] split = fqnPath.split("\\.");
    String stageIdentifier = split[2];
    YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
    Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();

    EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
    if (isEmpty(serviceId)) {
      // pipelines with inline service definitions
      serviceId = serviceRefAndFQN.getEntityRef();
    }
    serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);
    // get environment ref
    String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);
    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId);
    CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
        new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
    for (ParameterField<String> param : parameterFields) {
      String paramValue = (String) param.fetchFinalValue();
      if (isResolvableParameterField(param) && EngineExpressionEvaluator.hasExpressions(paramValue)) {
        param.updateWithValue(CDYamlExpressionEvaluator.renderExpression(paramValue));
      }
    }
  }

  private boolean isResolvableParameterField(ParameterField<String> parameterField) {
    return parameterField.isExpression() && !parameterField.isExecutionInput();
  }

  public String getResolvedExpression(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String param, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId, int secretFunctor) {
    if (EngineExpressionEvaluator.hasExpressions(param)) {
      String mergedCompleteYaml = getMergedCompleteYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
      if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
        mergedCompleteYaml = applyTemplatesOnGivenYaml(
            accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo);
      }
      String[] split = fqnPath.split("\\.");
      String stageIdentifier = split[2];
      YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
      Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();
      EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
      if (isEmpty(serviceId)) {
        // pipelines with inline service definitions
        serviceId = serviceRefAndFQN.getEntityRef();
      }
      serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);
      // get environment ref
      String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);
      List<YamlField> aliasYamlField =
          getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId);
      CDExpressionEvaluator CDExpressionEvaluator =
          new CDExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField, secretFunctor);
      param = CDExpressionEvaluator.renderExpression(param);
    }
    return param;
  }

  /**
   * Returns the serviceRef using stage identifier and fqnToObjectMap.
   * Field name should be serviceRef and fqn should have stage identifier to get the value of serviceRef
   *
   * @param fqnToObjectMap fqn to object map depicting yaml tree as key value pair
   * @param stageIdentifier stage identifier to fetch serviceRef from
   * @return String
   */
  private String getServiceRef(Map<FQN, Object> fqnToObjectMap, String stageIdentifier) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && YamlTypes.SERVICE_REF.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return ((TextNode) mapEntry.getValue()).asText();
      }
    }
    return null;
  }

  private EntityRefAndFQN getEntityRefAndFQN(
      Map<FQN, Object> fqnToObjectMap, String stageIdentifier, String yamlTypes) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && yamlTypes.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return EntityRefAndFQN.builder()
            .entityRef(((TextNode) mapEntry.getValue()).asText())
            .entityFQN(mapEntry.getKey().getExpressionFqn())
            .build();
      }
    }
    return EntityRefAndFQN.builder().build();
  }

  /**
   * Returns the environmentRef using stage identifier and fqnToObjectMap.
   * Field name should be environmentRef and fqn should have stage identifier to get the value of environmentRef
   *
   * @param fqnToObjectMap fqn to object map depicting yaml tree as key value pair
   * @param stageIdentifier stage identifier to fetch serviceRef from
   * @return String
   */
  private String getEnvironmentRef(Map<FQN, Object> fqnToObjectMap, String stageIdentifier) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && YamlTypes.ENVIRONMENT_REF.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return ((TextNode) mapEntry.getValue()).asText();
      }
    }
    return null;
  }

  private List<YamlField> getAliasYamlFields(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceId, String environmentId) {
    List<YamlField> yamlFields = new ArrayList<>();
    if (isNotEmpty(serviceId)) {
      Optional<ServiceEntity> optionalService =
          serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceId, false);
      optionalService.ifPresent(
          service -> yamlFields.add(getYamlField(service.fetchNonEmptyYaml(), YAMLFieldNameConstants.SERVICE)));
    }
    if (isNotEmpty(environmentId)) {
      Optional<Environment> optionalEnvironment =
          environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentId, false);
      optionalEnvironment.ifPresent(environment
          -> yamlFields.add(getYamlField(environment.fetchNonEmptyYaml(), YAMLFieldNameConstants.ENVIRONMENT)));
    }
    return yamlFields;
  }

  private YamlField getYamlField(String yaml, String fieldName) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField(fieldName);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid service yaml passed.");
    }
  }

  /**
   * Locates ArtifactConfig in a service entity for a given FQN of type
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   * @return ArtifactConfig
   */
  @NotNull
  public ArtifactConfig locateArtifactInService(
      String accountId, String orgId, String projectId, String serviceRef, String imageTagFqn) {
    String TEMPLATE_ACCESS_PERMISSION = "core_template_access";
    YamlNode artifactTagLeafNode =
        serviceEntityService.getYamlNodeForFqn(accountId, orgId, projectId, serviceRef, imageTagFqn);

    // node from service will have updated details
    YamlNode artifactSpecNode = artifactTagLeafNode.getParentNode().getParentNode();

    // In case of Nexus2&3 configs, coz they have one more spec in their ArtifactConfig like no other artifact source.
    if (artifactSpecNode.getFieldName().equals("spec")) {
      artifactSpecNode = artifactSpecNode.getParentNode();
    }

    if (artifactSpecNode.getParentNode() != null
        && "template".equals(artifactSpecNode.getParentNode().getFieldName())) {
      YamlNode templateNode = artifactSpecNode.getParentNode();
      String templateRef = templateNode.getField("templateRef").getNode().getCurrJsonNode().asText();
      String versionLabel = templateNode.getField("versionLabel") != null
          ? templateNode.getField("versionLabel").getNode().getCurrJsonNode().asText()
          : null;

      if (isNotEmpty(templateRef)) {
        IdentifierRef templateIdentifier =
            IdentifierRefHelper.getIdentifierRef(templateRef, accountId, orgId, projectId);
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, templateIdentifier.getOrgIdentifier(),
                                                      templateIdentifier.getProjectIdentifier()),
            Resource.of(ResourceTypeConstants.TEMPLATE, templateIdentifier.getIdentifier()),
            TEMPLATE_ACCESS_PERMISSION);
        TemplateResponseDTO response = NGRestUtils.getResponse(
            templateResourceClient.get(templateIdentifier.getIdentifier(), templateIdentifier.getAccountIdentifier(),
                templateIdentifier.getOrgIdentifier(), templateIdentifier.getProjectIdentifier(), versionLabel, false));
        if (!response.getTemplateEntityType().equals(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)) {
          throw new InvalidRequestException(
              String.format("Provided template ref: [%s], version: [%s] is not an artifact source template",
                  templateRef, versionLabel));
        }
        if (isEmpty(response.getYaml())) {
          throw new InvalidRequestException(
              String.format("Received empty artifact source template yaml for template ref: %s, version label: %s",
                  templateRef, versionLabel));
        }
        YamlNode artifactTemplateSpecNode;
        try {
          artifactTemplateSpecNode = YamlNode.fromYamlPath(response.getYaml(), "template/spec");

          String inputSetYaml = YamlUtils.writeYamlString(new YamlField(artifactSpecNode));
          String originalYaml = YamlUtils.writeYamlString(new YamlField(artifactTemplateSpecNode));
          String mergedArtifactYamlConfig =
              InputSetMergeUtility.mergeRuntimeInputValuesIntoOriginalYamlForArrayNode(originalYaml, inputSetYaml);

          artifactSpecNode = YamlUtils.readTree(mergedArtifactYamlConfig).getNode();
        } catch (IOException e) {
          throw new InvalidRequestException("Cannot read spec from the artifact source template");
        }
      }
    }
    final ArtifactInternalDTO artifactDTO;
    try {
      artifactDTO = YamlUtils.read(artifactSpecNode.toString(), ArtifactInternalDTO.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to read artifact spec in service yaml", e);
    }

    return artifactDTO.spec;
  }

  public static class ArtifactInternalDTO {
    @JsonProperty("type") public ArtifactSourceType sourceType;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    public ArtifactConfig spec;
  }

  public NexusResponseDTO getBuildDetails(String nexusConnectorIdentifier, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier, String groupId, String artifactId, String extension, String classifier,
      String packageName, String pipelineIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, String serviceRef, String accountId, String group) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      switch (nexusRegistryArtifactConfig.getRepositoryFormat().getValue()) {
        case NexusConstant.DOCKER:
          NexusRegistryDockerConfig nexusRegistryDockerConfig =
              (NexusRegistryDockerConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(repositoryPort)) {
            repositoryPort = (String) nexusRegistryDockerConfig.getRepositoryPort().fetchFinalValue();
          }

          if (isEmpty(artifactPath)) {
            artifactPath = (String) nexusRegistryDockerConfig.getArtifactPath().fetchFinalValue();
          }

          if (isEmpty(artifactRepositoryUrl)) {
            artifactRepositoryUrl = (String) nexusRegistryDockerConfig.getRepositoryUrl().fetchFinalValue();
          }
          break;
        case NexusConstant.NPM:
          NexusRegistryNpmConfig nexusRegistryNpmConfig =
              (NexusRegistryNpmConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(packageName)) {
            packageName = (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue();
          }
          break;
        case NexusConstant.NUGET:
          NexusRegistryNugetConfig nexusRegistryNugetConfig =
              (NexusRegistryNugetConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(packageName)) {
            packageName = (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue();
          }
          break;
        case NexusConstant.MAVEN:
          NexusRegistryMavenConfig nexusRegistryMavenConfig =
              (NexusRegistryMavenConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(artifactId)) {
            artifactId = (String) nexusRegistryMavenConfig.getArtifactId().fetchFinalValue();
          }
          if (isEmpty(groupId)) {
            groupId = (String) nexusRegistryMavenConfig.getGroupId().fetchFinalValue();
          }
          if (isEmpty(classifier)) {
            classifier = (String) nexusRegistryMavenConfig.getClassifier().fetchFinalValue();
          }
          if (isEmpty(extension)) {
            extension = (String) nexusRegistryMavenConfig.getExtension().fetchFinalValue();
          }
          break;
        case NexusConstant.RAW:
          NexusRegistryRawConfig nexusRegistryRawConfig =
              (NexusRegistryRawConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(group)) {
            group = (String) nexusRegistryRawConfig.getGroup().fetchFinalValue();
          }
          break;
        default:
          throw new NotFoundException(String.format(
              "Repository Format [%s] is not supported", nexusRegistryArtifactConfig.getRepositoryFormat().getValue()));
      }

      if (isEmpty(repositoryName)) {
        repositoryName = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }

      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexusRegistryArtifactConfig.getConnectorRef().getValue();
      }
    }

    nexusConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    groupId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        groupId, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactRepositoryUrl = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef);
    repositoryPort = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef);
    packageName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef);
    group = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        group, fqnPath, gitEntityBasicInfo, serviceRef);
    repositoryName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef);
    return nexusResourceService.getBuildDetails(connectorRef, repositoryName, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension,
        classifier, packageName, group);
  }

  public NexusResponseDTO getBuildDetailsNexus2(String nexusConnectorIdentifier, String repositoryName,
      String repositoryPort, String artifactPath, String repositoryFormat, String artifactRepositoryUrl,
      String orgIdentifier, String projectIdentifier, String groupId, String artifactId, String extension,
      String classifier, String packageName, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef, String accountId,
      String group) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
          (Nexus2RegistryArtifactConfig) artifactSpecFromService;
      switch (nexus2RegistryArtifactConfig.getRepositoryFormat().getValue()) {
        case NexusConstant.NPM:
          NexusRegistryNpmConfig nexusRegistryNpmConfig =
              (NexusRegistryNpmConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          packageName =
              isEmpty(packageName) ? (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue() : packageName;
          break;
        case NexusConstant.NUGET:
          NexusRegistryNugetConfig nexusRegistryNugetConfig =
              (NexusRegistryNugetConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          packageName =
              isEmpty(packageName) ? (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue() : packageName;
          break;
        case NexusConstant.MAVEN:
          NexusRegistryMavenConfig nexusRegistryMavenConfig =
              (NexusRegistryMavenConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (isEmpty(artifactId) || isEmpty(groupId)) {
            artifactId = nexusRegistryMavenConfig.getArtifactId().fetchFinalValue().toString();
            groupId = nexusRegistryMavenConfig.getGroupId().fetchFinalValue().toString();
            classifier = nexusRegistryMavenConfig.getClassifier().getValue();
            extension = nexusRegistryMavenConfig.getExtension().getValue();
          }

          break;
        default:
          throw new NotFoundException(String.format("Repository Format [%s] is not supported",
              nexus2RegistryArtifactConfig.getRepositoryFormat().getValue()));
      }

      if (isEmpty(repositoryName)) {
        repositoryName = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }
    }

    nexusConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    groupId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        groupId, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactRepositoryUrl = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef);
    repositoryPort = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef);
    packageName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef);
    group = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        group, fqnPath, gitEntityBasicInfo, serviceRef);
    repositoryName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef);
    return nexusResourceService.getBuildDetails(connectorRef, repositoryName, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension,
        classifier, packageName, group);
  }
  public GARResponseDTO getBuildDetailsV2GAR(String gcpConnectorIdentifier, String region, String repositoryName,
      String project, String pkg, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String version, String versionRegex, String fqnPath, String runtimeInputYaml,
      String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }

      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }

      if (isBlank(repositoryName)) {
        repositoryName = (String) googleArtifactRegistryConfig.getRepositoryName().fetchFinalValue();
      }

      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }

      if (isBlank(pkg)) {
        pkg = (String) googleArtifactRegistryConfig.getPkg().fetchFinalValue();
      }
    }

    // Getting the resolvedConnectorRef
    String resolvedConnectorRef = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolvedRegion
    String resolvedRegion = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolvedRepositoryName
    String resolvedRepositoryName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolvedProject
    String resolvedProject = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolvedPackage
    String resolvedPackage = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, pkg, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorRef, accountId, orgIdentifier, projectIdentifier);

    return garResourceService.getBuildDetails(connectorRef, resolvedRegion, resolvedRepositoryName, resolvedProject,
        resolvedPackage, version, versionRegex, orgIdentifier, projectIdentifier);
  }

  public GARBuildDetailsDTO getLastSuccessfulBuildV2GAR(String gcpConnectorIdentifier, String region,
      String repositoryName, String project, String pkg, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, GarRequestDTO garRequestDTO, String fqnPath,
      String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isEmpty(garRequestDTO.getVersion())) {
        garRequestDTO.setVersion((String) googleArtifactRegistryConfig.getVersion().fetchFinalValue());
      }

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }

      if (isBlank(repositoryName)) {
        repositoryName = (String) googleArtifactRegistryConfig.getRepositoryName().fetchFinalValue();
      }

      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }

      if (isBlank(pkg)) {
        pkg = (String) googleArtifactRegistryConfig.getPkg().fetchFinalValue();
      }

      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }

      if (isEmpty(garRequestDTO.getVersionRegex())) {
        garRequestDTO.setVersionRegex((String) googleArtifactRegistryConfig.getVersionRegex().fetchFinalValue());
      }
    }

    repositoryName = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), repositoryName, fqnPath, gitEntityBasicInfo, serviceRef);

    gcpConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    region = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), region, fqnPath, gitEntityBasicInfo, serviceRef);

    garRequestDTO.setVersion(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersion(), fqnPath, gitEntityBasicInfo, serviceRef));

    project = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), project, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    pkg = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), pkg, fqnPath, gitEntityBasicInfo, serviceRef);

    garRequestDTO.setVersionRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersionRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    return garResourceService.getLastSuccessfulBuild(
        connectorRef, region, repositoryName, project, pkg, garRequestDTO, orgIdentifier, projectIdentifier);
  }

  public ArtifactoryImagePathsDTO getArtifactoryImagePath(String repositoryType, String artifactoryConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String repository, String fqnPath,
      String runtimeInputYaml, String pipelineIdentifier, String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;

      if (isBlank(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier =
            artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }

      if (isBlank(repository)) {
        repository = artifactoryRegistryArtifactConfig.getRepository().fetchFinalValue().toString();
      }
    }

    // resolving connectorRef
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    // resolving Repository
    String resolvedRepository = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef);

    return artifactoryResourceService.getImagePaths(
        repositoryType, connectorRef, orgIdentifier, projectIdentifier, resolvedRepository);
  }

  public List<NexusRepositories> getRepositoriesNexus3(String orgIdentifier, String projectIdentifier,
      String repositoryFormat, String accountId, String pipelineIdentifier, String runtimeInputYaml,
      String nexusConnectorIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexusRegistryArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(repositoryFormat) || repositoryFormat.equalsIgnoreCase("defaultParam")) {
        repositoryFormat = nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
      }
    }
    repositoryFormat = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef);
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return nexusResourceService.getRepositories(connectorRef, orgIdentifier, projectIdentifier, repositoryFormat);
  }

  public List<NexusRepositories> getRepositoriesNexus2(String orgIdentifier, String projectIdentifier,
      String repositoryFormat, String accountId, String pipelineIdentifier, String runtimeInputYaml,
      String nexusConnectorIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
          (Nexus2RegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexus2RegistryArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(repositoryFormat) || repositoryFormat.equalsIgnoreCase("defaultParam")) {
        repositoryFormat = nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
      }
    }
    repositoryFormat = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef);
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return nexusResourceService.getRepositories(connectorRef, orgIdentifier, projectIdentifier, repositoryFormat);
  }

  public List<BuildDetails> getCustomGetBuildDetails(String arrayPath, String versionPath,
      CustomScriptInfo customScriptInfo, String serviceRef, String accountId, String orgIdentifier,
      String projectIdentifier, String fqnPath, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo) {
    String script = customScriptInfo.getScript();
    List<NGVariable> inputs = customScriptInfo.getInputs();
    List<TaskSelectorYaml> delegateSelector = customScriptInfo.getDelegateSelector();
    int secretFunctor = HashGenerator.generateIntegerHash();
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig customArtifactConfig =
          (io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig) artifactSpecFromService;
      if (isEmpty(customScriptInfo.getScript())) {
        if (customArtifactConfig.getScripts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource() != null
            && customArtifactConfig.getScripts()
                    .getFetchAllArtifacts()
                    .getShellScriptBaseStepInfo()
                    .getSource()
                    .getSpec()
                != null) {
          io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource customScriptInlineSource =
              (io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource) customArtifactConfig
                  .getScripts()
                  .getFetchAllArtifacts()
                  .getShellScriptBaseStepInfo()
                  .getSource()
                  .getSpec();
          if (customScriptInlineSource.getScript() != null
              && isNotEmpty(customScriptInlineSource.getScript().fetchFinalValue().toString())) {
            script = customScriptInlineSource.getScript().fetchFinalValue().toString();
          }
        }
        if (isEmpty(customScriptInfo.getInputs())) {
          inputs = customArtifactConfig.getInputs();
        }
        if (isEmpty(customScriptInfo.getDelegateSelector())) {
          delegateSelector =
              (List<io.harness.plancreator.steps.TaskSelectorYaml>) customArtifactConfig.getDelegateSelectors()
                  .fetchFinalValue();
        }
      }

      if (isEmpty(script) || NGExpressionUtils.isRuntimeField(script)) {
        return Collections.emptyList();
      }

      if (isEmpty(arrayPath) && customArtifactConfig.getScripts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath() != null) {
        arrayPath = customArtifactConfig.getScripts()
                        .getFetchAllArtifacts()
                        .getArtifactsArrayPath()
                        .fetchFinalValue()
                        .toString();
      }
      if (isEmpty(versionPath) && customArtifactConfig.getScripts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath() != null) {
        versionPath =
            customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue().toString();
      }
    }

    if (isEmpty(arrayPath) || arrayPath.equalsIgnoreCase("<+input>")) {
      throw new io.harness.exception.HintException("Array path can not be empty");
    }

    if (isEmpty(versionPath) || versionPath.equalsIgnoreCase("<+input>")) {
      throw new io.harness.exception.HintException("Version path can not be empty");
    }
    if (isNotEmpty(customScriptInfo.getRuntimeInputYaml())) {
      script = getResolvedExpression(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          customScriptInfo.getRuntimeInputYaml(), script, fqnPath, gitEntityBasicInfo, serviceRef, secretFunctor);
      arrayPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          customScriptInfo.getRuntimeInputYaml(), arrayPath, fqnPath, gitEntityBasicInfo, serviceRef);
      versionPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          customScriptInfo.getRuntimeInputYaml(), versionPath, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    return customResourceService.getBuilds(script, versionPath, arrayPath,
        NGVariablesUtils.getStringMapVariables(inputs, 0L), accountId, orgIdentifier, projectIdentifier, secretFunctor,
        delegateSelector);
  }

  public DockerBuildDetailsDTO getLastSuccessfulBuildV2Docker(String imagePath, String dockerConnectorIdentifier,
      String tag, String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, DockerRequestDTO requestDTO, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) dockerHubArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(dockerConnectorIdentifier)) {
        dockerConnectorIdentifier = dockerHubArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(requestDTO.getTag())) {
        requestDTO.setTag((String) dockerHubArtifactConfig.getTag().fetchFinalValue());
      }
      if (isEmpty(requestDTO.getTagRegex())) {
        requestDTO.setTagRegex((String) dockerHubArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    dockerConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        requestDTO.getRuntimeInputYaml(), dockerConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);
    imagePath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        requestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef);

    requestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        requestDTO.getRuntimeInputYaml(), requestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef));

    requestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        requestDTO.getRuntimeInputYaml(), requestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return dockerResourceService.getSuccessfulBuild(
        connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier);
  }

  public GcrBuildDetailsDTO getSuccessfulBuildV2GCR(String imagePath, String gcrConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String fqnPath, String serviceRef, String pipelineIdentifier,
      GitEntityFindInfoDTO gitEntityBasicInfo, GcrRequestDTO gcrRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) gcrArtifactConfig.getImagePath().fetchFinalValue();
      }

      if (isEmpty(gcrRequestDTO.getTag())) {
        gcrRequestDTO.setTag((String) gcrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(gcrRequestDTO.getRegistryHostname())) {
        gcrRequestDTO.setRegistryHostname((String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue());
      }

      if (isEmpty(gcrConnectorIdentifier)) {
        gcrConnectorIdentifier = (String) gcrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(gcrRequestDTO.getTagRegex())) {
        gcrRequestDTO.setTagRegex((String) gcrArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    imagePath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        gcrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef);

    gcrConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        gcrRequestDTO.getRuntimeInputYaml(), gcrConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    gcrRequestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef));

    gcrRequestDTO.setRegistryHostname(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getRegistryHostname(), fqnPath,
        gitEntityBasicInfo, serviceRef));

    gcrRequestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return gcrResourceService.getSuccessfulBuild(
        connectorRef, imagePath, gcrRequestDTO, orgIdentifier, projectIdentifier);
  }

  public EcrBuildDetailsDTO getLastSuccessfulBuildV2ECR(String registryId, String imagePath,
      String ecrConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier, String fqnPath,
      String serviceRef, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      EcrRequestDTO ecrRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;

      if (isEmpty(ecrRequestDTO.getTag())) {
        ecrRequestDTO.setTag((String) ecrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(ecrRequestDTO.getRegion())) {
        ecrRequestDTO.setRegion((String) ecrArtifactConfig.getRegion().fetchFinalValue());
      }

      if (isEmpty(imagePath)) {
        imagePath = (String) ecrArtifactConfig.getImagePath().fetchFinalValue();
      }

      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }

      if (isEmpty(ecrRequestDTO.getTagRegex())) {
        ecrRequestDTO.setTagRegex((String) ecrArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    ecrConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), ecrConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    ecrRequestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef));

    ecrRequestDTO.setRegion(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getRegion(), fqnPath, gitEntityBasicInfo, serviceRef));

    imagePath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef);

    registryId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), registryId, fqnPath, gitEntityBasicInfo, serviceRef);

    ecrRequestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return ecrResourceService.getSuccessfulBuild(
        connectorRef, registryId, imagePath, ecrRequestDTO, orgIdentifier, projectIdentifier);
  }

  public AcrBuildDetailsDTO getLastSuccessfulBuildV2ACR(String subscriptionId, String registry, String repository,
      String azureConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier, String fqnPath,
      String serviceRef, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      AcrRequestDTO acrRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          acrRequestDTO.getRuntimeInputYaml(), acrArtifactConfig.getStringParameterFields(), fqnPath,
          gitEntityBasicInfo, serviceRef);
      if (isEmpty(registry)) {
        registry = (String) acrArtifactConfig.getRegistry().fetchFinalValue();
      }

      if (isEmpty(subscriptionId)) {
        subscriptionId = (String) acrArtifactConfig.getSubscriptionId().fetchFinalValue();
      }

      if (isEmpty(acrRequestDTO.getTag())) {
        acrRequestDTO.setTag((String) acrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(acrRequestDTO.getTagRegex())) {
        acrRequestDTO.setTagRegex((String) acrArtifactConfig.getTagRegex().fetchFinalValue());
      }

      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = (String) acrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(repository)) {
        repository = (String) acrArtifactConfig.getRepository().fetchFinalValue();
      }
    }

    registry = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), registry, fqnPath, gitEntityBasicInfo, serviceRef);

    subscriptionId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef);

    acrRequestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef));

    acrRequestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    azureConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), azureConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        acrRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return acrResourceService.getLastSuccessfulBuild(
        connectorRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier, acrRequestDTO);
  }

  public NexusBuildDetailsDTO getLastSuccessfulBuildV2Nexus3(String repository, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String nexusConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef, NexusRequestDTO nexusRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      if (NexusConstant.DOCKER.equals(nexusRegistryArtifactConfig.getRepositoryFormat().getValue())) {
        NexusRegistryDockerConfig nexusRegistryDockerConfig =
            (NexusRegistryDockerConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();

        if (isEmpty(artifactRepositoryUrl)) {
          artifactRepositoryUrl = (String) nexusRegistryDockerConfig.getRepositoryUrl().fetchFinalValue();
        }

        if (isEmpty(artifactPath)) {
          artifactPath = (String) nexusRegistryDockerConfig.getArtifactPath().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryPort)) {
          repositoryPort = (String) nexusRegistryDockerConfig.getRepositoryPort().fetchFinalValue();
        }

        if (isEmpty(nexusRequestDTO.getTag())) {
          nexusRequestDTO.setTag((String) nexusRegistryArtifactConfig.getTag().fetchFinalValue());
        }

        if (isEmpty(nexusRequestDTO.getTagRegex())) {
          nexusRequestDTO.setTagRegex((String) nexusRegistryArtifactConfig.getTagRegex().fetchFinalValue());
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

      } else {
        throw new InvalidRequestException("Please select a docker artifact");
      }
    }

    nexusConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);
    repositoryPort = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef);

    artifactRepositoryUrl = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef);

    nexusRequestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef));

    nexusRequestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef));

    artifactPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        nexusRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return nexusResourceService.getSuccessfulBuild(connectorRef, repository, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, nexusRequestDTO, orgIdentifier, projectIdentifier);
  }

  public ArtifactoryBuildDetailsDTO getLastSuccessfulBuildV2Artifactory(String repository, String artifactPath,
      String repositoryFormat, String artifactRepositoryUrl, String artifactoryConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef, ArtifactoryRequestDTO artifactoryRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(repository)) {
        repository = (String) artifactoryRegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      // There is an overload in this endpoint so to make things clearer:
      // artifactPath is the artifactDirectory for Artifactory Generic
      // artifactPath is the artifactPath for Artifactory Docker
      if (isEmpty(artifactPath)) {
        if (artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().equals("docker")) {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactPath().fetchFinalValue();
        } else {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactDirectory().fetchFinalValue();
        }
      }

      if (isEmpty(artifactRepositoryUrl)) {
        artifactRepositoryUrl = (String) artifactoryRegistryArtifactConfig.getRepositoryUrl().fetchFinalValue();
      }

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier = (String) artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
      }
      if (isEmpty(artifactoryRequestDTO.getTagRegex())) {
        artifactoryRequestDTO.setTagRegex((String) artifactoryRegistryArtifactConfig.getTagRegex().fetchFinalValue());
      }

      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }

      if (isEmpty(artifactoryRequestDTO.getTag())) {
        artifactoryRequestDTO.setTag((String) artifactoryRegistryArtifactConfig.getTag().fetchFinalValue());
      }
    }

    artifactoryConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactoryConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef);
    repository = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        artifactoryRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef);
    artifactoryRequestDTO.setTag(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        artifactoryRequestDTO.getRuntimeInputYaml(), artifactoryRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef));

    artifactoryRequestDTO.setTagRegex(getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactoryRequestDTO.getTagRegex(), fqnPath,
        gitEntityBasicInfo, serviceRef));

    artifactPath = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        artifactoryRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo, serviceRef);

    artifactRepositoryUrl = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        artifactoryRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return artifactoryResourceService.getSuccessfulBuild(connectorRef, repository, artifactPath, repositoryFormat,
        artifactRepositoryUrl, artifactoryRequestDTO, orgIdentifier, projectIdentifier);
  }

  public AcrResponseDTO getBuildDetailsV2ACR(String subscriptionId, String registry, String repository,
      String azureConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(subscriptionId)) {
        subscriptionId = (String) acrArtifactConfig.getSubscriptionId().fetchFinalValue();
      }
      if (isEmpty(registry)) {
        registry = (String) acrArtifactConfig.getRegistry().fetchFinalValue();
      }
      if (isEmpty(repository)) {
        repository = (String) acrArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
    }

    subscriptionId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef);

    registry = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        registry, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef);

    azureConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return acrResourceService.getBuildDetails(
        connectorRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier);
  }

  public AcrRepositoriesDTO getAzureRepositoriesV3(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String subscriptionId, String registry,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = acrArtifactConfig.getSubscriptionId().getValue();
      }
      if (isEmpty(registry)) {
        registry = acrArtifactConfig.getRegistry().getValue();
      }
    }

    subscriptionId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef);

    registry = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
        registry, fqnPath, gitEntityBasicInfo, serviceRef);

    azureConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return acrResourceService.getRepositories(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, registry);
  }

  public AcrRegistriesDTO getAzureContainerRegisteriesV3(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String subscriptionId, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = acrArtifactConfig.getSubscriptionId().getValue();
      }
    }

    subscriptionId = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef);

    azureConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return acrResourceService.getRegistries(connectorRef, orgIdentifier, projectIdentifier, subscriptionId);
  }

  public AzureSubscriptionsDTO getAzureSubscriptionV2(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(azureConnectorIdentifier)) {
        resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
            acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
    }

    azureConnectorIdentifier = getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return azureResourceService.getSubscriptions(connectorRef, orgIdentifier, projectIdentifier);
  }

  @Data
  @Builder
  private static class EntityRefAndFQN {
    String entityRef;
    String entityFQN;
  }
}
