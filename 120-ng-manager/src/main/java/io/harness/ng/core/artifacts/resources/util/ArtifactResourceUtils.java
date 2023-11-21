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
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetValidatorType;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
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
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
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
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.artifacts.resources.custom.CustomScriptInfo;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
import io.harness.ng.core.buckets.resources.s3.BucketResponseDTO;
import io.harness.ng.core.buckets.resources.s3.FilePathDTO;
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
import io.harness.template.resources.beans.NGTemplateConstants;
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
import java.util.HashMap;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
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
  @Inject S3ResourceService s3ResourceService;
  @Inject BucketsResourceUtils bucketsResourceUtils;

  public final String SERVICE_GIT_BRANCH = "serviceGitBranch";
  public final String ENV_GIT_BRANCH = "envGitBranch";

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

  public boolean isRemoteService(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceRef) {
    Optional<ServiceEntity> optionalService =
        serviceEntityService.getMetadata(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, false);
    return optionalService.filter(serviceEntity -> StoreType.REMOTE.equals(serviceEntity.getStoreType())).isPresent();
  }

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedFieldValueWithYamlExpressionEvaluator(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String runtimeInputYaml, String fieldValuePath, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId, CDYamlExpressionEvaluator yamlExpressionEvaluator) {
    final ParameterField<String> fieldValueParameterField =
        RuntimeInputValuesValidator.getInputSetParameterField(fieldValuePath);
    if (fieldValueParameterField == null) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .yamlExpressionEvaluator(yamlExpressionEvaluator)
          .value(fieldValuePath)
          .build();
    } else if (!fieldValueParameterField.isExpression()) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .yamlExpressionEvaluator(yamlExpressionEvaluator)
          .value(fieldValueParameterField.getValue())
          .build();
    } else {
      // this check assumes ui sends -1 as pipeline identifier when pipeline is under construction
      if ("-1".equals(pipelineIdentifier)) {
        throw new InvalidRequestException(String.format(
            "Couldn't resolve artifact image path expression %s, as pipeline has not been saved yet.", fieldValuePath));
      }
      if (yamlExpressionEvaluator == null) {
        YamlExpressionEvaluatorWithContext evaluatorWithContext =
            getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
                runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId);
        yamlExpressionEvaluator = evaluatorWithContext.getYamlExpressionEvaluator();
      }
      String resolvedFieldValuePath = yamlExpressionEvaluator.renderExpression(
          fieldValuePath, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      final ParameterField<String> fieldValueParameter =
          RuntimeInputValuesValidator.getInputSetParameterField(resolvedFieldValuePath);
      if (fieldValueParameter == null || fieldValueParameter.isExpression()) {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .yamlExpressionEvaluator(yamlExpressionEvaluator)
            .value(null)
            .build();
      } else {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .yamlExpressionEvaluator(yamlExpressionEvaluator)
            .value(fieldValueParameter.getValue())
            .build();
      }
    }
  }

  CDYamlExpressionEvaluator getYamlExpressionEvaluator(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId) {
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
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, new HashMap<>());
    return new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
  }

  public YamlExpressionEvaluatorWithContext getYamlExpressionEvaluatorWithContext(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
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

    // add context needed for fetching git entities
    Map<String, String> contextMap = buildContextMap(fqnObjectMap, stageIdentifier);

    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, contextMap);
    return YamlExpressionEvaluatorWithContext.builder()
        .yamlExpressionEvaluator(new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField))
        .contextMap(contextMap)
        .build();
  }

  private Map<String, String> buildContextMap(Map<FQN, Object> fqnObjectMap, String stageIdentifier) {
    Map<String, String> contextMap = new HashMap<>();

    for (Map.Entry<FQN, Object> mapEntry : fqnObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && NGTemplateConstants.GIT_BRANCH.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        String parentFieldName = mapEntry.getKey().getParent().getFieldName();
        if (YamlTypes.SERVICE_ENTITY.equals(parentFieldName)) {
          contextMap.put(SERVICE_GIT_BRANCH, ((TextNode) mapEntry.getValue()).asText());
          continue;
        }

        if (YamlTypes.ENVIRONMENT_YAML.equals(parentFieldName)) {
          contextMap.put(ENV_GIT_BRANCH, ((TextNode) mapEntry.getValue()).asText());
        }
      }
    }

    return contextMap;
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
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, new HashMap<>());
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

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedExpression(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml, String param, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId, int secretFunctor,
      CDExpressionEvaluator cdExpressionEvaluator) {
    if (EngineExpressionEvaluator.hasExpressions(param)) {
      if (cdExpressionEvaluator == null) {
        cdExpressionEvaluator = getCDExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId, secretFunctor);
      }
      param = cdExpressionEvaluator.renderExpression(param);
    }
    return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
        .cdExpressionEvaluator(cdExpressionEvaluator)
        .value(param)
        .build();
  }

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedFieldValueWithCDExpressionEvaluator(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml, String param,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId, int secretFunctor,
      CDExpressionEvaluator cdExpressionEvaluator) {
    final ParameterField<String> fieldValueParameterField =
        RuntimeInputValuesValidator.getInputSetParameterField(param);
    if (fieldValueParameterField == null) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .cdExpressionEvaluator(cdExpressionEvaluator)
          .value(param)
          .build();
    } else if (!fieldValueParameterField.isExpression()) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .cdExpressionEvaluator(cdExpressionEvaluator)
          .value(fieldValueParameterField.getValue())
          .build();
    } else {
      // this check assumes ui sends -1 as pipeline identifier when pipeline is under construction
      if ("-1".equals(pipelineIdentifier)) {
        throw new InvalidRequestException(String.format(
            "Couldn't resolve artifact image path expression %s, as pipeline has not been saved yet.", param));
      }
      if (cdExpressionEvaluator == null) {
        cdExpressionEvaluator = getCDExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId, secretFunctor);
      }
      String resolvedFieldValuePath =
          cdExpressionEvaluator.renderExpression(param, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      final ParameterField<String> fieldValueParameter =
          RuntimeInputValuesValidator.getInputSetParameterField(resolvedFieldValuePath);
      if (fieldValueParameter == null || fieldValueParameter.isExpression()) {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .cdExpressionEvaluator(cdExpressionEvaluator)
            .value(null)
            .build();
      } else {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .cdExpressionEvaluator(cdExpressionEvaluator)
            .value(fieldValueParameter.getValue())
            .build();
      }
    }
  }

  CDExpressionEvaluator getCDExpressionEvaluator(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId, int secretFunctor) {
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

    // add context needed for fetching git entities
    Map<String, String> contextMap = buildContextMap(fqnObjectMap, stageIdentifier);

    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, contextMap);
    return new CDExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField, secretFunctor);
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

  private List<YamlField> getAliasYamlFields(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceId, String environmentId, Map<String, String> contextMap) {
    List<YamlField> yamlFields = new ArrayList<>();
    String serviceGitBranch = contextMap.get(SERVICE_GIT_BRANCH);
    if (isNotEmpty(serviceId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceGitBranch)) {
        Optional<ServiceEntity> optionalService =
            serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceId, false);
        optionalService.ifPresent(
            service -> yamlFields.add(getYamlField(service.fetchNonEmptyYaml(), YAMLFieldNameConstants.SERVICE)));
      }
    }
    String envGitBranch = contextMap.get(ENV_GIT_BRANCH);
    if (isNotEmpty(environmentId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(envGitBranch)) {
        Optional<Environment> optionalEnvironment =
            environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentId, false);
        optionalEnvironment.ifPresent(environment
            -> yamlFields.add(getYamlField(environment.fetchNonEmptyYaml(), YAMLFieldNameConstants.ENVIRONMENT)));
      }
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
    return locateArtifactInService(accountId, orgId, projectId, serviceRef, imageTagFqn, null);
  }

  /**
   * Locates ArtifactConfig in a service entity picked from a git branch for a given FQN of type
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   * @return ArtifactConfig
   */
  @NotNull
  public ArtifactConfig locateArtifactInService(
      String accountId, String orgId, String projectId, String serviceRef, String imageTagFqn, String gitBranch) {
    String TEMPLATE_ACCESS_PERMISSION = "core_template_access";
    YamlNode artifactTagLeafNode;
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(gitBranch)) {
      artifactTagLeafNode =
          serviceEntityService.getYamlNodeForFqn(accountId, orgId, projectId, serviceRef, imageTagFqn);
    }

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

    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    groupId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                  .getValue();
    artifactRepositoryUrl =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();
    artifactId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                     .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();
    packageName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef, null)
                      .getValue();
    group = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, group, fqnPath, gitEntityBasicInfo, serviceRef, null)
                .getValue();
    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();
    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef, null)
                       .getValue();
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

    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    groupId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                  .getValue();
    artifactRepositoryUrl =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();
    artifactId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                     .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();
    packageName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef, null)
                      .getValue();
    group = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, group, fqnPath, gitEntityBasicInfo, serviceRef, null)
                .getValue();
    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();
    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef, null)
                       .getValue();
    return nexusResourceService.getBuildDetails(connectorRef, repositoryName, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension,
        classifier, packageName, group);
  }

  public GcrResponseDTO getBuildDetailsV2GCR(String imagePath, String registryHostname, String gcrConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) gcrArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(registryHostname)) {
        registryHostname = (String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue();
      }

      if (isEmpty(gcrConnectorIdentifier)) {
        gcrConnectorIdentifier = (String) gcrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }
    // todo(hinger): resolve other expressions here?
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    gcrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcrConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();
    registryHostname = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registryHostname, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                           .getValue();
    return gcrResourceService.getBuildDetails(
        connectorRef, imagePath, registryHostname, orgIdentifier, projectIdentifier);
  }

  public GARResponseDTO getBuildDetailsV2GAR(String gcpConnectorIdentifier, String region, String repositoryName,
      String project, String pkg, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String version, String versionRegex, String fqnPath, String runtimeInputYaml,
      String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

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
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolvedConnectorRef
    String resolvedConnectorRef = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                      .getValue();

    // Getting the resolvedRegion
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                .getValue();

    // Getting the resolvedRepositoryName
    String resolvedRepositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                        .getValue();

    // Getting the resolvedProject
    String resolvedProject = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                 .getValue();

    // Getting the resolvedPackage
    String resolvedPackage =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, pkg, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
            .getValue();

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

    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), repositoryName, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                         .getValue();

    gcpConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), gcpConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    region = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                 .getValue();

    garRequestDTO.setVersion(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersion(), fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue());

    project = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), project, fqnPath, gitEntityBasicInfo, serviceRef, null)
                  .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    pkg = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), pkg, fqnPath, gitEntityBasicInfo, serviceRef, null)
              .getValue();

    garRequestDTO.setVersionRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersionRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                      .getValue());

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
    String resolvedRepository =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();

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
    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef, null)
                           .getValue();
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
    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef, null)
                           .getValue();
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
    ResolvedFieldValueWithYamlExpressionEvaluator resolvedFieldValueWithYamlExpressionEvaluator = null;
    ResolvedFieldValueWithYamlExpressionEvaluator resolvedFieldValueWithCDExpressionEvaluator = null;
    Map<String, String> inputVariables = NGVariablesUtils.getStringMapVariables(inputs, 0L);

    if (isNotEmpty(customScriptInfo.getRuntimeInputYaml())) {
      resolvedFieldValueWithCDExpressionEvaluator = getResolvedExpression(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), script, fqnPath, gitEntityBasicInfo, serviceRef,
          secretFunctor, null);
      script = resolvedFieldValueWithCDExpressionEvaluator.getValue();

      for (Map.Entry<String, String> entry : inputVariables.entrySet()) {
        resolvedFieldValueWithCDExpressionEvaluator = getResolvedFieldValueWithCDExpressionEvaluator(accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(),
            entry.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, secretFunctor,
            resolvedFieldValueWithCDExpressionEvaluator.getCdExpressionEvaluator());

        inputVariables.put(entry.getKey(), resolvedFieldValueWithCDExpressionEvaluator.getValue());
      }

      resolvedFieldValueWithYamlExpressionEvaluator = getResolvedFieldValueWithYamlExpressionEvaluator(accountId,
          orgIdentifier, projectIdentifier, pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), arrayPath,
          fqnPath, gitEntityBasicInfo, serviceRef, null);
      arrayPath = resolvedFieldValueWithYamlExpressionEvaluator.getValue();
      resolvedFieldValueWithYamlExpressionEvaluator =
          getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), versionPath, fqnPath, gitEntityBasicInfo,
              serviceRef, resolvedFieldValueWithYamlExpressionEvaluator.getYamlExpressionEvaluator());
      versionPath = resolvedFieldValueWithYamlExpressionEvaluator.getValue();
    }

    return customResourceService.getBuilds(script, versionPath, arrayPath, inputVariables, accountId, orgIdentifier,
        projectIdentifier, secretFunctor, delegateSelector);
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

    dockerConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, requestDTO.getRuntimeInputYaml(), dockerConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                    .getValue();
    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef, null)
                    .getValue();

    requestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), requestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                          .getValue());

    requestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), requestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                               .getValue());

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

    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                    .getValue();

    gcrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    gcrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                             .getValue());

    gcrRequestDTO.setRegistryHostname(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getRegistryHostname(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                          .getValue());

    gcrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                  .getValue());

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

    ecrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    ecrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                             .getValue());

    ecrRequestDTO.setRegion(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getRegion(), fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                .getValue());

    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                    .getValue();

    registryId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), registryId, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                     .getValue();

    ecrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                  .getValue());

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

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), registry, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                   .getValue();

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), subscriptionId, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                         .getValue();

    acrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                             .getValue());

    acrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                  .getValue());

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), azureConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                   .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                     .getValue();

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
    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                   .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), repositoryPort, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                         .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                .getValue();

    nexusRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTag(), fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                               .getValue());

    nexusRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                    .getValue());

    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                       .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                     .getValue();

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
    artifactoryConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef, null)
                                         .getValue();
    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                     .getValue();
    artifactoryRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef, null)
                                     .getValue());

    artifactoryRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef, null)
                                          .getValue());

    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                       .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl,
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                .getValue();

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

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registry, fqnPath, gitEntityBasicInfo, serviceRef, null)
                   .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef, null)
                     .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

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
    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registry, fqnPath, gitEntityBasicInfo, serviceRef, null)
                   .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

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
    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef, null)
                         .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

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

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                                   .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return azureResourceService.getSubscriptions(connectorRef, orgIdentifier, projectIdentifier);
  }

  public List<FilePathDTO> getFilePathsForServiceV2S3(String region, String awsConnectorIdentifier, String bucketName,
      String filePathRegex, String fileFilter, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String fqnPath, String runtimeInputYaml, String serviceRef,
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(bucketName)) {
        bucketName = (String) amazonS3ArtifactConfig.getBucketName().fetchFinalValue();
      }
      if (StringUtils.isBlank(fileFilter) && ParameterField.isNotNull(amazonS3ArtifactConfig.getFileFilter())) {
        fileFilter = (String) amazonS3ArtifactConfig.getFileFilter().fetchFinalValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                                .getValue();

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();

    // Getting the resolved bucketName in case of expressions
    String resolvedBucketName =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, bucketName, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();
    // Getting the resolved fileFilter in case of expressions
    fileFilter = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, fileFilter, fqnPath, gitEntityBasicInfo, serviceRef, null)
                     .getValue();

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> s3ArtifactPaths = s3ResourceService.getFilePaths(
        connectorRef, resolvedRegion, resolvedBucketName, fileFilter, orgIdentifier, projectIdentifier);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return artifactPathDTOS;
  }

  public List<FilePathDTO> getFilePathsS3(String region, String awsConnectorIdentifier, String bucketName,
      String filePathRegex, String fileFilter, String accountId, String orgIdentifier, String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> s3ArtifactPaths =
        s3ResourceService.getFilePaths(connectorRef, region, bucketName, fileFilter, orgIdentifier, projectIdentifier);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return artifactPathDTOS;
  }

  public List<BucketResponseDTO> getBucketsV2WithServiceV2S3(String region, String awsConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                                .getValue();

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();
      bucketResponse.add(bucket);
    }

    return bucketResponse;
  }

  public List<BucketResponseDTO> getBucketsV2S3(
      String region, String awsConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();

      bucketResponse.add(bucket);
    }

    return bucketResponse;
  }

  public Map<String, String> getBucketsInManifestsS3(String region, String awsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      if (StringUtils.isBlank(region)) {
        region = storeConfig.getRegion().getValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, null, serviceRef, null)
                                .getValue();

    // Getting the resolved region in case of expressions
    String resolvedConnectorIdentifier =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, null, serviceRef, null)
            .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier);
  }

  public Map<String, String> getBucketsS3(String region, String awsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String fqnPath, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      if (isEmpty(region)) {
        region = storeConfig.getRegion().getValue();
      }
      if (isEmpty(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier);
  }

  @Data
  @Builder
  private static class EntityRefAndFQN {
    String entityRef;
    String entityFQN;
  }
}
