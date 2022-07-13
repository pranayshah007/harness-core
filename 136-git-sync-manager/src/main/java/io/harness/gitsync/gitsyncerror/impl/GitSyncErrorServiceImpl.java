/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitsyncerror.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.ROOT;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.gitsyncerror.GitSyncErrorStatus;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError.GitSyncErrorKeys;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorAggregateByCommit;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorAggregateByCommit.GitSyncErrorAggregateByCommitKeys;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorType;
import io.harness.gitsync.gitsyncerror.beans.GitToHarnessErrorDetails;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorAggregateByCommitDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorCountDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorDTO;
import io.harness.gitsync.gitsyncerror.remote.GitSyncErrorMapper;
import io.harness.gitsync.gitsyncerror.service.GitSyncErrorService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.repositories.gitSyncError.GitSyncErrorRepository;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PL)
public class GitSyncErrorServiceImpl implements GitSyncErrorService {
  private static final String IS_ACTIVE_ERROR = "isActiveError";
  private final YamlGitConfigService yamlGitConfigService;
  private final GitSyncErrorRepository gitSyncErrorRepository;
  private final ScheduledExecutorService executorService;
  public static final String ERROR_DOCUMENT = "errorDocument";
  public static final Long DEFAULT_COMMIT_TIME = 0L;

  @Inject
  public GitSyncErrorServiceImpl(
      YamlGitConfigService yamlGitConfigService, GitSyncErrorRepository gitSyncErrorRepository) {
    this.yamlGitConfigService = yamlGitConfigService;
    this.gitSyncErrorRepository = gitSyncErrorRepository;
    this.executorService = Executors.newScheduledThreadPool(1);
    executorService.scheduleWithFixedDelay(this::markExpiredErrors, 1, 24, TimeUnit.HOURS);
  }

  @Override
  public PageResponse<GitSyncErrorAggregateByCommitDTO> listGitToHarnessErrorsGroupedByCommits(PageRequest pageRequest,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm, String repoId,
      String branch, Integer numberOfErrorsInSummary) {
    Pageable pageable = getPageRequest(pageRequest);
    Criteria criteria = createGitToHarnessErrorFilterCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, repoId, branch);
    criteria.and(GitSyncErrorKeys.status).in(GitSyncErrorStatus.ACTIVE, GitSyncErrorStatus.RESOLVED);
    SortOperation sortOperation = sort(Sort.Direction.DESC, GitSyncErrorAggregateByCommitKeys.createdAt);

    Aggregation aggregation = newAggregation(match(criteria), getProjectionOperationForProjectingActiveError(),
        getGroupOperationForGroupingErrorsWithCommitId(),
        getProjectionOperationForProjectingGitSyncErrorAggregateByCommitKeys(numberOfErrorsInSummary), sortOperation,
        skip(pageable.getOffset()), limit(pageable.getPageSize()));
    List<GitSyncErrorAggregateByCommit> gitSyncErrorAggregateByCommitList =
        gitSyncErrorRepository.aggregate(aggregation, GitSyncErrorAggregateByCommit.class).getMappedResults();
    long totalCount = getAggregateErrorsCount(criteria, pageable, numberOfErrorsInSummary);
    List<GitSyncErrorAggregateByCommitDTO> gitSyncErrorAggregateByCommitDTOList =
        emptyIfNull(gitSyncErrorAggregateByCommitList)
            .stream()
            .map(GitSyncErrorMapper::toGitSyncErrorAggregateByCommitDTO)
            .collect(toList());

