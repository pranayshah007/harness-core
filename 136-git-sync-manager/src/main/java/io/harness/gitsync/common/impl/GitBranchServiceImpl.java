/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.common.beans.BranchSyncStatus.UNSYNCED;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.beans.BranchSyncStatus;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.beans.GitBranch.GitBranchKeys;
import io.harness.gitsync.common.dtos.GitBranchDTO;
import io.harness.gitsync.common.dtos.GitBranchDTO.SyncedBranchDTOKeys;
import io.harness.gitsync.common.dtos.GitBranchListDTO;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.ng.beans.PageResponse;
import io.harness.repositories.gitBranches.GitBranchesRepository;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(DX)
public class GitBranchServiceImpl implements GitBranchService {
  private final GitBranchesRepository gitBranchesRepository;
  private final YamlGitConfigService yamlGitConfigService;
  private final ExecutorService executorService;
  private final GitBranchSyncService gitBranchSyncService;
  private final ScmOrchestratorService scmOrchestratorService;

  @Inject
  public GitBranchServiceImpl(GitBranchesRepository gitBranchesRepository, YamlGitConfigService yamlGitConfigService,
      ExecutorService executorService, GitBranchSyncService gitBranchSyncService,
      ScmOrchestratorService scmOrchestratorService) {
    this.gitBranchesRepository = gitBranchesRepository;
    this.yamlGitConfigService = yamlGitConfigService;
    this.executorService = executorService;
    this.gitBranchSyncService = gitBranchSyncService;
    this.scmOrchestratorService = scmOrchestratorService;
  }

  @Override
  public GitBranchListDTO listBranchesWithStatus(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigIdentifier, io.harness.ng.beans.PageRequest pageRequest,
      String searchTerm, BranchSyncStatus branchSyncStatus) {
    YamlGitConfigDTO yamlGitConfig =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
    Page<GitBranch> syncedBranchPage = gitBranchesRepository.findAll(
        getCriteria(accountIdentifier, yamlGitConfig.getRepo(), searchTerm, branchSyncStatus),
        PageRequest.of(pageRequest.getPageIndex(), pageRequest.getPageSize(),
            Sort.by(
                Sort.Order.asc(SyncedBranchDTOKeys.branchSyncStatus), Sort.Order.asc(SyncedBranchDTOKeys.branchName))));
    final List<GitBranchDTO> gitBranchDTOList = buildEntityDtoFromPage(syncedBranchPage);
    PageResponse<GitBranchDTO> ngPageResponse = PageUtils.getNGPageResponse(syncedBranchPage, gitBranchDTOList);
    GitBranchDTO defaultBranch =
        getDefaultBranchStatus(yamlGitConfig.getRepo(), yamlGitConfig.getBranch(), accountIdentifier);
    return GitBranchListDTO.builder().branches(ngPageResponse).defaultBranch(defaultBranch).build();
  }

  private GitBranchDTO getDefaultBranchStatus(String repo, String branch, String accountIdentifier) {
    GitBranch gitBranch = get(accountIdentifier, repo, branch);
    if (gitBranch == null) {
      return null;
    }
    return GitBranchDTO.builder().branchName(branch).branchSyncStatus(gitBranch.getBranchSyncStatus()).build();
  }

