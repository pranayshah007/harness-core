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

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.visitor.YamlTypes;
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
import java.util.Objects;
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
      String stageIndex = getStageIndex(mergedCompleteYaml, stageIdentifier);
      if (isEmpty(serviceId)) {
        // pipelines with inline service definitions
        serviceId = getServiceRef(mergedCompleteYaml, stageIndex);
      }
      // get environment ref
      String environmentId = getEnvironmentRef(mergedCompleteYaml, stageIndex);
      List<YamlField> aliasYamlField =
          getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId);
      CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
          new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
      imagePath = CDYamlExpressionEvaluator.renderExpression(imagePath);
    }
    return imagePath;
  }

  /**
   * Returns the serviceRef using stage index and pipeline yaml.
   * Two ways to reach environment ref node
   * pipeline/stages/[0]/stage/spec/serviceConfig/serviceRef
   * pipeline/stages/[0]/stage/spec/service/serviceRef
   * get Service ref node from pipeline yaml
   *
   * @param mergedCompleteYaml pipeline yaml with templates applied
   * @param stageIndex stage index to build fqn
   * @return String
   */
  private String getServiceRef(String mergedCompleteYaml, String stageIndex) {
    String postStageFqnV1 = "/stage/spec/serviceConfig/serviceRef";
    String postStageFqnV2 = "/stage/spec/service/serviceRef";

    YamlNode service;
    try {
      service = YamlNode.fromYamlPath(mergedCompleteYaml, preStageFqn + stageIndex + postStageFqnV1);
      if (service == null) {
        service = YamlNode.fromYamlPath(mergedCompleteYaml, preStageFqn + stageIndex + postStageFqnV2);
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while resolving serviceRef from pipeline yaml");
    }
    return service != null ? service.asText() : null;
  }

  /**
   * Returns the environmentRef using stage index and pipeline yaml.
   * Two ways to reach environment ref node
   * pipeline/stages/[0]/stage/spec/infrastructure/environmentRef
   * pipeline/stages/[0]/stage/spec/environment/environmentRef
   * get Env ref node from pipeline yaml
   *
   * @param mergedCompleteYaml pipeline yaml with templates applied
   * @param stageIndex stage index to build fqn
   * @return String
   */
  private String getEnvironmentRef(String mergedCompleteYaml, String stageIndex) {
    String postStageFqnV1 = "/stage/spec/infrastructure/environmentRef";
    String postStageFqnV2 = "/stage/spec/environment/environmentRef";

    YamlNode environment;
    try {
      environment = YamlNode.fromYamlPath(mergedCompleteYaml, preStageFqn + stageIndex + postStageFqnV1);
      if (environment == null) {
        environment = YamlNode.fromYamlPath(mergedCompleteYaml, preStageFqn + stageIndex + postStageFqnV2);
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while resolving environmentRef from pipeline yaml");
    }
    return environment != null ? environment.asText() : null;
  }

  private String getStageIndex(String mergedCompleteYaml, String stageIdentifier) {
    try {
      YamlNode stages = YamlNode.fromYamlPath(mergedCompleteYaml, preStageFqn);
      List<YamlNode> stageNodes = Objects.requireNonNull(stages).asArray();
      for (YamlNode stageNode : stageNodes) {
        String pipelineStageIndex = stageNode.getField(YamlTypes.STAGE)
                                        .getNode()
                                        .getField(NGCommonEntityConstants.IDENTIFIER_KEY)
                                        .getNode()
                                        .asText();
        if (isNotEmpty(pipelineStageIndex) && pipelineStageIndex.equals(stageIdentifier)) {
          return stageNode.getFieldName();
        }
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Exception while fetching stage index for stage: [%s]", stageIdentifier));
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
          service -> yamlFields.add(getYamlField(service.getYaml(), YAMLFieldNameConstants.SERVICE)));
    }
    if (isNotEmpty(environmentId)) {
      Optional<Environment> optionalEnvironment =
          environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentId, false);
      optionalEnvironment.ifPresent(
          environment -> yamlFields.add(getYamlField(environment.getYaml(), YAMLFieldNameConstants.ENVIRONMENT)));
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
