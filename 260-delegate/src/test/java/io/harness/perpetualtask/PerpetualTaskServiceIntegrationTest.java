/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.integration.IntegrationTestBase;

import javax.inject.Inject;
import com.google.protobuf.util.Durations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PerpetualTaskServiceIntegrationTest extends IntegrationTestBase {
  private final long HEARTBEAT_MILLIS = Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli();

  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  private final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID_" + this.getClass().getSimpleName();

  private final String DEFAULT_TASK_TYPE = PerpetualTaskType.ECS_CLUSTER;
  private final PerpetualTaskSchedule TASK_SCHEDULE = PerpetualTaskSchedule.newBuilder()
                                                          .setInterval(Durations.fromSeconds(1))
                                                          .setTimeout(Durations.fromMillis(1000))
                                                          .build();

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put("region", "default-region");
    return PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldCreatePerpetualTask() {
    String taskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, false, "");
    assertThat(taskId).isNotNull();
    String duplicateTaskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, false, "");
    assertThat(duplicateTaskId).isEqualTo(taskId);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldCreateDuplicatePerpetualTask() {
    String taskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, true, "");
    assertThat(taskId).isNotNull();
    String duplicateTaskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, true, "");
    assertThat(duplicateTaskId).isNotEqualTo(taskId);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldDeletePerpetualTask() {
    String taskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, false, "");
    boolean deleteTask = perpetualTaskService.deleteTask(TEST_ACCOUNT_ID, taskId);
    assertThat(deleteTask).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldUpdateHeartbeat() {
    String taskId = perpetualTaskService.createTask(
        DEFAULT_TASK_TYPE, TEST_ACCOUNT_ID, getPerpetualTaskClientContext(), TASK_SCHEDULE, false, "");
    PerpetualTaskResponse perpetualTaskResponse = PerpetualTaskResponse.builder().build();
    boolean heartbeatUpdated = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskResponse);
    assertThat(heartbeatUpdated).isTrue();

    PerpetualTaskRecord task = perpetualTaskRecordDao.getTask(taskId);
    assertThat(task).isNotNull();
    assertThat(task.getLastHeartbeat()).isEqualTo(HEARTBEAT_MILLIS);
  }

  @After
  public void clearCollection() {
    val ds = wingsPersistence.getDatastore(PerpetualTaskRecord.class);
    ds.delete(ds.createQuery(PerpetualTaskRecord.class).filter(PerpetualTaskRecordKeys.accountId, TEST_ACCOUNT_ID));
  }
}
