package io.harness.delegate.task.citasks.vm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.DestroyVmRequest;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmCleanupTaskHandler implements CICleanupTaskHandler {
  @NotNull private Type type = CICleanupTaskHandler.Type.VM;
  @Inject private HttpHelper httpHelper;

  @Override
  public Type getType() {
    return type;
  }

  public VmTaskExecutionResponse executeTaskInternal(CICleanupTaskParams ciCleanupTaskParams) {
    CIVmCleanupTaskParams params = (CIVmCleanupTaskParams) ciCleanupTaskParams;
    log.info("Received request to clean VM with stage runtime ID {}", params.getStageRuntimeId());
    return callRunnerForCleanup(params);
  }

  private VmTaskExecutionResponse callRunnerForCleanup(CIVmCleanupTaskParams params) {
    CommandExecutionStatus executionStatus = CommandExecutionStatus.FAILURE;
    String errMessage = "";
    try {
      Response<Void> response = httpHelper.cleanupStageWithRetries(convert(params));
      if (response.isSuccessful()) {
        executionStatus = CommandExecutionStatus.SUCCESS;
      }
    } catch (Exception e) {
      log.error("Failed to destory VM in runner", e);
      errMessage = e.getMessage();
    }

    return VmTaskExecutionResponse.builder().errorMessage(errMessage).commandExecutionStatus(executionStatus).build();
  }

  private DestroyVmRequest convert(CIVmCleanupTaskParams params) {
    return DestroyVmRequest.builder().poolID(params.getPoolId()).id(params.getStageRuntimeId()).build();
  }
}