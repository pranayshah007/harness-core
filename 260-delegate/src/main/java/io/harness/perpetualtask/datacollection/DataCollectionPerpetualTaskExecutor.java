/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.cvng.beans.DataCollectionExecutionStatus.FAILED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.SUCCESS;
import static io.harness.cvng.core.services.CVNextGenConstants.LOG_RECORD_THRESHOLD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.CVDataCollectionInfo;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog;
import io.harness.cvng.beans.LogDataCollectionInfo;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.service.HostRecordDataStoreService;
import io.harness.delegate.service.LogRecordDataStoreService;
import io.harness.delegate.service.TimeSeriesDataStoreService;
import io.harness.grpc.utils.AnyUtils;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;
import io.harness.verificationclient.CVNextGenServiceClient;

import software.wings.delegatetasks.DelegateLogService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class DataCollectionPerpetualTaskExecutor implements PerpetualTaskExecutor {
  @VisibleForTesting protected Integer dataCollectionTimeoutInMilliSeconds = 600_000;
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Inject private DelegateLogService delegateLogService;
  @Inject private TimeSeriesDataStoreService timeSeriesDataStoreService;
  @Inject private LogRecordDataStoreService logRecordDataStoreService;
  @Inject private HostRecordDataStoreService hostRecordDataStoreService;
  @Inject private KryoSerializer kryoSerializer;

  @Inject private DataCollectionDSLService dataCollectionDSLService;
  @Inject private CVNGRequestExecutor cvngRequestExecutor;
  @Inject @Named("verificationDataCollectorExecutor") protected ExecutorService dataCollectionService;
  @Inject @Named("cvngParallelExecutor") protected ExecutorService parallelExecutor;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    DataCollectionPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), DataCollectionPerpetualTaskParams.class);
    log.info("Executing for !! dataCollectionWorkerId: {}", taskParams.getDataCollectionWorkerId());
    CVDataCollectionInfo dataCollectionInfo =
        (CVDataCollectionInfo) kryoSerializer.asObject(taskParams.getDataCollectionInfo().toByteArray());
    log.info("DataCollectionInfo {} ", dataCollectionInfo);
    try (DataCollectionLogContext ignored = new DataCollectionLogContext(
             taskParams.getDataCollectionWorkerId(), dataCollectionInfo.getDataCollectionType(), OVERRIDE_ERROR)) {
      List<DecryptableEntity> decryptableEntities = dataCollectionInfo.getConnectorConfigDTO().getDecryptableEntities();
      List<List<EncryptedDataDetail>> encryptedDataDetails = dataCollectionInfo.getEncryptedDataDetails();

      if (isNotEmpty(decryptableEntities)) {
        if (decryptableEntities.size() != encryptedDataDetails.size()) {
          log.warn(
              "Size of decryptableEntities is not same as size of encryptedDataDetails. Probably it is because of version difference between delegate and manager and decyptable entities got added/removed.");
        }
        // using min of encryptedDataDetails, decryptableEntities size to avoid index out of bound exception because of
        // comparability issues. This allows us to add/remove decryptableEntities without breaking this. This can still
        // cause issues if not done carefully.
        for (int index = 0; index < Math.min(encryptedDataDetails.size(), decryptableEntities.size()); index++) {
          DecryptableEntity decryptableEntity = decryptableEntities.get(index);
          secretDecryptionService.decrypt(decryptableEntity, encryptedDataDetails.get(index));
        }
      }

      dataCollectionDSLService.registerDatacollectionExecutorService(dataCollectionService);
      List<DataCollectionTaskDTO> dataCollectionTasks = new ArrayList<>();
      // TODO: What happens if this task takes more time then the schedule?
      while (true) {
        try {
          dataCollectionTasks = getNextDataCollectionTasks(taskParams);
        } catch (Exception e) {
          log.error("Data collection task failed.", e);
        }
        if (isEmpty(dataCollectionTasks)) {
          log.info("Nothing to process.");
          break;
        } else {
          log.info("Next tasks to process: {}", dataCollectionTasks);
          List<CompletableFuture> completableFutures =
              dataCollectionTasks.stream()
                  .map(dct
                      -> CompletableFuture
                             .runAsync(()
                                           -> run(taskParams, dataCollectionInfo.getConnectorConfigDTO(), dct),
                                 parallelExecutor)
                             .orTimeout(dataCollectionTimeoutInMilliSeconds, TimeUnit.MILLISECONDS)
                             .exceptionally(exception -> {
                               updateStatusWithException(taskParams, dct, exception);
                               return null;
                             }))
                  .collect(Collectors.toList());
          // execute and wait for all CFs to complete parallely
          CompletableFuture.allOf(completableFutures.toArray(n -> new CompletableFuture[n]))
              .exceptionally(ex -> {
                log.error("Exception in parallel execution:", ex);
                return null;
              })
              .join();
        }
      }
    }

    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private List<DataCollectionTaskDTO> getNextDataCollectionTasks(DataCollectionPerpetualTaskParams taskParams) {
    return cvngRequestExecutor
        .executeWithRetry(cvNextGenServiceClient.getNextDataCollectionTasks(
            taskParams.getAccountId(), taskParams.getDataCollectionWorkerId()))
        .getResource();
  }

  @SuppressWarnings("PMD")
  @VisibleForTesting
  protected void run(DataCollectionPerpetualTaskParams taskParams, ConnectorConfigDTO connectorConfigDTO,
      DataCollectionTaskDTO dataCollectionTask) {
    try (DataCollectionLogContext closableLogContext = new DataCollectionLogContext(dataCollectionTask)) {
      DataCollectionInfo dataCollectionInfo = dataCollectionTask.getDataCollectionInfo();
      log.info("collecting data for {}", dataCollectionTask.getVerificationTaskId());
      List<ExecutionLog> executionLogs = new ArrayList<>();
      final RuntimeParameters runtimeParameters =
          RuntimeParameters.builder()
              .baseUrl(dataCollectionTask.getDataCollectionInfo().getBaseUrl(connectorConfigDTO))
              .commonHeaders(dataCollectionTask.getDataCollectionInfo().collectionHeaders(connectorConfigDTO))
              .commonOptions(dataCollectionTask.getDataCollectionInfo().collectionParams(connectorConfigDTO))
              .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(connectorConfigDTO))
              .hostNamesToCollect(dataCollectionInfo.getServiceInstances())
              .endTime(dataCollectionTask.getEndTime())
              .startTime(dataCollectionTask.getStartTime())
              .build();
      switch (dataCollectionInfo.getVerificationType()) {
        case TIME_SERIES:
          List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
              dataCollectionInfo.getDataCollectionDsl(), runtimeParameters,
              new ThirdPartyCallHandler(dataCollectionTask.getAccountId(), dataCollectionTask.getVerificationTaskId(),
                  delegateLogService, dataCollectionTask.getStartTime(), dataCollectionTask.getEndTime()));
          if (CollectionUtils.isNotEmpty(dataCollectionInfo.getServiceInstances())) {
            timeSeriesRecords = timeSeriesRecords.stream()
                                    .filter(tsr -> dataCollectionInfo.getServiceInstances().contains(tsr.getHostname()))
                                    .collect(Collectors.toList());
          }
          timeSeriesDataStoreService.saveTimeSeriesDataRecords(
              dataCollectionTask.getAccountId(), dataCollectionTask.getVerificationTaskId(), timeSeriesRecords);

          break;

        case LOG:
          List<LogDataRecord> logDataRecords = (List<LogDataRecord>) dataCollectionDSLService.execute(
              dataCollectionInfo.getDataCollectionDsl(), runtimeParameters,
              new ThirdPartyCallHandler(dataCollectionTask.getAccountId(), dataCollectionTask.getVerificationTaskId(),
                  delegateLogService, dataCollectionTask.getStartTime(), dataCollectionTask.getEndTime()));
          if (CollectionUtils.isNotEmpty(dataCollectionInfo.getServiceInstances())) {
            logDataRecords =
                logDataRecords.stream()
                    .filter(
                        logDataRecord -> dataCollectionInfo.getServiceInstances().contains(logDataRecord.getHostname()))
                    .collect(Collectors.toList());
          }
          if (logDataRecords.size() > LOG_RECORD_THRESHOLD) {
            logDataRecords = pickNRandomElements(logDataRecords);
            ExecutionLog delegateLogs =
                ExecutionLog.builder()
                    .logLevel(LogLevel.WARN)
                    .log(String.format("Log query is not optimized. Logs collected %s are capped at %s",
                        logDataRecords.size(), LOG_RECORD_THRESHOLD))
                    .build();
            executionLogs.add(delegateLogs);
          }
          List<LogDataRecord> validRecords =
              logDataRecords.stream()
                  .filter(log -> isNotEmpty(log.getLog()) && isNotEmpty(log.getHostname()))
                  .collect(Collectors.toList());
          if (validRecords.size() < logDataRecords.size()) {
            log.info(
                "Log query is not optimized. Some records with invalid messageIdentifier and serviceInstanceIdentifier are present for verification task id: {}",
                dataCollectionTask.getVerificationTaskId());
            ExecutionLog delegateLog =
                ExecutionLog.builder()
                    .logLevel(LogLevel.WARN)
                    .log(String.format(
                        "Log query is not optimized. Some records with invalid messageIdentifier and serviceInstanceIdentifier are present."))
                    .build();
            executionLogs.add(delegateLog);
          }
          logRecordDataStoreService.save(
              dataCollectionTask.getAccountId(), dataCollectionTask.getVerificationTaskId(), validRecords);
          if (dataCollectionInfo.isCollectHostData()) {
            Set<String> hosts;
            LogDataCollectionInfo logDataCollectionInfo = (LogDataCollectionInfo) dataCollectionInfo;
            if (isNotEmpty(logDataCollectionInfo.getHostCollectionDSL())) {
              hosts = new HashSet<>((Collection<String>) dataCollectionDSLService.execute(
                  logDataCollectionInfo.getHostCollectionDSL(), runtimeParameters,
                  new ThirdPartyCallHandler(dataCollectionTask.getAccountId(),
                      dataCollectionTask.getVerificationTaskId(), delegateLogService, dataCollectionTask.getStartTime(),
                      dataCollectionTask.getEndTime())));
            } else {
              hosts = validRecords.stream().map(LogDataRecord::getHostname).collect(Collectors.toSet());
            }
            hostRecordDataStoreService.save(dataCollectionTask.getAccountId(),
                dataCollectionTask.getVerificationTaskId(), dataCollectionTask.getStartTime(),
                dataCollectionTask.getEndTime(), hosts);
          }
          break;
        default:
          throw new IllegalArgumentException("Invalid type " + dataCollectionInfo.getVerificationType());
      }
      log.info("data collection success for {}.", dataCollectionTask.getVerificationTaskId());
      DataCollectionTaskResult result = DataCollectionTaskResult.builder()
                                            .dataCollectionTaskId(dataCollectionTask.getUuid())
                                            .status(SUCCESS)
                                            .executionLogs(executionLogs)
                                            .build();
      cvngRequestExecutor.execute(cvNextGenServiceClient.updateTaskStatus(taskParams.getAccountId(), result));
      log.info("Updated task status to success for {}.", dataCollectionTask.getVerificationTaskId());
    } catch (Throwable e) {
      updateStatusWithException(taskParams, dataCollectionTask, e);
    }
  }

  private <E> List<E> pickNRandomElements(List<E> logDataRecords) {
    int length = logDataRecords.size();
    Random random = new Random();
    for (int i = length - 1; i >= length - LOG_RECORD_THRESHOLD; --i) {
      Collections.swap(logDataRecords, i, random.nextInt(i + 1));
    }
    return new ArrayList<>(logDataRecords.subList(length - LOG_RECORD_THRESHOLD, length));
  }

  @VisibleForTesting
  protected void updateStatusWithException(
      DataCollectionPerpetualTaskParams taskParams, DataCollectionTaskDTO dataCollectionTask, Throwable e) {
    DataCollectionTaskResult result = DataCollectionTaskResult.builder()
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .status(FAILED)
                                          .exception(ExceptionUtils.getMessage(e))
                                          .stacktrace(ExceptionUtils.getStackTrace(e))
                                          .build();
    log.error("Data collection task failed for {} with exception Result: {} exception: {}",
        dataCollectionTask.getVerificationTaskId(), result, e);
    try {
      cvngRequestExecutor.execute(cvNextGenServiceClient.updateTaskStatus(taskParams.getAccountId(), result));
    } catch (Exception exceptionOnExecute) {
      // ignore
      log.error("status Update call failed with {}", exceptionOnExecute);
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return true;
  }
}
