/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.delegatetasks.cv.CVConstants.MAX_RETRIES;
import static software.wings.delegatetasks.cv.CVConstants.RETRY_SLEEP_DURATION;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.delegatetasks.DelegateCVTaskService;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.VerificationLogContext;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Call;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class AbstractDataCollectionTask<T extends DataCollectionInfoV2> extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private Injector injector;
  @Inject private DataCollectorFactory dataCollectorFactory;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateCVActivityLogService cvActivityLogService;
  @Inject private DelegateCVTaskService cvTaskService;
  @Inject private RequestExecutor requestExecutor;
  private DataCollectionInfoV2 dataCollectionInfo;
  private DataCollector<T> dataCollector;
  private DataCollectionExecutionContext dataCollectionExecutionContext;
  private Logger activityLogger;

  public AbstractDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new RuntimeException("Not implemented. This should not get called for data collection tasks.");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    dataCollectionInfo = (DataCollectionInfoV2) parameters;
    DataCollectionTaskResult dataCollectionTaskResult;
    try (VerificationLogContext ignored =
             new VerificationLogContext(dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvConfigId(),
                 dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getStateType(), OVERRIDE_ERROR)) {
      try {
        dataCollectionTaskResult = run(dataCollectionInfo);
        cvTaskService.updateCVTaskStatus(
            dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvTaskId(), dataCollectionTaskResult);
      } catch (Exception e) {
        dataCollectionTaskResult = DataCollectionTaskResult.builder()
                                       .status(DataCollectionTaskStatus.FAILURE)
                                       .errorMessage(e.getMessage())
                                       .stateType(dataCollectionInfo.getStateType())
                                       .build();
        try {
          cvTaskService.updateCVTaskStatus(
              dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvTaskId(), dataCollectionTaskResult);
        } catch (TimeoutException ex) {
          throw new DataCollectionException(ex);
        }
        log.error("Data collection failed. Exception: ", e);
      }
    }
    return dataCollectionTaskResult;
  }
  private DataCollectionTaskResult run(DataCollectionInfoV2 dataCollectionInfo) {
    decryptIfHasEncryptableSetting(dataCollectionInfo);
    DataCollectionTaskResult taskResult = DataCollectionTaskResult.builder()
                                              .status(DataCollectionTaskStatus.SUCCESS)
                                              .stateType(dataCollectionInfo.getStateType())
                                              .build();

    initializeActivityLogger();
    RetryPolicy<Object> retryPolicy =
        new RetryPolicy<>()
            .handle(Exception.class)
            .withDelay(RETRY_SLEEP_DURATION)
            .withMaxRetries(MAX_RETRIES)
            .onFailedAttempt(event -> {
              activityLogger.warn(
                  "[Retrying] Data collection task failed with exception: " + event.getLastFailure().getMessage());
              taskResult.setErrorMessage(event.getLastFailure().getMessage());
            })
            .onFailure(event -> {
              activityLogger.error("Data collection failed with exception: " + event.getFailure().getMessage());
              taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
              taskResult.setErrorMessage(event.getFailure().getMessage());
            });
    activityLogger.info("Starting data collection.");

    Failsafe.with(retryPolicy).run(() -> {
      initializeDataCollector();
      dataCollectionExecutionContext = createDataCollectionExecutionContext();
      log.info("Starting init");
      dataCollector.init(dataCollectionExecutionContext, (T) dataCollectionInfo);
      log.info("Finished init and starting collectAndSaveData");
      collectAndSaveData((T) dataCollectionInfo);
      log.info("Finished collectAndSaveData");
    });

    log.info("Data collection task completed {}", dataCollectionInfo.getStateExecutionId());
    activityLogger.info("Finished data collection with status: " + taskResult.getStatus());

    return taskResult;
  }
  private void decryptIfHasEncryptableSetting(DataCollectionInfoV2 dataCollectionInfo) {
    if (dataCollectionInfo.getEncryptableSetting().isPresent()) {
      encryptionService.decrypt(
          dataCollectionInfo.getEncryptableSetting().get(), dataCollectionInfo.getEncryptedDataDetails(), false);
    }
  }

  private void initializeDataCollector() {
    try {
      this.dataCollector =
          (DataCollector<T>) dataCollectorFactory.newInstance(dataCollectionInfo.getDataCollectorImplClass());
      injector.injectMembers(dataCollector);
    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private void initializeActivityLogger() {
    activityLogger =
        cvActivityLogService.getLogger(dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvConfigId(),
            TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getEndTime().toEpochMilli()),
            dataCollectionInfo.getStateExecutionId(), "[Time range: %t-%t]",
            dataCollectionInfo.getStartTime().toEpochMilli(), dataCollectionInfo.getEndTime().toEpochMilli() + 1);
  }

  private DataCollectionExecutionContext createDataCollectionExecutionContext() {
    return new DataCollectionExecutionContext() {
      @Override
      public ThirdPartyApiCallLog createApiCallLog() {
        return AbstractDataCollectionTask.this.createApiCallLog();
      }

      @Override
      public void saveThirdPartyApiCallLog(ThirdPartyApiCallLog thirdPartyApiCallLog) {
        AbstractDataCollectionTask.this.saveThirdPartyApiCallLog(thirdPartyApiCallLog);
      }

      @Override
      public Logger getActivityLogger() {
        return activityLogger;
      }

      @Override
      public <U> U executeRequest(
          String thirdPartyApiCallTitle, Call<U> request, Map<String, String> patternsToMaskInCallLog) {
        ThirdPartyApiCallLog thirdPartyApiCallLog = createApiCallLog();
        thirdPartyApiCallLog.setTitle(thirdPartyApiCallTitle);
        return requestExecutor.executeRequest(thirdPartyApiCallLog, request, patternsToMaskInCallLog);
      }

      @Override
      public <T> T executeRequest(String thirdPartyApiCallTitle, Call<T> request) {
        ThirdPartyApiCallLog thirdPartyApiCallLog = createApiCallLog();
        thirdPartyApiCallLog.setTitle(thirdPartyApiCallTitle);
        return requestExecutor.executeRequest(thirdPartyApiCallLog, request);
      }
    };
  }

  protected abstract void collectAndSaveData(T parameters) throws DataCollectionException;

  private void saveThirdPartyApiCallLog(ThirdPartyApiCallLog thirdPartyApiCallLog) {
    delegateLogService.save(dataCollectionInfo.getAccountId(), thirdPartyApiCallLog);
  }

  private ThirdPartyApiCallLog createApiCallLog() {
    // TODO: need to fix this for 24  * 7. For 24 * 7 task stateExecutionId is set to CV_24x7_STATE_EXECUTION + "-" +
    // cvConfigId
    return ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId()));
  }

  protected DataCollector<T> getDataCollector() {
    return dataCollector;
  }

  protected Logger getActivityLogger() {
    return activityLogger;
  }
}
