/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.AGORODETKI;

import static software.wings.scheduler.approval.ApprovalPollingHandler.TARGET_INTERVAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.service.intfc.ApprovalPolingService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class ShellScriptApprovalServiceTest extends CategoryTest {
  private static final int RETRY_INTERVAL = 30000;
  @Mock private ApprovalPolingService approvalPolingService;
  ShellScriptApprovalService shellScriptApprovalService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    shellScriptApprovalService = Mockito.spy(new ShellScriptApprovalService(null, null, approvalPolingService));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldUpdateNextIteration() {
    Mockito.doReturn(false)
        .when(shellScriptApprovalService)
        .tryShellScriptApproval(any(), any(), any(), any(), any(), any(), any(), anyInt());
    shellScriptApprovalService.handleShellScriptPolling(
        ApprovalPollingJobEntity.builder().retryInterval(RETRY_INTERVAL).approvalId("id").build());
    Mockito.verify(approvalPolingService).updateNextIteration(any(), anyLong());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotUpdateNextIterationWhenNotRetryScript() {
    Mockito.doReturn(true)
        .when(shellScriptApprovalService)
        .tryShellScriptApproval(any(), any(), any(), any(), any(), any(), any(), anyInt());
    shellScriptApprovalService.handleShellScriptPolling(
        ApprovalPollingJobEntity.builder().retryInterval(RETRY_INTERVAL).approvalId("id").build());
    Mockito.verify(approvalPolingService, Mockito.never()).updateNextIteration(any(), anyLong());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotUpdateNextIterationWhenRetryIntervalIsEqualToTargetInterval() {
    Mockito.doReturn(true)
        .when(shellScriptApprovalService)
        .tryShellScriptApproval(any(), any(), any(), any(), any(), any(), any(), anyInt());
    shellScriptApprovalService.handleShellScriptPolling(
        ApprovalPollingJobEntity.builder().retryInterval(TARGET_INTERVAL.toMillis()).approvalId("id").build());
    Mockito.verify(approvalPolingService, Mockito.never()).updateNextIteration(any(), anyLong());
  }
}
