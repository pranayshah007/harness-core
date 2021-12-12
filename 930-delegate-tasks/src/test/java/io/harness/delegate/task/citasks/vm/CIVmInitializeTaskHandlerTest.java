package io.harness.delegate.task.citasks.vm;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.SetupVmResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
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
public class CIVmInitializeTaskHandlerTest extends CategoryTest {
  @Mock private HttpHelper httpHelper;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @InjectMocks private CIVmInitializeTaskHandler ciVmInitializeTaskHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternal() throws IOException {
    CIVmInitializeTaskParams params = CIVmInitializeTaskParams.builder().stageRuntimeId("stage").build();
    Response<SetupVmResponse> setupResponse =
        Response.success(SetupVmResponse.builder().instanceID("test").ipAddress("1.1.1.1").build());
    when(httpHelper.setupStageWithRetries(any())).thenReturn(setupResponse);
    VmTaskExecutionResponse response = ciVmInitializeTaskHandler.executeTaskInternal(params, logStreamingTaskClient);
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalFailure() throws IOException {
    CIVmInitializeTaskParams params = CIVmInitializeTaskParams.builder().stageRuntimeId("stage").build();
    ResponseBody body = mock(ResponseBody.class);
    Response<SetupVmResponse> setupResponse = Response.error(400, body);
    when(httpHelper.setupStageWithRetries(any())).thenReturn(setupResponse);
    VmTaskExecutionResponse response = ciVmInitializeTaskHandler.executeTaskInternal(params, logStreamingTaskClient);
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }
}