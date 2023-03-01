/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper.BOOLEAN_TRUE_VALUE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.exception.NGTemplateResolveException;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
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

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(PipelineEntity pipelineEntity, String loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml(), loadFromCache);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      PipelineEntity pipelineEntity, boolean getMergedTemplateWithTemplateReferences, boolean loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml(), false, getMergedTemplateWithTemplateReferences,
        parseLoadFromCache(loadFromCache));
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      String accountId, String orgId, String projectId, String yaml, String loadFromCache) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, false, false, loadFromCache);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(String accountId, String orgId, String projectId,
      String yaml, boolean checkForTemplateAccess, boolean getMergedTemplateWithTemplateReferences,
      String loadFromCache) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, checkForTemplateAccess,
        getMergedTemplateWithTemplateReferences, loadFromCache, false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipelineAndAppendInputSetValidators(String accountId,
      String orgId, String projectId, String yaml, boolean checkForTemplateAccess,
      boolean getMergedTemplateWithTemplateReferences, String loadFromCache) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, checkForTemplateAccess,
        getMergedTemplateWithTemplateReferences, loadFromCache, true);
  }

  private TemplateMergeResponseDTO resolveTemplateRefsInPipeline(String accountId, String orgId, String projectId,
      String yaml, boolean checkForTemplateAccess, boolean getMergedTemplateWithTemplateReferences,
      String loadFromCache, boolean appendInputSetValidator) {
    // validating the duplicate fields in yaml field
    if (TemplateRefHelper.hasTemplateRef(yaml, true)
        && pipelineEnforcementService.isFeatureRestricted(accountId, FeatureRestrictionName.TEMPLATE_SERVICE.name())) {
      String TEMPLATE_RESOLVE_EXCEPTION_MSG = "Exception in resolving template refs in given pipeline yaml.";
      long start = System.currentTimeMillis();
      try {
        GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
        if (gitEntityInfo != null) {
          return NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId, projectId,
              gitEntityInfo.getBranch(), gitEntityInfo.getYamlGitConfigId(), true, getConnectorRef(), getRepoName(),
              accountId, orgId, projectId, loadFromCache,
              TemplateApplyRequestDTO.builder()
                  .originalEntityYaml(yaml)
                  .checkForAccess(checkForTemplateAccess)
                  .getMergedYamlWithTemplateField(getMergedTemplateWithTemplateReferences)
                  .build(),
              appendInputSetValidator));
        }
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().build()).build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          log.info("Principal before the interService call is {}", SourcePrincipalContextBuilder.getSourcePrincipal());
          return NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId, projectId,
              null, null, null, null, null, null, null, null, loadFromCache,
              TemplateApplyRequestDTO.builder()
                  .originalEntityYaml(yaml)
                  .checkForAccess(checkForTemplateAccess)
                  .getMergedYamlWithTemplateField(getMergedTemplateWithTemplateReferences)
                  .build(),
              appendInputSetValidator));
        }
      } catch (InvalidRequestException e) {
        if (e.getMetadata() instanceof TemplateInputsErrorMetadataDTO) {
          throw new NGTemplateResolveException(
              TEMPLATE_RESOLVE_EXCEPTION_MSG, USER, (TemplateInputsErrorMetadataDTO) e.getMetadata(), yaml);
        } else if (e.getMetadata() instanceof ValidateTemplateInputsResponseDTO) {
          throw new NGTemplateResolveExceptionV2(
              TEMPLATE_RESOLVE_EXCEPTION_MSG, USER, (ValidateTemplateInputsResponseDTO) e.getMetadata(), yaml);
        } else {
          throw new NGTemplateException(e.getMessage(), e);
        }
      } catch (NGTemplateResolveException e) {
        throw new NGTemplateResolveException(e.getMessage(), USER, e.getErrorResponseDTO(), null);
      } catch (NGTemplateResolveExceptionV2 e) {
        throw new NGTemplateResolveExceptionV2(e.getMessage(), USER, e.getValidateTemplateInputsResponseDTO(), null);
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
    return TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).mergedPipelineYamlWithTemplateRef(yaml).build();
  }

  public List<EntityDetailProtoDTO> getTemplateReferencesForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(accountId, orgId,
          projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, TemplateReferenceRequestDTO.builder().yaml(yaml).build()));
    }

    return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(
        accountId, orgId, projectId, null, null, null, TemplateReferenceRequestDTO.builder().yaml(yaml).build()));
  }

  public RefreshResponseDTO getRefreshedYaml(String accountId, String orgId, String projectId, String yaml,
      PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.getRefreshedYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
          pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), loadFromCache, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.getRefreshedYaml(
        accountId, orgId, projectId, null, null, null, null, null, null, null, null, loadFromCache, refreshRequest));
  }

  public ValidateTemplateInputsResponseDTO validateTemplateInputsForGivenYaml(String accountId, String orgId,
      String projectId, String yaml, PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    long start = System.currentTimeMillis();
    try {
      if (gitEntityInfo != null) {
        return NGRestUtils.getResponse(templateResourceClient.validateTemplateInputsForGivenYaml(accountId, orgId,
            projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
            gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
            pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), loadFromCache, refreshRequest));
      }
      return NGRestUtils.getResponse(templateResourceClient.validateTemplateInputsForGivenYaml(
          accountId, orgId, projectId, null, null, null, null, null, null, null, null, loadFromCache, refreshRequest));
    } finally {
      log.info(
          "[PMS_PipelineTemplate] validating template inputs for given yaml took {}ms for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectId, orgId, accountId);
    }
  }

  public YamlFullRefreshResponseDTO refreshAllTemplatesForYaml(String accountId, String orgId, String projectId,
      String yaml, PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
          pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), loadFromCache, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(
        accountId, orgId, projectId, null, null, null, null, null, null, null, null, loadFromCache, refreshRequest));
  }

  public HashSet<String> getTemplatesModuleInfo(TemplateMergeResponseDTO templateMergeResponseDTO) {
    HashSet<String> templateModuleInfo = new HashSet<>();
    if (isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      for (TemplateReferenceSummary templateReferenceSummary :
          templateMergeResponseDTO.getTemplateReferenceSummaries()) {
        templateModuleInfo.addAll(templateReferenceSummary.getModuleInfo());
      }
    }
    return templateModuleInfo;
  }

  private String getConnectorRef() {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityConnectorRef())) {
      return gitEntityInfo.getConnectorRef();
    }
    return gitEntityInfo.getParentEntityConnectorRef();
  }

  private String getRepoName() {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoName())) {
      return gitEntityInfo.getRepoName();
    }
    return gitEntityInfo.getParentEntityRepoName();
  }

  private String parseLoadFromCache(boolean loadFromCache) {
    if (loadFromCache) {
      return BOOLEAN_TRUE_VALUE;
    }
    return BOOLEAN_FALSE_VALUE;
  }
}
