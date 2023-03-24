/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.exception.HelmClientException;
import io.harness.helm.HelmCliCommandType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.artifact.ArtifactsPublishedCache;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.dto.HelmChart;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class ManifestPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String APP_MANIFEST_ID = "APP_MANIFEST_ID";
  public static final String SERVICE_ID = "SERVICE_ID";

  private ManifestPerpetualTaskExecutor manifestPerpetualTaskExecutor;

  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Mock private ManifestRepositoryService manifestRepositoryService;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  private PerpetualTaskId perpetualTaskId;

  @Before
  public void setUp() throws Exception {
    manifestPerpetualTaskExecutor = new ManifestPerpetualTaskExecutor(
        manifestRepositoryService, delegateAgentManagerClient, kryoSerializer, referenceFalseKryoSerializer);
    perpetualTaskId = PerpetualTaskId.newBuilder().setId(UUIDGenerator.generateUuid()).build();
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void shouldRunManifestCollection() throws Exception {
    assertThat(runOnce(false, new HashSet<>(Arrays.asList("1", "2", "3")), false).getResponseMessage())
        .isEqualTo("success");

    // Manifest  collection is done once as there are no unpublished build details initially.
    verify(manifestRepositoryService, times(1)).collectManifests(any(ManifestCollectionParams.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);

    // Chart versions are published 2 times because of batching.
    verify(delegateAgentManagerClient, times(2))
        .publishManifestCollectionResultV2(anyString(), any(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);

    ManifestCollectionExecutionResponse executionResponse =
        (ManifestCollectionExecutionResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());

    assertThat(executionResponse.getAppManifestId()).isEqualTo(APP_MANIFEST_ID);
    assertThat(executionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    List<RequestBody> responses = captor.getAllValues();

    responses.get(0).writeTo(bufferedSink);
    ManifestCollectionExecutionResponse executionResponse0 =
        (ManifestCollectionExecutionResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());

    assertThat(executionResponse0.getManifestCollectionResponse().getToBeDeletedKeys()).containsExactly("1", "2");
    assertThat(executionResponse0.getManifestCollectionResponse().getHelmCharts().size()).isEqualTo(500);
    assertThat(executionResponse0.getManifestCollectionResponse().isStable()).isFalse();

    responses.get(1).writeTo(bufferedSink);
    ManifestCollectionExecutionResponse executionResponse1 =
        (ManifestCollectionExecutionResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());
    assertThat(executionResponse1.getManifestCollectionResponse().getToBeDeletedKeys()).isEmpty();
    assertThat(executionResponse1.getManifestCollectionResponse().getHelmCharts().size()).isEqualTo(7);
    assertThat(executionResponse1.getManifestCollectionResponse().isStable()).isTrue();

    ArtifactsPublishedCache<HelmChart> manifestCache =
        manifestPerpetualTaskExecutor.getCache().getIfPresent(APP_MANIFEST_ID);
    assertThat(manifestCache).isNotNull();
    assertThat(manifestCache.needsToPublish()).isFalse();
    assertThat(manifestCache.getPublishedArtifactKeys().size()).isEqualTo(508);

    verify(call, times(2)).execute();
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category({UnitTests.class})
  public void testExceptionInPublishToManager() throws Exception {
    assertThat(runOnce(true, new HashSet<>(Arrays.asList("1", "2", "3")), false).getResponseMessage())
        .isEqualTo("success");

    // Manifest collection is done once as there are no unpublished build details initially.
    verify(manifestRepositoryService, times(1)).collectManifests(any(ManifestCollectionParams.class));

    verify(delegateAgentManagerClient, times(1))
        .publishManifestCollectionResultV2(anyString(), any(), any(RequestBody.class));
    verify(call, times(1)).execute();
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category({UnitTests.class})
  public void testExceptionInCollectingManifests() throws Exception {
    assertThat(runOnce(false, new HashSet<>(Arrays.asList("1", "2", "3")), true).getResponseMessage())
        .isEqualTo("success");

    // Manifest collection is done once as there are no unpublished build details initially.
    verify(manifestRepositoryService, times(1)).collectManifests(any(ManifestCollectionParams.class));

    ArgumentCaptor<RequestBody> responseArgumentCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient, times(1))
        .publishManifestCollectionResultV2(anyString(), any(), responseArgumentCaptor.capture());
    verify(call, times(1)).execute();

    Buffer bufferedSink = new Buffer();
    responseArgumentCaptor.getValue().writeTo(bufferedSink);
    ManifestCollectionExecutionResponse executionResponse =
        (ManifestCollectionExecutionResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());

    assertThat(executionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("COLLECTION_ERROR");
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void shouldNotPublishToManagerWhenNoChange() throws Exception {
    Set<String> publishedVersions =
        IntStream.rangeClosed(3, ArtifactsPublishedCache.ARTIFACT_ONE_TIME_PUBLISH_LIMIT + 10)
            .boxed()
            .map(String::valueOf)
            .collect(Collectors.toSet());
    assertThat(runOnce(false, publishedVersions, false).getResponseMessage()).isEqualTo("success");

    // Manifest collection is done once as there are no unpublished build details initially.
    verify(manifestRepositoryService, times(1)).collectManifests(any(ManifestCollectionParams.class));

    // Build details are published 1 time: 1 time for cleanup and then exit early because of exception.
    verify(delegateAgentManagerClient, never())
        .publishArtifactCollectionResultV2(anyString(), anyString(), any(RequestBody.class));
    verify(call, never()).execute();
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testCleanup() throws Exception {
    ManifestCollectionParams manifestCollectionParams =
        HelmChartCollectionParams.builder()
            .appManifestId(APP_MANIFEST_ID)
            .serviceId(SERVICE_ID)
            .publishedVersions(new HashSet<>(Arrays.asList("1", "2", "3")))
            .build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(manifestCollectionParams));
    ManifestCollectionTaskParams manifestCollectionTaskParams = ManifestCollectionTaskParams.newBuilder()
                                                                    .setAppManifestId(APP_MANIFEST_ID)
                                                                    .setManifestCollectionParams(bytes)
                                                                    .build();
    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(manifestCollectionTaskParams)).build();
    assertThat(manifestPerpetualTaskExecutor.cleanup(perpetualTaskId, params)).isFalse();
    verify(manifestRepositoryService, times(1)).cleanup(manifestCollectionParams);
    assertThat(manifestPerpetualTaskExecutor.getCache().getIfPresent(APP_MANIFEST_ID)).isNull();
  }

  private PerpetualTaskResponse runOnce(boolean throwErrorWhilePublishing, Set<String> publishedVersions,
      boolean throwErrorWhileCollection) throws Exception {
    // Old build details: 1-3
    ManifestCollectionParams manifestCollectionParams = HelmChartCollectionParams.builder()
                                                            .appManifestId(APP_MANIFEST_ID)
                                                            .serviceId(SERVICE_ID)
                                                            .publishedVersions(publishedVersions)
                                                            .build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(manifestCollectionParams));
    ManifestCollectionTaskParams manifestCollectionTaskParams = ManifestCollectionTaskParams.newBuilder()
                                                                    .setAppManifestId(APP_MANIFEST_ID)
                                                                    .setManifestCollectionParams(bytes)
                                                                    .build();
    when(delegateAgentManagerClient.publishManifestCollectionResultV2(any(), any(), any(RequestBody.class)))
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

    // New build details: 3-510, unpublished: 4-510, toBeDeleted: 1-2
    List<HelmChart> helmCharts = IntStream.rangeClosed(3, ArtifactsPublishedCache.ARTIFACT_ONE_TIME_PUBLISH_LIMIT + 10)
                                     .boxed()
                                     .map(versionNo -> HelmChart.builder().version(String.valueOf(versionNo)).build())
                                     .collect(Collectors.toList());

    if (throwErrorWhileCollection) {
      when(manifestRepositoryService.collectManifests(any(ManifestCollectionParams.class)))
          .thenThrow(new HelmClientException("COLLECTION_ERROR", HelmCliCommandType.FETCH_ALL_VERSIONS));
    } else {
      when(manifestRepositoryService.collectManifests(any(ManifestCollectionParams.class))).thenReturn(helmCharts);
    }

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(manifestCollectionTaskParams)).build();
    return manifestPerpetualTaskExecutor.runOnce(perpetualTaskId, params, Instant.now());
  }
}
