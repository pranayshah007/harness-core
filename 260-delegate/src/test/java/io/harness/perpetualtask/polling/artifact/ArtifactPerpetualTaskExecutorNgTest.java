/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutorBase;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.ArtifactCollectionTaskParamsNg;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class ArtifactPerpetualTaskExecutorNgTest extends DelegateTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String CONNECTOR_REF = "CONNECTOR_REF";

  private ArtifactPerpetualTaskExecutorNg artifactPerpetualTaskExecutorNg;
  private PerpetualTaskExecutorBase perpetualTaskExecutorBase;
  private PerpetualTaskId perpetualTaskId;
  private String polling_doc_id;

  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock private ArtifactRepositoryServiceImpl artifactRepositoryService;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @Before
  public void setup() {
    PollingResponsePublisher pollingResponsePublisher =
        new PollingResponsePublisher(kryoSerializer, referenceFalseKryoSerializer, delegateAgentManagerClient);
    artifactPerpetualTaskExecutorNg = new ArtifactPerpetualTaskExecutorNg(
        artifactRepositoryService, pollingResponsePublisher, kryoSerializer, referenceFalseKryoSerializer);
    perpetualTaskId = PerpetualTaskId.newBuilder().setId(UUIDGenerator.generateUuid()).build();
    polling_doc_id = UUIDGenerator.generateUuid();
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessfulArtifactCollection() throws IOException {
    assertThat(runOnce(0, 10, false, false).getResponseCode()).isEqualTo(200);

    verify(artifactRepositoryService).collectBuilds(any(ArtifactTaskParameters.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient).publishPollingResultV2(anyString(), anyString(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse response =
        (PollingDelegateResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());
    validateRunOnceOutput(response, 11, true, 11, 0);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testArtifactsChangeInRepository() throws IOException {
    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    Buffer bufferedSink = new Buffer();

    // initially repo has 0-10000 versions.
    assertThat(runOnce(0, 10, false, false).getResponseCode()).isEqualTo(200);

    verify(delegateAgentManagerClient, times(1)).publishPollingResultV2(anyString(), anyString(), captor.capture());
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse1 =
        (PollingDelegateResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());
    validateRunOnceOutput(pollingDelegateResponse1, 11, true, 11, 0);

    // now repo has 2-10005 versions.
    assertThat(runOnce(2, 15, false, false).getResponseCode()).isEqualTo(200);

    verify(delegateAgentManagerClient, times(2)).publishPollingResultV2(anyString(), anyString(), captor.capture());
    verify(artifactRepositoryService, times(2)).collectBuilds(any(ArtifactTaskParameters.class));

    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse2 =
        (PollingDelegateResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());

    validateRunOnceOutput(pollingDelegateResponse2, 14, false, 5, 2);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testExceptionInArtifactCollection() throws IOException {
    assertThat(runOnce(0, 10, true, false).getResponseCode()).isEqualTo(200);
    verify(artifactRepositoryService, times(1)).collectBuilds(any(ArtifactTaskParameters.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient).publishPollingResultV2(anyString(), anyString(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse pollingDelegateResponse =
        (PollingDelegateResponse) referenceFalseKryoSerializer.asObject(bufferedSink.readByteArray());
    assertThat(pollingDelegateResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(pollingDelegateResponse.getErrorMessage()).isEqualTo("");
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testExceptionInPublishToManager() throws IOException {
    assertThat(runOnce(0, 10, false, true).getResponseCode()).isEqualTo(200);

    verify(artifactRepositoryService).collectBuilds(any(ArtifactTaskParameters.class));
    verify(delegateAgentManagerClient).publishPollingResultV2(anyString(), anyString(), any(RequestBody.class));
    ArtifactsCollectionCache artifactsCollectionCache =
        artifactPerpetualTaskExecutorNg.getCache().getIfPresent(polling_doc_id);
    assertThat(artifactsCollectionCache).isNotNull();
    assertThat(artifactsCollectionCache.getUnpublishedArtifactKeys().size()).isEqualTo(11);
    assertThat(artifactsCollectionCache.getToBeDeletedArtifactKeys()).isEmpty();
    assertThat(artifactsCollectionCache.needsToPublish()).isTrue();
    assertThat(artifactsCollectionCache.getPublishedArtifactKeys()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testCleanup() {
    ArtifactCollectionTaskParamsNg taskParams =
        ArtifactCollectionTaskParamsNg.newBuilder().setPollingDocId(polling_doc_id).build();

    PerpetualTaskExecutionParams executionParams = PerpetualTaskExecutionParams.newBuilder()
                                                       .setCustomizedParams(Any.pack(taskParams))
                                                       .setReferenceFalseKryoSerializer(true)
                                                       .build();

    artifactPerpetualTaskExecutorNg.cleanup(perpetualTaskId, executionParams);
    assertThat(artifactPerpetualTaskExecutorNg.getCache().getIfPresent(polling_doc_id)).isNull();
  }

  private void validateRunOnceOutput(PollingDelegateResponse response, int publishedManifestsInCacheSize,
      boolean isFirstCollectionOnDelegate, int unpublishedManifestsInResponseSize,
      int manifestsToBeDeletedInResponseSize) {
    assertThat(response).isNotNull();
    assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getPollingDocId()).isEqualTo(polling_doc_id);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getPollingResponseInfc()).isNotNull().isInstanceOf(ArtifactPollingDelegateResponse.class);

    ArtifactPollingDelegateResponse delegateResponse =
        (ArtifactPollingDelegateResponse) response.getPollingResponseInfc();
    assertThat(delegateResponse.getToBeDeletedKeys().size()).isEqualTo(manifestsToBeDeletedInResponseSize);
    assertThat(delegateResponse.getUnpublishedArtifacts().size()).isEqualTo(unpublishedManifestsInResponseSize);
    assertThat(delegateResponse.isFirstCollectionOnDelegate()).isEqualTo(isFirstCollectionOnDelegate);

    ArtifactsCollectionCache artifactsCollectionCache =
        artifactPerpetualTaskExecutorNg.getCache().getIfPresent(polling_doc_id);
    assertThat(artifactsCollectionCache).isNotNull();
    assertThat(artifactsCollectionCache.getUnpublishedArtifactKeys()).isEmpty();
    assertThat(artifactsCollectionCache.getToBeDeletedArtifactKeys()).isEmpty();
    assertThat(artifactsCollectionCache.needsToPublish()).isFalse();
    assertThat(artifactsCollectionCache.getPublishedArtifactKeys().size()).isEqualTo(publishedManifestsInCacheSize);
  }

  private PerpetualTaskResponse runOnce(int startIndex, int endIndex, boolean throwErrorWhileCollection,
      boolean throwErrorWhilePublishing) throws IOException {
    ArtifactSourceDelegateRequest artifactSourceDelegateRequest =
        DockerArtifactDelegateRequest.builder().connectorRef(CONNECTOR_REF).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .attributes(artifactSourceDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    ArtifactCollectionTaskParamsNg taskParams = ArtifactCollectionTaskParamsNg.newBuilder()
                                                    .setPollingDocId(polling_doc_id)
                                                    .setArtifactCollectionParams(ByteString.copyFrom(
                                                        referenceFalseKryoSerializer.asBytes(artifactTaskParameters)))
                                                    .build();

    PerpetualTaskExecutionParams executionParams = PerpetualTaskExecutionParams.newBuilder()
                                                       .setCustomizedParams(Any.pack(taskParams))
                                                       .setReferenceFalseKryoSerializer(true)
                                                       .build();

    Mockito.when(delegateAgentManagerClient.publishPollingResultV2(anyString(), anyString(), any(RequestBody.class)))
        .thenReturn(call);
    Mockito.when(call.execute())
        .thenReturn(throwErrorWhilePublishing
                ? Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"),
                    new okhttp3.Response.Builder()
                        .code(401)
                        .protocol(Protocol.HTTP_1_1)
                        .message("")
                        .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                        .build())
                : Response.success(new RestResponse<>()));
    List<ArtifactDelegateResponse> dockerTags = IntStream.rangeClosed(startIndex, endIndex)
                                                    .boxed()
                                                    .map(tag
                                                        -> DockerArtifactDelegateResponse.builder()
                                                               .tag(String.valueOf(tag))
                                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                                               .build())
                                                    .collect(Collectors.toList());

    ArtifactTaskExecutionResponse response = ArtifactTaskExecutionResponse.builder()
                                                 .artifactDelegateResponses(dockerTags)
                                                 .isArtifactServerValid(true)
                                                 .isArtifactServerValid(true)
                                                 .build();

    if (throwErrorWhileCollection) {
      when(artifactRepositoryService.collectBuilds(artifactTaskParameters)).thenThrow(new InvalidRequestException(""));
    } else {
      when(artifactRepositoryService.collectBuilds(artifactTaskParameters)).thenReturn(response);
    }

    return artifactPerpetualTaskExecutorNg.runOnce(perpetualTaskId, executionParams, Instant.now());
  }
}
