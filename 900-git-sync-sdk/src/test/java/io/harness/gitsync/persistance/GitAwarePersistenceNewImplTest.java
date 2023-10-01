/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.SHIVAM;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.GitSdkTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SampleBean;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.SampleBeanEntityGitPersistenceHelperServiceImpl;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmCreateFileResponse;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(DX)
public class GitAwarePersistenceNewImplTest extends GitSdkTestBase {
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock Map<String, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMap;
  @Mock SCMGitSyncHelper scmGitSyncHelper;
  @Mock io.harness.gitsync.persistance.GitSyncMsvcHelper gitSyncMsvcHelper;
  @Mock private TransactionTemplate transactionTemplate;
  @Inject MongoTemplate mongoTemplate;
  io.harness.gitsync.persistance.GitAwarePersistenceNewImpl gitAwarePersistence;
  final String projectIdentifier = "proj";
  final String orgIdentifier = "org";
  final String accountIdentifier = "acc";
  final String identifier = "id";
  final String identifier_1 = "id1";

  final SampleBean sampleBean = SampleBean.builder()
                                    .test1("test")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();

  final SampleBean sampleBean1 = SampleBean.builder()
                                     .test1("test1")
                                     .projectIdentifier(projectIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .accountIdentifier(accountIdentifier)
                                     .identifier(identifier_1)
                                     .build();

  @Before
  public void setup() {
    initMocks(this);
    gitAwarePersistence = new io.harness.gitsync.persistance.GitAwarePersistenceNewImpl(mongoTemplate,
        gitSyncSdkService, gitPersistenceHelperServiceMap, scmGitSyncHelper, gitSyncMsvcHelper,
        NG_DEFAULT_OBJECT_MAPPER, mock(TransactionTemplate.class));
    doNothing().when(gitSyncMsvcHelper).postPushInformationToGitMsvc(any(), any(), any());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSave_0() {
    doSave(sampleBean, false, "branch", false, "ygs");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testFind() {
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      final SampleBean sampleBean_saved = doSave(sampleBean, false, "branch", true, "ygs");
      final SampleBean sampleBean_saved_1 = doSave(sampleBean1, false, "branch", true, "ygs");
      assertFindReturnsObject(sampleBean_saved);
      assertFindReturnsObject(sampleBean_saved_1);
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testExists() {
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder()
              .gitBranchInfo(GitEntityInfo.builder().repoName("repo").branch("branch").build())
              .gitBranchInfo(newBranch)
              .build());
      final SampleBean sampleBean_saved = doSave(sampleBean, false, "branch", true, "ygs");
      final Criteria criteria = Criteria.where("uuid").is(sampleBean_saved.getUuid());
      assertThat(
          gitAwarePersistence.exists(criteria, projectIdentifier, orgIdentifier, accountIdentifier, SampleBean.class))
          .isTrue();
    }
  }

  private void assertFindReturnsObject(SampleBean sampleBean_saved) {
    final Criteria criteria = Criteria.where("uuid").is(sampleBean_saved.getUuid());
    final Optional<SampleBean> retObject =
        gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountIdentifier, SampleBean.class);
    assertThat(retObject.isPresent()).isTrue();
  }

  private SampleBean doSave(
      SampleBean sampleBean, boolean isDefaultBranch, String branch, boolean isGitSyncEnabled, String ygs) {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId(ygs)
                 .branch(branch)
                 .pushToDefaultBranch(isDefaultBranch)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(isGitSyncEnabled).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    return gitAwarePersistence.save(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSave_1() {
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      final SampleBean sampleBean_saved = doSave(sampleBean, false, "branch", true, "ygs");
    }
    final GitEntityInfo branch_1 = GitEntityInfo.builder().branch("master").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());

      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      final SampleBean sampleBean_saved = doSave(sampleBean_1, true, "master", true, "ygs");
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSave_2() {
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      final SampleBean sampleBean_saved = doSave(sampleBean, false, "branch", true, "ygs");
    }
    final GitEntityInfo branch_1 = GitEntityInfo.builder().branch("master").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());

      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test1")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      final SampleBean sampleBean_saved = doSave(sampleBean_1, true, "master", true, "ygs");
    }

