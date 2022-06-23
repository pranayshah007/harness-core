/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;

import java.util.Optional;
import java.util.function.Supplier;
import lombok.Data;
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

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";

  Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                          .is(false)
                          .and(PipelineEntityKeys.identifier)
                          .is(pipelineId)
                          .and(PipelineEntityKeys.projectIdentifier)
                          .is(projectIdentifier)
                          .and(PipelineEntityKeys.orgIdentifier)
                          .is(orgIdentifier)
                          .and(PipelineEntityKeys.accountId)
                          .is(accountIdentifier);
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
        pipelineMetadataService, gitAwareEntityHelper, outboxService, gitSyncSdkService);
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

    DummyClassForSupplier dummy = new DummyClassForSupplier();
    Supplier<OutboxEvent> randomSupplier = () -> {
      dummy.setVal(10);
      return null;
    };
    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, randomSupplier);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    assertThat(dummy.getVal()).isEqualTo(10);
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
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withYaml("")
                                                     .withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    DummyClassForSupplier dummy = new DummyClassForSupplier();
    Supplier<OutboxEvent> randomSupplier = () -> {
      dummy.setVal(10);
      return null;
    };
    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, randomSupplier);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    assertThat(dummy.getVal()).isEqualTo(10);
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFindInlinePipeline() {
    PipelineEntity inlinePipelineEntity = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .yaml(pipelineYaml)
                                              .storeType(StoreType.INLINE)
                                              .build();
    Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                            .is(false)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineId)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.accountId)
                            .is(accountIdentifier);
    Query query = new Query(criteria);
    doReturn(inlinePipelineEntity).when(mongoTemplate).findOne(query, PipelineEntity.class);
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineRepository.find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(inlinePipelineEntity);
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
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineRepository.find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
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

  @Data
  public static class DummyClassForSupplier {
    int val;
  }
}
