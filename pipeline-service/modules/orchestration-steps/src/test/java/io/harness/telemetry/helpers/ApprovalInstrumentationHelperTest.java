/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.AutoApprovalDTO;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadlineDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.telemetry.TelemetryReporter;

import java.util.concurrent.CompletableFuture;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApprovalInstrumentationHelperTest extends CategoryTest {
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String PIPE = "pipe_id";
  private static final String HARNESS_APPROVAL_MESSAGE = "Approval Message";
  @InjectMocks ApprovalInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Reflect.on(instrumentationHelper).set("telemetryReporter", telemetryReporter);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testLastPublishedTagTrackSend() {
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(HARNESS_APPROVAL_MESSAGE)
            .includePipelineExecutionHistory(true)
            .isAutoRejectEnabled(false)
            .approvers(ApproversDTO.builder().build())
            .autoApproval(
                AutoApprovalDTO.builder()
                    .scheduledDeadline(
                        ScheduledDeadlineDTO.builder().time("2023-05-05 04:24 am").timeZone("Asia/Kolkata").build())
                    .build())
            .build();
    harnessApprovalInstance.setAccountId(ACCOUNT);
    harnessApprovalInstance.setOrgIdentifier(ORG);
    harnessApprovalInstance.setProjectIdentifier(PROJECT);
    harnessApprovalInstance.setPipelineIdentifier(PIPE);
    harnessApprovalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    CompletableFuture<Void> telemetryTask = instrumentationHelper.sendApprovalEvent(harnessApprovalInstance);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }
}
