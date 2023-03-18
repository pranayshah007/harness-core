/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.gitpolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.polling.GitPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.delegate.task.gitpolling.GitPollingTaskType;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.delegate.task.gitpolling.response.GitPollingTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.GitPollingTaskParamsNg;
import io.harness.perpetualtask.polling.PollingResponsePublisher;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class GitPollingPerpetualTaskExecutorNgTest extends DelegateTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String CONNECTOR_REF = "CONNECTOR_REF";

  private GitPollingPerpetualTaskExecutorNg gitPollingPerpetualTaskExecutorNg;
  private PerpetualTaskId perpetualTaskId;
  private String polling_doc_id;
  @Inject KryoSerializer kryoSerializer;
  @Mock private GitPollingServiceImpl gitPollingService;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @Before
  public void setup() {
    PollingResponsePublisher pollingResponsePublisher =
        new PollingResponsePublisher(kryoSerializer, delegateAgentManagerClient);
    gitPollingPerpetualTaskExecutorNg =
        new GitPollingPerpetualTaskExecutorNg(kryoSerializer, gitPollingService, pollingResponsePublisher);
    perpetualTaskId = PerpetualTaskId.newBuilder().setId(UUIDGenerator.generateUuid()).build();
    polling_doc_id = UUIDGenerator.generateUuid();
  }

  @Test
  @Owner(developers = OwnerRule.SRIDHAR)
  @Category(UnitTests.class)
  @Ignore("TODO: Flaky test - to fix later")
  public void testSuccessfulGitPollingWebhookEvents() throws IOException {
    assertThat(runOnce(0, 10000, false, false).getResponseCode()).isEqualTo(200);

    verify(gitPollingService).getWebhookRecentDeliveryEvents(any(GitPollingTaskParameters.class));

    ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
    verify(delegateAgentManagerClient).publishPollingResult(anyString(), anyString(), captor.capture());

    Buffer bufferedSink = new Buffer();
    captor.getValue().writeTo(bufferedSink);
    PollingDelegateResponse response = (PollingDelegateResponse) kryoSerializer.asObject(bufferedSink.readByteArray());
    validateRunOnceOutput(response, 10001, true, 10001, 0);
  }

  private PerpetualTaskResponse runOnce(int startIndex, int endIndex, boolean throwErrorWhileCollection,
      boolean throwErrorWhilePublishing) throws IOException {
    GitPollingSourceDelegateRequest gitPollingSourceDelegateRequest = GitHubPollingDelegateRequest.builder()
                                                                          .connectorRef(CONNECTOR_REF)
                                                                          .sourceType(GitPollingSourceType.GITHUB)
                                                                          .build();

    GitPollingTaskParameters gitPollingTaskParameters = GitPollingTaskParameters.builder()
                                                            .accountId(ACCOUNT_ID)
                                                            .attributes(gitPollingSourceDelegateRequest)
                                                            .gitPollingTaskType(GitPollingTaskType.GET_WEBHOOK_EVENTS)
                                                            .build();

    GitPollingTaskParamsNg taskParams =
        GitPollingTaskParamsNg.newBuilder()
            .setPollingDocId(polling_doc_id)
            .setGitpollingWebhookParams(ByteString.copyFrom(kryoSerializer.asBytes(gitPollingTaskParameters)))
            .build();

    PerpetualTaskExecutionParams executionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();

    when(delegateAgentManagerClient.publishPollingResult(anyString(), anyString(), any(RequestBody.class)))
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

    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    headers.add("X-GitHub-Delivery", "b4f1c9b0-11de-11ed-8227-8d270a85257");
    headers.add("X-GitHub-Event", "issue_comment");

    List<GitPollingWebhookData> gitPollingWebhookData = IntStream.rangeClosed(startIndex, endIndex)
                                                            .boxed()
                                                            .map(index
                                                                -> GitPollingWebhookData.builder()
                                                                       .deliveryId(String.valueOf(index))
                                                                       .payload(getPayload())
                                                                       .headers(headers)
                                                                       .build())
                                                            .collect(Collectors.toList());

    GitPollingTaskExecutionResponse response = GitPollingTaskExecutionResponse.builder()
                                                   .gitPollingWebhookEventResponses(gitPollingWebhookData)
                                                   .gitPollingSourceType(GitPollingSourceType.GITHUB)
                                                   .build();

    if (throwErrorWhileCollection) {
      when(gitPollingService.getWebhookRecentDeliveryEvents(gitPollingTaskParameters))
          .thenThrow(new InvalidRequestException(""));
    } else {
      when(gitPollingService.getWebhookRecentDeliveryEvents(gitPollingTaskParameters)).thenReturn(response);
    }

    return gitPollingPerpetualTaskExecutorNg.runOnce(perpetualTaskId, executionParams, Instant.now());
  }

  private void validateRunOnceOutput(PollingDelegateResponse response, int publishedWebhooksInCacheSize,
      boolean isFirstCollectionOnDelegate, int unpublishedWebhookInResponseSize, int webhookToBeDeletedInResponseSize) {
    assertThat(response).isNotNull();
    assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getPollingDocId()).isEqualTo(polling_doc_id);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getPollingResponseInfc()).isNotNull().isInstanceOf(GitPollingDelegateResponse.class);

    GitPollingDelegateResponse delegateResponse = (GitPollingDelegateResponse) response.getPollingResponseInfc();
    assertThat(delegateResponse.getToBeDeletedIds().size()).isEqualTo(webhookToBeDeletedInResponseSize);
    assertThat(delegateResponse.getUnpublishedEvents().size()).isEqualTo(unpublishedWebhookInResponseSize);
    assertThat(delegateResponse.isFirstCollectionOnDelegate()).isEqualTo(isFirstCollectionOnDelegate);

    GitPollingCache gitPollingCache = gitPollingPerpetualTaskExecutorNg.getCache().getIfPresent(polling_doc_id);
    assertThat(gitPollingCache).isNotNull();
    assertThat(gitPollingCache.getUnpublishedWebhookDeliveryIds()).isEmpty();
    assertThat(gitPollingCache.getToBeDeletedWebookDeliveryIds()).isEmpty();
    assertThat(gitPollingCache.needsToPublish()).isFalse();
    assertThat(gitPollingCache.getPublishedWebhookDeliveryIds().size()).isEqualTo(publishedWebhooksInCacheSize);
  }

  private String getPayload() {
    return "\"payload\":{\n"
        + "        \"action\":\"edited\",\n"
        + "        \"changes\":{\n"
        + "        \"body\":{\n"
        + "        \"from\":\"test\"\n"
        + "        }\n"
        + "        }\n"
        + "}";
  }
}
