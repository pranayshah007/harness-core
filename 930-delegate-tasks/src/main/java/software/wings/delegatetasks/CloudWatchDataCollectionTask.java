/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DURATION_TO_ASK_MINUTES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.delegate.AwsLambdaHelperServiceDelegateImpl;
import software.wings.service.impl.cloudwatch.AwsNameSpace;
import software.wings.service.impl.cloudwatch.CloudWatchDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchDelegateServiceImpl;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by rsingh on 5/18/17.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CloudWatchDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private CloudWatchDataCollectionInfo dataCollectionInfo;
  @Inject private AwsHelperService awsHelperService;
  @Inject private MetricDataStoreService metricStoreService;
  @Inject private CloudWatchDelegateServiceImpl cloudWatchDelegateService;
  @Inject private AwsLambdaHelperServiceDelegateImpl awsLambdaHelperServiceDelegate;

  public CloudWatchDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (CloudWatchDataCollectionInfo) parameters;
    log.info("metric collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskStatus.SUCCESS)
        .stateType(DelegateStateType.CLOUD_WATCH)
        .build();
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.CLOUD_WATCH;
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.CLOUD_WATCH_COLLECT_24_7_METRIC_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new CloudWatchMetricCollector(dataCollectionInfo, taskResult, is24X7Task());
  }

  private class CloudWatchMetricCollector implements Runnable {
    private final CloudWatchDataCollectionInfo dataCollectionInfo;
    private final DataCollectionTaskResult taskResult;
    boolean is247Task;
    private long collectionStartTime;
    private int dataCollectionMinute;
    private Map<String, Long> hostStartTimeMap;

    private CloudWatchMetricCollector(
        CloudWatchDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult, boolean is247Task) {
      this.dataCollectionInfo = dataCollectionInfo;
      this.taskResult = taskResult;
      this.collectionStartTime = Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
      this.dataCollectionMinute = dataCollectionInfo.getDataCollectionMinute();
      this.is247Task = is247Task;
      this.hostStartTimeMap = new HashMap<>();
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      encryptionService.decrypt(dataCollectionInfo.getAwsConfig(), dataCollectionInfo.getEncryptedDataDetails(), false);
      int retry = 0;
      while (!completed.get() && retry < RETRIES) {
        try {
          log.info("Running data collection for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
              dataCollectionMinute);
          dataCollectionInfo.setDataCollectionMinute(dataCollectionMinute);

          TreeBasedTable<String, Long, NewRelicMetricDataRecord> metricDataRecords = getMetricsData();
          // HeartBeat
          metricDataRecords.put(HARNESS_HEARTBEAT_METRIC_NAME, 0L,
              NewRelicMetricDataRecord.builder()
                  .stateType(getStateType())
                  .name(HARNESS_HEARTBEAT_METRIC_NAME)
                  .appId(dataCollectionInfo.getApplicationId())
                  .workflowId(dataCollectionInfo.getWorkflowId())
                  .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                  .serviceId(dataCollectionInfo.getServiceId())
                  .cvConfigId(dataCollectionInfo.getCvConfigId())
                  .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                  .dataCollectionMinute(getDataCollectionMinuteForHeartbeat(is247Task))
                  .timeStamp(collectionStartTime)
                  .level(ClusterLevel.H0)
                  .groupName(DEFAULT_GROUP_NAME)
                  .build());

          List<NewRelicMetricDataRecord> recordsToSave = getAllMetricRecords(metricDataRecords);
          if (!saveMetrics(dataCollectionInfo.getAwsConfig().getAccountId(), dataCollectionInfo.getApplicationId(),
                  dataCollectionInfo.getStateExecutionId(), recordsToSave)) {
            retry = RETRIES;
            taskResult.setErrorMessage("Cannot save new CloudWatch metric records to Harness");
            throw new RuntimeException("Cannot save new CloudWatch metric records to Harness");
          }
          log.info("Sent {} CloudWatch metric records to the server for minute {}.", recordsToSave.size(),
              getDataCollectionMinuteForHeartbeat(is247Task));

          dataCollectionMinute++;
          collectionStartTime += TimeUnit.MINUTES.toMillis(1);
          if (dataCollectionMinute >= dataCollectionInfo.getCollectionTime() || is247Task) {
            // We are done with all data collection, so setting task status to success and quitting.
            log.info(
                "Completed CloudWatch collection task, so setting task status to success and quitting for StateExecutionId {}",
                dataCollectionInfo.getStateExecutionId());
            completed.set(true);
            taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
          }
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("Error fetching metrics for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                dataCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            if (retry == 1) {
              taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
            }
            log.warn("Error fetching CloudWatch metrics for minute " + dataCollectionMinute + ". retrying in "
                    + DATA_COLLECTION_RETRY_SLEEP + "s",
                ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }
      if (taskResult.getStatus() == DataCollectionTaskStatus.FAILURE) {
        completed.set(true);
        taskResult.setErrorMessage("Error fetching cloud watch metrics for minute " + dataCollectionMinute);
        log.error("Error fetching CloudWatch metrics for minute " + dataCollectionMinute);
      }

      if (completed.get()) {
        log.info("Shutting down CloudWatch data collection");
        shutDownCollection();
        return;
      }
    }

    private int getDataCollectionMinuteForHeartbeat(boolean is247Task) {
      int collectionMin = dataCollectionMinute;
      if (is247Task) {
        collectionMin = (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime())
            + dataCollectionInfo.getCollectionTime();
      }
      return collectionMin;
    }

    public TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricsData() {
      final TreeBasedTable<String, Long, NewRelicMetricDataRecord> metricDataResponses = TreeBasedTable.create();
      List<Callable<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> callables = new ArrayList<>();
      try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
               new CloseableAmazonWebServiceClient(awsHelperService.getAwsCloudWatchClient(
                   dataCollectionInfo.getRegion(), dataCollectionInfo.getAwsConfig()))) {
        Map<String, List<CloudWatchMetric>> cloudWatchMetricsByLambdaFunction =
            dataCollectionInfo.getLambdaFunctionNames();
        Map<String, List<CloudWatchMetric>> cloudWatchMetricsByECSClusterName =
            dataCollectionInfo.getMetricsByECSClusterName();
        Map<String, List<CloudWatchMetric>> cloudWatchMetricsByELBName = dataCollectionInfo.getLoadBalancerMetrics();

        if (isNotEmpty(cloudWatchMetricsByECSClusterName)) {
          log.info("for {} fetching metrics for ECS Cluster {}", dataCollectionInfo.getStateExecutionId(),
              cloudWatchMetricsByECSClusterName);
          addCallablesForGetMetricData(closeableAmazonCloudWatchClient.getClient(), callables, AwsNameSpace.ECS,
              cloudWatchMetricsByECSClusterName);
        }
        if (isNotEmpty(cloudWatchMetricsByLambdaFunction)) {
          log.info("for {} fetching metrics for lambda functions {}", dataCollectionInfo.getStateExecutionId(),
              cloudWatchMetricsByLambdaFunction);
          addCallablesForGetMetricData(closeableAmazonCloudWatchClient.getClient(), callables, AwsNameSpace.LAMBDA,
              cloudWatchMetricsByLambdaFunction);
        }
        if (isNotEmpty(cloudWatchMetricsByELBName)) {
          log.info("for {} fetching metrics for load balancers {}", dataCollectionInfo.getStateExecutionId(),
              cloudWatchMetricsByELBName);
          addCallablesForGetMetricData(
              closeableAmazonCloudWatchClient.getClient(), callables, AwsNameSpace.ELB, cloudWatchMetricsByELBName);
        }
        if (isNotEmpty(dataCollectionInfo.getHosts()) && isNotEmpty(dataCollectionInfo.getEc2Metrics())) {
          log.info("for {} fetching {} metrics for hosts {}", dataCollectionInfo.getStateExecutionId(),
              dataCollectionInfo.getEc2Metrics(), dataCollectionInfo.getHosts());
          dataCollectionInfo.getHosts().forEach(
              (host, groupName)
                  -> dataCollectionInfo.getEc2Metrics().forEach(cloudWatchMetric
                      -> callables.add(()
                                           -> cloudWatchDelegateService.getMetricDataRecords(AwsNameSpace.EC2,
                                               closeableAmazonCloudWatchClient.getClient(), cloudWatchMetric, host,
                                               groupName, dataCollectionInfo, dataCollectionInfo.getApplicationId(),
                                               collectionStartTime - TimeUnit.MINUTES.toMillis(DURATION_TO_ASK_MINUTES),
                                               collectionStartTime,
                                               ThirdPartyApiCallLog.fromDetails(
                                                   createApiCallLog(dataCollectionInfo.getStateExecutionId())),
                                               is247Task, hostStartTimeMap))));
        }
        log.info("Fetching CloudWatch metrics for {} strategy {} for min {}", dataCollectionInfo.getStateExecutionId(),
            dataCollectionInfo.getAnalysisComparisonStrategy(), dataCollectionMinute);
        List<Optional<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> results = executeParallel(callables);
        log.info("Done fetching CloudWatch metrics for {} strategy {} for min {}",
            dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getAnalysisComparisonStrategy(),
            dataCollectionMinute);
        results.forEach(result -> {
          if (result.isPresent()) {
            TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = result.get();
            for (Cell<String, Long, NewRelicMetricDataRecord> cell : records.cellSet()) {
              NewRelicMetricDataRecord metricDataRecord =
                  metricDataResponses.get(cell.getRowKey(), cell.getColumnKey());
              if (metricDataRecord != null) {
                metricDataRecord.getValues().putAll(cell.getValue().getValues());
              } else {
                metricDataResponses.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
              }
            }
          }
        });
      } catch (Exception e) {
        log.error("Exception getMetricsData", e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
      }
      return metricDataResponses;
    }

    private void addCallablesForGetMetricData(AmazonCloudWatchClient cloudWatchClient,
        List<Callable<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> callables, AwsNameSpace nameSpace,
        Map<String, List<CloudWatchMetric>> cloudWatchMetricsByType) {
      cloudWatchMetricsByType.forEach((entityType, cloudWatchMetrics) -> cloudWatchMetrics.forEach(cloudWatchMetric -> {
        callables.add(
            ()
                -> cloudWatchDelegateService.getMetricDataRecords(nameSpace, cloudWatchClient, cloudWatchMetric,
                    entityType, DEFAULT_GROUP_NAME, dataCollectionInfo, dataCollectionInfo.getApplicationId(),
                    System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(DURATION_TO_ASK_MINUTES),
                    System.currentTimeMillis(),
                    ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())),
                    is247Task, hostStartTimeMap));
      }));
    }
  }
}