    gitSyncErrorAggregateByCommitDTOList.forEach(gitSyncErrorAggregateByCommitDTO -> {
      String repoUrl = gitSyncErrorAggregateByCommitDTO.getErrorsForSummaryView().get(0).getRepoUrl();
      String gitConfigId = getRepoId(repoUrl, accountIdentifier, orgIdentifier, projectIdentifier);
      gitSyncErrorAggregateByCommitDTO.setRepoId(gitConfigId);
    });
    Page<GitSyncErrorAggregateByCommitDTO> page =
        new PageImpl<>(gitSyncErrorAggregateByCommitDTOList, pageable, totalCount);
    return getNGPageResponse(page);
  }

  private ProjectionOperation getProjectionOperationForProjectingActiveError() {
    Criteria activeErrorCriteria = Criteria.where(GitSyncErrorKeys.status).is(GitSyncErrorStatus.ACTIVE);
    return Aggregation.project()
        .and(ConditionalOperators.Cond.when(activeErrorCriteria).then(1).otherwise(0))
        .as(IS_ACTIVE_ERROR)
        .andExpression(ROOT)
        .as(ERROR_DOCUMENT)
        .andExpression(GitSyncErrorKeys.gitCommitId)
        .as(GitSyncErrorKeys.gitCommitId)
        .andExpression(GitSyncErrorKeys.commitMessage)
        .as(GitSyncErrorKeys.commitMessage)
        .andInclude(GitSyncErrorKeys.branchName)
        .andInclude(GitSyncErrorKeys.createdAt);
  }

  private GroupOperation getGroupOperationForGroupingErrorsWithCommitId() {
    return group(GitSyncErrorKeys.gitCommitId)
        .sum(IS_ACTIVE_ERROR)
        .as(GitSyncErrorAggregateByCommitKeys.failedCount)
        .first(GitSyncErrorKeys.gitCommitId)
        .as(GitSyncErrorAggregateByCommitKeys.gitCommitId)
        .first(GitSyncErrorKeys.commitMessage)
        .as(GitSyncErrorAggregateByCommitKeys.commitMessage)
        .first(GitSyncErrorKeys.branchName)
        .as(GitSyncErrorAggregateByCommitKeys.branchName)
        .first(GitSyncErrorKeys.createdAt)
        .as(GitSyncErrorAggregateByCommitKeys.createdAt)
        .push(ERROR_DOCUMENT)
        .as(GitSyncErrorAggregateByCommitKeys.errorsForSummaryView);
  }

  private ProjectionOperation getProjectionOperationForProjectingGitSyncErrorAggregateByCommitKeys(
      Integer numberOfErrorsInSummary) {
    return project()
        .andInclude(GitSyncErrorAggregateByCommitKeys.gitCommitId)
        .andInclude(GitSyncErrorAggregateByCommitKeys.createdAt)
        .andInclude(GitSyncErrorAggregateByCommitKeys.commitMessage)
        .andInclude(GitSyncErrorAggregateByCommitKeys.branchName)
        .andInclude(GitSyncErrorAggregateByCommitKeys.failedCount)
        .andExpression(GitSyncErrorAggregateByCommitKeys.errorsForSummaryView)
        .slice(numberOfErrorsInSummary)
        .as(GitSyncErrorAggregateByCommitKeys.errorsForSummaryView);
  }

  private long getAggregateErrorsCount(Criteria criteria, Pageable pageable, Integer numberOfErrorsInSummary) {
    Aggregation aggregation = newAggregation(match(criteria), getProjectionOperationForProjectingActiveError(),
        getGroupOperationForGroupingErrorsWithCommitId(),
        getProjectionOperationForProjectingGitSyncErrorAggregateByCommitKeys(numberOfErrorsInSummary),
        skip(pageable.getOffset()));
    return gitSyncErrorRepository.aggregate(aggregation, GitSyncErrorAggregateByCommit.class).getMappedResults().size();
  }

  private String getRepoId(String repoUrl, String accountId, String orgId, String projectId) {
    return yamlGitConfigService.getByProjectIdAndRepo(accountId, orgId, projectId, repoUrl).getIdentifier();
  }

  @Override
  public PageResponse<GitSyncErrorDTO> listAllGitToHarnessErrors(PageRequest pageRequest, String accountId,
      String orgIdentifier, String projectIdentifier, String searchTerm, String repoId, String branch) {
    Criteria criteria =
        createGitToHarnessErrorFilterCriteria(accountId, orgIdentifier, projectIdentifier, searchTerm, repoId, branch);
    criteria.and(GitSyncErrorKeys.status).is(GitSyncErrorStatus.ACTIVE);
    Page<GitSyncErrorDTO> gitSyncErrorPage =
        gitSyncErrorRepository.findAll(criteria, PageUtils.getPageRequest(pageRequest))
            .map(GitSyncErrorMapper::toGitSyncErrorDTO);

    gitSyncErrorPage.forEach(gitSyncErrorDTO -> {
      String gitConfigId = getRepoId(gitSyncErrorDTO.getRepoUrl(), accountId, orgIdentifier, projectIdentifier);
      gitSyncErrorDTO.setRepoId(gitConfigId);
    });
    return getNGPageResponse(gitSyncErrorPage);
  }

  private Criteria createGitToHarnessErrorFilterCriteria(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, String repoId, String branch) {
    // when no filter is chosen - take all repos and their default branches
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitSyncErrorKeys.scopes)
                            .is(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                            .and(GitSyncErrorKeys.errorType)
                            .is(GitSyncErrorType.GIT_TO_HARNESS);
    Criteria repoBranchCriteria = getRepoBranchCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, repoId, branch, GitSyncErrorType.GIT_TO_HARNESS);
    criteria.andOperator(repoBranchCriteria)
        .and(GitSyncErrorKeys.createdAt)
        .gt(OffsetDateTime.now().minusDays(30).toInstant().toEpochMilli());
    if (isNotBlank(searchTerm)) {
      criteria.orOperator(Criteria.where(GitSyncErrorKeys.gitCommitId).regex(searchTerm, "i"),
          Criteria.where(GitSyncErrorKeys.completeFilePath).regex(searchTerm, "i"),
          Criteria.where(GitSyncErrorKeys.entityType).regex(searchTerm, "i"));
    }
    return criteria;
  }

  private Criteria getRepoBranchCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String repoIdentifier, String branch, GitSyncErrorType errorType) {
    Criteria criteria = new Criteria();
    List<String> branches = new ArrayList<>();
    List<Pair<String, String>> repoBranchList = new ArrayList<>();
    if (StringUtils.isEmpty(repoIdentifier)) {
      List<YamlGitConfigDTO> yamlGitConfigDTOS =
          yamlGitConfigService.list(projectIdentifier, orgIdentifier, accountIdentifier);
      repoBranchList = emptyIfNull(yamlGitConfigDTOS)
                           .stream()
                           .map(yamlGitConfigDTO -> {
                             String repo = yamlGitConfigDTO.getRepo();
                             String defaultBranch = yamlGitConfigDTO.getBranch();
                             return Pair.of(repo, defaultBranch);
                           })
                           .collect(Collectors.toList());
    } else {
      YamlGitConfigDTO yamlGitConfigDTO =
          yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, repoIdentifier);
      branch = StringUtils.isEmpty(branch) ? yamlGitConfigDTO.getBranch() : branch;
      repoBranchList.add(Pair.of(yamlGitConfigDTO.getRepo(), branch));
    }

    if (errorType.equals(GitSyncErrorType.GIT_TO_HARNESS)) {
      List<Criteria> criteriaList = new ArrayList<>();
      for (Pair<String, String> repoBranch : repoBranchList) {
        criteriaList.add(Criteria.where(GitSyncErrorKeys.repoUrl)
                             .is(repoBranch.getLeft())
                             .and(GitSyncErrorKeys.branchName)
                             .is(repoBranch.getRight()));
      }
      criteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    } else {
      List<String> repoUrls = repoBranchList.stream().map(repoBranch -> repoBranch.getLeft()).collect(toList());
      criteria.and(GitSyncErrorKeys.repoUrl).in(repoUrls);
    }
    return criteria;
  }

  @Override
  public PageResponse<GitSyncErrorDTO> listGitToHarnessErrorsForCommit(PageRequest pageRequest, String commitId,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String repoId, String branch) {
    String repoUrl = yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, repoId).getRepo();
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitSyncErrorKeys.scopes)
                            .is(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                            .and(GitSyncErrorKeys.errorType)
                            .is(GitSyncErrorType.GIT_TO_HARNESS)
                            .and(GitSyncErrorKeys.repoUrl)
                            .is(repoUrl)
                            .and(GitSyncErrorKeys.branchName)
                            .is(branch)
                            .and(GitSyncErrorKeys.gitCommitId)
                            .is(commitId)
                            .and(GitSyncErrorKeys.status)
                            .in(GitSyncErrorStatus.ACTIVE, GitSyncErrorStatus.RESOLVED);
    Page<GitSyncError> gitSyncErrorPage =
        gitSyncErrorRepository.findAll(criteria, PageUtils.getPageRequest(pageRequest));
    return getNGPageResponse(gitSyncErrorPage.map(GitSyncErrorMapper::toGitSyncErrorDTO));
  }

  @Override
  public Optional<GitSyncErrorDTO> save(GitSyncErrorDTO gitSyncErrorDTO) {
    return save(GitSyncErrorMapper.toGitSyncError(gitSyncErrorDTO, gitSyncErrorDTO.getAccountIdentifier()));
  }

  private Optional<GitSyncErrorDTO> save(GitSyncError gitSyncError) {
    try {
      validate(gitSyncError);
      GitSyncError savedError = gitSyncErrorRepository.save(gitSyncError);
      return Optional.of(GitSyncErrorMapper.toGitSyncErrorDTO(savedError));
    } catch (DuplicateKeyException ex) {
      log.info("A git sync error for this commitId and File already exists.", ex);
      if (gitSyncError.getErrorType().equals(GitSyncErrorType.CONNECTIVITY_ISSUE)) {
        return getConnectivityError(gitSyncError.getAccountIdentifier(), gitSyncError.getRepoUrl());
      } else {
        GitToHarnessErrorDetails additionalErrorDetails =
            (GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails();
        return getGitToHarnessError(gitSyncError.getAccountIdentifier(), additionalErrorDetails.getGitCommitId(),
            gitSyncError.getRepoUrl(), gitSyncError.getBranchName(), gitSyncError.getCompleteFilePath());
      }
    }
  }

  private Optional<GitSyncErrorDTO> getConnectivityError(String accountId, String repoUrl) {
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountId)
                            .and(GitSyncErrorKeys.errorType)
                            .is(GitSyncErrorType.CONNECTIVITY_ISSUE)
                            .and(GitSyncErrorKeys.repoUrl)
                            .is(repoUrl);
    GitSyncError gitSyncError = gitSyncErrorRepository.find(criteria);
    if (gitSyncError == null) {
      return Optional.empty();
    } else {
      return Optional.of(GitSyncErrorMapper.toGitSyncErrorDTO(gitSyncError));
    }
  }

  @Override
  public List<GitSyncErrorDTO> saveAll(List<GitSyncErrorDTO> gitSyncErrorDTOList) {
    List<GitSyncError> gitSyncErrors =
        gitSyncErrorDTOList.stream()
            .map(gitSyncErrorDTO
                -> GitSyncErrorMapper.toGitSyncError(gitSyncErrorDTO, gitSyncErrorDTO.getAccountIdentifier()))
            .collect(toList());
    List<GitSyncError> gitSyncErrorsSaved = new ArrayList<>();
    try {
      gitSyncErrorRepository.saveAll(gitSyncErrors).iterator().forEachRemaining(gitSyncErrorsSaved::add);
      return gitSyncErrorsSaved.stream().map(GitSyncErrorMapper::toGitSyncErrorDTO).collect(toList());
    } catch (DuplicateKeyException ex) {
      log.info("Git sync error already exist", ex);
      return null;
    }
  }

  @Override
  public void overrideGitToHarnessErrors(String accountId, String repoUrl, String branchName, Set<String> filePaths) {
    Criteria criteria = createActiveErrorsFilterCriteria(
        accountId, GitSyncErrorType.GIT_TO_HARNESS, repoUrl, branchName, new ArrayList<>(filePaths));
    Update update = update(GitSyncErrorKeys.status, GitSyncErrorStatus.OVERRIDDEN);
    gitSyncErrorRepository.updateError(criteria, update);
  }

  @Override
  public void resolveGitToHarnessErrors(
      String accountId, String repoUrl, String branchName, Set<String> filePaths, String commitId) {
    Criteria criteria = createActiveErrorsFilterCriteria(
        accountId, GitSyncErrorType.GIT_TO_HARNESS, repoUrl, branchName, new ArrayList<>(filePaths));
    Update update =
        update(GitSyncErrorKeys.status, GitSyncErrorStatus.RESOLVED).set(GitSyncErrorKeys.resolvedByCommitId, commitId);
    gitSyncErrorRepository.updateError(criteria, update);
  }

  private Criteria createActiveErrorsFilterCriteria(
      String accountId, GitSyncErrorType errorType, String repoUrl, String branchName, List<String> filePaths) {
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountId)
                            .and(GitSyncErrorKeys.errorType)
                            .is(errorType)
                            .and(GitSyncErrorKeys.repoUrl)
                            .is(repoUrl);
    if (errorType.equals(GitSyncErrorType.GIT_TO_HARNESS)) {
      criteria.and(GitSyncErrorKeys.branchName).is(branchName).and(GitSyncErrorKeys.completeFilePath).in(filePaths);
    }
    return criteria.and(GitSyncErrorKeys.status).is(GitSyncErrorStatus.ACTIVE);
  }

  @Override
  public Optional<GitSyncErrorDTO> getGitToHarnessError(
      String accountId, String commitId, String repoUrl, String branchName, String filePath) {
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountId)
                            .and(GitSyncErrorKeys.gitCommitId)
                            .is(commitId)
                            .and(GitSyncErrorKeys.repoUrl)
                            .is(repoUrl)
                            .and(GitSyncErrorKeys.branchName)
                            .is(branchName)
                            .and(GitSyncErrorKeys.completeFilePath)
                            .is(filePath);
    GitSyncError error = gitSyncErrorRepository.find(criteria);
    if (error == null) {
      return Optional.empty();
    } else {
      return Optional.of(GitSyncErrorMapper.toGitSyncErrorDTO(error));
    }
  }

  private void markExpiredErrors() {
    Criteria criteria =
        Criteria.where(GitSyncErrorKeys.createdAt).lte(OffsetDateTime.now().minusDays(30).toInstant().toEpochMilli());
    Update update = update(GitSyncErrorKeys.status, GitSyncErrorStatus.EXPIRED);
    gitSyncErrorRepository.updateError(criteria, update);
  }

  @Override
  public boolean deleteGitSyncErrors(List<String> errorIds, String accountId) {
    return gitSyncErrorRepository.deleteByIds(errorIds).wasAcknowledged();
  }

  @Override
  public void removeScope(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Update update =
        new Update().pull(GitSyncErrorKeys.scopes, Scope.of(accountIdentifier, orgIdentifier, projectIdentifier));
    Criteria criteriaForProject = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                                      .is(accountIdentifier)
                                      .and(GitSyncErrorKeys.orgIdentifier)
                                      .is(orgIdentifier)
                                      .and(GitSyncErrorKeys.projectIdentifier)
                                      .is(projectIdentifier);
    gitSyncErrorRepository.updateError(criteriaForProject, update);
    Criteria criteria = Criteria.where(GitSyncErrorKeys.scopes).size(0);
    gitSyncErrorRepository.deleteAll(criteria);
  }

  @Override
  public void saveConnectivityError(
      String accountIdentifier, String repoUrl, String errorMessage, RepoProviders repoProvider) {
    List<Scope> scopes = getScopes(accountIdentifier, repoUrl);
    Optional<GitSyncErrorDTO> gitSyncError = getConnectivityError(accountIdentifier, repoUrl);
    if (!gitSyncError.isPresent()) {
      GitSyncError error = GitSyncError.builder()
                               .accountIdentifier(accountIdentifier)
                               .errorType(GitSyncErrorType.CONNECTIVITY_ISSUE)
                               .repoUrl(repoUrl)
                               .failureReason(errorMessage)
                               .status(GitSyncErrorStatus.ACTIVE)
                               .scopes(scopes)
                               .createdAt(System.currentTimeMillis())
                               .repoProvider(repoProvider)
                               .build();
      save(error);
    } else {
      Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                              .is(accountIdentifier)
                              .and(GitSyncErrorKeys.errorType)
                              .is(GitSyncErrorType.CONNECTIVITY_ISSUE)
                              .and(GitSyncErrorKeys.repoUrl)
                              .is(repoUrl);
      Update update = update(GitSyncErrorKeys.failureReason, errorMessage)
                          .set(GitSyncErrorKeys.scopes, scopes)
                          .set(GitSyncErrorKeys.status, GitSyncErrorStatus.ACTIVE)
                          .set(GitSyncErrorKeys.createdAt, System.currentTimeMillis())
                          .set(GitSyncErrorKeys.lastUpdatedAt, System.currentTimeMillis());
      gitSyncErrorRepository.upsert(criteria, update);
    }
  }

  private List<Scope> getScopes(String accountIdentifier, String repoUrl) {
    List<YamlGitConfigDTO> yamlGitConfigs = yamlGitConfigService.getByAccountAndRepo(accountIdentifier, repoUrl);
    return yamlGitConfigs.stream()
        .map(yamlGitConfigDTO
            -> Scope.of(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
                yamlGitConfigDTO.getProjectIdentifier()))
        .collect(Collectors.toList());
  }

  @Override
  public PageResponse<GitSyncErrorDTO> listConnectivityErrors(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String repoIdentifier, PageRequest pageRequest) {
    Criteria criteria = createConnectivityErrorFilterCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, repoIdentifier, null);

    Page<GitSyncError> gitSyncErrors = gitSyncErrorRepository.findAll(criteria, PageUtils.getPageRequest(pageRequest));
    Page<GitSyncErrorDTO> gitSyncErrorPage = gitSyncErrors.map(GitSyncErrorMapper::toGitSyncErrorDTO);

    gitSyncErrorPage.forEach(gitSyncErrorDTO -> {
      String gitConfigId = getRepoId(gitSyncErrorDTO.getRepoUrl(), accountIdentifier, orgIdentifier, projectIdentifier);
      gitSyncErrorDTO.setRepoId(gitConfigId);
    });
    return getNGPageResponse(gitSyncErrorPage);
  }

  private Criteria createConnectivityErrorFilterCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String repoIdentifier, String branch) {
    Criteria criteria = Criteria.where(GitSyncErrorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitSyncErrorKeys.scopes)
                            .is(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                            .and(GitSyncErrorKeys.errorType)
                            .in(GitSyncErrorType.FULL_SYNC, GitSyncErrorType.CONNECTIVITY_ISSUE);
    Criteria repoBranchCriteria = getRepoBranchCriteria(accountIdentifier, orgIdentifier, projectIdentifier,
        repoIdentifier, branch, GitSyncErrorType.CONNECTIVITY_ISSUE);

    criteria.andOperator(repoBranchCriteria);
    criteria.and(GitSyncErrorKeys.status)
        .is(GitSyncErrorStatus.ACTIVE)
        .and(GitSyncErrorKeys.createdAt)
        .gt(OffsetDateTime.now().minusDays(30).toInstant().toEpochMilli());
    return criteria;
  }

  @Override
  public GitSyncErrorCountDTO getErrorCount(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, String repoId, String branch) {
    return GitSyncErrorCountDTO.builder()
        .gitToHarnessErrorCount(
            getGitToHarnessErrorCount(accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, repoId, branch))
        .connectivityErrorCount(
            getConnectivityErrorCount(accountIdentifier, orgIdentifier, projectIdentifier, repoId, branch))
        .build();
  }

  private long getGitToHarnessErrorCount(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, String repoId, String branch) {
    Criteria criteria = createGitToHarnessErrorFilterCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, repoId, branch);
    criteria.and(GitSyncErrorKeys.status).is(GitSyncErrorStatus.ACTIVE);
    return gitSyncErrorRepository.count(criteria);
  }

  private long getConnectivityErrorCount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String repoId, String branch) {
    Criteria criteria =
        createConnectivityErrorFilterCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoId, branch);
    return gitSyncErrorRepository.count(criteria);
  }

  @Override
  public void resolveConnectivityErrors(String accountIdentifier, String repoUrl) {
    Criteria criteria = createActiveErrorsFilterCriteria(
        accountIdentifier, GitSyncErrorType.CONNECTIVITY_ISSUE, repoUrl, null, Collections.EMPTY_LIST);
    Update update = update(GitSyncErrorKeys.status, GitSyncErrorStatus.RESOLVED);
    gitSyncErrorRepository.updateError(criteria, update);
  }
}
