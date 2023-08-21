/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.pms.contracts.execution.Status.APPROVAL_REJECTED;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.ASYNC_WAITING;
import static io.harness.pms.contracts.execution.Status.EXPIRED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.PAUSING;
import static io.harness.pms.contracts.execution.Status.QUEUED;
import static io.harness.pms.contracts.execution.Status.QUEUED_LICENSE_LIMIT_REACHED;
import static io.harness.pms.contracts.execution.Status.RESOURCE_WAITING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.pms.contracts.execution.Status.SUSPENDED;
import static io.harness.pms.contracts.execution.Status.TASK_WAITING;
import static io.harness.pms.contracts.execution.Status.TIMED_WAITING;
import static io.harness.pms.contracts.execution.Status.WAIT_STEP_RUNNING;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class StatusUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeAllowedStatusInCaseOfTimeout() {
    EnumSet<Status> statuses = StatusUtils.nodeAllowedStartSet(Status.DISCONTINUING);
    assertThat(statuses.contains(Status.EXPIRED)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusSucceeded() {
    List<Status> statuses = Arrays.asList(
        Status.SUCCEEDED, Status.SKIPPED, Status.SUCCEEDED, Status.SUSPENDED, Status.SKIPPED, Status.SKIPPED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.SUCCEEDED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusIgnoreFailed() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.SKIPPED, Status.SKIPPED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.IGNORE_FAILED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.IGNORE_FAILED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusAborted() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.ABORTED,
        Status.SUSPENDED, Status.RUNNING, Status.QUEUED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ABORTED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusFailed() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.FAILED,
        Status.SUSPENDED, Status.RUNNING, Status.QUEUED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.FAILED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.FAILED);

    statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.FAILED, Status.SUSPENDED,
        Status.RUNNING, Status.INTERVENTION_WAITING);
    status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.FAILED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusErrored() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.ERRORED,
        Status.SUSPENDED, Status.RUNNING, Status.QUEUED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ERRORED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ERRORED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusExpired() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.EXPIRED,
        Status.SUSPENDED, Status.RUNNING, Status.QUEUED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.EXPIRED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.EXPIRED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusRunningNotPaused() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.PAUSED,
        Status.SUSPENDED, Status.RUNNING, Status.RUNNING);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RUNNING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RUNNING);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testCalculateStatusPaused() {
    List<Status> statuses =
        Arrays.asList(Status.SUCCEEDED, Status.SUCCEEDED, Status.IGNORE_FAILED, Status.PAUSED, Status.SUSPENDED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.PAUSED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.PAUSED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusQueued() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.QUEUED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.QUEUED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.QUEUED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusInterventionWaiting() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.TASK_WAITING, Status.INTERVENTION_WAITING);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.INTERVENTION_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.INTERVENTION_WAITING);

    // If a node is queued, and other is in waiting, so final status should be waiting.
    statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED, Status.SUSPENDED,
        Status.RUNNING, Status.QUEUED, Status.INTERVENTION_WAITING);
    status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.INTERVENTION_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.INTERVENTION_WAITING);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusApprovalWaiting() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.TASK_WAITING, Status.APPROVAL_WAITING);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.APPROVAL_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.APPROVAL_WAITING);

    // If a node is queued, and other is in waiting, so final status should be waiting.
    statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED, Status.SUSPENDED,
        Status.RUNNING, Status.QUEUED, Status.APPROVAL_WAITING);
    status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.APPROVAL_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.APPROVAL_WAITING);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCalculateStatusRunning() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.TASK_WAITING, Status.SUCCEEDED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RUNNING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RUNNING);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCalculateStatusApprovalRejected() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.TASK_WAITING, APPROVAL_REJECTED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(APPROVAL_REJECTED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(APPROVAL_REJECTED);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCalculateStatusResourceWaiting() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.IGNORE_FAILED, Status.SUCCEEDED,
        Status.SUSPENDED, Status.RUNNING, Status.TASK_WAITING, Status.RESOURCE_WAITING);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RESOURCE_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.RESOURCE_WAITING);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCalculateStatusUnrecognized() {
    List<Status> statuses = Arrays.asList(Status.SUCCEEDED, Status.SKIPPED, Status.UNRECOGNIZED);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ERRORED);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(Status.ERRORED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCalculateStatusInputWaiting() {
    List<Status> statuses = Collections.singletonList(INPUT_WAITING);

    Status status = StatusUtils.calculateStatus(statuses, "PLAN_EXECUTION_ID");
    assertThat(status).isEqualTo(INPUT_WAITING);

    status = StatusUtils.calculateStatusForNode(statuses, "NODE_EXECUTION_ID");
    assertThat(status).isEqualTo(INPUT_WAITING);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeAllowedStartSet() {
    assertThat(StatusUtils.nodeAllowedStartSet(Status.RUNNING))
        .containsExactlyInAnyOrder(QUEUED, ASYNC_WAITING, APPROVAL_WAITING, RESOURCE_WAITING, TASK_WAITING,
            TIMED_WAITING, INTERVENTION_WAITING, PAUSED, PAUSING, APPROVAL_REJECTED, INPUT_WAITING, WAIT_STEP_RUNNING,
            QUEUED_LICENSE_LIMIT_REACHED);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.INTERVENTION_WAITING)).isEqualTo(StatusUtils.brokeStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.TIMED_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.ASYNC_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.APPROVAL_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.RESOURCE_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.TASK_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.PAUSING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(INPUT_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.SKIPPED)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.PAUSED)).containsExactlyInAnyOrder(QUEUED, RUNNING, PAUSING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.DISCONTINUING))
        .containsExactlyInAnyOrder(QUEUED, RUNNING, INTERVENTION_WAITING, TIMED_WAITING, ASYNC_WAITING, TASK_WAITING,
            PAUSING, RESOURCE_WAITING, APPROVAL_WAITING, WAIT_STEP_RUNNING, PAUSED, FAILED, SUSPENDED, EXPIRED,
            APPROVAL_REJECTED, INPUT_WAITING, QUEUED_LICENSE_LIMIT_REACHED);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.QUEUED)).containsExactlyInAnyOrder(PAUSED, PAUSING);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.ABORTED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.ERRORED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.SUSPENDED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.FAILED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.EXPIRED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(APPROVAL_REJECTED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.nodeAllowedStartSet(Status.SUCCEEDED))
        .containsExactlyInAnyOrder(INTERVENTION_WAITING, RUNNING, QUEUED);
    assertThat(StatusUtils.nodeAllowedStartSet(Status.IGNORE_FAILED))
        .containsExactlyInAnyOrder(EXPIRED, FAILED, INTERVENTION_WAITING, RUNNING, APPROVAL_REJECTED, QUEUED);
    assertThatThrownBy(() -> StatusUtils.nodeAllowedStartSet(Status.UNRECOGNIZED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testPlanAllowedStartSet() {
    assertThat(StatusUtils.planAllowedStartSet(Status.INTERVENTION_WAITING))
        .containsExactlyInAnyOrder(RUNNING, PAUSING, PAUSED, WAIT_STEP_RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.PAUSED))
        .containsExactlyInAnyOrder(QUEUED, RUNNING, PAUSING, INTERVENTION_WAITING);
    assertThat(StatusUtils.planAllowedStartSet(Status.SUCCEEDED))
        .containsExactlyInAnyOrder(
            PAUSING, INTERVENTION_WAITING, RUNNING, WAIT_STEP_RUNNING, APPROVAL_WAITING, INPUT_WAITING);
    assertThat(StatusUtils.planAllowedStartSet(Status.RUNNING))
        .containsExactlyInAnyOrder(QUEUED, ASYNC_WAITING, APPROVAL_WAITING, RESOURCE_WAITING, TASK_WAITING,
            TIMED_WAITING, INTERVENTION_WAITING, PAUSED, PAUSING, APPROVAL_REJECTED, INPUT_WAITING, WAIT_STEP_RUNNING,
            QUEUED_LICENSE_LIMIT_REACHED);
    assertThat(StatusUtils.planAllowedStartSet(Status.TIMED_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.ASYNC_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.APPROVAL_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.RESOURCE_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.TASK_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.PAUSING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.nodeAllowedStartSet(INPUT_WAITING)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.SKIPPED)).containsExactlyInAnyOrder(QUEUED, RUNNING);
    assertThat(StatusUtils.planAllowedStartSet(Status.DISCONTINUING))
        .containsExactlyInAnyOrder(RUNNING, INTERVENTION_WAITING, TIMED_WAITING, ASYNC_WAITING, TASK_WAITING, PAUSING,
            RESOURCE_WAITING, APPROVAL_WAITING, WAIT_STEP_RUNNING, QUEUED, PAUSED, FAILED, SUSPENDED, EXPIRED,
            APPROVAL_REJECTED, INPUT_WAITING, QUEUED_LICENSE_LIMIT_REACHED);
    assertThat(StatusUtils.planAllowedStartSet(Status.QUEUED)).containsExactlyInAnyOrder(PAUSED, PAUSING);
    assertThat(StatusUtils.planAllowedStartSet(Status.ABORTED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(Status.ERRORED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(Status.SUSPENDED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(Status.FAILED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(Status.EXPIRED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(APPROVAL_REJECTED)).isEqualTo(StatusUtils.finalizableStatuses());
    assertThat(StatusUtils.planAllowedStartSet(Status.IGNORE_FAILED))
        .containsExactlyInAnyOrder(EXPIRED, FAILED, INTERVENTION_WAITING, RUNNING, APPROVAL_REJECTED, QUEUED);
    assertThatThrownBy(() -> StatusUtils.planAllowedStartSet(Status.UNRECOGNIZED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckIfAllChildrenSkipped() {
    assertThat(StatusUtils.checkIfAllChildrenSkipped(Collections.emptyList())).isTrue();
    assertThat(StatusUtils.checkIfAllChildrenSkipped(List.of(Status.SKIPPED, SKIPPED))).isTrue();
    assertThat(StatusUtils.checkIfAllChildrenSkipped(List.of(Status.SKIPPED, EXPIRED))).isFalse();
  }
}
