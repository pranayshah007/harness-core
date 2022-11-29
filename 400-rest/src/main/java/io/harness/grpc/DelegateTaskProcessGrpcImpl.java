package io.harness.grpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.harness.beans.DelegateTask;
import io.harness.delegate.DelegateTaskAcquireRequest;
import io.harness.delegate.DelegateTaskAcquireResponse;
import io.harness.delegate.DelegateTaskMetaData;
import io.harness.delegate.ProcessDelegateTaskRequest;
import io.harness.delegate.ProcessDelegateTaskResponse;
import lombok.extern.slf4j.Slf4j;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.DelegateTaskProcessService;
import io.harness.delegate.DelegateTaskProcessServiceGrpc.DelegateTaskProcessServiceImplBase;

import java.util.concurrent.TimeUnit;


@Singleton
@Slf4j
public class DelegateTaskProcessGrpcImpl extends DelegateTaskProcessServiceImplBase {

  private DelegateTaskProcessService delegateTaskProcessService;
  private DelegateTaskBroadcastHelper delegateTaskBroadcastHelper;

    @Inject
    public DelegateTaskProcessGrpcImpl(DelegateTaskProcessService delegateTaskProcessService,DelegateTaskBroadcastHelper delegateTaskBroadcastHelper) {
      this.delegateTaskProcessService = delegateTaskProcessService;
      this.delegateTaskBroadcastHelper = delegateTaskBroadcastHelper;
    }

    @Override
    public void processDelegateTask(ProcessDelegateTaskRequest request, StreamObserver<ProcessDelegateTaskResponse> responseObserver) {
        //async
      try {
          DelegateTaskMetaData delegateTaskMetaData = request.getTaskMetaData();
          DelegateTask delegateTask = delegateTaskProcessService.processDelegateTask(request);
          delegateTask.getData().setData(request.getDelegateTaskData().toByteArray());
          delegateTask.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
          delegateTaskProcessService.saveDelegateTask(delegateTask);
          delegateTaskBroadcastHelper.broadcastNewDelegateTaskAsync(delegateTask);

      } catch (Exception e){

      }
    }

    @Override
    public void acquireDelegateTask(DelegateTaskAcquireRequest request, StreamObserver<DelegateTaskAcquireResponse> responseObserver) {
        String delegateId = request.getDelegateId();
        String taskId = request.getTaskId().getId();
        String delegateTaskInstanceId = request.getDelegateInstanceId();
        String accountId = request.getAccountId().getId();
        delegateTaskProcessService.acquireDelegateTask(accountId,delegateId,taskId,delegateTaskInstanceId);
    }


}
