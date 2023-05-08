/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.artifact.ArtifactCollectionTaskParams;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactCollectionPTaskServiceClientTest extends WingsBaseTest {
  private String accountId = "ACCOUNT_ID";
  private String artifactStreamId = "ARTIFACT_STREAM_ID";
  private String taskId = "TASK_ID";
  private Map<String, String> clientParamsMap = new HashMap<>();

  private static final String ARTIFACT_STREAM_ID = "artifactStreamId";

  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;

  @Inject @InjectMocks private ArtifactCollectionPTaskServiceClient artifactCollectionPTaskServiceClient;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    when(perpetualTaskService.createTask(eq(PerpetualTaskType.ARTIFACT_COLLECTION), eq(accountId),
             isA(PerpetualTaskClientContext.class), isA(PerpetualTaskSchedule.class), eq(false), eq("")))
        .thenReturn(taskId);

    clientParamsMap.put(ARTIFACT_STREAM_ID, artifactStreamId);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTaskParams() {
    final PerpetualTaskClientContext perpetualTaskClientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamsMap).build();
    ArtifactCollectionTaskParams collectionTaskParams =
        artifactCollectionPTaskServiceClient.getTaskParams(perpetualTaskClientContext, true);
    assertThat(collectionTaskParams).isNotNull();
    assertThat(collectionTaskParams.getArtifactStreamId()).isEqualTo(artifactStreamId);
    assertThat(collectionTaskParams.getBuildSourceParams()).isNotEmpty();
    verify(artifactCollectionUtils).prepareBuildSourceParameters(artifactStreamId);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetValidationTask() {
    PerpetualTaskClientContext perpetualTaskClientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamsMap).build();
    when(artifactCollectionUtils.prepareValidateTask(artifactStreamId, accountId))
        .thenReturn(DelegateTask.builder().build());
    assertThat(artifactCollectionPTaskServiceClient.getValidationTask(perpetualTaskClientContext, accountId))
        .isNotNull();
    verify(artifactCollectionUtils).prepareValidateTask(artifactStreamId, accountId);
  }
}
