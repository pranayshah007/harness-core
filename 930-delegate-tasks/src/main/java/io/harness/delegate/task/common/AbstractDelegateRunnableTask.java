/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.common;

import static io.harness.exception.WingsException.ExecutionContext.DELEGATE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.DelegateTaskResponseBuilder;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData.ErrorNotifyResponseDataBuilder;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.ThirdPartyApiCallLogDetails;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.delegate.exceptionhandler.core.DelegateExceptionManager;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.logging.AccountLogContext;
import io.harness.manage.GlobalContextManager;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public abstract class AbstractDelegateRunnableTask implements DelegateRunnableTask {
  private String delegateHostname;
  @Getter private String delegateId;
  @Getter private String accountId;
  @Getter private String taskId;
  @Getter private String taskType;
  @Getter private boolean isAsync;
  @Getter private Object[] parameters;
  @Getter private ILogStreamingTaskClient logStreamingTaskClient;
  private Consumer<DelegateTaskResponse> consumer;
  private BooleanSupplier preExecute;
  @Inject DelegateExceptionManager delegateExceptionManager;
  @Inject HarnessMetricRegistry metricRegistry;

  @Inject private DataCollectionExecutorService dataCollectionService;
  public static final String TASK_FAILED = "task_failed";
  private static String DELEGATE_NAME =
      isNotBlank(System.getenv().get("DELEGATE_NAME")) ? System.getenv().get("DELEGATE_NAME") : "";

  public AbstractDelegateRunnableTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    this.delegateId = delegateTaskPackage.getDelegateId();
    this.taskId = delegateTaskPackage.getDelegateTaskId();
    this.parameters = delegateTaskPackage.getData().getParameters();
    this.accountId = delegateTaskPackage.getAccountId();
    this.consumer = consumer;
    this.preExecute = preExecute;
    this.taskType = delegateTaskPackage.getData().getTaskType();
    this.isAsync = delegateTaskPackage.getData().isAsync();
    this.logStreamingTaskClient = logStreamingTaskClient;
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  @SuppressWarnings("PMD")
  public void run() {
    try (io.harness.delegate.task.tasklogging.TaskLogContext ignore = new TaskLogContext(this.taskId, OVERRIDE_ERROR);
         AccountLogContext ignore2 = new AccountLogContext(this.accountId, OVERRIDE_ERROR)) {
      runDelegateTask();
    } catch (Throwable e) {
      log.error("Unexpected error executing delegate taskId: [{}] in accountId: [{}]", taskId, accountId, e);
    }
  }

  @SuppressWarnings("PMD")
  private void runDelegateTask() {
    if (!preExecute.getAsBoolean()) {
      log.info("Pre-execute returned false for task {}", taskId);
      return;
    }

    DelegateMetaInfo delegateMetaInfo = DelegateMetaInfo.builder().hostName(delegateHostname).id(delegateId).build();

    DelegateTaskResponseBuilder taskResponse =
        DelegateTaskResponse.builder().accountId(accountId).taskTypeName(taskType).responseCode(ResponseCode.OK);

    ErrorNotifyResponseDataBuilder errorNotifyResponseDataBuilder =
        ErrorNotifyResponseData.builder().delegateMetaInfo(delegateMetaInfo);

    try {
      log.debug("Started executing task {}", taskId);

      if (!GlobalContextManager.isAvailable()) {
        GlobalContextManager.set(new GlobalContext());
      }
      GlobalContextManager.upsertGlobalContextRecord(
          ErrorHandlingGlobalContextData.builder().isSupportedErrorFramework(isSupportingErrorFramework()).build());

      DelegateResponseData result = parameters.length == 1 && parameters[0] instanceof TaskParameters
          ? run((TaskParameters) parameters[0])
          : run(parameters);

      if (result != null) {
        if (result instanceof DelegateTaskNotifyResponseData) {
          ((DelegateTaskNotifyResponseData) result).setDelegateMetaInfo(delegateMetaInfo);
        } else if (result instanceof RemoteMethodReturnValueData) {
          RemoteMethodReturnValueData returnValueData = (RemoteMethodReturnValueData) result;
          if (returnValueData.getException() instanceof DelegateRetryableException) {
            taskResponse.responseCode(ResponseCode.RETRY_ON_OTHER_DELEGATE);
          }
        }
        taskResponse.response(result);
      } else {
        String errorMessage = "No response from delegate task " + taskId;
        log.error(errorMessage);
        taskResponse.response(errorNotifyResponseDataBuilder.failureTypes(EnumSet.of(FailureType.APPLICATION_ERROR))
                                  .errorMessage(errorMessage)
                                  .build());
        taskResponse.responseCode(ResponseCode.FAILED);
        metricRegistry.registerCounterMetric(
            TASK_FAILED, new String[] {DELEGATE_NAME, taskType}, "Total number of task failed");
      }
      log.debug("Completed executing task {}", taskId);
    } catch (DelegateRetryableException exception) {
      ExceptionLogger.logProcessedMessages(exception, DELEGATE, log);
      taskResponse.response(errorNotifyResponseDataBuilder.failureTypes(ExceptionUtils.getFailureTypes(exception))
                                .errorMessage(ExceptionUtils.getMessage(exception))
                                .build());
      taskResponse.responseCode(ResponseCode.RETRY_ON_OTHER_DELEGATE);
    } catch (Throwable throwable) {
      if (!(throwable instanceof Exception) || !isSupportingErrorFramework()) {
        log.error(
            format("Unexpected error while executing delegate taskId: [%s] in accountId: [%s]", taskId, accountId),
            throwable);
      }
      taskResponse.response(delegateExceptionManager.getResponseData(
          throwable, errorNotifyResponseDataBuilder, isSupportingErrorFramework()));
      taskResponse.responseCode(ResponseCode.FAILED);
      metricRegistry.recordGaugeInc(TASK_FAILED, new String[] {delegateHostname, taskType});
    } finally {
      GlobalContextManager.unset();
      if (consumer != null) {
        consumer.accept(taskResponse.build());
      }
    }
  }

  protected <T> List<Optional<T>> executeParallel(List<Callable<T>> callables) {
    return dataCollectionService.executeParrallel(callables);
  }

  public ThirdPartyApiCallLogDetails createApiCallLog(String stateExecutionId) {
    return ThirdPartyApiCallLogDetails.builder()
        .accountId(getAccountId())
        .delegateId(getDelegateId())
        .delegateTaskId(getTaskId())
        .stateExecutionId(stateExecutionId)
        .build();
  }

  public boolean isSupportingErrorFramework() {
    return false;
  }

  @Override
  public String toString() {
    return "DelegateRunnableTask - " + getTaskType() + " - " + getTaskId() + " - " + (isAsync() ? "async" : "sync");
  }

  public void setDelegateHostname(String delegateHostname) {
    this.delegateHostname = delegateHostname;
  }
}