  @Override
  public Boolean syncNewBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigIdentifier, String branchName) {
    YamlGitConfigDTO yamlGitConfig =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
    checkBranchIsNotAlreadyShortlisted(yamlGitConfig.getRepo(), accountIdentifier, branchName);
    executorService.submit(
        ()
            -> gitBranchSyncService.createBranchSyncEvent(accountIdentifier, orgIdentifier, projectIdentifier,
                yamlGitConfigIdentifier, yamlGitConfig.getRepo(), branchName, null));
    return true;
  }

  @Override
  public void updateBranchSyncStatus(
      String accountIdentifier, String repoURL, String branchName, BranchSyncStatus branchSyncStatus) {
    Criteria criteria = Criteria.where(GitBranchKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitBranchKeys.repoURL)
                            .is(repoURL)
                            .and(GitBranchKeys.branchName)
                            .is(branchName);
    Update updateOperation = new Update();
    updateOperation.set(GitBranchKeys.branchSyncStatus, branchSyncStatus);
    gitBranchesRepository.update(new Query(criteria), updateOperation);
  }

  @Override
  public void createBranches(String accountId, String orgIdentifier, String projectIdentifier, String gitConnectorRef,
      String repoUrl, String yamlGitConfigIdentifier) {
    final int MAX_BRANCH_SIZE = 5000;
    List<String> branches = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.listBranchesForRepoByConnector(accountId, orgIdentifier, projectIdentifier,
            gitConnectorRef, repoUrl,
            io.harness.ng.beans.PageRequest.builder().pageSize(MAX_BRANCH_SIZE).pageIndex(0).build(), null),
        projectIdentifier, orgIdentifier, accountId, gitConnectorRef, null, null);

    for (String branchName : branches) {
      GitBranch gitBranch = GitBranch.builder()
                                .accountIdentifier(accountId)
                                .branchName(branchName)
                                .branchSyncStatus(BranchSyncStatus.UNSYNCED)
                                .repoURL(GitUtils.convertToUrlWithGit(repoUrl))
                                .build();
      save(gitBranch);
    }
  }

  @Override
  public void save(GitBranch gitBranch) {
    try {
      gitBranchesRepository.save(gitBranch);
    } catch (DuplicateKeyException duplicateKeyException) {
      // log.error("The branch {} in repo {} is already saved", gitBranch.getBranchName(), gitBranch.getRepoURL());
    }
  }

  private List<GitBranchDTO> buildEntityDtoFromPage(Page<GitBranch> gitBranchPage) {
    return gitBranchPage.get().map(this::buildSyncedBranchDTO).collect(Collectors.toList());
  }

  private GitBranchDTO buildSyncedBranchDTO(GitBranch entity) {
    return GitBranchDTO.builder()
        .branchName(entity.getBranchName())
        .branchSyncStatus(entity.getBranchSyncStatus())
        .build();
  }

  private Criteria getCriteria(
      String accountIdentifier, String repoURL, String searchTerm, BranchSyncStatus branchSyncStatus) {
    Criteria criteria =
        Criteria.where(GitBranchKeys.accountIdentifier).is(accountIdentifier).and(GitBranchKeys.repoURL).is(repoURL);
    if (isNotBlank(searchTerm)) {
      criteria.and(GitBranchKeys.branchName).regex(searchTerm, "i");
    }
    if (branchSyncStatus != null) {
      criteria.and(GitBranchKeys.branchSyncStatus).is(branchSyncStatus);
    }
    return criteria;
  }

  @Override
  public GitBranch get(String accountIdentifier, String repoURL, String branchName) {
    Criteria criteria = Criteria.where(GitBranchKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitBranchKeys.repoURL)
                            .is(repoURL)
                            .and(GitBranchKeys.branchName)
                            .is(branchName);
    return gitBranchesRepository.findOne(criteria);
  }

  @Override
  public void checkBranchIsNotAlreadyShortlisted(String repoURL, String accountId, String branch) {
    GitBranch gitBranch = get(accountId, repoURL, branch);
    if (gitBranch.getBranchSyncStatus() != UNSYNCED) {
      throw new InvalidRequestException(
          String.format("The branch %s in repo %s is already %s", branch, repoURL, gitBranch.getBranchSyncStatus()));
    }
  }

  @Override
  public boolean isBranchExists(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String yamlGitConfigId, String branch) {
    YamlGitConfigDTO yamlGitConfig =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigId);
    GitBranch gitBranch = get(accountIdentifier, yamlGitConfig.getRepo(), branch);
    return gitBranch != null;
  }

  @Override
  public DeleteResult delete(String repoUrl, String branchName, String accountIdentifier) {
    final Criteria criteria = Criteria.where(GitBranchKeys.accountIdentifier)
                                  .is(accountIdentifier)
                                  .and(GitBranchKeys.repoURL)
                                  .is(repoUrl)
                                  .and(GitBranchKeys.branchName)
                                  .is(branchName);
    return gitBranchesRepository.delete(criteria);
  }

  @Override
  public DeleteResult deleteAll(String accountIdentifier, String repoUrl) {
    final Criteria criteria =
        Criteria.where(GitBranchKeys.accountIdentifier).is(accountIdentifier).and(GitBranchKeys.repoURL).is(repoUrl);
    return gitBranchesRepository.delete(criteria);
  }
}
