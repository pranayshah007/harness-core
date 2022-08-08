/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineNotificationUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetStatusForImage() {
    assertEquals(PipelineNotificationUtils.getStatusForImage(null), "running");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.SUCCEEDED), "completed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.IGNORE_FAILED), "completed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.FAILED), "failed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.ERRORED), "failed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.PAUSED), "paused");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.ABORTED), "aborted");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.APPROVAL_REJECTED), "rejected");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.EXPIRED), "expired");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.QUEUED), "resumed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.RUNNING), "resumed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.SKIPPED), "failed");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNodeStatus() {
    assertEquals(PipelineNotificationUtils.getNodeStatus(null), "started");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.SUCCEEDED), "completed");
    assertEquals(PipelineNotificationUtils.getStatusForImage(Status.IGNORE_FAILED), "completed");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.FAILED), "failed");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.ERRORED), "failed");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.PAUSED), "paused");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.ABORTED), "aborted");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.APPROVAL_REJECTED), "rejected");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.EXPIRED), "expired");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.QUEUED), "started");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.RUNNING), "started");
    assertEquals(PipelineNotificationUtils.getNodeStatus(Status.SKIPPED), "started");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetThemeColor() {
    assertEquals(
        PipelineNotificationUtils.getThemeColor(Status.SUCCEEDED), PipelineNotificationConstants.SUCCEEDED_COLOR);
    assertEquals(
        PipelineNotificationUtils.getThemeColor(Status.IGNORE_FAILED), PipelineNotificationConstants.SUCCEEDED_COLOR);
    assertEquals(PipelineNotificationUtils.getThemeColor(Status.EXPIRED), PipelineNotificationConstants.FAILED_COLOR);
    assertEquals(
        PipelineNotificationUtils.getThemeColor(Status.APPROVAL_REJECTED), PipelineNotificationConstants.FAILED_COLOR);
    assertEquals(PipelineNotificationUtils.getThemeColor(Status.FAILED), PipelineNotificationConstants.FAILED_COLOR);
    assertEquals(PipelineNotificationUtils.getThemeColor(Status.PAUSED), PipelineNotificationConstants.PAUSED_COLOR);
    assertEquals(PipelineNotificationUtils.getThemeColor(Status.ABORTED), PipelineNotificationConstants.ABORTED_COLOR);
    assertEquals(PipelineNotificationUtils.getThemeColor(Status.RUNNING), PipelineNotificationConstants.BLUE_COLOR);
  }
}
