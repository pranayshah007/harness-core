/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.service.impl;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dto.PolledResponse;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.dto.PollingResponseDTO;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pipeline.triggers.TriggersClient;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.ManifestPolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.repositories.polling.PollingRepository;
import io.harness.rule.Owner;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class PollingServiceImplTest extends CategoryTest {
  @InjectMocks PollingServiceImpl pollingService;
  @Mock PollingRepository pollingRepository;
  @Mock TriggersClient triggersClient;
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pollingDocId = "pollingDocId";
  String perpetualTaskId = "perpetualTaskId";
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetPollingInfoForTriggers() {
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();
    PollingInfoForTriggers pollingInfoForTriggers =
        PollingInfoForTriggers.builder()
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(PolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .pollingDocId("pollingDocId")
            .build();

    // For artifacts
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);

    // For manifests
    pollingDocument.setPollingType(PollingType.MANIFEST);
    pollingDocument.setPolledResponse(ManifestPolledResponse.builder().allPolledKeys(Set.of("key1")).build());
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetPollingInfoWithPollingDocIdForTriggers() {
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();
    PollingInfoForTriggers pollingInfoForTriggers =
        PollingInfoForTriggers.builder()
            .pollingDocId(pollingDocId)
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(PolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    // For artifacts
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);

    // For manifests
    pollingDocument.setPollingType(PollingType.MANIFEST);
    pollingDocument.setPolledResponse(ManifestPolledResponse.builder().allPolledKeys(Set.of("key1")).build());
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSave() {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().connectorRef("connectorRef").build();
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    doReturn(null)
        .when(pollingRepository)
        .addSubscribersToExistingPollingDoc(accountId, orgId, projectId, PollingType.ARTIFACT, dockerHubArtifactInfo,
            Collections.singletonList("trigger1"), Collections.emptyMap());
    doReturn(pollingDocument).when(pollingRepository).save(any());
    pollingService.save(pollingDocument);
    ArgumentCaptor<PollingDocument> pollingDocumentArgumentCaptor = ArgumentCaptor.forClass(PollingDocument.class);
    verify(pollingRepository, times(1)).save(pollingDocumentArgumentCaptor.capture());
    assertThat(pollingDocumentArgumentCaptor.getValue().getUuid()).isNull();
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testSavePollingDocument() {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().connectorRef("connectorRef").build();
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    PollingDocument pollingDocumentExisting =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .lastModifiedAt(300L)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    doReturn(pollingDocumentExisting)
        .when(pollingRepository)
        .addSubscribersToExistingPollingDoc(accountId, orgId, projectId, PollingType.ARTIFACT, dockerHubArtifactInfo,
            Collections.singletonList("trigger1"), null);
    PollingResponseDTO dto = pollingService.save(pollingDocument);
    assertEquals(dto.getPollingDocId(), pollingDocument.getUuid());
    assertEquals(dto.getLastPollingUpdate(), pollingDocumentExisting.getLastModifiedAt());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testSavePollingDocumentLastModifiedPolledReaponseTime() {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().connectorRef("connectorRef").build();
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    PollingDocument pollingDocumentExisting =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .lastModifiedAt(300L)
            .lastModifiedPolledResponseTime(400L)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    doReturn(pollingDocumentExisting)
        .when(pollingRepository)
        .addSubscribersToExistingPollingDoc(accountId, orgId, projectId, PollingType.ARTIFACT, dockerHubArtifactInfo,
            Collections.singletonList("trigger1"), null);
    PollingResponseDTO dto = pollingService.save(pollingDocument);
    assertEquals(dto.getPollingDocId(), pollingDocument.getUuid());
    assertNotSame(dto.getLastPollingUpdate(), pollingDocumentExisting.getLastModifiedAt());
    assertEquals(dto.getLastPollingUpdate(), pollingDocumentExisting.getLastModifiedPolledResponseTime());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateTriggerPollingStatusSuccess() throws IOException {
    PollingTriggerStatusUpdateDTO expectedStatusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                             .signatures(Collections.singletonList("sig"))
                                                             .success(true)
                                                             .errorMessage("")
                                                             .lastCollectedVersions(Collections.singletonList("1.0"))
                                                             .errorStatusValidUntil(null)
                                                             .build();
    ArgumentCaptor<PollingTriggerStatusUpdateDTO> updateDTOCaptor =
        ArgumentCaptor.forClass(PollingTriggerStatusUpdateDTO.class);
    Call<ResponseDTO<Boolean>> call = mock(Call.class);
    when(triggersClient.updateTriggerPollingStatus(any(), updateDTOCaptor.capture())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    boolean result = pollingService.updateTriggerPollingStatus(
        accountId, Collections.singletonList("sig"), true, "", Collections.singletonList("1.0"), null);
    assertThat(result).isTrue();
    expectedStatusUpdate.setLastCollectedTime(updateDTOCaptor.getValue().getLastCollectedTime());
    verify(triggersClient, times(1)).updateTriggerPollingStatus(eq(accountId), any());
    assertThat(expectedStatusUpdate).isEqualToComparingFieldByField(updateDTOCaptor.getValue());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateTriggerPollingStatusFailure() throws IOException {
    PollingTriggerStatusUpdateDTO expectedStatusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                             .signatures(Collections.singletonList("sig"))
                                                             .success(true)
                                                             .errorMessage("")
                                                             .lastCollectedVersions(Collections.singletonList("1.0"))
                                                             .build();
    ArgumentCaptor<PollingTriggerStatusUpdateDTO> updateDTOCaptor =
        ArgumentCaptor.forClass(PollingTriggerStatusUpdateDTO.class);
    Call<ResponseDTO<Boolean>> call = mock(Call.class);
    when(triggersClient.updateTriggerPollingStatus(any(), updateDTOCaptor.capture())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    boolean result = pollingService.updateTriggerPollingStatus(accountId, Collections.singletonList("sig"), true, "",
        Collections.singletonList("1.0"), Duration.ofMinutes(2).toMillis());
    assertThat(result).isFalse();
    expectedStatusUpdate.setLastCollectedTime(updateDTOCaptor.getValue().getLastCollectedTime());
    expectedStatusUpdate.setErrorStatusValidUntil(
        updateDTOCaptor.getValue().getLastCollectedTime() + Duration.ofMinutes(2).toMillis());
    verify(triggersClient, times(1)).updateTriggerPollingStatus(eq(accountId), any());
    assertThat(expectedStatusUpdate).isEqualToComparingFieldByField(updateDTOCaptor.getValue());
  }
}
