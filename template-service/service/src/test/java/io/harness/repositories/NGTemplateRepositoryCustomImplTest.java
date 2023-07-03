/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
public class NGTemplateRepositoryCustomImplTest extends CategoryTest {
  NGTemplateRepositoryCustomImpl ngTemplateRepositoryCustom;
  @InjectMocks NGTemplateServiceHelper templateServiceHelper;
  @Mock GitAwarePersistence gitAwarePersistence;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock MongoTemplate mongoTemplate;
  @Mock OutboxService outboxService;
  @Mock NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;

  @Mock TemplateGitXService templateGitXService;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String templateId = "template";
  String templateVersion = "v1";
  String pipelineYaml = "pipeline: yaml";

  Scope scope = Scope.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .build();

  String repoName = "repoName";
  String branch = "isThisMaster";
  String connectorRef = "conn";
  String filePath = "./harness/filepath.yaml";
  String templateComment = "template comment";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    ngTemplateRepositoryCustom = new NGTemplateRepositoryCustomImpl(gitAwarePersistence, gitSyncSdkService,
        gitAwareEntityHelper, mongoTemplate, templateGitXService, outboxService);

    doReturn(true)
        .when(gitSyncSdkService)
        .isGitSimplificationEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSaveInlineTemplateEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    setupGitContext(branchInfo);

    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .build();

    TemplateEntity templateToSaveWithStoreType = templateToSave.withStoreType(StoreType.INLINE);

    TemplateEntity templateToSaveWithStoreTypeWithExtraFields =
        templateToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(templateToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(templateToSaveWithStoreType);

    TemplateEntity savedTemplateEntity = ngTemplateRepositoryCustom.save(templateToSave, templateComment);

    assertThat(savedTemplateEntity).isEqualTo(templateToSaveWithStoreTypeWithExtraFields);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSaveRemoteTemplateEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);

    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .yaml(pipelineYaml)
                                        .build();
    TemplateEntity templateToSaveWithStoreType = templateToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFallBackBranch(branch)
                                                     .withFilePath(filePath);

