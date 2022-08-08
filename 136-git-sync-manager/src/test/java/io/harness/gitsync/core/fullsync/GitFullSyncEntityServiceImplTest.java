/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoFilterDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.repositories.fullSync.GitFullSyncEntityRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class GitFullSyncEntityServiceImplTest extends GitSyncTestBase {
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String FILE_PATH = "filePath";
  public static final String ERROR_MSG = "error";
  private GitFullSyncEntityServiceImpl gitFullSyncEntityService;
  @Inject private GitFullSyncEntityRepository gitFullSyncEntityRepository;
  @Inject private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;

  @Before
  public void setup() {
    gitFullSyncEntityService = new GitFullSyncEntityServiceImpl(gitFullSyncEntityRepository);
  }
  private String getStringValueFromProtoString(StringValue stringValue) {
    if (stringValue != null) {
      return stringValue.getValue();
    }
    return null;
  }

  private GitFullSyncEntityInfo getFullSyncEntityInfo(EntityScopeInfo entityScopeInfo, String messageId,
      Microservice microservice, FileChange entityForFullSync, SyncStatus syncStatus) {
    return GitFullSyncEntityInfo.builder()
        .accountIdentifier(entityScopeInfo.getAccountId())
        .filePath(entityForFullSync.getFilePath())
        .fullSyncJobId("fullSyncJobId")
        .projectIdentifier(getStringValueFromProtoString(entityScopeInfo.getProjectId()))
        .orgIdentifier(getStringValueFromProtoString(entityScopeInfo.getOrgId()))
        .microservice(microservice.name())
        .messageId(messageId)
        .entityDetail(entityDetailProtoToRestMapper.createEntityDetailDTO(entityForFullSync.getEntityDetail()))
        .syncStatus(syncStatus.toString())
        .retryCount(0)
        .build();
  }

  private void createFullSyncFile(String account, String org, String project, String filePath,
      EntityTypeProtoEnum entityTypeProtoEnum, String name, SyncStatus syncStatus, String messageId) {
    EntityScopeInfo entityScopeInfo = EntityScopeInfo.newBuilder()
                                          .setAccountId(account)
                                          .setOrgId(StringValue.of(org))
                                          .setProjectId(StringValue.of(project))
                                          .build();
    FileChange fileChange =
        FileChange.newBuilder()
            .setFilePath(filePath)
            .setEntityDetail(EntityDetailProtoDTO.newBuilder().setType(entityTypeProtoEnum).setName(name).build())
            .build();
    GitFullSyncEntityInfo gitFullSyncEntityInfo =
        getFullSyncEntityInfo(entityScopeInfo, messageId, random(Microservice.class), fileChange, syncStatus);
    gitFullSyncEntityService.save(gitFullSyncEntityInfo);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testListFullSyncFiles() {
    for (int i = 0; i < 6; i++) {
      createFullSyncFile(ACCOUNT, ORG, PROJECT, random(String.class), EntityTypeProtoEnum.CONNECTORS,
          random(String.class), SyncStatus.QUEUED, "messageId");
    }
    for (int i = 0; i < 6; i++) {
      createFullSyncFile(ACCOUNT, ORG, PROJECT, random(String.class), EntityTypeProtoEnum.INPUT_SETS,
          random(String.class), SyncStatus.SUCCESS, "messageId");
    }
    PageResponse<GitFullSyncEntityInfoDTO> response =
        gitFullSyncEntityService.list(ACCOUNT, ORG, PROJECT, PageRequest.builder().pageIndex(0).pageSize(5).build(),
            null, GitFullSyncEntityInfoFilterDTO.builder().syncStatus(SyncStatus.QUEUED).build());
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getTotalItems()).isEqualTo(6);
    assertThat(response.getPageItemCount()).isEqualTo(5);

    response =
        gitFullSyncEntityService.list(ACCOUNT, null, null, PageRequest.builder().pageIndex(0).pageSize(5).build(), null,
            GitFullSyncEntityInfoFilterDTO.builder().syncStatus(SyncStatus.QUEUED).build());
    assertThat(response.getContent()).isEmpty();

    response = gitFullSyncEntityService.list(ACCOUNT, ORG, PROJECT,
        PageRequest.builder().pageIndex(0).pageSize(5).build(), null,
        GitFullSyncEntityInfoFilterDTO.builder()
            .syncStatus(SyncStatus.SUCCESS)
            .entityTypes(Arrays.asList(EntityType.INPUT_SETS))
            .build());
    assertThat(response.getContent()).hasSize(5);
    assertThat(response.getTotalItems()).isEqualTo(6);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCountOfFilesWithFilter() {
    for (int i = 0; i < 6; i++) {
      createFullSyncFile(ACCOUNT, ORG, PROJECT, random(String.class), EntityTypeProtoEnum.CONNECTORS,
          random(String.class), SyncStatus.QUEUED, "messageId");
    }
    for (int i = 0; i < 6; i++) {
      createFullSyncFile(ACCOUNT, ORG, PROJECT, random(String.class), EntityTypeProtoEnum.INPUT_SETS,
          random(String.class), SyncStatus.SUCCESS, "messageId");
    }

    long count =
        gitFullSyncEntityService.count(ACCOUNT, ORG, PROJECT, GitFullSyncEntityInfoFilterDTO.builder().build());
    assertThat(count).isEqualTo(12);

    count = gitFullSyncEntityService.count(ACCOUNT, null, null, GitFullSyncEntityInfoFilterDTO.builder().build());
    assertThat(count).isEqualTo(0);

    count = gitFullSyncEntityService.count(ACCOUNT, ORG, PROJECT,
        GitFullSyncEntityInfoFilterDTO.builder().entityTypes(Arrays.asList(EntityType.CONNECTORS)).build());
    assertThat(count).isEqualTo(6);

    count = gitFullSyncEntityService.count(
        ACCOUNT, ORG, PROJECT, GitFullSyncEntityInfoFilterDTO.builder().syncStatus(SyncStatus.SUCCESS).build());
    assertThat(count).isEqualTo(6);

    count = gitFullSyncEntityService.count(ACCOUNT, ORG, PROJECT,
        GitFullSyncEntityInfoFilterDTO.builder()
            .syncStatus(SyncStatus.SUCCESS)
            .entityTypes(Arrays.asList(EntityType.INPUT_SETS))
            .build());
    assertThat(count).isEqualTo(6);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.QUEUED, "messageId");
    Optional<GitFullSyncEntityInfo> gitFullSyncEntityInfo =
        gitFullSyncEntityService.get(ACCOUNT, ORG, PROJECT, FILE_PATH);

    gitFullSyncEntityService.updateStatus(ACCOUNT, gitFullSyncEntityInfo.get().getUuid(), SyncStatus.FAILED, ERROR_MSG);
    Optional<GitFullSyncEntityInfo> updateGitFullSyncEntityInfo =
        gitFullSyncEntityService.get(ACCOUNT, ORG, PROJECT, FILE_PATH);
    assertThat(updateGitFullSyncEntityInfo.isPresent()).isEqualTo(true);
    assertThat(updateGitFullSyncEntityInfo.get().getSyncStatus()).isEqualTo(SyncStatus.FAILED.toString());
    assertThat(updateGitFullSyncEntityInfo.get().getErrorMessage()).isEqualTo(Collections.singletonList(ERROR_MSG));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAll() {
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.QUEUED, "messageId");
    gitFullSyncEntityService.deleteAll(ACCOUNT, ORG, PROJECT);
    assertThat(gitFullSyncEntityService.get(ACCOUNT, ORG, PROJECT, FILE_PATH).isPresent()).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getQueuedEntityFromPrevJobTest() {
    // Case 1: When no prev job exits
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.QUEUED, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.QUEUED, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.QUEUED, "messageId");
    final List<GitFullSyncEntityInfo> previousJobsList =
        gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(ACCOUNT, ORG, PROJECT, "messageId");
    assertThat(previousJobsList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getQueuedEntityFromPrevJobTest1() {
    // Case 2: When a prev job exits with 2 queued and 2 successful files
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.PIPELINES, random(String.class),
        SyncStatus.QUEUED, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.PIPELINES, random(String.class),
        SyncStatus.QUEUED, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.SUCCESS, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.CONNECTORS, random(String.class),
        SyncStatus.SUCCESS, "messageId");
    createFullSyncFile(ACCOUNT, ORG, PROJECT, FILE_PATH, EntityTypeProtoEnum.PIPELINES, random(String.class),
        SyncStatus.QUEUED, "messageId1");
    final List<GitFullSyncEntityInfo> previousJobsList =
        gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(ACCOUNT, ORG, PROJECT, "messageId1");
    assertThat(previousJobsList.size()).isEqualTo(2);
  }
}
