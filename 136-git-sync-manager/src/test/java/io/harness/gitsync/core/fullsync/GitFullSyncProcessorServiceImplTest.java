/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.Microservice.CORE;
import static io.harness.Microservice.PMS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.GitFullSyncEntityInfoBuilder;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.QUEUED;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetReference;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.FullSyncServiceGrpc;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.FullSyncMsvcProcessingResponse;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.beans.FullSyncFilesGroupedByMsvc;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class GitFullSyncProcessorServiceImplTest extends CategoryTest {
  private static final String YAML_GIT_CONFIG = "yamlGitConfig";
  private static final String BRANCH = "branch";
  private static final String PR_TITLE = "pr title";
  private static final String ROOT_FOLDER = "rootFolder";
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String REPO_URL = "repo_url";
  private static final String MESSAGE_ID = "messageId";
  private static final String REF_CONNECTOR = "refConnector";
  private static GitFullSyncEntityInfoBuilder gitFullSyncEntityInfoBuilder;
  @Inject GitFullSyncProcessorServiceImpl gitFullSyncProcessorService;
  @Inject Map<Microservice, FullSyncServiceGrpc.FullSyncServiceBlockingStub> fullSyncServiceBlockingStubMap;
  @Inject EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Mock List<Microservice> microservicesProcessingOrder;
  @Mock GitFullSyncEntityService gitFullSyncEntityService;
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock GitBranchSyncService gitBranchSyncService;
  @Mock FullSyncJobService fullSyncJobService;
  @Mock ScmOrchestratorService scmOrchestratorService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    gitFullSyncProcessorService = new GitFullSyncProcessorServiceImpl(fullSyncServiceBlockingStubMap,
        yamlGitConfigService, entityDetailRestToProtoMapper, gitFullSyncEntityService, fullSyncJobService,
        scmOrchestratorService, microservicesProcessingOrder, gitBranchSyncService);
    gitFullSyncEntityInfoBuilder = GitFullSyncEntityInfo.builder()
                                       .accountIdentifier(ACCOUNT)
                                       .projectIdentifier(PROJECT)
                                       .orgIdentifier(ORG)
                                       .messageId(MESSAGE_ID)
                                       .syncStatus(QUEUED.name())
                                       .yamlGitConfigId(YAML_GIT_CONFIG)
                                       .repoUrl(REPO_URL)
                                       .branchName(BRANCH)
                                       .rootFolder(ROOT_FOLDER)
                                       .retryCount(0);
    when(yamlGitConfigService.get(any(), any(), any(), any()))
        .thenReturn(YamlGitConfigDTO.builder()
                        .gitConnectorRef(REF_CONNECTOR)
                        .accountIdentifier(ACCOUNT)
                        .projectIdentifier(PROJECT)
                        .organizationIdentifier(ORG)
                        .build());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSortFilesInProcessingOrder() {
    List<GitFullSyncEntityInfo> entitiesToSync = new ArrayList<>();
    EntityDetail entityDetail =
        EntityDetail.builder().entityRef(InputSetReference.builder().identifier("input1").build()).build();
    entitiesToSync.add(createGitFullSyncEntity("input1", 1, entityDetail, PMS.name()));

    entityDetail = EntityDetail.builder().entityRef(IdentifierRef.builder().identifier("pip1").build()).build();
    entitiesToSync.add(createGitFullSyncEntity("pip1", 0, entityDetail, PMS.name()));

    entityDetail = EntityDetail.builder()
                       .entityRef(IdentifierRef.builder()
                                      .identifier(REF_CONNECTOR)
                                      .accountIdentifier(ACCOUNT)
                                      .orgIdentifier(ORG)
                                      .projectIdentifier(PROJECT)
                                      .build())
                       .build();
    entitiesToSync.add(createGitFullSyncEntity(REF_CONNECTOR, 0, entityDetail, CORE.name()));

    entityDetail = EntityDetail.builder().entityRef(IdentifierRef.builder().identifier("con1").build()).build();
    entitiesToSync.add(createGitFullSyncEntity("con1", 1, entityDetail, CORE.name()));

    List<FullSyncFilesGroupedByMsvc> filesGroupedByMsvcs =
        gitFullSyncProcessorService.sortTheFilesInTheProcessingOrder(entitiesToSync);

    assertThat(filesGroupedByMsvcs.size()).isEqualTo(2);
    assertThat(filesGroupedByMsvcs.get(0).getMicroservice()).isEqualTo(CORE);
    assertThat(filesGroupedByMsvcs.get(0).getGitFullSyncEntityInfoList().get(0).getFileProcessingSequenceNumber())
        .isEqualTo(1);
    assertThat(filesGroupedByMsvcs.get(0).getGitFullSyncEntityInfoList().get(1).getFilePath()).isEqualTo(REF_CONNECTOR);

    assertThat(filesGroupedByMsvcs.get(1).getMicroservice()).isEqualTo(PMS);
    assertThat(filesGroupedByMsvcs.get(1).getGitFullSyncEntityInfoList().get(0).getFilePath()).isEqualTo("pip1");
    assertThat(filesGroupedByMsvcs.get(1).getGitFullSyncEntityInfoList().get(1).getFilePath()).isEqualTo("input1");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testProcessBatchHavingNullList() {
    FullSyncMsvcProcessingResponse fullSyncMsvcProcessingResponse =
        gitFullSyncProcessorService.processFilesInBatches(PMS, null);
    assertThat(fullSyncMsvcProcessingResponse.getFullSyncFileResponses()).isEqualTo(Collections.EMPTY_LIST);
  }

  private GitFullSyncEntityInfo createGitFullSyncEntity(
      String filePath, int fileProcessingSequenceNumber, EntityDetail entityDetail, String microservice) {
    return gitFullSyncEntityInfoBuilder.filePath(filePath)
        .fileProcessingSequenceNumber(fileProcessingSequenceNumber)
        .entityDetail(entityDetail)
        .microservice(microservice)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void changeTheStatusOfFilesWhichSucceeded_Test() {
    GitFullSyncEntityInfo gitFullSyncEntityInfo = GitFullSyncEntityInfo.builder()
                                                      .accountIdentifier(ACCOUNT)
                                                      .orgIdentifier(ORG)
                                                      .projectIdentifier(PROJECT)
                                                      .filePath("filePath1")
                                                      .build();
    GitFullSyncEntityInfo gitFullSyncEntityInfo1 = GitFullSyncEntityInfo.builder()
                                                       .accountIdentifier(ACCOUNT)
                                                       .orgIdentifier(ORG)
                                                       .projectIdentifier(PROJECT)
                                                       .filePath("filePath2")
                                                       .build();
    when(gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(ACCOUNT, ORG, PROJECT, MESSAGE_ID))
        .thenReturn(Arrays.asList(gitFullSyncEntityInfo, gitFullSyncEntityInfo1));

    GitFullSyncEntityInfo newEntityToBeSynced1 = GitFullSyncEntityInfo.builder()
                                                     .accountIdentifier(ACCOUNT)
                                                     .orgIdentifier(ORG)
                                                     .projectIdentifier(PROJECT)
                                                     .filePath("filePath3")
                                                     .build();
    GitFullSyncEntityInfo newEntityToBeSynced2 = GitFullSyncEntityInfo.builder()
                                                     .accountIdentifier(ACCOUNT)
                                                     .orgIdentifier(ORG)
                                                     .projectIdentifier(PROJECT)
                                                     .filePath("filePath4")
                                                     .build();
    GitFullSyncEntityInfo newEntityToBeSynced3 = GitFullSyncEntityInfo.builder()
                                                     .accountIdentifier(ACCOUNT)
                                                     .orgIdentifier(ORG)
                                                     .projectIdentifier(PROJECT)
                                                     .filePath("filePath1")
                                                     .build();
    gitFullSyncProcessorService.markAlreadyProcessedFilesAsSuccess(ACCOUNT, ORG, PROJECT, "messageId",
        Arrays.asList(newEntityToBeSynced1, newEntityToBeSynced2, newEntityToBeSynced3));
    verify(gitFullSyncEntityService, times(1)).updateStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void changeTheStatusOfFilesWhichSucceeded_Test1() {
    when(gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(ACCOUNT, ORG, PROJECT, "messageId"))
        .thenReturn(Collections.emptyList());
    GitFullSyncEntityInfo newEntityToBeSynced1 = GitFullSyncEntityInfo.builder()
                                                     .accountIdentifier(ACCOUNT)
                                                     .orgIdentifier(ORG)
                                                     .projectIdentifier(PROJECT)
                                                     .filePath("filePath3")
                                                     .build();
    gitFullSyncProcessorService.markAlreadyProcessedFilesAsSuccess(
        ACCOUNT, ORG, PROJECT, MESSAGE_ID, Arrays.asList(newEntityToBeSynced1));
    verify(gitFullSyncEntityService, never()).updateStatus(any(), any(), any(), any());
  }
}
