/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.polling;

import static io.harness.delegate.task.artifacts.ArtifactSourceType.ARTIFACTORY_REGISTRY;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.DOCKER_REGISTRY;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.NEXUS3_REGISTRY;
import static io.harness.polling.contracts.Type.ACR;
import static io.harness.polling.contracts.Type.ARTIFACTORY;
import static io.harness.polling.contracts.Type.DOCKER_HUB;
import static io.harness.polling.contracts.Type.ECR;
import static io.harness.polling.contracts.Type.GCR;
import static io.harness.polling.contracts.Type.GCS_HELM;
import static io.harness.polling.contracts.Type.GITHUB_PACKAGES;
import static io.harness.polling.contracts.Type.GIT_POLL;
import static io.harness.polling.contracts.Type.GOOGLE_ARTIFACT_REGISTRY;
import static io.harness.polling.contracts.Type.HTTP_HELM;
import static io.harness.polling.contracts.Type.NEXUS3;
import static io.harness.polling.contracts.Type.S3_HELM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.GitPollingDelegateResponse;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingResponseInfc;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.k8s.model.HelmVersion;
import io.harness.lock.PersistentLocker;
import io.harness.lock.noop.AcquiredNoopLock;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.polling.artifact.ArtifactCollectionUtilsNg;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.GitHubPollingInfo;
import io.harness.polling.bean.GitPollingInfo;
import io.harness.polling.bean.GitPollingPolledResponse;
import io.harness.polling.bean.HelmChartManifestInfo;
import io.harness.polling.bean.ManifestPolledResponse;
import io.harness.polling.bean.PolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.AcrArtifactInfo;
import io.harness.polling.bean.artifact.ArtifactoryRegistryArtifactInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.EcrArtifactInfo;
import io.harness.polling.bean.artifact.GARArtifactInfo;
import io.harness.polling.bean.artifact.GcrArtifactInfo;
import io.harness.polling.bean.artifact.GithubPackagesArtifactInfo;
import io.harness.polling.bean.artifact.NexusRegistryArtifactInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.contracts.Type;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class PollingResponseHandlerTest extends CategoryTest {
  private final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private final String POLLING_DOC_ID = "pollingDocId";
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_ID = "orgId";
  private final String PROJECT_ID = "projectId";
  private final String SIGNATURE_1 = "signature1";
  private final String PERPETUAL_TASK_ID_2 = "perpetualTaskId2";
  private final String POLLING_DOC_ID_2 = "pollingDocId2";
  private final String SIGNATURE_2 = "signature2";
  private Call<ResponseDTO<SettingValueResponseDTO>> triggerForAllArtifactsOrManifestsSettingCall;

  @InjectMocks private PollingResponseHandler pollingResponseHandler;
  @Mock PollingPerpetualTaskService pollingPerpetualTaskService;
  @Mock PollingService pollingService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock PolledItemPublisher polledItemPublisher;
  @Mock PersistentLocker persistentLocker;
  @Mock NGSettingsClient settingsClient;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any())).thenReturn(false);
    triggerForAllArtifactsOrManifestsSettingCall = mock(Call.class);
    when(settingsClient.getSetting(eq(SettingIdentifiers.TRIGGER_FOR_ALL_ARTIFACTS_OR_MANIFESTS), any(), any(), any()))
        .thenReturn(triggerForAllArtifactsOrManifestsSettingCall);
    when(triggerForAllArtifactsOrManifestsSettingCall.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("false").build())));
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskOnDifferentPerpetualTaskId() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    when(pollingService.get(anyString(), anyString())).thenReturn(PollingDocument.builder().build());
    pollingResponseHandler.handlePollingResponse("ptTask", ACCOUNT_ID, delegateResponse);
    verify(pollingPerpetualTaskService).deletePerpetualTask("ptTask", ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePollingDocumentOnEmptySignatureList() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument =
        PollingDocument.builder().uuid(POLLING_DOC_ID).perpetualTaskId(PERPETUAL_TASK_ID).build();
    when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    verify(pollingService).delete(pollingDocument);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testFailureResponse() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument = getHttpHelmPollingDocument(null);
    when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);

    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);

    verify(pollingService).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService).updateFailedAttempts(ACCOUNT_ID, POLLING_DOC_ID, 1);
    verify(pollingService)
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), false,
            delegateResponse.getErrorMessage(), Collections.emptyList());
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskOnHighFailedAttempts() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument = getHttpHelmPollingDocument(null);

    int failedAttempts = 7100;
    for (int i = 0; i < 100; i++) {
      pollingDocument.setFailedAttempts(failedAttempts + i);
      when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);
      pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    }
    verify(pollingService, times(100)).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService, times(100)).updateFailedAttempts(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), anyInt());
    verify(pollingPerpetualTaskService, times(0)).resetPerpetualTask(any());
    verify(pollingService, times(100))
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), false,
            delegateResponse.getErrorMessage(), Collections.emptyList());
    verify(pollingPerpetualTaskService).deletePerpetualTask(PERPETUAL_TASK_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessHttpHelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(HTTP_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessS3HelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(S3_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.YUVRAJ)
  @Category(UnitTests.class)
  public void testSuccessS3HelmPollingResponseWithDelegateRebalanceWithEnabledForAllManifests() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG))
        .thenReturn(true);
    testSuccessResponse(S3_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testSuccessS3HelmPollingResponseWithDelegateRebalanceWithEnabledSettingForAllManifests()
      throws IOException {
    when(triggerForAllArtifactsOrManifestsSettingCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("true").build())));
    testSuccessResponse(S3_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessGcsHelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GCS_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessDockerHubPollingResponseWithDelegateRebalance() {
    testSuccessResponse(DOCKER_HUB, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.YUVRAJ)
  @Category(UnitTests.class)
  public void testSuccessDockerHubPollingResponseWithDelegateRebalanceWithEnabledForAllArtifacts() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG))
        .thenReturn(true);
    testSuccessResponse(DOCKER_HUB, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testSuccessDockerHubPollingResponseWithDelegateRebalanceWithSettingEnabledForAllArtifacts()
      throws IOException {
    when(triggerForAllArtifactsOrManifestsSettingCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("true").build())));
    testSuccessResponse(DOCKER_HUB, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessGcrPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GCR, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessEcrPollingResponseWithDelegateRebalance() {
    testSuccessResponse(ECR, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.vivekveman)
  @Category(UnitTests.class)
  public void testSuccessGARPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GOOGLE_ARTIFACT_REGISTRY, PollingType.ARTIFACT);
  }
  @Test
  @Owner(developers = OwnerRule.vivekveman)
  @Category(UnitTests.class)
  public void testSuccessGithubPackagePollingResponseWithDelegateRebalance() {
    testSuccessResponse(GITHUB_PACKAGES, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessNexusRegistryPollingResponseWithDelegateRebalance() {
    testSuccessResponse(NEXUS3, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessArtifactoryRegistryPollingResponseWithDelegateRebalance() {
    testSuccessResponse(ARTIFACTORY, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.SRIDHAR)
  @Category(UnitTests.class)
  public void testSuccessGitPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GIT_POLL, PollingType.WEBHOOK_POLLING);
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testSuccessAcrPollingResponseWithDelegateRebalance() {
    testSuccessResponse(ACR, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testSuccessResponseForMultiRegionArtifactShouldPublish() {
    // pollingDoc1 receives tag 1.1 and pollingDoc2 already has it, so we SHOULD publish the trigger event.
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)).thenReturn(true);
    PollingDocument pollingDocument1 =
        PollingDocument.builder()
            .uuid(POLLING_DOC_ID)
            .accountId(ACCOUNT_ID)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .signatures(Collections.singletonList(SIGNATURE_1))
            .signaturesLock(Map.of(SIGNATURE_1, Collections.singletonList(SIGNATURE_2)))
            .pollingType(PollingType.ARTIFACT)
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("imagePath").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(Arrays.asList("1.0"))).build())
            .build();
    PollingDocument pollingDocument2 =
        PollingDocument.builder()
            .uuid(POLLING_DOC_ID_2)
            .accountId(ACCOUNT_ID)
            .perpetualTaskId(PERPETUAL_TASK_ID_2)
            .signatures(Collections.singletonList(SIGNATURE_2))
            .signaturesLock(Map.of(SIGNATURE_1, Collections.singletonList(SIGNATURE_2)))
            .pollingType(PollingType.ARTIFACT)
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("imagePath2").build())
            .polledResponse(
                ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(Arrays.asList("1.0", "1.1"))).build())
            .build();
    PollingDelegateResponse delegateResponse = getPollingDelegateResponse(
        ArtifactPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(false)
            .toBeDeletedKeys(Collections.emptySet())
            .unpublishedArtifacts(Collections.singletonList(
                DockerArtifactDelegateResponse.builder().sourceType(DOCKER_REGISTRY).tag("1.1").build()))
            .build());

    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(pollingDocument1);
    when(pollingService.getUuidsBySignatures(eq(ACCOUNT_ID), any()))
        .thenReturn(Collections.singletonList(POLLING_DOC_ID_2));
    when(pollingService.getMany(eq(ACCOUNT_ID), any())).thenReturn(Collections.singletonList(pollingDocument2));
    when(persistentLocker.waitToAcquireLock(any(), any(), any())).thenReturn(AcquiredNoopLock.builder().build());
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    assertAndGetArtifactPolledResponse(1, 2);
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService, times(1)).getUuidsBySignatures(eq(ACCOUNT_ID), any());
    verify(pollingService, times(1)).getMany(eq(ACCOUNT_ID), any());
    verify(persistentLocker, times(1)).waitToAcquireLock(POLLING_DOC_ID, Duration.ofMinutes(1), Duration.ofSeconds(10));
    verify(persistentLocker, times(1))
        .waitToAcquireLock(POLLING_DOC_ID_2, Duration.ofMinutes(1), Duration.ofSeconds(10));
    assertPublishedItem(DOCKER_HUB, 1, 1, PollingType.ARTIFACT);
    verify(pollingService, times(2))
        .updateTriggerPollingStatus(ACCOUNT_ID, List.of(SIGNATURE_1), true, null, List.of("1.1"));
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testSuccessResponseForMultiRegionArtifactShouldNotPublish() {
    // pollingDoc1 receives tag 1.1 and pollingDoc2 doesn't have it, so we should NOT publish the trigger event.
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)).thenReturn(true);
    PollingDocument pollingDocument1 =
        PollingDocument.builder()
            .uuid(POLLING_DOC_ID)
            .accountId(ACCOUNT_ID)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .signatures(Collections.singletonList(SIGNATURE_1))
            .signaturesLock(Map.of(SIGNATURE_1, Collections.singletonList(SIGNATURE_2)))
            .pollingType(PollingType.ARTIFACT)
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("imagePath").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(Arrays.asList("1.0"))).build())
            .build();
    PollingDocument pollingDocument2 =
        PollingDocument.builder()
            .uuid(POLLING_DOC_ID_2)
            .accountId(ACCOUNT_ID)
            .perpetualTaskId(PERPETUAL_TASK_ID_2)
            .signatures(Collections.singletonList(SIGNATURE_2))
            .signaturesLock(Map.of(SIGNATURE_1, Collections.singletonList(SIGNATURE_2)))
            .pollingType(PollingType.ARTIFACT)
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("imagePath2").build())
            .polledResponse(
                ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(Arrays.asList("1.0", "1.2"))).build())
            .build();
    PollingDelegateResponse delegateResponse = getPollingDelegateResponse(
        ArtifactPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(false)
            .toBeDeletedKeys(Collections.emptySet())
            .unpublishedArtifacts(Collections.singletonList(
                DockerArtifactDelegateResponse.builder().sourceType(DOCKER_REGISTRY).tag("1.1").build()))
            .build());

    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(pollingDocument1);
    when(pollingService.getUuidsBySignatures(eq(ACCOUNT_ID), any()))
        .thenReturn(Collections.singletonList(POLLING_DOC_ID_2));
    when(pollingService.getMany(eq(ACCOUNT_ID), any())).thenReturn(Collections.singletonList(pollingDocument2));
    when(persistentLocker.waitToAcquireLock(any(), any(), any())).thenReturn(AcquiredNoopLock.builder().build());
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    assertAndGetArtifactPolledResponse(1, 2);
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService, times(1)).getUuidsBySignatures(eq(ACCOUNT_ID), any());
    verify(pollingService, times(1)).getMany(eq(ACCOUNT_ID), any());
    verify(persistentLocker, times(1)).waitToAcquireLock(POLLING_DOC_ID, Duration.ofMinutes(1), Duration.ofSeconds(10));
    verify(persistentLocker, times(1))
        .waitToAcquireLock(POLLING_DOC_ID_2, Duration.ofMinutes(1), Duration.ofSeconds(10));
    verify(polledItemPublisher, times(0)).publishPolledItems(any()); // No publishing happens.
    verify(pollingService, times(1))
        .updateTriggerPollingStatus(ACCOUNT_ID, List.of(SIGNATURE_1), true, null, List.of("1.1"));
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testGetSignaturesWithLock() {
    List<String> signatures = List.of("sig1", "sig2", "sig3", "sig4");
    Map<String, List<String>> signaturesLock =
        Map.of("sig1", List.of("sig3", "sig4"), "sig3", List.of("sig5"), "sig4", List.of());
    List<String> signaturesWithLock = pollingResponseHandler.getSignaturesWithLock(signatures, signaturesLock);
    assertThat(signaturesWithLock.size()).isEqualTo(2);
    assertThat(signaturesWithLock.contains("sig1")).isTrue();
    assertThat(signaturesWithLock.contains("sig2")).isFalse();
    assertThat(signaturesWithLock.contains("sig3")).isTrue();
    assertThat(signaturesWithLock.contains("sig4")).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testGetSignaturesWithNoLock() {
    List<String> signatures = List.of("sig1", "sig2", "sig3", "sig4");
    Map<String, List<String>> signaturesLock =
        Map.of("sig1", List.of("sig3", "sig4"), "sig3", List.of("sig5"), "sig4", List.of());
    PollingDocument pollingDocument1 =
        PollingDocument.builder()
            .uuid(POLLING_DOC_ID)
            .accountId(ACCOUNT_ID)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .signatures(signatures)
            .signaturesLock(signaturesLock)
            .pollingType(PollingType.ARTIFACT)
            .pollingInfo(DockerHubArtifactInfo.builder().imagePath("imagePath").build())
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(Arrays.asList("1.0"))).build())
            .build();
    List<String> signaturesWithoutLock = pollingResponseHandler.getSignaturesWithNoLock(pollingDocument1);
    assertThat(signaturesWithoutLock.size()).isEqualTo(2);
    assertThat(signaturesWithoutLock.contains("sig1")).isFalse();
    assertThat(signaturesWithoutLock.contains("sig2")).isTrue();
    assertThat(signaturesWithoutLock.contains("sig3")).isFalse();
    assertThat(signaturesWithoutLock.contains("sig4")).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.VINICIUS)
  @Category(UnitTests.class)
  public void testShouldTriggerForAllArtifactsOrManifests() throws IOException {
    PollingDocument pollingDoc =
        PollingDocument.builder().accountId(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).build();
    when(triggerForAllArtifactsOrManifestsSettingCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("true").build())));
    assertThat(pollingResponseHandler.shouldTriggerForAllArtifactsOrManifests(pollingDoc)).isTrue();
    verify(settingsClient, times(1))
        .getSetting(SettingIdentifiers.TRIGGER_FOR_ALL_ARTIFACTS_OR_MANIFESTS, ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  private void testSuccessResponse(Type type, PollingType pollingType) {
    PollingDocument pollingDocument = getPollingDocumentFromType(type, null);
    PollingDelegateResponse delegateResponse = getPollingDelegateResponse(type, pollingType, true, 0, 1000, -1);

    // first collection. unpublished keys - [0-1000], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(pollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    PolledResponse polledResponse = null;
    List<String> polledKeys = null;
    if (pollingType.equals(PollingType.MANIFEST)) {
      polledResponse = validateFirstManifestCollectionOnManager();
      polledKeys =
          ((ManifestPollingDelegateResponse) delegateResponse.getPollingResponseInfc()).getUnpublishedManifests();
    } else if (pollingType.equals(PollingType.ARTIFACT)) {
      polledResponse = validateFirstArtifactCollectionOnManager();
      polledKeys = ((ArtifactPollingDelegateResponse) delegateResponse.getPollingResponseInfc())
                       .getUnpublishedArtifacts()
                       .stream()
                       .map(ArtifactCollectionUtilsNg::getArtifactKey)
                       .collect(Collectors.toList());
    } else if (pollingType.equals(PollingType.WEBHOOK_POLLING)) {
      polledResponse = validateFirstGitPollingOnManager();
      polledKeys = ((GitPollingDelegateResponse) delegateResponse.getPollingResponseInfc())
                       .getUnpublishedEvents()
                       .stream()
                       .map(GitPollingWebhookData::getDeliveryId)
                       .collect(Collectors.toList());
    }
    verify(pollingService, times(1))
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), true, null, polledKeys);

    PollingDocument savedPollingDocument = getPollingDocumentFromType(type, polledResponse);
    PollingDelegateResponse newDelegateResponse = getPollingDelegateResponse(type, pollingType, false, 1001, 1005, -1);

    // response with unpublished keys [1001-1005], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(savedPollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, newDelegateResponse);
    verify(pollingService, times(2)).get(ACCOUNT_ID, POLLING_DOC_ID);

    PolledResponse newPolledResponse = null;
    List<String> newPolledKeys = null;
    if (pollingType.equals(PollingType.MANIFEST)) {
      newPolledResponse = assertAndGetManifestPolledResponse(2, 1006);
      newPolledKeys =
          ((ManifestPollingDelegateResponse) newDelegateResponse.getPollingResponseInfc()).getUnpublishedManifests();
    } else if (pollingType.equals(PollingType.ARTIFACT)) {
      newPolledResponse = assertAndGetArtifactPolledResponse(2, 1006);
      newPolledKeys = ((ArtifactPollingDelegateResponse) newDelegateResponse.getPollingResponseInfc())
                          .getUnpublishedArtifacts()
                          .stream()
                          .map(ArtifactCollectionUtilsNg::getArtifactKey)
                          .collect(Collectors.toList());
    } else if (pollingType.equals(PollingType.WEBHOOK_POLLING)) {
      newPolledResponse = assertAndGetGitPolledResponse(2, 1006);
      newPolledKeys = ((GitPollingDelegateResponse) newDelegateResponse.getPollingResponseInfc())
                          .getUnpublishedEvents()
                          .stream()
                          .map(GitPollingWebhookData::getDeliveryId)
                          .collect(Collectors.toList());
    }

    verify(pollingService, times(1))
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), true, null, newPolledKeys);
    if (ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG)
        || pollingResponseHandler.shouldTriggerForAllArtifactsOrManifests(pollingDocument)) {
      assertPublishedItem(type, 1, 5, pollingType);
    } else {
      assertPublishedItem(type, 5, 1, pollingType);
    }

    PollingDocument savedPollingDocument1 = getPollingDocumentFromType(type, newPolledResponse);
    PollingDelegateResponse newDelegateResponse1 = getPollingDelegateResponse(type, pollingType, true, 3, 1011, 2);

    // Delegate rebalanced. response with unpublished keys [3-1011], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(savedPollingDocument1);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, newDelegateResponse1);
    verify(pollingService, times(3)).get(ACCOUNT_ID, POLLING_DOC_ID);
    if (pollingType.equals(PollingType.MANIFEST)) {
      assertAndGetManifestPolledResponse(3, 1009);
    } else if (pollingType.equals(PollingType.ARTIFACT)) {
      assertAndGetArtifactPolledResponse(3, 1009);
    } else if (pollingType.equals(PollingType.WEBHOOK_POLLING)) {
      assertAndGetGitPolledResponse(3, 1009);
    }
    verify(pollingService, times(1))
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), true, null,
            List.of("1006", "1007", "1008", "1009", "1010", "1011"));
    if (ngFeatureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG)
        || pollingResponseHandler.shouldTriggerForAllArtifactsOrManifests(pollingDocument)) {
      assertPublishedItem(type, 1, 11, pollingType);
    } else {
      assertPublishedItem(type, 6, 2, pollingType);
    }

    PollingDelegateResponse newDelegateResponse2 = getPollingDelegateResponse(type, pollingType, true, 10, 1000, -1);
    savedPollingDocument1.setFailedAttempts(1);
    // Delegate rebalanced, previous polling failed. Response with unpublished keys [10-1000], toBeDeletedKeys = []
    // But all keys are already stored in the polling document
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, newDelegateResponse2);
    verify(pollingService, times(1)).updateFailedAttempts(ACCOUNT_ID, pollingDocument.getUuid(), 0);
    verify(pollingService, times(1))
        .updateTriggerPollingStatus(ACCOUNT_ID, pollingDocument.getSignatures(), true, null, Collections.emptyList());
  }

  private ArtifactPolledResponse assertAndGetArtifactPolledResponse(int nofOfTimes, int expectedSize) {
    ArgumentCaptor<ArtifactPolledResponse> captor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService, times(nofOfTimes))
        .updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ArtifactPolledResponse newPolledResponse = captor.getValue();
    assertThat(newPolledResponse).isNotNull();
    assertThat(newPolledResponse.getAllPolledKeys()).hasSize(expectedSize);
    return newPolledResponse;
  }

  private ManifestPolledResponse assertAndGetManifestPolledResponse(int nofOfTimes, int expectedSize) {
    ArgumentCaptor<ManifestPolledResponse> captor = ArgumentCaptor.forClass(ManifestPolledResponse.class);
    verify(pollingService, times(nofOfTimes))
        .updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ManifestPolledResponse newPolledResponse = captor.getValue();
    assertThat(newPolledResponse).isNotNull();
    assertThat(newPolledResponse.getAllPolledKeys()).hasSize(expectedSize);
    return newPolledResponse;
  }

  private GitPollingPolledResponse assertAndGetGitPolledResponse(int nofOfTimes, int expectedSize) {
    ArgumentCaptor<GitPollingPolledResponse> captor = ArgumentCaptor.forClass(GitPollingPolledResponse.class);
    verify(pollingService, times(nofOfTimes))
        .updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    GitPollingPolledResponse newPolledResponse = captor.getValue();
    assertThat(newPolledResponse).isNotNull();
    assertThat(newPolledResponse.getAllPolledKeys()).hasSize(expectedSize);
    return newPolledResponse;
  }

  private void assertPublishedItem(Type type, int publishedSize, int noOfTimePublished, PollingType pollingType) {
    ArgumentCaptor<PollingResponse> pollingResponseArgumentCaptor1 = ArgumentCaptor.forClass(PollingResponse.class);
    if (pollingType == PollingType.ARTIFACT || pollingType == PollingType.MANIFEST) {
      verify(polledItemPublisher, times(noOfTimePublished))
          .publishPolledItems(pollingResponseArgumentCaptor1.capture());
      PollingResponse pollingResponse = pollingResponseArgumentCaptor1.getValue();
      assertThat(pollingResponse).isNotNull();
      assertThat(pollingResponse.getType()).isEqualTo(type);
      assertThat(pollingResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(pollingResponse.getSignaturesCount()).isEqualTo(1);
      assertThat(pollingResponse.getSignatures(0)).isEqualTo(SIGNATURE_1);
      assertThat(pollingResponse.getBuildInfo()).isNotNull();
      assertThat(pollingResponse.getBuildInfo().getName())
          .isEqualTo(pollingType == PollingType.MANIFEST ? "chartName" : "imagePath");
      assertThat(pollingResponse.getBuildInfo().getVersionsCount()).isEqualTo(publishedSize);
      if (pollingType == PollingType.ARTIFACT) {
        assertThat(pollingResponse.getBuildInfo().getMetadataCount()).isEqualTo(publishedSize);
      }
    } else {
      ArgumentCaptor<List<GitPollingWebhookData>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
      verify(polledItemPublisher, times(noOfTimePublished)).sendWebhookRequest(any(), listCaptor.capture());
      List<GitPollingWebhookData> response = listCaptor.getValue();
      assertThat(response).isNotNull();
      assertThat(response.size()).isEqualTo(publishedSize);
    }
  }

  private PolledResponse validateFirstManifestCollectionOnManager() {
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);

    ArgumentCaptor<ManifestPolledResponse> captor = ArgumentCaptor.forClass(ManifestPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ManifestPolledResponse polledResponse = captor.getValue();
    assertThat(polledResponse).isNotNull();
    assertThat(polledResponse.getAllPolledKeys()).hasSize(1001);
    verify(polledItemPublisher, never()).publishPolledItems(any());
    return polledResponse;
  }

  private PolledResponse validateFirstArtifactCollectionOnManager() {
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);

    ArgumentCaptor<ArtifactPolledResponse> captor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ArtifactPolledResponse polledResponse = captor.getValue();
    assertThat(polledResponse).isNotNull();
    assertThat(polledResponse.getAllPolledKeys()).hasSize(1001);
    verify(polledItemPublisher, never()).publishPolledItems(any());
    return polledResponse;
  }

  private PolledResponse validateFirstGitPollingOnManager() {
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);

    ArgumentCaptor<GitPollingPolledResponse> captor = ArgumentCaptor.forClass(GitPollingPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    GitPollingPolledResponse polledResponse = captor.getValue();
    assertThat(polledResponse).isNotNull();
    assertThat(polledResponse.getAllPolledKeys()).hasSize(1001);
    verify(polledItemPublisher, never()).publishPolledItems(any());
    return polledResponse;
  }

  private PollingDelegateResponse getPollingDelegateResponse(Type type, PollingType pollingType,
      boolean firstCollectionOnDelegate, int startIndexUnpublishedManifests, int endIndexUnpublishedManifest,
      int endIndexToBeDeleted) {
    if (pollingType.equals(PollingType.MANIFEST)) {
      return getManifestPollingDelegateResponse(
          firstCollectionOnDelegate, startIndexUnpublishedManifests, endIndexUnpublishedManifest, endIndexToBeDeleted);
    } else if (pollingType.equals(PollingType.ARTIFACT)) {
      return getArtifactPollingDelegateResponse(type, firstCollectionOnDelegate, startIndexUnpublishedManifests,
          endIndexUnpublishedManifest, endIndexToBeDeleted);
    } else {
      return getAGitPollingDelegateResponse(type, firstCollectionOnDelegate, startIndexUnpublishedManifests,
          endIndexUnpublishedManifest, endIndexToBeDeleted);
    }
  }

  private PollingDelegateResponse getManifestPollingDelegateResponse(boolean firstCollectionOnDelegate,
      int startIndexUnpublishedManifests, int endIndexUnpublishedManifest, int endIndexToBeDeleted) {
    List<String> unpublishedManifests =
        IntStream.rangeClosed(startIndexUnpublishedManifests, endIndexUnpublishedManifest)
            .boxed()
            .map(String::valueOf)
            .collect(Collectors.toList());
    Set<String> toBeDeletedKeys =
        IntStream.rangeClosed(0, endIndexToBeDeleted).boxed().map(String::valueOf).collect(Collectors.toSet());
    ManifestPollingDelegateResponse manifestPollingDelegateResponse =
        ManifestPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(firstCollectionOnDelegate)
            .unpublishedManifests(unpublishedManifests)
            .toBeDeletedKeys(toBeDeletedKeys)
            .build();
    return getPollingDelegateResponse(manifestPollingDelegateResponse);
  }

  private PollingDelegateResponse getArtifactPollingDelegateResponse(Type type, boolean firstCollectionOnDelegate,
      int startIndexUnpublished, int endIndexUnpublished, int endIndexToBeDeleted) {
    Set<String> toBeDeletedKeys =
        IntStream.rangeClosed(0, endIndexToBeDeleted).boxed().map(String::valueOf).collect(Collectors.toSet());
    List<ArtifactDelegateResponse> artifactDelegateResponses;
    switch (type) {
      case DOCKER_HUB:
        artifactDelegateResponses = getDockerArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case GCR:
        artifactDelegateResponses = getGcrArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case NEXUS3:
        artifactDelegateResponses = getNexusArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case ARTIFACTORY:
        artifactDelegateResponses =
            getArtifactoryArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case ACR:
        artifactDelegateResponses = getAcrArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case GOOGLE_ARTIFACT_REGISTRY:
        artifactDelegateResponses = getGARArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case GITHUB_PACKAGES:
        artifactDelegateResponses =
            getGithubPackagesArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      default:
        artifactDelegateResponses = getEcrArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
    }
    ArtifactPollingDelegateResponse artifactPollingDelegateResponse =
        ArtifactPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(firstCollectionOnDelegate)
            .toBeDeletedKeys(toBeDeletedKeys)
            .unpublishedArtifacts(artifactDelegateResponses)
            .build();
    return getPollingDelegateResponse(artifactPollingDelegateResponse);
  }

  private PollingDelegateResponse getAGitPollingDelegateResponse(Type type, boolean firstCollectionOnDelegate,
      int startIndexUnpublished, int endIndexUnpublished, int endIndexToBeDeleted) {
    Set<String> toBeDeletedIds =
        IntStream.rangeClosed(0, endIndexToBeDeleted).boxed().map(String::valueOf).collect(Collectors.toSet());
    List<GitPollingWebhookData> gitPollingWebhookDataResponse;
    gitPollingWebhookDataResponse = getGitPollingDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
    GitPollingDelegateResponse gitPollingDelegateResponse = GitPollingDelegateResponse.builder()
                                                                .firstCollectionOnDelegate(firstCollectionOnDelegate)
                                                                .toBeDeletedIds(toBeDeletedIds)
                                                                .unpublishedEvents(gitPollingWebhookDataResponse)
                                                                .build();
    return getPollingDelegateResponse(gitPollingDelegateResponse);
  }

  private List<ArtifactDelegateResponse> getDockerArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i -> DockerArtifactDelegateResponse.builder().sourceType(DOCKER_REGISTRY).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getGcrArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> GcrArtifactDelegateResponse.builder().sourceType(ArtifactSourceType.GCR).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getEcrArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> EcrArtifactDelegateResponse.builder().sourceType(ArtifactSourceType.ECR).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getNexusArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i -> NexusArtifactDelegateResponse.builder().sourceType(NEXUS3_REGISTRY).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getArtifactoryArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> ArtifactoryArtifactDelegateResponse.builder()
                   .sourceType(ARTIFACTORY_REGISTRY)
                   .tag(String.valueOf(i))
                   .build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getAcrArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> AcrArtifactDelegateResponse.builder().sourceType(ArtifactSourceType.ACR).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }
  private List<ArtifactDelegateResponse> getGARArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> GarDelegateResponse.builder()
                   .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                   .version(String.valueOf(i))
                   .build())
        .collect(Collectors.toList());
  }
  private List<ArtifactDelegateResponse> getGithubPackagesArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> GithubPackagesArtifactDelegateResponse.builder()
                   .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                   .version(String.valueOf(i))
                   .build())
        .collect(Collectors.toList());
  }

  private List<GitPollingWebhookData> getGitPollingDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i -> GitPollingWebhookData.builder().deliveryId(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private PollingDelegateResponse getPollingDelegateResponse(PollingResponseInfc pollingResponseInfc) {
    return PollingDelegateResponse.builder()
        .pollingDocId(POLLING_DOC_ID)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .accountId(ACCOUNT_ID)
        .pollingResponseInfc(pollingResponseInfc)
        .build();
  }

  private PollingDelegateResponse getFailedPollingDelegateResponse() {
    return PollingDelegateResponse.builder()
        .pollingDocId(POLLING_DOC_ID)
        .accountId(ACCOUNT_ID)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .errorMessage("Incorrect Credentials")
        .build();
  }

  private PollingDocument getPollingDocumentFromType(Type type, PolledResponse polledResponse) {
    switch (type) {
      case HTTP_HELM:
        return getHttpHelmPollingDocument(polledResponse);
      case S3_HELM:
        return getS3HelmPollingDocument(polledResponse);
      case GCS_HELM:
        return getGcsHelmPollingDocument(polledResponse);
      case DOCKER_HUB:
        return getDockerHubPollingDocument(polledResponse);
      case GCR:
        return getGcrPollingDocument(polledResponse);
      case NEXUS3:
        return getNexusRegistryPollingDocument(polledResponse);
      case ARTIFACTORY:
        return getArtifactoryRegistryPollingDocument(polledResponse);
      case ACR:
        return getAcrRegistryPollingDocument(polledResponse);
      case ECR:
        return getEcrPollingDocument(polledResponse);
      case GIT_POLL:
        return getGitPollingDocument(polledResponse);
      case GOOGLE_ARTIFACT_REGISTRY:
        return getGoogleArtifactRegistryPollingDocument(polledResponse);
      case GITHUB_PACKAGES:
        return getGithubPackagesPollingDocument(polledResponse);
      default:
        return null;
    }
  }

  private PollingDocument getHttpHelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.HTTP)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(HttpStoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getS3HelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.S3)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(S3StoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getGcsHelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.GCS)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(GcsStoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getDockerHubPollingDocument(PolledResponse polledResponse) {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, dockerHubArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getGcrPollingDocument(PolledResponse polledResponse) {
    GcrArtifactInfo gcrArtifactInfo = GcrArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, gcrArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getEcrPollingDocument(PolledResponse polledResponse) {
    EcrArtifactInfo ecrArtifactInfo = EcrArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, ecrArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getNexusRegistryPollingDocument(PolledResponse polledResponse) {
    NexusRegistryArtifactInfo nexusRegistryArtifactInfo =
        NexusRegistryArtifactInfo.builder().artifactPath("imagePath").build();
    return getPollingDocument(polledResponse, nexusRegistryArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getArtifactoryRegistryPollingDocument(PolledResponse polledResponse) {
    ArtifactoryRegistryArtifactInfo artifactoryRegistryArtifactInfo =
        ArtifactoryRegistryArtifactInfo.builder().artifactPath("imagePath").build();
    return getPollingDocument(polledResponse, artifactoryRegistryArtifactInfo, PollingType.ARTIFACT);
  }
  private PollingDocument getGoogleArtifactRegistryPollingDocument(PolledResponse polledResponse) {
    GARArtifactInfo garArtifactInfo = GARArtifactInfo.builder().pkg("imagePath").build();
    return getPollingDocument(polledResponse, garArtifactInfo, PollingType.ARTIFACT);
  }
  private PollingDocument getGithubPackagesPollingDocument(PolledResponse polledResponse) {
    GithubPackagesArtifactInfo githubPackagesArtifactInfo =
        GithubPackagesArtifactInfo.builder().packageName("imagePath").build();
    return getPollingDocument(polledResponse, githubPackagesArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getAcrRegistryPollingDocument(PolledResponse polledResponse) {
    AcrArtifactInfo acrArtifactInfo = AcrArtifactInfo.builder().repository("imagePath").build();
    return getPollingDocument(polledResponse, acrArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getGitPollingDocument(PolledResponse polledResponse) {
    GitPollingInfo gitPollingInfo = GitHubPollingInfo.builder().pollInterval(2).webhookId("123").build();
    return getPollingDocument(polledResponse, gitPollingInfo, PollingType.WEBHOOK_POLLING);
  }

  private PollingDocument getPollingDocument(
      PolledResponse polledResponse, PollingInfo pollingInfo, PollingType pollingType) {
    return PollingDocument.builder()
        .uuid(POLLING_DOC_ID)
        .accountId(ACCOUNT_ID)
        .perpetualTaskId(PERPETUAL_TASK_ID)
        .signatures(Collections.singletonList(SIGNATURE_1))
        .pollingType(pollingType)
        .pollingInfo(pollingInfo)
        .polledResponse(polledResponse)
        .build();
  }
}
