package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PIPELINE)
public class NGTemplateRepositoryCustomImplTest {
  NGTemplateRepositoryCustomImpl ngTemplateRepositoryCustom;
  @Mock GitAwarePersistence gitAwarePersistence;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock MongoTemplate mongoTemplate;
  @Mock OutboxService outboxService;
  @Mock NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
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
        gitAwareEntityHelper, mongoTemplate, ngTemplateFeatureFlagHelperService, outboxService);

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
                                        .identifier(pipelineId)
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
                                        .identifier(pipelineId)

                                        .yaml(pipelineYaml)
                                        .build();
    TemplateEntity templateToSaveWithStoreType = templateToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);

    TemplateEntity templateToSaveWithStoreTypeWithExtraFields =
        templateToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(templateToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(templateToSaveWithStoreType);

    TemplateEntity savedTemplateEntity = ngTemplateRepositoryCustom.save(templateToSave, templateComment);
    assertThat(savedTemplateEntity).isEqualTo(templateToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(templateToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDPresent() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    boolean isNewGitXEnabled = ngTemplateRepositoryCustom.isNewGitXEnabled(templateToSave, branchInfo);
    assertTrue(isNewGitXEnabled);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDMissingWithoutFeatureFlag() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    boolean isNewGitXEnabled = ngTemplateRepositoryCustom.isNewGitXEnabled(templateToSave, branchInfo);
    assertFalse(isNewGitXEnabled);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDMissingWithFeatureFlagEnabled() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();

    when(ngTemplateFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.FF_TEMPLATE_GITSYNC))
        .thenReturn(true);

    boolean isNewGitXEnabled = ngTemplateRepositoryCustom.isNewGitXEnabled(templateToSave, branchInfo);
    assertTrue(isNewGitXEnabled);
  }
}
