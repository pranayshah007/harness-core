/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.MEET;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.FileChanges;
import io.harness.gitsync.FullSyncEventRequest;
import io.harness.gitsync.FullSyncServiceGrpc;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PL)
public class FullSyncAccumulatorServiceImplTest extends GitSyncTestBase {
  @InjectMocks FullSyncAccumulatorServiceImpl fullSyncAccumulatorService;
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock FullSyncServiceGrpc.FullSyncServiceBlockingStub fullSyncServiceBlockingStub;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock GitFullSyncEntityService gitFullSyncEntityService;
  @Mock GitBranchService gitBranchService;
  @Mock FullSyncJobService fullSyncJobService;

  Map<Microservice, FullSyncServiceGrpc.FullSyncServiceBlockingStub> fullSyncServiceBlockingStubMap = new HashMap<>();
  FullSyncEventRequest fullSyncEventRequest;
  FileChanges fileChanges;
  FileChange fileChange;
  EntityDetail entityDetail;
  EntityScopeInfo entityScopeInfo;
  YamlGitConfigDTO yamlGitConfigDTO;
  GitFullSyncJob gitFullSyncJob;
  String messageId = "messageId";
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String identifier = "identifier";
  String filePath = "filePath";
  String name = "name";
  String repo = "repo";

  @Before
  public void setup() throws Exception {
    fullSyncServiceBlockingStubMap.put(Microservice.CD, fullSyncServiceBlockingStub);
    FieldUtils.writeField(
        fullSyncAccumulatorService, "fullSyncServiceBlockingStubMap", fullSyncServiceBlockingStubMap, true);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testTriggerFullSync() throws Exception {
    entityScopeInfo = EntityScopeInfo.newBuilder()
                          .setAccountId(accountId)
                          .setOrgId(StringValue.of(orgId))
                          .setProjectId(StringValue.of(projectId))
                          .setIdentifier(identifier)
                          .build();
    fullSyncEventRequest = FullSyncEventRequest.newBuilder().setGitConfigScope(entityScopeInfo).build();
    fileChange = FileChange.newBuilder()
                     .setFilePath(filePath)
                     .setEntityDetail(EntityDetailProtoDTO.newBuilder().build())
                     .build();
    fileChanges = FileChanges.newBuilder().addFileChanges(fileChange).build();
    entityDetail = EntityDetail.builder().build();

    yamlGitConfigDTO = YamlGitConfigDTO.builder().name(name).repo(repo).build();
    when(yamlGitConfigService.get(any(), any(), any(), any())).thenReturn(yamlGitConfigDTO);
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             fullSyncServiceBlockingStub::getEntitiesForFullSync, any(ScopeDetails.class)))
        .thenReturn(fileChanges);
    when(entityDetailProtoToRestMapper.createEntityDetailDTO(any())).thenReturn(entityDetail);
    doNothing().when(gitFullSyncEntityService).updateStatus(any(), any(), any(), any(), any(), any());
    doNothing().when(gitBranchService).updateBranchSyncStatus(any(), any(), any(), any());
    doThrow(DuplicateKeyException.class).when(fullSyncJobService).save(any());
    fullSyncAccumulatorService.triggerFullSync(fullSyncEventRequest, messageId);
    verify(fullSyncServiceBlockingStub).getEntitiesForFullSync(any());
    verify(fullSyncJobService).save(any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void markTheQueuedFilesAsSuccessfullySynced_Test() {
    GitFullSyncEntityInfo gitFullSyncEntityInfo = GitFullSyncEntityInfo.builder()
                                                      .accountIdentifier(accountId)
                                                      .orgIdentifier(orgId)
                                                      .projectIdentifier(projectId)
                                                      .filePath("filePath1")
                                                      .build();
    GitFullSyncEntityInfo gitFullSyncEntityInfo1 = GitFullSyncEntityInfo.builder()
                                                       .accountIdentifier(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .filePath("filePath2")
                                                       .build();
    when(gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(accountId, orgId, projectId, messageId))
        .thenReturn(Arrays.asList(gitFullSyncEntityInfo, gitFullSyncEntityInfo1));
    fullSyncAccumulatorService.markTheQueuedFilesAsSuccessfullySynced(accountId, orgId, projectId, messageId);
    verify(gitFullSyncEntityService, times(2)).updateStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void markTheQueuedFilesAsSuccessfullySynced_Test1() {
    when(gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(accountId, orgId, projectId, messageId))
        .thenReturn(Collections.emptyList());
    fullSyncAccumulatorService.markTheQueuedFilesAsSuccessfullySynced(accountId, orgId, projectId, messageId);
    verify(gitFullSyncEntityService, never()).updateStatus(any(), any(), any(), any());
  }
}
