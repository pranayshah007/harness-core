/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitsyncerror.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.gitsyncerror.GitSyncErrorStatus;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError.GitSyncErrorKeys;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorDetails;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorType;
import io.harness.gitsync.gitsyncerror.beans.GitToHarnessErrorDetails;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorAggregateByCommitDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorCountDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorDetailsDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitToHarnessErrorDetailsDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.repositories.gitSyncError.GitSyncErrorRepository;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class GitSyncErrorServiceImplTest extends GitSyncTestBase {
  private final String accountId = "accountId";
  private final String orgId = "orgId";
  private final String projectId = "projectId";
  private final String commitId = "commitId";
  private final String commitMessage = "message";
  private final GitSyncErrorType errorType = GitSyncErrorType.GIT_TO_HARNESS;
  private final GitSyncErrorStatus status = GitSyncErrorStatus.ACTIVE;
  private final String failureReason = "failed";
  private final String repoUrl = "repo";
  private final String branch = "branch";
  private final String repoId = "repoId";
  private final Scope scope = Scope.of(accountId, orgId, projectId);
  private GitSyncErrorDetails additionalErrorDetails;
  private GitSyncErrorDetailsDTO additionalErrorDetailsDTO;
  private YamlGitConfigDTO yamlGitConfigDTO;
  @Inject GitSyncErrorServiceImpl gitSyncErrorService;
  @Inject GitSyncErrorRepository gitSyncErrorRepository;
  @Mock YamlGitConfigService yamlGitConfigService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    additionalErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId(commitId).commitMessage(commitMessage).build();
    additionalErrorDetailsDTO =
        GitToHarnessErrorDetailsDTO.builder().gitCommitId(commitId).commitMessage(commitMessage).build();
    yamlGitConfigDTO = YamlGitConfigDTO.builder()
                           .accountIdentifier(accountId)
                           .projectIdentifier(projectId)
                           .organizationIdentifier(orgId)
                           .branch(branch)
                           .repo(repoUrl)
                           .identifier(repoId)
                           .gitConnectorType(ConnectorType.GIT)
                           .build();
    when(yamlGitConfigService.get(any(), any(), any(), any())).thenReturn(yamlGitConfigDTO);
    doReturn(yamlGitConfigDTO).when(yamlGitConfigService).getByProjectIdAndRepo(any(), any(), any(), any());
    when(yamlGitConfigService.getByAccountAndRepo(anyString(), anyString()))
        .thenReturn(Collections.singletonList(yamlGitConfigDTO));
    when(yamlGitConfigService.list(anyString(), anyString(), anyString())).thenReturn(Arrays.asList(yamlGitConfigDTO));

    FieldUtils.writeField(gitSyncErrorService, "yamlGitConfigService", yamlGitConfigService, true);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  @Ignore("Test Framework does not support aggregation present in code")
  public void test_listGitToHarnessErrorsGroupedByCommits() {
    long createdAt = OffsetDateTime.now().minusDays(12).toInstant().toEpochMilli();
    Scope scope1 = Scope.of(accountId, "org1", "proj1");
    gitSyncErrorRepository.save(build("filepath1", additionalErrorDetails, createdAt, Arrays.asList(scope1, scope)));
    gitSyncErrorRepository.save(
        build("filePath2", additionalErrorDetails, createdAt, Collections.singletonList(scope1)));
    additionalErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commit2").commitMessage(commitMessage).build();
    createdAt = OffsetDateTime.now().toInstant().toEpochMilli();
    gitSyncErrorRepository.save(
        build("filepath3", additionalErrorDetails, createdAt, Collections.singletonList(scope)));

    PageRequest pageRequest = PageRequest.builder().pageSize(10).pageIndex(0).build();

    List<GitSyncErrorAggregateByCommitDTO> dto =
        gitSyncErrorService
            .listGitToHarnessErrorsGroupedByCommits(pageRequest, accountId, orgId, projectId, null, null, null, 1)
            .getContent();

    assertThat(dto.size()).isEqualTo(2);
    assertThat(dto.get(0).getGitCommitId()).isEqualTo("commit2");
    assertThat(dto.get(1).getFailedCount()).isEqualTo(1);
    assertThat(dto.get(1).getErrorsForSummaryView().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_listAllGitToHarnessErrors() {
    long createdAt = OffsetDateTime.now().minusDays(12).toInstant().toEpochMilli();
    Scope scope1 = Scope.of(accountId, "org1", "proj1");
    gitSyncErrorRepository.save(build("filepath1", additionalErrorDetails, createdAt, Arrays.asList(scope1, scope)));
    gitSyncErrorRepository.save(build("filePath2", additionalErrorDetails, createdAt, Arrays.asList(scope1)));
    additionalErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commit2").commitMessage(commitMessage).build();
    createdAt = OffsetDateTime.now().toInstant().toEpochMilli();
    gitSyncErrorRepository.save(
        build("filepath3", additionalErrorDetails, createdAt, Collections.singletonList(scope)));

    SortOrder order =
        SortOrder.Builder.aSortOrder().withField(GitSyncErrorKeys.createdAt, SortOrder.OrderType.DESC).build();
    PageRequest pageRequest =
        PageRequest.builder().pageSize(10).pageIndex(0).sortOrders(ImmutableList.of(order)).build();

    List<GitSyncErrorDTO> dto =
        gitSyncErrorService.listAllGitToHarnessErrors(pageRequest, accountId, orgId, projectId, null, repoId, branch)
            .getContent();

    assertThat(dto.size()).isEqualTo(2);
    assertThat(dto.get(0).getCompleteFilePath()).isEqualTo("filepath3");
    assertThat(dto.get(1).getRepoId()).isEqualTo(repoId);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_listGitToHarnessErrorsForACommit() {
    Scope scope1 = Scope.of(accountId, "org1", "proj1");
    gitSyncErrorService.save(buildDTO("filePath1", additionalErrorDetailsDTO, Arrays.asList(scope, scope1)));
    gitSyncErrorService.save(buildDTO("filePath2", additionalErrorDetailsDTO, Collections.singletonList(scope)));
    PageRequest pageRequest = PageRequest.builder().pageSize(10).pageIndex(0).build();
    List<GitSyncErrorDTO> dto =
        gitSyncErrorService
            .listGitToHarnessErrorsForCommit(pageRequest, commitId, accountId, orgId, projectId, repoId, branch)
            .getContent();

    assertThat(dto.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_save() {
    GitSyncErrorDTO dto = buildDTO("filePath", additionalErrorDetailsDTO, Collections.singletonList(scope));
    gitSyncErrorService.save(dto);
    Optional<GitSyncErrorDTO> savedError =
        gitSyncErrorService.getGitToHarnessError(accountId, commitId, repoUrl, branch, "filePath");
    assertThat(savedError.isPresent()).isEqualTo(true);
    GitSyncErrorDTO expectedDTO = buildDTOWithEntityUrl("filePath", Collections.singletonList(scope));
    assertThat(savedError.get()).isEqualTo(expectedDTO);
  }

  private GitSyncErrorDTO buildDTOWithEntityUrl(String filePath, List<Scope> singletonList) {
    additionalErrorDetailsDTO = GitToHarnessErrorDetailsDTO.builder()
                                    .gitCommitId(commitId)
                                    .commitMessage(commitMessage)
                                    .entityUrl("repo/blob/branch/filePath")
                                    .build();
    return buildDTO(filePath, additionalErrorDetailsDTO, singletonList);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_saveAll() {
    GitSyncErrorDTO dto = buildDTO("filePath", additionalErrorDetailsDTO, Collections.singletonList(scope));
    gitSyncErrorService.saveAll(Collections.singletonList(dto));
    Iterable<GitSyncError> savedErrors = gitSyncErrorRepository.findAll();
    assertThat(savedErrors.iterator().hasNext()).isEqualTo(true);
    assertThat(savedErrors.iterator().next()).isNotNull();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_overrideGitToHarnessErrors() {
    GitSyncErrorDTO dto = buildDTO("filePath", additionalErrorDetailsDTO, Collections.singletonList(scope));
    gitSyncErrorService.save(dto);
    Set<String> filePathsHavingError = new HashSet<>();
    filePathsHavingError.add("filePath");
    filePathsHavingError.add("filePath1");
    gitSyncErrorService.overrideGitToHarnessErrors(accountId, repoUrl, branch, filePathsHavingError);
    Optional<GitSyncErrorDTO> savedError =
        gitSyncErrorService.getGitToHarnessError(accountId, commitId, repoUrl, branch, "filePath");
    assertThat(savedError.isPresent()).isEqualTo(true);
    assertThat(savedError.get().getStatus()).isEqualByComparingTo(GitSyncErrorStatus.OVERRIDDEN);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_resolveGitToHarnessErrors() {
    GitSyncErrorDTO dto = buildDTO("filePath", additionalErrorDetailsDTO, Collections.singletonList(scope));
    gitSyncErrorService.save(dto);
    Set<String> filePathsWithoutError = new HashSet<>();
    filePathsWithoutError.add("filePath");
    filePathsWithoutError.add("filePath1");
    gitSyncErrorService.resolveGitToHarnessErrors(accountId, repoUrl, branch, filePathsWithoutError, "commitId1");
    Optional<GitSyncErrorDTO> savedError =
        gitSyncErrorService.getGitToHarnessError(accountId, commitId, repoUrl, branch, "filePath");
    assertThat(savedError.isPresent()).isEqualTo(true);
    GitToHarnessErrorDetailsDTO errorDetails =
        (GitToHarnessErrorDetailsDTO) savedError.get().getAdditionalErrorDetails();
    assertThat(savedError.get().getStatus()).isEqualByComparingTo(GitSyncErrorStatus.RESOLVED);
    assertThat(errorDetails.getResolvedByCommitId()).isEqualTo("commitId1");
  }

  private GitSyncErrorDTO buildDTO(String filepath, GitSyncErrorDetailsDTO additionalErrorDetails, List<Scope> scopes) {
    return GitSyncErrorDTO.builder()
        .accountIdentifier(accountId)
        .errorType(errorType)
        .scopes(scopes)
        .completeFilePath(filepath)
        .repoUrl(repoUrl)
        .branchName(branch)
        .status(status)
        .failureReason(failureReason)
        .additionalErrorDetails(additionalErrorDetails)
        .build();
  }

  private GitSyncError build(
      String filepath, GitSyncErrorDetails additionalErrorDetails, long createdAt, List<Scope> scopes) {
    return GitSyncError.builder()
        .accountIdentifier(accountId)
        .errorType(errorType)
        .scopes(scopes)
        .completeFilePath(filepath)
        .repoUrl(repoUrl)
        .branchName(branch)
        .status(status)
        .failureReason(failureReason)
        .additionalErrorDetails(additionalErrorDetails)
        .createdAt(createdAt)
        .build();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testRecordConnectivityIssue() {
    gitSyncErrorService.saveConnectivityError(
        accountId, repoUrl, "Unable to connect to git provider", RepoProviders.GITHUB);

    PageResponse<GitSyncErrorDTO> gitSyncErrorList = gitSyncErrorService.listConnectivityErrors(
        accountId, orgId, projectId, repoId, new PageRequest(0, 10, new ArrayList<>()));
    assertThat(gitSyncErrorList.getContent()).isNotEmpty();
    assertThat(gitSyncErrorList.getContent()).hasSize(1);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testListGitSyncErrors() {
    gitSyncErrorService.saveConnectivityError(
        accountId, repoUrl, "Unable to connect to git provider", RepoProviders.GITHUB);

    gitSyncErrorService.saveConnectivityError(accountId, repoUrl, "Delegates are down", RepoProviders.GITHUB);

    gitSyncErrorService.saveConnectivityError(
        accountId, "repoUrl1", "Something went wrong, Please contact Harness Support.", RepoProviders.GITHUB);

    PageResponse<GitSyncErrorDTO> gitSyncErrorList = gitSyncErrorService.listConnectivityErrors(
        accountId, orgId, projectId, repoId, new PageRequest(0, 10, new ArrayList<>()));
    assertThat(gitSyncErrorList.getContent()).isNotEmpty();
    assertThat(gitSyncErrorList.getContent()).hasSize(1);

    when(yamlGitConfigService.list(anyString(), anyString(), anyString()))
        .thenReturn(Arrays.asList(yamlGitConfigDTO, YamlGitConfigDTO.builder().repo("repoUrl1").build()));

    gitSyncErrorList = gitSyncErrorService.listConnectivityErrors(
        accountId, orgId, projectId, null, new PageRequest(0, 10, new ArrayList<>()));
    assertThat(gitSyncErrorList.getContent()).isNotEmpty();
    assertThat(gitSyncErrorList.getContent()).hasSize(2);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_getErrorCount() {
    long createdAt = OffsetDateTime.now().minusDays(12).toInstant().toEpochMilli();
    Scope scope1 = Scope.of(accountId, "org1", "proj1");
    gitSyncErrorRepository.save(build("filepath1", additionalErrorDetails, createdAt, Arrays.asList(scope, scope1)));
    gitSyncErrorRepository.save(
        build("filePath2", additionalErrorDetails, createdAt, Collections.singletonList(scope1)));
    GitSyncErrorCountDTO gitSyncErrorCountDTO =
        gitSyncErrorService.getErrorCount(accountId, orgId, projectId, null, repoId, branch);
    assertThat(gitSyncErrorCountDTO.getGitToHarnessErrorCount()).isEqualTo(1);
    assertThat(gitSyncErrorCountDTO.getConnectivityErrorCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testListConnectivityErrorsForDefaultBranchesOfAllRepos() {
    gitSyncErrorService.saveConnectivityError(
        accountId, repoUrl, "Unable to connect to git provider", RepoProviders.GITHUB);
    PageResponse<GitSyncErrorDTO> gitSyncErrorList = gitSyncErrorService.listConnectivityErrors(
        accountId, orgId, projectId, null, new PageRequest(0, 10, new ArrayList<>()));
    assertThat(gitSyncErrorList.getContent()).isNotEmpty();
    assertThat(gitSyncErrorList.getContent()).hasSize(1);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_resolveConnectivityErrors() {
    gitSyncErrorService.saveConnectivityError(
        accountId, repoUrl, "Unable to connect to git provider", RepoProviders.GITHUB);
    YamlGitConfigDTO yamlGitConfigDTO1 = YamlGitConfigDTO.builder()
                                             .accountIdentifier(accountId)
                                             .organizationIdentifier("org1")
                                             .projectIdentifier("proj1")
                                             .repo(repoUrl)
                                             .build();
    when(yamlGitConfigService.getByAccountAndRepo(anyString(), anyString()))
        .thenReturn(Arrays.asList(yamlGitConfigDTO, yamlGitConfigDTO1));
    gitSyncErrorService.resolveConnectivityErrors(accountId, repoUrl);
    PageResponse<GitSyncErrorDTO> gitSyncErrorList = gitSyncErrorService.listConnectivityErrors(
        accountId, orgId, projectId, null, new PageRequest(0, 10, new ArrayList<>()));
    assertThat(gitSyncErrorList.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testRemoveScope() {
    long createdAt = OffsetDateTime.now().minusDays(12).toInstant().toEpochMilli();
    Scope scope1 = Scope.of(accountId, "org1", "proj1");
    gitSyncErrorRepository.save(build("filepath1", additionalErrorDetails, createdAt, Arrays.asList(scope1, scope)));
    gitSyncErrorService.removeScope(accountId, orgId, projectId);
    assertThat(
        gitSyncErrorRepository.findByAccountIdentifierAndCompleteFilePathAndErrorType(accountId, "filepath1", errorType)
            .getScopes()
            .contains(scope))
        .isFalse();
  }
}
