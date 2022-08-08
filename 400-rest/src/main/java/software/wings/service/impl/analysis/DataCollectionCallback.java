/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import static software.wings.common.VerificationConstants.WORKFLOW_CV_COLLECTION_CRON_GROUP;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.beans.alert.cv.ContinuousVerificationDataCollectionAlert;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 5/18/17.
 */
@Data
@Builder
@Slf4j
public class DataCollectionCallback implements OldNotifyCallback {
  @Inject private transient WaitNotifyEngine waitNotifyEngine;
  @Inject private transient WingsPersistence wingsPersistence;
  @Inject private transient AlertService alertService;
  @Inject private transient AppService appService;
  @Inject private transient CVConfigurationService cvConfigurationService;
  @Inject private transient CVActivityLogService cvActivityLogService;

  private String appId;
  private boolean isDataCollectionPerMinuteTask;
  private StateExecutionData executionData;
  private String stateExecutionId;
  private StateType stateType;
  private String cvConfigId;
  private long dataCollectionStartTime;
  private long dataCollectionEndTime;

  @Override
  public void notify(Map<String, ResponseData> response) {
    final DataCollectionTaskResult result = (DataCollectionTaskResult) response.values().iterator().next();
    log.info("data collection result for state {} is: {}", stateExecutionId, result);
    activityLog(result);
    if (result.getStatus() == DataCollectionTaskStatus.FAILURE) {
      sendErrorNotification(result.getErrorMessage());
    }
    alertIfNecessary(result.getStatus(), result.getErrorMessage());
  }

  private void activityLog(DataCollectionTaskResult result) {
    String accountId = appService.getAccountIdByAppId(appId);

    CVActivityLogger activityLogger = cvActivityLogService.getLogger(
        accountId, cvConfigId, TimeUnit.MILLISECONDS.toMinutes(dataCollectionEndTime), stateExecutionId);
    if (result.getStatus() == DataCollectionTaskStatus.SUCCESS) {
      activityLogger.info(
          "Data collection successful for time range %t to %t", dataCollectionStartTime, dataCollectionEndTime);
    } else {
      activityLogger.error(
          "Data collection failed with error \"" + result.getErrorMessage() + "\" for time range %t to %t",
          dataCollectionStartTime, dataCollectionEndTime);
    }
  }

  // TODO what is this used for
  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.info("notify error for {} ", response.values().iterator().next());
    if (response.values().iterator().next() instanceof ErrorNotifyResponseData) {
      final ErrorNotifyResponseData result = (ErrorNotifyResponseData) response.values().iterator().next();
      sendErrorNotification(result.getErrorMessage());
      alertIfNecessary(DataCollectionTaskStatus.FAILURE, result.getErrorMessage());
    }
  }

  private void sendErrorNotification(String errorMsg) {
    Preconditions.checkNotNull(executionData, "execution data is null for appId" + appId);
    VerificationStateAnalysisExecutionData analysisExecutionData =
        (VerificationStateAnalysisExecutionData) executionData;
    analysisExecutionData.setStatus(ExecutionStatus.ERROR);
    analysisExecutionData.setErrorMsg(errorMsg);
    VerificationDataAnalysisResponse verificationDataAnalysisResponse =
        VerificationDataAnalysisResponse.builder().stateExecutionData(analysisExecutionData).build();
    verificationDataAnalysisResponse.setExecutionStatus(ExecutionStatus.ERROR);
    waitNotifyEngine.doneWith(analysisExecutionData.getCorrelationId(), verificationDataAnalysisResponse);
    if (isDataCollectionPerMinuteTask) {
      deleteDataCollectionCron();
    }
  }

  private void alertIfNecessary(DataCollectionTaskStatus status, String errorMessage) {
    if (isEmpty(cvConfigId)) {
      return;
    }
    final CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    if (cvConfiguration == null) {
      return;
    }
    switch (status) {
      case SUCCESS:
        alertService.closeAlert(appService.getAccountIdByAppId(appId), appId,
            AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT,
            ContinuousVerificationDataCollectionAlert.builder().cvConfiguration(cvConfiguration).build());
        break;
      case FAILURE:
        String message = "Failed to collect data for " + cvConfiguration.getName()
            + "(Application: " + cvConfiguration.getAppName() + ", Environment: " + cvConfiguration.getEnvName()
            + ", Service: " + cvConfiguration.getServiceName() + ") for Time: "
            + new SimpleDateFormat(ContinuousVerificationAlertData.DEFAULT_TIME_FORMAT)
                  .format(new Date(dataCollectionEndTime))
            + "\nReason: " + errorMessage;
        alertService.openAlert(appService.getAccountIdByAppId(appId), appId,
            AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT,
            ContinuousVerificationDataCollectionAlert.builder()
                .cvConfiguration(cvConfiguration)
                .message(message)
                .build());
        break;

      default:
        throw new IllegalStateException("Invalid status " + status);
    }
  }

  private void deleteDataCollectionCron() {
    log.info("Data collection failed with stateExecutionId {}, deleting data collection cron", stateExecutionId);
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "quartz_verification_jobs");
    BasicDBObject object = new BasicDBObject();
    object.put("keyName", stateExecutionId);
    object.put("keyGroup", stateType.name().toUpperCase() + WORKFLOW_CV_COLLECTION_CRON_GROUP);
    collection.findAndRemove(object);
  }
}
