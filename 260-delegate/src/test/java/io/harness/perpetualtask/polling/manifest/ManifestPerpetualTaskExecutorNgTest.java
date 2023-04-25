/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.exception.HelmClientException;
import io.harness.helm.HelmCliCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.ManifestCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.PollingResponsePublisher;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class ManifestPerpetualTaskExecutorNgTest extends DelegateTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String CHART_NAME = "CHART_NAME";

  private ManifestPerpetualTaskExecutorNg manifestPerpetualTaskExecutor;
  private PerpetualTaskId perpetualTaskId;
  private String polling_doc_id;

  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private ManifestCollectionService manifestCollectionService;
  @Mock private Call<RestResponse<Boolean>> call;

  @Before
  public void setup() {
    PollingResponsePublisher pollingResponsePublisher =
        new PollingResponsePublisher(kryoSerializer, referenceFalseKryoSerializer, delegateAgentManagerClient);
    manifestPerpetualTaskExecutor = new ManifestPerpetualTaskExecutorNg(
        manifestCollectionService, pollingResponsePublisher, kryoSerializer, referenceFalseKryoSerializer);
    perpetualTaskId = PerpetualTaskId.newBuilder().setId(UUIDGenerator.generateUuid()).build();
    polling_doc_id = UUIDGenerator.generateUuid();
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessfulManifestCollection() throws IOException {
    assertThat(runOnce(0, 10000, false, false).getResponseCode()).isEqualTo(200);

    verify(manifestCollectionService).collectManifests(any(ManifestDelegateConfig.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient).publishPollingResult(anyString(), anyString(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse =
        (PollingDelegateResponse) kryoSerializer.asObject(bufferedSink.readByteArray());

    validateRunOnceOutput(pollingDelegateResponse, 10001, 10001, 0, true);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testManifestsChangeInRepository() throws IOException {
    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    Buffer bufferedSink = new Buffer();

    // initially repo has 0-10000 versions.
    assertThat(runOnce(0, 10000, false, false).getResponseCode()).isEqualTo(200);

    verify(delegateAgentManagerClient, times(1)).publishPollingResult(anyString(), anyString(), captor.capture());
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse1 =
        (PollingDelegateResponse) kryoSerializer.asObject(bufferedSink.readByteArray());
    validateRunOnceOutput(pollingDelegateResponse1, 10001, 10001, 0, true);

    // now repo has 2-10005 versions.
    assertThat(runOnce(2, 10005, false, false).getResponseCode()).isEqualTo(200);

    verify(delegateAgentManagerClient, times(2)).publishPollingResult(anyString(), anyString(), captor.capture());
    verify(manifestCollectionService, times(2)).collectManifests(any(ManifestDelegateConfig.class));

    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse2 =
        (PollingDelegateResponse) kryoSerializer.asObject(bufferedSink.readByteArray());

    validateRunOnceOutput(pollingDelegateResponse2, 10004, 5, 2, false);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testExceptionInManifestCollection() throws IOException {
    assertThat(runOnce(0, 10000, false, true).getResponseCode()).isEqualTo(200);
    verify(manifestCollectionService, times(1)).collectManifests(any(ManifestDelegateConfig.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient).publishPollingResult(anyString(), anyString(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse =
        (PollingDelegateResponse) kryoSerializer.asObject(bufferedSink.readByteArray());
    assertThat(pollingDelegateResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(pollingDelegateResponse.getErrorMessage()).isEqualTo("COLLECTION_ERROR");
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testExceptionInPublishToManager() throws IOException {
    assertThat(runOnce(0, 10000, true, false).getResponseCode()).isEqualTo(200);

    verify(manifestCollectionService).collectManifests(any(ManifestDelegateConfig.class));
    verify(delegateAgentManagerClient).publishPollingResult(anyString(), anyString(), any(RequestBody.class));
    ManifestsCollectionCache manifestsCollectionCache =
        manifestPerpetualTaskExecutor.getCache().getIfPresent(polling_doc_id);
    assertThat(manifestsCollectionCache).isNotNull();
    assertThat(manifestsCollectionCache.getUnpublishedManifestKeys().size()).isEqualTo(10001);
    assertThat(manifestsCollectionCache.getToBeDeletedManifestKeys()).isEmpty();
    assertThat(manifestsCollectionCache.needsToPublish()).isTrue();
    assertThat(manifestsCollectionCache.getPublishedManifestKeys()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testCleanUp() {
    ManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder().chartName(CHART_NAME).helmVersion(HelmVersion.V2).build();

    ManifestCollectionTaskParamsNg manifestCollectionTaskParamsNg =
        ManifestCollectionTaskParamsNg.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .setPollingDocId(polling_doc_id)
            .setManifestCollectionParams(ByteString.copyFrom(kryoSerializer.asBytes(manifestDelegateConfig)))
            .build();

    PerpetualTaskExecutionParams executionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(manifestCollectionTaskParamsNg)).build();
    assertThat(manifestPerpetualTaskExecutor.cleanup(perpetualTaskId, executionParams)).isTrue();
    verify(manifestCollectionService).cleanup(manifestDelegateConfig);
    assertThat(manifestPerpetualTaskExecutor.getCache().getIfPresent(polling_doc_id)).isNull();
  }

  private void validateRunOnceOutput(PollingDelegateResponse pollingDelegateResponse, int publishedManifestsInCacheSize,
      int unpublishedManifestsInResponseSize, int manifestsToBeDeletedInResponseSize,
      boolean isFirstCollectionOnDelegate) {
    assertThat(pollingDelegateResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pollingDelegateResponse.getPollingDocId()).isEqualTo(polling_doc_id);
    assertThat(pollingDelegateResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ManifestPollingDelegateResponse manifestPollingDelegateResponse =
        (ManifestPollingDelegateResponse) pollingDelegateResponse.getPollingResponseInfc();
    assertThat(manifestPollingDelegateResponse).isNotNull();
    assertThat(manifestPollingDelegateResponse.getUnpublishedManifests().size())
        .isEqualTo(unpublishedManifestsInResponseSize);
    assertThat(manifestPollingDelegateResponse.isFirstCollectionOnDelegate()).isEqualTo(isFirstCollectionOnDelegate);
    assertThat(manifestPollingDelegateResponse.getToBeDeletedKeys().size())
        .isEqualTo(manifestsToBeDeletedInResponseSize);

    ManifestsCollectionCache manifestsCollectionCache =
        manifestPerpetualTaskExecutor.getCache().getIfPresent(polling_doc_id);
    assertThat(manifestsCollectionCache).isNotNull();
    assertThat(manifestsCollectionCache.getUnpublishedManifestKeys()).isEmpty();
    assertThat(manifestsCollectionCache.getToBeDeletedManifestKeys()).isEmpty();
    assertThat(manifestsCollectionCache.needsToPublish()).isFalse();
    assertThat(manifestsCollectionCache.getPublishedManifestKeys().size()).isEqualTo(publishedManifestsInCacheSize);
  }

  private PerpetualTaskResponse runOnce(int startIndex, int endIndex, boolean throwErrorWhilePublishing,
      boolean throwErrorWhileCollection) throws IOException {
    ManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder().chartName(CHART_NAME).helmVersion(HelmVersion.V2).build();

    ManifestCollectionTaskParamsNg manifestCollectionTaskParamsNg =
        ManifestCollectionTaskParamsNg.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .setPollingDocId(polling_doc_id)
            .setManifestCollectionParams(ByteString.copyFrom(kryoSerializer.asBytes(manifestDelegateConfig)))
            .build();

    PerpetualTaskExecutionParams executionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(manifestCollectionTaskParamsNg)).build();

    when(delegateAgentManagerClient.publishPollingResult(anyString(), anyString(), any(RequestBody.class)))
        .thenReturn(call);
    when(call.execute())
        .thenReturn(throwErrorWhilePublishing
                ? Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"),
                    new okhttp3.Response.Builder()
                        .code(401)
                        .protocol(Protocol.HTTP_1_1)
                        .message("")
                        .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                        .build())
                : Response.success(new RestResponse<>()));

    List<String> chartVersions =
        IntStream.rangeClosed(startIndex, endIndex).boxed().map(String::valueOf).collect(Collectors.toList());
    if (throwErrorWhileCollection) {
      when(manifestCollectionService.collectManifests(manifestDelegateConfig))
          .thenThrow(new HelmClientException("COLLECTION_ERROR", HelmCliCommandType.FETCH_ALL_VERSIONS));
    } else {
      when(manifestCollectionService.collectManifests(manifestDelegateConfig)).thenReturn(chartVersions);
    }

    return manifestPerpetualTaskExecutor.runOnce(perpetualTaskId, executionParams, Instant.now());
  }
}
