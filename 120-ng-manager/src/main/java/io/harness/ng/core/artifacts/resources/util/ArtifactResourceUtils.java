/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.evaluators.YamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactResourceUtils {
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject TemplateResourceClient templateResourceClient;

  // Checks whether field is fixed value or not, if empty then also we return false for fixed value.
  public static boolean isFieldFixedValue(String fieldValue) {
    return !EmptyPredicate.isEmpty(fieldValue) && !NGExpressionUtils.isRuntimeOrExpressionField(fieldValue);
  }

  private String getMergedCompleteYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (EmptyPredicate.isEmpty(pipelineIdentifier)) {
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
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (EngineExpressionEvaluator.hasExpressions(imagePath)) {
      String mergedCompleteYaml = getMergedCompleteYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
      if (EmptyPredicate.isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
        mergedCompleteYaml = applyTemplatesOnGivenYaml(
            accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo);
      }
      YamlExpressionEvaluator yamlExpressionEvaluator = new YamlExpressionEvaluator(mergedCompleteYaml, fqnPath);
      imagePath = yamlExpressionEvaluator.renderExpression(imagePath);
    }
    return imagePath;
  }
}
