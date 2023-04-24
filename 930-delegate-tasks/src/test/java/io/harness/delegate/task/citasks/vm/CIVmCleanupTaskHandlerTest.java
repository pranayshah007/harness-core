/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmCleanupTaskHandlerTest extends CategoryTest {
  @Mock private HttpHelper httpHelper;
  @InjectMocks private CIVmCleanupTaskHandler ciVmCleanupTaskHandler;
  //@InjectMocks private CIVmCleanupTaskHandler ciVmCleanupTaskHandler;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").build();
  private static final CIInitializeTaskParams.Type vmInfraInfo = CIInitializeTaskParams.Type.VM;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternal() throws IOException {
    CIVmCleanupTaskParams params =
        CIVmCleanupTaskParams.builder().stageRuntimeId("stage").infraInfo(vmInfraInfo).build();
    Response<Void> cleanupResponse = Response.success(null);
    when(httpHelper.cleanupStageWithRetries(any())).thenReturn(cleanupResponse);
    VmTaskExecutionResponse response = ciVmCleanupTaskHandler.executeTaskInternal(params, "");
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalFailure() {
    CIVmCleanupTaskParams params =
        CIVmCleanupTaskParams.builder().stageRuntimeId("stage").infraInfo(vmInfraInfo).build();
    ResponseBody body = mock(ResponseBody.class);
    Response<Void> cleanupResponse = Response.error(400, body);
    when(httpHelper.cleanupStageWithRetries(any())).thenReturn(cleanupResponse);
    VmTaskExecutionResponse response = ciVmCleanupTaskHandler.executeTaskInternal(params, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }
}