    final long count = mongoTemplate.count(query(new Criteria()), SampleBean.class);
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSave_3() {
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      SampleBean sampleBean_saved = doSave(sampleBean, true, "branch", true, "ygs");
      assertFindReturnsObject(sampleBean_saved);
    }
    final GitEntityInfo branch_1 = GitEntityInfo.builder().branch("master").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());
      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test1")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      SampleBean sampleBean_saved_1 = doSave(sampleBean_1, false, "master", true, "ygs");
      assertFindReturnsObject(sampleBean_saved_1);
    }

    final long count = mongoTemplate.count(query(new Criteria()), SampleBean.class);
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testList() {
    SampleBean sampleBean_saved;
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      sampleBean_saved = doSave(sampleBean, true, "branch", true, "ygs");
      doSave(sampleBean1, true, "branch", true, "ygs");
    }
    final GitEntityInfo branch_1 = GitEntityInfo.builder().branch("master").yamlGitConfigId("ygs").build();
    SampleBean sampleBean_saved_1;
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());
      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test2")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      sampleBean_saved_1 = doSave(sampleBean_1, false, "master", true, "ygs");
    }
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      final List<SampleBean> sampleBeans = gitAwarePersistence.find(new Criteria(), Pageable.unpaged(),
          projectIdentifier, orgIdentifier, accountIdentifier, SampleBean.class, false);
      assertThat(sampleBeans.size()).isEqualTo(2);
    }
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());
      final List<SampleBean> sampleBeans = gitAwarePersistence.find(new Criteria(), Pageable.unpaged(),
          projectIdentifier, orgIdentifier, accountIdentifier, SampleBean.class, false);
      assertThat(sampleBeans.size()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testListFromDefault() {
    SampleBean sampleBean_saved;
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").build();
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      sampleBean_saved = doSave(sampleBean, true, "branch", true, "ygs");
    }
    final GitEntityInfo branch_1 = GitEntityInfo.builder().branch("master").yamlGitConfigId("ygs1").build();
    SampleBean sampleBean_saved_1;
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());
      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test2")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      sampleBean_saved_1 = doSave(sampleBean_1, true, "master", true, "ygs1");
    }

    final GitEntityInfo branch_2 = GitEntityInfo.builder().branch("master1").yamlGitConfigId("ygs1").build();

    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branch_1).build());
      SampleBean sampleBean_1 = SampleBean.builder()
                                    .test1("test2")
                                    .projectIdentifier(projectIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .accountIdentifier(accountIdentifier)
                                    .identifier(identifier)
                                    .build();
      sampleBean_saved_1 = doSave(sampleBean_1, false, "master1", true, "ygs1");
    }
    try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      final GitEntityInfo finalBranch =
          GitEntityInfo.builder().branch("branch").yamlGitConfigId("ygs").findDefaultFromOtherRepos(true).build();
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(finalBranch).build());
      final List<SampleBean> sampleBeans = gitAwarePersistence.find(new Criteria(), Pageable.unpaged(),
          projectIdentifier, orgIdentifier, accountIdentifier, SampleBean.class, false);
      assertThat(sampleBeans.size()).isEqualTo(2);
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSave_4() {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId("ygs")
                 .branch("branch")
                 .pushToDefaultBranch(false)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    SampleBean sampleBean_saved_1 = gitAwarePersistence.save(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class, null);
    assertThat(sampleBean_saved_1).isNull();
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    sampleBean_saved_1 = gitAwarePersistence.save(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.NONE, SampleBean.class, null);
    assertThat(sampleBean_saved_1).isNotNull();
    assertThat(sampleBean_saved_1).isEqualTo(sampleBean);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSaveGitSyncEnabled() {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId("ygs")
                 .branch("branch")
                 .pushToDefaultBranch(false)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    SampleBean sampleBean_saved_1 = gitAwarePersistence.save(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class, null);
    assertThat(sampleBean_saved_1).isNotNull();
    assertThat(sampleBean_saved_1).isEqualTo(sampleBean);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId("ygs")
                 .branch("branch")
                 .pushToDefaultBranch(false)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    assertThatThrownBy(()
                           -> gitAwarePersistence.delete(sampleBean, NGYamlUtils.getYamlString(sampleBean),
                               ChangeType.ADD, SampleBean.class, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ChangeType : ADD, is not supported for delete operation");
    gitAwarePersistence.save(sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class, null);
    gitAwarePersistence.delete(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.DELETE, SampleBean.class, null);
    verify(scmGitSyncHelper, times(2)).pushToGit(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDelete_1() {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId("ygs")
                 .branch("branch")
                 .pushToDefaultBranch(false)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    assertThatThrownBy(() -> gitAwarePersistence.delete(sampleBean, ChangeType.ADD, SampleBean.class))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ChangeType : ADD, is not supported for delete operation");
    gitAwarePersistence.save(sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class, null);
    gitAwarePersistence.delete(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.DELETE, SampleBean.class, null);
    verify(scmGitSyncHelper, times(2)).pushToGit(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCount() {
    doReturn(new SampleBeanEntityGitPersistenceHelperServiceImpl())
        .when(gitPersistenceHelperServiceMap)
        .get(anyString());
    final String objectIdOfYaml = EntityObjectIdUtils.getObjectIdOfYaml(sampleBean);
    doReturn(ScmCreateFileResponse.builder()
                 .accountIdentifier("acc")
                 .objectId(objectIdOfYaml)
                 .yamlGitConfigId("ygs")
                 .branch("branch")
                 .pushToDefaultBranch(false)
                 .build())
        .when(scmGitSyncHelper)
        .pushToGit(any(), anyString(), any(), any());
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    SampleBean sampleBean_saved_1 = gitAwarePersistence.save(
        sampleBean, NGYamlUtils.getYamlString(sampleBean), ChangeType.ADD, SampleBean.class, null);
    assertThat(sampleBean_saved_1).isNotNull();
    assertThat(sampleBean_saved_1).isEqualTo(sampleBean);
    Criteria criteria = new Criteria();
    long count = gitAwarePersistence.count(criteria, null, null, "acc", SampleBean.class);
    assertThat(count).isEqualTo(1);
  }
}
