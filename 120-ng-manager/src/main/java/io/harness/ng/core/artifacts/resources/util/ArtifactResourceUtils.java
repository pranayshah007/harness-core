/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactResourceUtils {
  private final String preStageFqn = "pipeline/stages/";
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject TemplateResourceClient templateResourceClient;
  @Inject ServiceEntityService serviceEntityService;
  @Inject EnvironmentService environmentService;

  // Checks whether field is fixed value or not, if empty then also we return false for fixed value.
  public static boolean isFieldFixedValue(String fieldValue) {
    return !isEmpty(fieldValue) && !NGExpressionUtils.isRuntimeOrExpressionField(fieldValue);
  }

  private String getMergedCompleteYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isEmpty(pipelineIdentifier)) {
      return runtimeInputYaml;
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
        gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getDefaultFromOtherRepo(),
        TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build()));
    return response.getMergedPipelineYaml();
  }

  public String getResolvedImagePath(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String imagePath, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
    if (EngineExpressionEvaluator.hasExpressions(imagePath)) {
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

      if (isEmpty(serviceId)) {
        // pipelines with inline service definitions
        serviceId = getServiceRef(fqnObjectMap, stageIdentifier);
      }
      // get environment ref
      String environmentId = getEnvironmentRef(fqnObjectMap, stageIdentifier);
      List<YamlField> aliasYamlField =
          getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId);
      CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
          new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
      imagePath = CDYamlExpressionEvaluator.renderExpression(imagePath);
    }
    return imagePath;
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
      if (stageIdentifier.equals(nodeStageIdentifier) && "serviceRef".equals(fieldName)) {
        return mapEntry.getValue().toString();
      }
    }
    return null;
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
      if (stageIdentifier.equals(nodeStageIdentifier) && "environmentRef".equals(fieldName)) {
        return mapEntry.getValue().toString();
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
    YamlNode artifactTagLeafNode =
        serviceEntityService.getYamlNodeForFqn(accountId, orgId, projectId, serviceRef, imageTagFqn);

    YamlNode artifactSpecNode = artifactTagLeafNode.getParentNode().getParentNode();

    final ArtifactInternalDTO artifactDTO;
    try {
      artifactDTO = YamlUtils.read(artifactSpecNode.toString(), ArtifactInternalDTO.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to read artifact spec in service yaml", e);
    }

    return artifactDTO.spec;
  }

  static class ArtifactInternalDTO {
    @JsonProperty("type") ArtifactSourceType sourceType;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    ArtifactConfig spec;
  }
}
