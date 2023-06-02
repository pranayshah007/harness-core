/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactCollectionPTaskServiceClient;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClientRegistry;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import com.google.protobuf.util.Durations;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class ArtifactStreamPTaskHelperTest extends CategoryTest {
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";
  private static final String ARTIFACT_STREAM_ID_KEY = "artifactStreamId";
  private static final long INTERVAL_MINUTES = 1;
  private static final long TIMEOUT_MINUTES = 2;

  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private PerpetualTaskServiceClientRegistry clientRegistry;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private ArtifactCollectionPTaskServiceClient artifactCollectionPTaskServiceClient;

  @Inject @InjectMocks private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    when(clientRegistry.getClient(eq(PerpetualTaskType.ARTIFACT_COLLECTION)))
        .thenReturn(artifactCollectionPTaskServiceClient);
    when(perpetualTaskService.createTask(eq(PerpetualTaskType.ARTIFACT_COLLECTION), eq(ACCOUNT_ID),
             any(PerpetualTaskClientContext.class), any(PerpetualTaskSchedule.class), eq(false), eq("")))
        .thenReturn(PERPETUAL_TASK_ID);
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testCreatePerpetualTask() {
    ArtifactStream artifactStreamWithoutId = prepareArtifactStreamWithoutId();
    assertThatThrownBy(() -> artifactStreamPTaskHelper.createPerpetualTask(artifactStreamWithoutId))
        .isInstanceOf(GeneralException.class);

    ArtifactStream artifactStream = prepareArtifactStream();
    when(artifactStreamService.attachPerpetualTaskId(eq(artifactStream), eq(PERPETUAL_TASK_ID))).thenReturn(true);
    artifactStreamPTaskHelper.createPerpetualTask(artifactStream);

    ArgumentCaptor<PerpetualTaskClientContext> clientContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    ArgumentCaptor<PerpetualTaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(PerpetualTaskSchedule.class);

    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.ARTIFACT_COLLECTION), eq(ACCOUNT_ID), clientContextCaptor.capture(),
            scheduleCaptor.capture(), eq(false), eq(""));
    clientContextCaptor.getAllValues().forEach(clientContext -> {
      assertThat(clientContext.getClientParams().get(ARTIFACT_STREAM_ID_KEY)).isEqualTo(ARTIFACT_STREAM_ID);
    });
    scheduleCaptor.getAllValues().forEach(schedule -> {
      assertThat(schedule.getInterval()).isEqualTo(Durations.fromMinutes(INTERVAL_MINUTES));
      assertThat(schedule.getTimeout()).isEqualTo(Durations.fromMinutes(TIMEOUT_MINUTES));
    });
    verify(perpetualTaskService, never()).deleteTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    when(artifactStreamService.attachPerpetualTaskId(eq(artifactStream), eq(PERPETUAL_TASK_ID))).thenReturn(false);
    artifactStreamPTaskHelper.createPerpetualTask(artifactStream);
    verify(perpetualTaskService, times(2))
        .createTask(eq(PerpetualTaskType.ARTIFACT_COLLECTION), eq(ACCOUNT_ID), any(PerpetualTaskClientContext.class),
            any(PerpetualTaskSchedule.class), eq(false), eq(""));
    verify(perpetualTaskService, times(1)).deleteTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    when(artifactStreamService.attachPerpetualTaskId(eq(artifactStream), eq(PERPETUAL_TASK_ID)))
        .thenThrow(new RuntimeException());
    artifactStreamPTaskHelper.createPerpetualTask(artifactStream);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.ARTIFACT_COLLECTION), eq(ACCOUNT_ID), any(PerpetualTaskClientContext.class),
            any(PerpetualTaskSchedule.class), eq(false), eq(""));
    verify(perpetualTaskService, times(2)).deleteTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID));

    ArtifactStream artifactStreamWithPerpetualTaskId = prepareArtifactStream();
    artifactStreamWithPerpetualTaskId.setPerpetualTaskId(PERPETUAL_TASK_ID);
    assertThatThrownBy(() -> artifactStreamPTaskHelper.createPerpetualTask(artifactStreamWithPerpetualTaskId))
        .isInstanceOf(InvalidRequestException.class);
  }

  private ArtifactStream prepareArtifactStream() {
    return DockerArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .build();
  }

  private ArtifactStream prepareArtifactStreamWithoutId() {
    return DockerArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .serviceId(SERVICE_ID)
        .build();
  }
}
