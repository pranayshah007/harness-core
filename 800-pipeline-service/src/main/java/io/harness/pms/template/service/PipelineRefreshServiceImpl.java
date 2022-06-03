/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.template.beans.refresh.NodeInfo;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.refresh.YamlDiffResponseDTO;
import io.harness.template.beans.refresh.YamlFullRefreshResponseDTO;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDC)
public class PipelineRefreshServiceImpl implements PipelineRefreshService {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private PMSPipelineService pmsPipelineService;

  @Override
  public boolean refreshTemplateInputsInPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    PipelineEntity pipelineEntity = getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier);
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      RefreshResponseDTO refreshResponseDTO =
          pmsPipelineTemplateHelper.getRefreshedYaml(accountId, orgId, projectId, pipelineEntity.getYaml());
      updatePipelineWithYaml(pipelineEntity, refreshResponseDTO.getRefreshedYaml());
    }
    return true;
  }

  private void updatePipelineWithYaml(PipelineEntity pipelineEntity, String refreshedYaml) {
    PipelineEntity updatedPipelineEntity = pipelineEntity.withYaml(refreshedYaml);
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.updatePipelineYaml(updatedPipelineEntity, ChangeType.MODIFY);
    PipelineCRUDErrorResponse.checkForGovernanceErrorAndThrow(pipelineCRUDResult.getGovernanceMetadata());
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    PipelineEntity pipelineEntity = getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier);

    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
          pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(
              accountId, orgId, projectId, pipelineEntity.getYaml());
      if (!validateTemplateInputsResponse.isValidYaml()) {
        validateTemplateInputsResponse.getErrorNodeSummary().setNodeInfo(
            NodeInfo.builder().identifier(pipelineIdentifier).name(pipelineEntity.getName()).build());
        return validateTemplateInputsResponse;
      }
    }
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  private PipelineEntity getPipelineEntity(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.get(accountId, orgId, projectId, pipelineIdentifier, false);
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given id: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  @Override
  public YamlDiffResponseDTO getYamlDiff(String accountId, String orgId, String projectId, String pipelineIdentifier) {
    PipelineEntity pipelineEntity = getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier);

    String pipelineYaml = pipelineEntity.getYaml();
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      RefreshResponseDTO refreshResponseDTO =
          pmsPipelineTemplateHelper.getRefreshedYaml(accountId, orgId, projectId, pipelineEntity.getYaml());
      return YamlDiffResponseDTO.builder()
          .originalYaml(pipelineYaml)
          .refreshedYaml(refreshResponseDTO.getRefreshedYaml())
          .build();
    }
    return YamlDiffResponseDTO.builder().originalYaml(pipelineYaml).refreshedYaml(pipelineYaml).build();
  }

  @Override
  public boolean recursivelyRefreshAllTemplateInputsInPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    PipelineEntity pipelineEntity = getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier);

    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      YamlFullRefreshResponseDTO refreshResponse =
          pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(accountId, orgId, projectId, pipelineEntity.getYaml());
      if (refreshResponse.isShouldRefreshYaml()) {
        updatePipelineWithYaml(pipelineEntity, refreshResponse.getRefreshedYaml());
      }
    }
    return true;
  }
}
