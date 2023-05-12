/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.ScmBadRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.service.PipelineEntityReadHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PMSPipelineRepositoryCustomImplTest extends CategoryTest {
  PMSPipelineRepositoryCustomImpl pipelineRepository;
  @Mock MongoTemplate mongoTemplate;
  @Mock GitAwarePersistence gitAwarePersistence;
  @Mock TransactionHelper transactionHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock OutboxService outboxService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock PipelineEntityReadHelper pipelineEntityReadHelper;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";
  String repoURL = "repoURL";

  String scmBadRequest = "SCM bad request";

  Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                          .is(accountIdentifier)
                          .and(PipelineEntityKeys.orgIdentifier)
                          .is(orgIdentifier)
                          .and(PipelineEntityKeys.projectIdentifier)
                          .is(projectIdentifier)
                          .and(PipelineEntityKeys.identifier)
                          .is(pipelineId)
                          .and(PipelineEntityKeys.deleted)
                          .is(false);
  Query query = new Query(criteria);

  Scope scope = Scope.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .build();

  String repoName = "repoName";
  String branch = "isThisMaster";
  String connectorRef = "conn";
  String filePath = "./harness/filepath.yaml";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineRepository = new PMSPipelineRepositoryCustomImpl(mongoTemplate, gitAwarePersistence, transactionHelper,
        pipelineMetadataService, gitAwareEntityHelper, outboxService, gitSyncSdkService, pipelineEntityReadHelper);
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
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSave() {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity somethingAddedToPipelineToSave = pipelineToSave.withVersion(0L);
    doReturn(somethingAddedToPipelineToSave).when(transactionHelper).performTransaction(any());
    PipelineEntity savedEntity = pipelineRepository.save(pipelineToSave);
    assertThat(savedEntity).isEqualTo(somethingAddedToPipelineToSave);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveInlinePipelineEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.INLINE);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveRemotePipelineEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  // Test pipeline entity properties for inline pipeline (both with and without metadata flag)
  public void testFindInlinePipeline() {
    PipelineEntity inlinePipelineEntity = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .yaml(pipelineYaml)
                                              .storeType(StoreType.INLINE)
                                              .build();

    doReturn(inlinePipelineEntity).when(mongoTemplate).findOne(query, PipelineEntity.class);
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    PipelineEntity pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(inlinePipelineEntity);
    assertThat(pipelineEntityFound.getYaml()).isNotEmpty();
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());

    Query withMetadataQuery = query;
    for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
      withMetadataQuery.fields().exclude(nonMetadataField);
    }
    PipelineEntity withMetadataEntity = PipelineEntity.builder()
                                            .accountId(accountIdentifier)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .identifier(pipelineId)
                                            .storeType(StoreType.INLINE)
                                            .build();

    doReturn(withMetadataEntity).when(mongoTemplate).findOne(withMetadataQuery, PipelineEntity.class);
    optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(withMetadataEntity);
    assertThat(pipelineEntityFound.getYaml()).isNullOrEmpty();
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFindRemotePipeline() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(query, PipelineEntity.class);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    PipelineEntity pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(remotePipelineWithYAML);
    assertThat(pipelineEntityFound.getYaml()).isNotEmpty();
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());

    // With metadata flag true test
    Query withMetadataQuery = query;
    for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
      withMetadataQuery.fields().exclude(nonMetadataField);
    }

    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(withMetadataQuery, PipelineEntity.class);
    optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(remotePipelineFromDB);
    assertThat(pipelineEntityFound.getYaml()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemotePipelineWithLoadFromCache() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(query, PipelineEntity.class);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(remotePipelineWithYAML);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateInlinePipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.INLINE)
                                          .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name("new name")
                                        .description("new desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.INLINE)
                                        .version(1L)
                                        .build();
    doReturn(pipelineEntity).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(0)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateRemotePipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name("new name")
                                        .description("new desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.REMOTE)
                                        .version(1L)
                                        .build();
    doReturn(pipelineEntity).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(1)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateNonExistentPipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    doReturn(null).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate);
    assertThat(updatedEntity).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineEntityInDB() {
    Query query = new Query();
    Update update = new Update();
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    PipelineEntity oldEntityFromDB = PipelineEntity.builder()
                                         .accountId(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier(pipelineId)
                                         .name("name")
                                         .description("desc")
                                         .yaml(newYaml)
                                         .storeType(StoreType.REMOTE)
                                         .createdAt(0L)
                                         .build();
    doReturn(oldEntityFromDB).when(mongoTemplate).findAndModify(any(), any(), any(), any(Class.class));
    PipelineEntity pipelineEntity = pipelineRepository.updatePipelineEntityInDB(query, update, pipelineToUpdate, 1L);
    assertThat(pipelineEntity.getCreatedAt()).isEqualTo(0L);
    assertThat(pipelineEntity.getLastUpdatedAt()).isEqualTo(1L);
    assertThat(pipelineEntity.getYaml()).isEqualTo(newYaml);
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    Update update = new Update();
    update.set(PipelineEntityKeys.deleted, true);
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .deleted(true)
                                        .build();
    doReturn(pipelineEntity).when(mongoTemplate).findAndRemove(any(), any());
    pipelineRepository.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId);
    verify(mongoTemplate, times(1)).findAndRemove(any(), any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteAllPipelineInProject() {
    Update update = new Update();
    update.set(PipelineEntityKeys.deleted, true);
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .deleted(true)
                                        .build();
    List<PipelineEntity> entityList = Arrays.asList(pipelineEntity);
    doReturn(entityList).when(mongoTemplate).findAllAndRemove(any(), (Class<PipelineEntity>) any());
    pipelineRepository.deleteAllPipelinesInAProject(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(mongoTemplate, times(1)).findAllAndRemove(any(), (Class<PipelineEntity>) any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCountFileInstances() {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFileUniquenessCheck(accountIdentifier, repoURL, filePath);
    Query query = new Query(criteria);
    doReturn(17L).when(pipelineEntityReadHelper).findCount(query);
    assertThat(pipelineRepository.countFileInstances(accountIdentifier, repoURL, filePath)).isEqualTo(17L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNotPresent() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false));
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWhenLoadFromCache() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .branch(branch)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, true);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsSameDefaultBranch() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false));
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsDifferentDefaultBranch() {
    // when fetch from the default branch fails and branch present in metadata are different from fetched branch
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .branch(branch)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);

    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doReturn(remotePipelineWithYAML)
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false);
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsDifferentDefaultBrancAndGitContextIsSet() {
    // when fetch from the default branch fails and branch present in metadata are different from fetched branch
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);

    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doReturn(remotePipelineWithYAML)
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());

    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false);

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
    assertEquals(fallBackBranch, gitEntityInfo.getBranch());
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndNonDefaultFails() {
    // when fetch from default fails and branch present in metadata also fails
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false));
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdatePipelineOperationsInlineToRemote() {
    Criteria pipelineCriteria = new Criteria();
    Update pipelineUpdate = new Update();
    Criteria metadataCriteria = new Criteria();
    Update metadataUpdate = new Update();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields)
        .when(mongoTemplate)
        .findAndModify(any(), any(), any(), any(Class.class));

    PipelineEntity movedPipeline = pipelineRepository.moveConfigOperations(
        pipelineToSave, pipelineUpdate, pipelineCriteria, metadataUpdate, metadataCriteria, INLINE_TO_REMOTE);
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);

    verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), any(Class.class));
    verify(pipelineMetadataService, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdatePipelineOperationsRemoteToInline() {
    Criteria pipelineCriteria = new Criteria();
    Update pipelineUpdate = new Update();
    Criteria metadataCriteria = new Criteria();
    Update metadataUpdate = new Update();

    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields)
        .when(mongoTemplate)
        .findAndModify(any(), any(), any(), any(Class.class));

    PipelineEntity movedPipeline = pipelineRepository.moveConfigOperations(
        pipelineToSave, pipelineUpdate, pipelineCriteria, metadataUpdate, metadataCriteria, REMOTE_TO_INLINE);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);

    verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), any(Class.class));
    verify(pipelineMetadataService, times(1)).update(any(), any());
  }
}
