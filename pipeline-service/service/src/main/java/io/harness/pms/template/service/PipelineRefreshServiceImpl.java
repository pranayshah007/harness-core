/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template.service;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.template.utils.PipelineTemplateUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.template.remote.TemplateResourceClient;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PipelineRefreshServiceImpl implements PipelineRefreshService {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private PMSPipelineService pmsPipelineService;

  @Override
  public boolean refreshTemplateInputsInPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String loadFromCache) {
    PipelineEntity pipelineEntity =
        getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE);
    RefreshResponseDTO refreshResponseDTO = pmsPipelineTemplateHelper.getRefreshedYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    if (refreshResponseDTO != null) {
      pmsPipelineService.validateAndUpdatePipeline(pipelineEntity.withYaml(refreshResponseDTO.getRefreshedYaml()),
          ChangeType.MODIFY, true, HarnessYamlVersion.V0, null);
    }
    return true;
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String loadFromCache) {
    PipelineEntity pipelineEntity = getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier, loadFromCache);

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(
            accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    if (!validateTemplateInputsResponse.isValidYaml()) {
      validateTemplateInputsResponse.getErrorNodeSummary().setNodeInfo(
          NodeInfo.builder().identifier(pipelineIdentifier).name(pipelineEntity.getName()).build());
      return validateTemplateInputsResponse;
    }
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  private PipelineEntity getPipelineEntity(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId,
        pipelineIdentifier, false, false, false, PipelineTemplateUtils.parseLoadFromCache(loadFromCache));
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given id: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  @Override
  public YamlDiffResponseDTO getYamlDiff(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String loadFromCache) {
    PipelineEntity pipelineEntity =
        getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE);

    String pipelineYaml = pipelineEntity.getYaml();
    RefreshResponseDTO refreshResponseDTO = pmsPipelineTemplateHelper.getRefreshedYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    return YamlDiffResponseDTO.builder()
        .originalYaml(pipelineYaml)
        .refreshedYaml(refreshResponseDTO.getRefreshedYaml())
        .build();
  }

  @Override
  public boolean recursivelyRefreshAllTemplateInputsInPipeline(String accountId, String orgId, String projectId,
      String pipelineIdentifier, GitEntityUpdateInfoDTO gitEntityBasicInfo, String loadFromCache) {
    PipelineEntity pipelineEntity =
        getPipelineEntity(accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE);
    YamlFullRefreshResponseDTO refreshResponse = pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    if (refreshResponse != null && refreshResponse.isShouldRefreshYaml()) {
      pmsPipelineService.validateAndUpdatePipeline(pipelineEntity.withYaml(refreshResponse.getRefreshedYaml()),
          ChangeType.MODIFY, true, HarnessYamlVersion.V0, null);
    }
    return true;
  }
}
