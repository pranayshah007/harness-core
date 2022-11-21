package io.harness.grpc;

import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;

import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capabilities;
import io.harness.delegate.Capability;
import io.harness.delegate.DelegateTaskData;
import io.harness.delegate.DelegateTaskMetaData;
import io.harness.delegate.DelegateTaskProcessServiceGrpc;
import io.harness.delegate.DelegateTaskRank;
import io.harness.delegate.Secrets;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskOwner;
import io.harness.delegate.TaskRequest;
import io.harness.delegate.TaskResponse;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.grpc.utils.HTimestamps;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;

import com.google.inject.Inject;
import com.google.protobuf.util.Timestamps;
import io.fabric8.utils.Strings;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DelegateTaskProcessGrpcClient {
  private final DelegateAsyncService delegateAsyncService;

  private final DelegateSyncService delegateSyncService;

  private final DelegateTaskProcessServiceGrpc
      .DelegateTaskProcessServiceBlockingStub delegateTaskProcessServiceBlockingStub;

  @Inject
  public DelegateTaskProcessGrpcClient(
      DelegateTaskProcessServiceGrpc.DelegateTaskProcessServiceBlockingStub delegateTaskProcessServiceBlockingStub,
      DelegateAsyncService delegateAsyncService, DelegateSyncService delegateSyncService) {
    this.delegateTaskProcessServiceBlockingStub = delegateTaskProcessServiceBlockingStub;
    this.delegateAsyncService = delegateAsyncService;
    this.delegateSyncService = delegateSyncService;
  }


  public String queueTask(DelegateCallbackToken delegateCallbackToken, AccountId accountId,
      TaskSetupAbstractions taskSetupAbstractions, TaskLogAbstractions taskLogAbstractions, TaskDetails taskDetails,
      List<Capability> capabilities, List<TaskSelector> selectors, Duration holdFor, boolean forceExecute) {
    TaskResponse response = submitTask(delegateCallbackToken, accountId, taskSetupAbstractions, taskLogAbstractions,
        taskDetails, capabilities, selectors, holdFor, forceExecute);

    delegateAsyncService.setupTimeoutForTask(response.getTaskId().getId(),
        Timestamps.toMillis(response.getTotalExpiry()), currentTimeMillis() + holdFor.toMillis());
    return response.getTaskId().getId();
  }

  public <T extends DelegateResponseData> T executeSyncTask(DelegateCallbackToken delegateCallbackToken,
      AccountId accountId, TaskSetupAbstractions taskSetupAbstractions, TaskLogAbstractions taskLogAbstractions,
      TaskDetails taskDetails, List<Capability> capabilities, List<TaskSelector> selectors, Duration holdFor,
      boolean forceExecute) {
    TaskResponse submitTaskResponse = submitTask(delegateCallbackToken, accountId, taskSetupAbstractions,
        taskLogAbstractions, taskDetails, capabilities, selectors, holdFor, forceExecute);

    final String taskId = submitTaskResponse.getTaskId().getId();
    return delegateSyncService.waitForTask(taskId, Strings.defaultIfEmpty("des", taskDetails.getType().toString()),
        Duration.ofMillis(HTimestamps.toMillis(submitTaskResponse.getTotalExpiry()) - currentTimeMillis()), null);
  }

  public TaskResponse submitTask(DelegateCallbackToken delegateCallbackToken, AccountId accountId,
      TaskSetupAbstractions taskSetupAbstractions, TaskLogAbstractions taskLogAbstractions, TaskDetails taskDetails,
      List<Capability> capabilities,  List<TaskSelector> selectors, Duration holdFor, boolean forceExecute) {
    try {
      DelegateTaskMetaData.Builder delegateTaskBuilder = DelegateTaskMetaData.newBuilder()
                                                             .setAccountId(accountId)
                                                             .setNg(true)
                                                             .setTaskOwner(TaskOwner.newBuilder().build())
                                                             .setType(taskDetails.getType())
                                                             .setDescription("task des")
                                                             .setForceExecute(forceExecute)
                                                             .setRank(DelegateTaskRank.CRITICAL)
                                                             .setLogAbstractions(taskLogAbstractions);
      DelegateTaskData.Builder delegateTaskData =
          DelegateTaskData.newBuilder().setTaskParameters(taskDetails.getNewParameters());
      Capabilities.Builder taskCapabilities = Capabilities.newBuilder().addAllCapabilities(
          capabilities.stream().map(capability -> Capability.newBuilder().build()).collect(toList()));
      Secrets.Builder secrets = Secrets.newBuilder();
      TaskRequest.Builder taskRequestBuilder = TaskRequest.newBuilder()
                                                   .setCallbackToken(delegateCallbackToken)
                                                   .setDelegateTaskData(delegateTaskData)
                                                   .setTaskMetaData(delegateTaskBuilder.build())
                                                   .setCapabilities(taskCapabilities)
                                                   .setSecrets(secrets.build());
      TaskResponse response = delegateTaskProcessServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                                  .processDelegateTask(taskRequestBuilder.build());

      return response;

    } catch (Exception e) {
      throw new DelegateServiceDriverException("Unexpected error occurred while submitting task.", e);
    }
  }
}
