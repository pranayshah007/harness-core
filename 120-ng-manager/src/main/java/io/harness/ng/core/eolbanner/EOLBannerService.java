/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.eolbanner;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.template.TemplateEntityType.PIPELINE_TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityType.STAGE_TEMPLATE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.eolbanner.dto.EOLBannerRequestDTO;
import io.harness.ng.core.eolbanner.dto.EOLBannerResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class EOLBannerService {
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private TemplateResourceClient templateResourceClient;

  public EOLBannerResponseDTO checkPipelineUsingV1Stage(
      @NonNull String accountId, @NonNull EOLBannerRequestDTO requestDTO) {
    boolean showBanner = false;
    List<String> stageIdentifiersUsingV1Stage = new ArrayList<>();
    List<String> failures = new ArrayList<>();

    try {
      final PMSPipelineResponseDTO existingPipeline =
          NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(requestDTO.getPipelineIdentifier(),
              accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), null, null, null, "true"));
      if (existingPipeline == null || isEmpty(existingPipeline.getYamlPipeline())) {
        // return or throw? // want to be silent
        throw new InvalidRequestException(
            format("pipeline doesn't exist with this identifier: %s", requestDTO.getPipelineIdentifier()));
      }
      String pipelineYaml = existingPipeline.getYamlPipeline();

      YamlField pipelineYamlField = getYamlField(pipelineYaml, "pipeline");
      // checking if pipeline contains template refs
      if (TemplateRefHelper.hasTemplateRef(pipelineYaml)) {
        try {
          // not checking for template access
          pipelineYaml =
              NGRestUtils
                  .getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId,
                      requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), null, null, null, null, null,
                      null, null, null, "true",
                      TemplateApplyRequestDTO.builder().originalEntityYaml(pipelineYaml).checkForAccess(false).build(),
                      false))
                  .getMergedPipelineYaml();
        } catch (Exception ex) {
          throw new InvalidRequestException(
              format("error occurred in generating resolved pipeline yaml: %s", ex.getMessage()));
        }
        pipelineYamlField = getYamlField(pipelineYaml, "pipeline");
      }
      ArrayNode stageArrayNode = (ArrayNode) pipelineYamlField.getNode().getField("stages").getNode().getCurrJsonNode();
      if (stageArrayNode.size() < 1) {
        return EOLBannerResponseDTO.builder().build();
      }

      // Loop over each node of stages and update their yaml
      for (int currentIndex = 0; currentIndex < stageArrayNode.size(); currentIndex++) {
        JsonNode currentNode = stageArrayNode.get(currentIndex);
        YamlNode stageYamlNode = new YamlNode(currentNode);
        // checking parallel stages exist at this node
        boolean isCurrentNodeParallel = checkParallelStagesExistence(stageYamlNode);
        if (isCurrentNodeParallel) {
          ArrayNode parallelStageArrayNode = (ArrayNode) stageYamlNode.getField("parallel").getNode().getCurrJsonNode();
          // Loop over each node of parallel stages and check if they are v1
          for (int currentParallelIndex = 0; currentParallelIndex < parallelStageArrayNode.size();
               currentParallelIndex++) {
            JsonNode currentParallelNode = parallelStageArrayNode.get(currentParallelIndex);
            YamlNode parallelStageYamlNode = new YamlNode(currentParallelNode);

            if (checkIfV1Stage(requestDTO, accountId, parallelStageYamlNode, stageIdentifiersUsingV1Stage)) {
              showBanner = true;
            }
          }
        } else {
          if (checkIfV1Stage(requestDTO, accountId, stageYamlNode, stageIdentifiersUsingV1Stage)) {
            showBanner = true;
          }
        }
      }
    } catch (WingsException e) {
      throw new InvalidRequestException(
          String.format("Exception while checking if pipeline using v1 stages: %s", e.getMessage()));
    } catch (Exception e) {
      failures.add(e.getMessage());
    }
    return EOLBannerResponseDTO.builder()
        .showBanner(showBanner)
        .stageIdentifiers(stageIdentifiersUsingV1Stage)
        .failures(failures)
        .build();
  }

  private YamlField getYamlField(String yaml, String fieldName) {
    try {
      return YamlUtils.readTree(yaml).getNode().getField(fieldName);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("not able to parse %s yaml because of error: %s", fieldName, e.getMessage()));
    }
  }

  private boolean checkIfV1Stage(@NonNull EOLBannerRequestDTO requestDto, @NonNull String accountId,
      YamlNode stageParentNode, List<String> stageIdentifiers) {
    if (!"Deployment".equals(getStageType(stageParentNode))) {
      return false;
    }

    YamlNode stageNode = stageParentNode.getField(YAMLFieldNameConstants.STAGE).getNode();
    return checkStageYaml(accountId, stageNode, requestDto, stageIdentifiers);
  }

  private boolean checkStageYaml(
      String accountId, YamlNode stageNode, EOLBannerRequestDTO requestDto, List<String> stageIdentifiers) {
    try {
      return checkStage(stageNode, accountId, requestDto, stageIdentifiers);
    } catch (Exception ex) {
      log.error("Error while checking if stage using V1 structure");
      return false;
    }
  }

  public boolean checkStage(
      YamlNode stageNode, String accountId, EOLBannerRequestDTO requestDto, List<String> stageIdentifiers) {
    if (checkDeploymentStageIsV1(stageNode)) {
      stageIdentifiers.add(
          stageNode.getField(YAMLFieldNameConstants.IDENTIFIER).getNode().getCurrJsonNode().textValue());
      return true;
    }

    return false;
  }

  private String getStageType(YamlNode stageParentNode) {
    YamlNode stageNode = stageParentNode.getField(YAMLFieldNameConstants.STAGE).getNode();
    if (stageNode != null && stageNode.getField(YAMLFieldNameConstants.TYPE) != null) {
      return stageNode.getField(YAMLFieldNameConstants.TYPE).getNode().getCurrJsonNode().textValue();
    }
    return null;
  }

  private boolean checkParallelStagesExistence(YamlNode currentYamlNode) {
    return currentYamlNode.getField(YAMLFieldNameConstants.PARALLEL) != null;
  }

  public EOLBannerResponseDTO checkTemplateUsingV1Stage(
      @NonNull String accountId, @NonNull EOLBannerRequestDTO requestDTO) {
    List<String> failures = new ArrayList<>();
    try {
      TemplateResponseDTO response = NGRestUtils.getResponse(
          templateResourceClient.get(requestDTO.getTemplateIdentifier(), accountId, requestDTO.getOrgIdentifier(),
              requestDTO.getProjectIdentifier(), requestDTO.getVersionLabel(), false, "true"));

      // do not show banner for other template types
      if (PIPELINE_TEMPLATE.equals(response.getTemplateEntityType())) {
        return checkPipelineTemplate(accountId, requestDTO, response.getYaml());
      } else if (STAGE_TEMPLATE.equals(response.getTemplateEntityType())) {
        return checkStageTemplate(response.getYaml());
      }
    } catch (WingsException e) {
      throw new InvalidRequestException(
          String.format("Exception while checking if template using v1 stages: %s", e.getMessage()));
    } catch (Exception e) {
      failures.add(e.getMessage());
    }

    return EOLBannerResponseDTO.builder().showBanner(false).failures(failures).build();
  }

  private EOLBannerResponseDTO checkStageTemplate(String stageTemplateYaml) {
    boolean showBanner = false;

    YamlNode stageNode = getYamlField(stageTemplateYaml, YAMLFieldNameConstants.TEMPLATE)
                             .getNode()
                             .getField(YAMLFieldNameConstants.SPEC)
                             .getNode();

    String stageType = stageNode != null
        ? stageNode.getField(YAMLFieldNameConstants.TYPE).getNode().getCurrJsonNode().textValue()
        : null;

    if ("Deployment".equals(stageType)) {
      showBanner = checkDeploymentStageIsV1(stageNode);
    }

    return EOLBannerResponseDTO.builder().showBanner(showBanner).stageIdentifiers(new ArrayList<>()).build();
  }

  private boolean checkDeploymentStageIsV1(YamlNode stageNode) {
    if (stageNode == null) {
      return false;
    }
    YamlField infraField = stageNode.getField(YAMLFieldNameConstants.SPEC)
                               .getNode()
                               .getField(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    YamlField serviceConfigField =
        stageNode.getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.SERVICE_CONFIG);

    if (infraField != null || serviceConfigField != null) {
      return true;
    }

    return false;
  }

  private EOLBannerResponseDTO checkPipelineTemplate(
      @NotNull String accountId, @NotNull EOLBannerRequestDTO requestDTO, String templateYaml) {
    boolean showBanner = false;
    List<String> stageIdentifiersUsingV1Stage = new ArrayList<>();

    String resolvedTemplateYaml =
        NGRestUtils
            .getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, requestDTO.getOrgIdentifier(),
                requestDTO.getProjectIdentifier(), null, null, null, null, null, null, null, null, "true",
                TemplateApplyRequestDTO.builder().originalEntityYaml(templateYaml).checkForAccess(false).build(),
                false))
            .getMergedPipelineYaml();

    YamlField pipelineYamlField = getYamlField(resolvedTemplateYaml, YAMLFieldNameConstants.TEMPLATE);

    ArrayNode stageArrayNode = (ArrayNode) pipelineYamlField.getNode()
                                   .getField(YAMLFieldNameConstants.SPEC)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.STAGES)
                                   .getNode()
                                   .getCurrJsonNode();
    if (stageArrayNode.size() < 1) {
      return EOLBannerResponseDTO.builder().build();
    }

    // Loop over each node of stages and check their versions
    for (int currentIndex = 0; currentIndex < stageArrayNode.size(); currentIndex++) {
      JsonNode currentNode = stageArrayNode.get(currentIndex);
      YamlNode stageYamlNode = new YamlNode(currentNode);
      // checking parallel stages exist at this node
      boolean isCurrentNodeParallel = checkParallelStagesExistence(stageYamlNode);
      if (isCurrentNodeParallel) {
        ArrayNode parallelStageArrayNode = (ArrayNode) stageYamlNode.getField("parallel").getNode().getCurrJsonNode();
        // Loop over each node of parallel stages and check if they are v1
        for (int currentParallelIndex = 0; currentParallelIndex < parallelStageArrayNode.size();
             currentParallelIndex++) {
          JsonNode currentParallelNode = parallelStageArrayNode.get(currentParallelIndex);
          YamlNode parallelStageYamlNode = new YamlNode(currentParallelNode);

          if (checkIfV1Stage(requestDTO, accountId, parallelStageYamlNode, stageIdentifiersUsingV1Stage)) {
            showBanner = true;
          }
        }
      } else {
        if (checkIfV1Stage(requestDTO, accountId, stageYamlNode, stageIdentifiersUsingV1Stage)) {
          showBanner = true;
        }
      }
    }

    return EOLBannerResponseDTO.builder().showBanner(showBanner).stageIdentifiers(stageIdentifiersUsingV1Stage).build();
  }
}
