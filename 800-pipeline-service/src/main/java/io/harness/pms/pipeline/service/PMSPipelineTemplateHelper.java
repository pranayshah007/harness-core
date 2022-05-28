/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.exception.NGTemplateResolveException;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.refresh.YamlFullRefreshResponseDTO;
import io.harness.template.remote.TemplateResourceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineTemplateHelper {
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final TemplateResourceClient templateResourceClient;
  private final PipelineEnforcementService pipelineEnforcementService;

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(PipelineEntity pipelineEntity) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml());
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      String accountId, String orgId, String projectId, String yaml) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      String accountId, String orgId, String projectId, String yaml, boolean checkForTemplateAccess) {
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.NG_TEMPLATES)
        && pipelineEnforcementService.isFeatureRestricted(accountId, FeatureRestrictionName.TEMPLATE_SERVICE.name())) {
      String TEMPLATE_RESOLVE_EXCEPTION_MSG = "Exception in resolving template refs in given pipeline yaml.";
      long start = System.currentTimeMillis();
      try {
        GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
        if (gitEntityInfo != null) {
          return NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYaml(accountId, orgId, projectId,
              gitEntityInfo.getBranch(), gitEntityInfo.getYamlGitConfigId(), true,
              TemplateApplyRequestDTO.builder()
                  .originalEntityYaml(yaml)
                  .checkForAccess(checkForTemplateAccess)
                  .build()));
        }
        return NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYaml(accountId, orgId, projectId,
            null, null, null,
            TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).checkForAccess(checkForTemplateAccess).build()));
      } catch (InvalidRequestException e) {
        if (e.getMetadata() instanceof TemplateInputsErrorMetadataDTO) {
          throw new NGTemplateResolveException(
              TEMPLATE_RESOLVE_EXCEPTION_MSG, USER, (TemplateInputsErrorMetadataDTO) e.getMetadata());
        } else {
          throw new NGTemplateException(e.getMessage(), e);
        }
      } catch (NGTemplateResolveException e) {
        throw new NGTemplateResolveException(e.getMessage(), USER, e.getErrorResponseDTO());
      } catch (UnexpectedException e) {
        log.error("Error connecting to Template Service", e);
        throw new NGTemplateException(TEMPLATE_RESOLVE_EXCEPTION_MSG, e);
      } catch (Exception e) {
        log.error("Unknown un-exception in resolving templates", e);
        throw new NGTemplateException(TEMPLATE_RESOLVE_EXCEPTION_MSG, e);
      } finally {
        log.info("[PMS_Template] template resolution took {}ms for projectId {}, orgId {}, accountId {}",
            System.currentTimeMillis() - start, projectId, orgId, accountId);
      }
    }
    return TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
  }

  public List<EntityDetailProtoDTO> getTemplateReferencesForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.NG_TEMPLATE_REFERENCES_SUPPORT)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null) {
        return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(accountId, orgId,
            projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
            gitEntityInfo.getYamlGitConfigId(), true, yaml));
      }

      return NGRestUtils.getResponse(
          templateResourceClient.getTemplateReferenceForGivenYaml(accountId, orgId, projectId, null, null, null, yaml));
    }
    return new ArrayList<>();
  }

  public RefreshResponseDTO getRefreshedYaml(String accountId, String orgId, String projectId, String yaml) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.getRefreshedYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, refreshRequest));
    }

    return NGRestUtils.getResponse(
        templateResourceClient.getRefreshedYaml(accountId, orgId, projectId, null, null, null, refreshRequest));
  }

  public ValidateTemplateInputsResponseDTO validateTemplateInputsForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.validateTemplateInputsForGivenYaml(accountId, orgId,
          projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.validateTemplateInputsForGivenYaml(
        accountId, orgId, projectId, null, null, null, refreshRequest));
  }

  public YamlFullRefreshResponseDTO refreshAllTemplatesForYaml(
      String accountId, String orgId, String projectId, String yaml) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(
        accountId, orgId, projectId, null, null, null, refreshRequest));
  }
}
