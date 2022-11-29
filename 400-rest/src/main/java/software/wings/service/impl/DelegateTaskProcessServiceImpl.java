package software.wings.service.impl;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static java.lang.System.currentTimeMillis;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.harness.beans.DelegateTask;
import io.harness.delegate.AccountId;
import io.harness.delegate.DelegateTaskAcquireResponse;
import io.harness.delegate.DelegateTaskData;
import io.harness.delegate.DelegateTaskMetaData;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.ProcessDelegateTaskRequest;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.NgSetupFields;
import io.harness.delegate.beans.TaskData;
import io.harness.persistence.HPersistence;
;
import io.harness.version.VersionInfoManager;

import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateTaskProcessService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegateTaskProcessServiceImpl implements DelegateTaskProcessService {
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private Clock clock;
  @Inject private HPersistence persistence;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;

  private static final SecureRandom random = new SecureRandom();

  @Override
  public DelegateTask processDelegateTask(ProcessDelegateTaskRequest processDelegateTaskRequest) {
    DelegateTaskMetaData delegateTaskMetaData = processDelegateTaskRequest.getTaskMetaData();
    DelegateTask.DelegateTaskBuilder taskBuilder =
        DelegateTask.builder()
            .uuid(generateUuid())
            .status(QUEUED)
            .taskType(delegateTaskMetaData.getDelegateTaskDetails().getType().toString())
            .isNG(true)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(delegateTaskMetaData.getDelegateTaskDetails().toString()).data(processDelegateTaskRequest.getDelegateTaskData().toByteArray())
                      .build())
            .accountId(delegateTaskMetaData.getAccountId().getId())
            .lastBroadcastAt(clock.millis())
            .version(versionInfoManager.getVersionInfo().getVersion());
    if (delegateTaskMetaData.getExecuteOnHarnessHostedDelegates()) {
      taskBuilder.secondaryAccountId(delegateTaskMetaData.getAccountId().getId());
    }
    long timeout = Duration.ofMillis(delegateTaskMetaData.getTimeout().getSeconds()).toMillis();
    taskBuilder.expiry(currentTimeMillis() + timeout);
    List<String> eligibleListOfDelegates =
        assignDelegateService.getEligibleDelegatesToExecuteTaskV2(taskBuilder.build());
    if (eligibleListOfDelegates.isEmpty()) {
      throw new NoEligibleDelegatesInAccountException("No Eligible delegates");
    }
    // shuffle the eligible delegates to evenly distribute the load
    Collections.shuffle(eligibleListOfDelegates);

    taskBuilder.broadcastToDelegateIds(
        Lists.newArrayList(getDelegateIdForFirstBroadcast(taskBuilder.build(), eligibleListOfDelegates)));

    taskBuilder.eligibleToExecuteDelegateIds(new LinkedList<>(eligibleListOfDelegates));

    return taskBuilder.build();
  }

  @Override
  public void saveDelegateTask(DelegateTask delegateTask) {
    persistence.save(delegateTask);
  }

  @Override
  public DelegateTaskAcquireResponse acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId) {
    /* DelegateTaskPackage.DelegateTaskPackageBuilder delegateTaskPackageBuilder =
             DelegateTaskPackage.builder()
                     .accountId(delegateTask.getAccountId())
                     .delegateId(delegateTask.getDelegateId())
                     .delegateInstanceId(delegateTask.getDelegateInstanceId())
                     .delegateTaskId(delegateTask.getUuid())
                     .data(delegateTask.getData())
                     .executionCapabilities(executionCapabilityList)
                     .delegateCallbackToken(delegateTask.getDriverId());
        String logStreamingAccountToken = logStreamingAccountTokenCache.get(delegateTask.getAccountId());

           if (isNotBlank(logStreamingAccountToken)) {
             delegateTaskPackageBuilder.logStreamingToken(logStreamingAccountToken);
           }

                     */


    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, taskId, delegateInstanceId);
    try {
     Any data = Any.parseFrom(ByteString.copyFrom(delegateTaskPackage.getData().getData()));
      io.harness.delegate.DelegateTaskPackage dp = io.harness.delegate.DelegateTaskPackage.newBuilder()
              .setDelegateTaskId(delegateTaskPackage.getDelegateTaskId())
              .setDelegateId(delegateTaskPackage.getDelegateId())
              .setDelegateInstanceId(delegateTaskPackage.getDelegateInstanceId())
              .setAccountId(AccountId.newBuilder().setId(accountId).build())
              .setDelegateTaskData(DelegateTaskData.newBuilder().setTaskParameters(data).build())
              .build();
      return DelegateTaskAcquireResponse.newBuilder().setDelegateTaskPackage(dp).build();
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }

   return null;
  }

  private String getDelegateIdForFirstBroadcast(DelegateTask delegateTask, List<String> eligibleListOfDelegates) {
    for (String delegateId : eligibleListOfDelegates) {
      if (assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegateId)
          || assignDelegateService.isWhitelisted(delegateTask, delegateId)) {
        return delegateId;
      }
    }
    return eligibleListOfDelegates.get(random.nextInt(eligibleListOfDelegates.size()));
  }

  public boolean isNGTask(Map<String, String> setupAbstractions) {
    return !isEmpty(setupAbstractions) && setupAbstractions.get(NgSetupFields.NG) != null
        && Boolean.TRUE.equals(Boolean.valueOf(setupAbstractions.get(NgSetupFields.NG)));
  }
}
