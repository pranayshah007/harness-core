/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.artifact;

import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class ArtifactPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String ARTIFACT_STREAM_ID = "ARTIFACT_STREAM_ID";

  private ArtifactPerpetualTaskExecutor artifactPerpetualTaskExecutor;

  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Mock private ArtifactRepositoryServiceImpl artifactRepositoryService;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  private PerpetualTaskId perpetualTaskId;

  @Before
  public void setUp() throws Exception {
    artifactPerpetualTaskExecutor = new ArtifactPerpetualTaskExecutor(
        artifactRepositoryService, delegateAgentManagerClient, kryoSerializer, referenceFalseKryoSerializer);
    perpetualTaskId = PerpetualTaskId.newBuilder().setId(UUIDGenerator.generateUuid()).build();
  }

  @Test
  @Owner(developers = OwnerRule.SRINIVAS)
  @Category(UnitTests.class)
  public void shouldRunArtifactCollection() throws IOException {
    assertThat(runOnce(false).getResponseCode()).isEqualTo(org.eclipse.jetty.server.Response.SC_OK);

    // Artifact collection is done once as there are no unpublished build details initially.
    verify(artifactRepositoryService, times(1)).publishCollectedArtifacts(any(BuildSourceParameters.class), any());

    // Build details are published 3 time: 1 time for cleanup and then 2 times for collection because of batching.
    verify(delegateAgentManagerClient, times(3))
        .publishArtifactCollectionResultV2(any(), any(), any(RequestBody.class));
    verify(call, times(3)).execute();
  }

  @Test
  @Owner(developers = OwnerRule.SRINIVAS)
  @Category(UnitTests.class)
  public void testExceptionInPublishToManager() throws IOException {
    assertThat(runOnce(true).getResponseCode()).isEqualTo(org.eclipse.jetty.server.Response.SC_OK);

    // Artifact collection is done once as there are no unpublished build details initially.
    verify(artifactRepositoryService, times(1)).publishCollectedArtifacts(any(BuildSourceParameters.class), any());

    // Build details are published 1 time: 1 time for cleanup and then exit early because of exception.
    verify(delegateAgentManagerClient, times(1))
        .publishArtifactCollectionResultV2(any(), any(), any(RequestBody.class));
    verify(call, times(1)).execute();
  }

  private PerpetualTaskResponse runOnce(boolean throwErrorWhilePublishing) throws IOException {
    // Old build details: 1-3
    BuildSourceParameters buildSourceParameters = BuildSourceParameters.builder()
                                                      .artifactStreamType(DOCKER.name())
                                                      .savedBuildDetailsKeys(new HashSet<>(asList("1", "2", "3")))
                                                      .build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(buildSourceParameters));
    ArtifactCollectionTaskParams artifactCollectionTaskParams = ArtifactCollectionTaskParams.newBuilder()
                                                                    .setArtifactStreamId(ARTIFACT_STREAM_ID)
                                                                    .setBuildSourceParams(bytes)
                                                                    .build();
    when(delegateAgentManagerClient.publishArtifactCollectionResultV2(any(), any(), any(RequestBody.class)))
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
    List<BuildDetails> builds = IntStream.rangeClosed(3, ArtifactsPublishedCache.ARTIFACT_ONE_TIME_PUBLISH_LIMIT + 10)
                                    .boxed()
                                    .map(buildNo -> aBuildDetails().withNumber(String.valueOf(buildNo)).build())
                                    .collect(Collectors.toList());
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(builds).build())
            .build();
    when(artifactRepositoryService.publishCollectedArtifacts(any(BuildSourceParameters.class), any()))
        .thenReturn(buildSourceExecutionResponse);

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(artifactCollectionTaskParams)).build();
    return artifactPerpetualTaskExecutor.runOnce(perpetualTaskId, params, Instant.now());
  }
}