    TemplateEntity templateToSaveWithStoreTypeWithExtraFields =
        templateToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(templateToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(templateToSaveWithStoreType);
    when(templateGitXService.isNewGitXEnabledAndIsRemoteEntity(any(), any())).thenReturn(true);
    TemplateEntity savedTemplateEntity = ngTemplateRepositoryCustom.save(templateToSave, templateComment);
    assertThat(savedTemplateEntity).isEqualTo(templateToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(templateToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAllTemplatesInAProject() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .build();
    List<TemplateEntity> entityList = Arrays.asList(templateEntity);
    doReturn(entityList).when(mongoTemplate).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    ngTemplateRepositoryCustom.deleteAllTemplatesInAProject(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(mongoTemplate, times(1)).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    verify(outboxService, times(1)).save(any());

    // exception cases
    doThrow(new NullPointerException("npe")).when(mongoTemplate).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    boolean deleted =
        ngTemplateRepositoryCustom.deleteAllTemplatesInAProject(accountIdentifier, orgIdentifier, projectIdentifier);
    assertFalse(deleted);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAllTemplatesInAOrg() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .build();
    List<TemplateEntity> entityList = Arrays.asList(templateEntity);
    doReturn(entityList).when(mongoTemplate).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    ngTemplateRepositoryCustom.deleteAllOrgLevelTemplates(accountIdentifier, orgIdentifier);
    verify(mongoTemplate, times(1)).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    verify(outboxService, times(1)).save(any());

    // exception cases
    doThrow(new NullPointerException("npe")).when(mongoTemplate).findAllAndRemove(any(), (Class<TemplateEntity>) any());
    boolean deleted = ngTemplateRepositoryCustom.deleteAllOrgLevelTemplates(accountIdentifier, orgIdentifier);
    assertFalse(deleted);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCountInstances() {
    Long occurance = ngTemplateRepositoryCustom.countFileInstances(accountIdentifier, "repoUrl", "filePath");
    assertThat(occurance.toString().equals("0"));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDMissingWithoutFeatureFlag() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    boolean isNewGitXEnabled = templateGitXService.isNewGitXEnabledAndIsRemoteEntity(templateToSave, branchInfo);
    assertFalse(isNewGitXEnabled);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledNotEnabled() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    setupGitContext(branchInfo);

    when(templateGitXService.isNewGitXEnabledAndIsRemoteEntity(any(), any())).thenReturn(false);
    assertThatThrownBy(() -> ngTemplateRepositoryCustom.save(templateToSave, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Remote git simplification or feature flag was not enabled for Organisation [org] or Account [acc]");

    assertThatThrownBy(() -> ngTemplateRepositoryCustom.save(templateToSave.withProjectIdentifier("proj"), ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Remote git simplification was not enabled for Project [proj] in Organisation [org] in Account [acc]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .versionLabel(templateVersion)
                                        .build();
    ngTemplateRepositoryCustom
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync(
            accountIdentifier, orgIdentifier, projectIdentifier, templateId, templateVersion, true);
    verify(gitAwarePersistence, times(1)).findOne(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .versionLabel(templateVersion)
                                        .build();
    ngTemplateRepositoryCustom
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync(
            accountIdentifier, orgIdentifier, projectIdentifier, templateId, true);
    verify(gitAwarePersistence, times(1)).findOne(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithFallBackBranch() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .versionLabel(templateVersion)
                                        .branch("main")
                                        .fallBackBranch("fallback")
                                        .build();
    doThrow(new ScmException("error", ErrorCode.SCM_BAD_REQUEST))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThatThrownBy(()
                           -> ngTemplateRepositoryCustom.fetchRemoteEntityWithFallBackBranch(
                               accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "main", false))
        .isInstanceOf(ScmException.class);

    doReturn(templateEntity).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    TemplateEntity entity = ngTemplateRepositoryCustom.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "main", false);
    assertEquals(entity, templateEntity);

    doThrow(new InvalidRequestException("error"))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThatThrownBy(()
                           -> ngTemplateRepositoryCustom.fetchRemoteEntityWithFallBackBranch(
                               accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "main", false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateYamlWithGitXWhenTemplateDoesNotExist() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .versionLabel(templateVersion)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    when(templateGitXService.isNewGitXEnabledAndIsRemoteEntity(any(), any())).thenReturn(false);
    assertThatThrownBy(()
                           -> ngTemplateRepositoryCustom.updateTemplateYaml(templateEntity, templateEntity,
                               ChangeType.UPDATE_V2, "", TemplateUpdateEventType.OTHERS_EVENT, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template with identifier [template] and versionLabel [v1], under Project[null], Organization [org] could not be updated.");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForRemoteTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    setupGitContext(branchInfo);

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());
    doReturn(templateEntity).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<TemplateEntity> optionalPipelineEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, true, false, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForInlineTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());

    Optional<TemplateEntity> optionalPipelineEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, true, true, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForRemoteTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    setupGitContext(branchInfo);

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());
    doReturn(templateEntity).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<TemplateEntity> optionalTemplateEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, templateVersion, true, false, false,
                false);
    assertThat(optionalTemplateEntity.isPresent()).isTrue();
    assertThat(optionalTemplateEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());

    // load from fallback branch
    doReturn(templateEntity).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    optionalTemplateEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, templateVersion, true, false, false,
                true);
    assertThat(optionalTemplateEntity.isPresent()).isTrue();
    assertThat(optionalTemplateEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForInlineTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());

    Optional<TemplateEntity> optionalPipelineEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, templateVersion, true, true, false,
                false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForRemoteTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    setupGitContext(branchInfo);

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());
    doReturn(templateEntity).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<TemplateEntity> optionalPipelineEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, true, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForInlineTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();

    doReturn(templateEntity).when(mongoTemplate).findOne(any(), any());

    Optional<TemplateEntity> optionalPipelineEntity =
        ngTemplateRepositoryCustom
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
                accountIdentifier, orgIdentifier, projectIdentifier, templateId, true, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(templateEntity);
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateInlinePipeline() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    setupGitContext(branchInfo);
    String newYaml = "pipeline: new yaml";
    TemplateEntity templateToUpdate = TemplateEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(templateId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.INLINE)
                                          .build();
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .name("old name")
                                        .description("old desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.INLINE)
                                        .version(1L)
                                        .build();

    doReturn(templateToUpdate).when(mongoTemplate).save(any());
    TemplateEntity updatedEntity = ngTemplateRepositoryCustom.updateTemplateYaml(templateToUpdate, templateEntity,
        ChangeType.MODIFY, "", TemplateUpdateEventType.TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE_EVENT, true);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(0)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateRemotePipeline() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    String newYaml = "template: new yaml";
    TemplateEntity templateToUpdate = TemplateEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(templateId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .name("old name")
                                        .description("old desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.REMOTE)
                                        .version(1L)
                                        .build();

    doReturn(templateToUpdate).when(mongoTemplate).save(any());
    when(templateGitXService.isNewGitXEnabledAndIsRemoteEntity(any(), any())).thenReturn(true);
    TemplateEntity updatedEntity = ngTemplateRepositoryCustom.updateTemplateYaml(templateToUpdate, templateEntity,
        ChangeType.MODIFY, "", TemplateUpdateEventType.TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE_EVENT, true);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(1)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListMetadataRemote() {
    String newYaml = "template: new yaml";

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .name("old name")
                                        .description("old desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.REMOTE)
                                        .version(1L)
                                        .build();

    doReturn(Arrays.asList(templateEntity)).when(mongoTemplate).find(any(Query.class), any());
    Criteria criteria = templateServiceHelper.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        ngTemplateRepositoryCustom.findAll(accountIdentifier, orgIdentifier, projectIdentifier, criteria, pageRequest);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(templateId);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testDeleteTemplate() {
    Update update = new Update();
    update.set(TemplateEntityKeys.deleted, true);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(templateId)
                                        .yaml(pipelineYaml)
                                        .deleted(true)
                                        .build();
    doReturn(templateEntity).when(mongoTemplate).findAndRemove(any(), any());
    ngTemplateRepositoryCustom.deleteTemplate(templateEntity, "", false);
    verify(mongoTemplate, times(1)).findAndRemove(any(), any());
    verify(outboxService, times(1)).save(any());
  }
}
