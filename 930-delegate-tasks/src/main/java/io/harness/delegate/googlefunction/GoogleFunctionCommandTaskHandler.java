package io.harness.delegate.googlefunction;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class GoogleFunctionCommandTaskHandler {
  public GoogleFunctionCommandResponse executeTask(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    return executeTaskInternal(googleFunctionCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  protected abstract GoogleFunctionCommandResponse executeTaskInternal(
      GoogleFunctionCommandRequest googleFunctionCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception;
}
