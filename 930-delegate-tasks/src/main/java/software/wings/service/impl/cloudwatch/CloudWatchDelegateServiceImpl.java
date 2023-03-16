/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cloudwatch;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.govern.Switch.unhandled;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.PREDECTIVE_HISTORY_MINUTES;
import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DURATION_TO_ASK_MINUTES;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.PREDICTIVE;

import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.AwsConfig;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.CVConstants;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.cloudwatch.CloudWatchDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;

/**
 * Created by Pranjal on 09/04/2018
 */
@Singleton
@Slf4j
public class CloudWatchDelegateServiceImpl implements CloudWatchDelegateService {
  private static final int CANARY_DAYS_TO_COLLECT = 7;
  @Inject private AwsHelperService awsHelperService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(final AwsConfig config,
      List<EncryptedDataDetail> encryptionDetails, CloudWatchSetupTestNodeData setupTestNodeData,
      ThirdPartyApiCallLog thirdPartyApiCallLog, String hostName) {
    log.info("Initiating getDataForNode for hostname : " + hostName + " setupTestNodeData : " + setupTestNodeData);
    List<Callable<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> callables = new ArrayList<>();

    setupTestNodeData.setFromTime(TimeUnit.SECONDS.toMillis(setupTestNodeData.getFromTime()));
    setupTestNodeData.setToTime(TimeUnit.SECONDS.toMillis(setupTestNodeData.getToTime()));
    encryptionService.decrypt(config, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(
                 awsHelperService.getAwsCloudWatchClient(setupTestNodeData.getRegion(), config))) {
      // Fetch ELB Metrics
      if (!isEmpty(setupTestNodeData.getLoadBalancerMetricsByLBName())) {
        setupTestNodeData.getLoadBalancerMetricsByLBName().forEach(
            (loadBalancerName, cloudWatchMetrics) -> cloudWatchMetrics.forEach(cloudWatchMetric -> {
              callables.add(()
                                -> getMetricDataRecords(AwsNameSpace.ELB, closeableAmazonCloudWatchClient.getClient(),
                                    cloudWatchMetric, loadBalancerName, DEFAULT_GROUP_NAME,
                                    CloudWatchDataCollectionInfo.builder()
                                        .awsConfig(config)
                                        .analysisComparisonStrategy(COMPARE_WITH_PREVIOUS)
                                        .build(),
                                    setupTestNodeData.getAppId(), setupTestNodeData.getFromTime(),
                                    setupTestNodeData.getToTime(), thirdPartyApiCallLog, false, new HashMap<>()));
            }));
      }

      // Fetch EC2 Metrics
      if (!isEmpty(setupTestNodeData.getEc2Metrics())) {
        setupTestNodeData.getEc2Metrics().forEach(cloudWatchMetric
            -> callables.add(()
                                 -> getMetricDataRecords(AwsNameSpace.EC2, closeableAmazonCloudWatchClient.getClient(),
                                     cloudWatchMetric, hostName, DEFAULT_GROUP_NAME,
                                     CloudWatchDataCollectionInfo.builder()
                                         .awsConfig(config)
                                         .analysisComparisonStrategy(COMPARE_WITH_PREVIOUS)
                                         .build(),
                                     setupTestNodeData.getAppId(), setupTestNodeData.getFromTime(),
                                     setupTestNodeData.getToTime(), thirdPartyApiCallLog, false, new HashMap<>())));
      }

      // Fetch ECS Metrics
      if (!isEmpty(setupTestNodeData.getEcsMetrics())) {
        setupTestNodeData.getEcsMetrics().forEach(
            (clusterName, cloudWatchMetrics) -> cloudWatchMetrics.forEach(cloudWatchMetric -> {
              callables.add(()
                                -> getMetricDataRecords(AwsNameSpace.ECS, closeableAmazonCloudWatchClient.getClient(),
                                    cloudWatchMetric, clusterName, DEFAULT_GROUP_NAME,
                                    CloudWatchDataCollectionInfo.builder()
                                        .awsConfig(config)
                                        .analysisComparisonStrategy(COMPARE_WITH_PREVIOUS)
                                        .build(),
                                    setupTestNodeData.getAppId(), setupTestNodeData.getFromTime(),
                                    setupTestNodeData.getToTime(), thirdPartyApiCallLog, false, new HashMap<>()));
            }));
      }

      // Fetch Lambda Metrics
      if (!isEmpty(setupTestNodeData.getLambdaFunctionsMetrics())) {
        setupTestNodeData.getLambdaFunctionsMetrics().forEach(
            (clusterName, cloudWatchMetrics) -> cloudWatchMetrics.forEach(cloudWatchMetric -> {
              callables.add(
                  ()
                      -> getMetricDataRecords(AwsNameSpace.LAMBDA, closeableAmazonCloudWatchClient.getClient(),
                          cloudWatchMetric, clusterName, DEFAULT_GROUP_NAME,
                          CloudWatchDataCollectionInfo.builder()
                              .awsConfig(config)
                              .analysisComparisonStrategy(COMPARE_WITH_PREVIOUS)
                              .build(),
                          setupTestNodeData.getAppId(), setupTestNodeData.getFromTime(), setupTestNodeData.getToTime(),
                          thirdPartyApiCallLog, false, new HashMap<>()));
            }));
      }

      List<Optional<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> metricsResults =
          dataCollectionService.executeParrallel(callables);
      List<NewRelicMetricDataRecord> metricDataRecords = new ArrayList<>();

      metricsResults.forEach(metricDataRecordsOptional -> {
        if (metricDataRecordsOptional.isPresent()) {
          metricDataRecords.addAll(metricDataRecordsOptional.get().values());
        }
      });

      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder()
                            .loadResponse(metricDataRecords)
                            .isLoadPresent(!metricDataRecords.isEmpty())
                            .build())
          .dataForNode(metricDataRecords)
          .build();
    } catch (Exception e) {
      log.error("Exception getMetricsWithDataForNode", e);
      throw new InvalidRequestException(io.harness.exception.ExceptionUtils.getMessage(e), e);
    }
  }

  public TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricDataRecords(AwsNameSpace awsNameSpace,
      AmazonCloudWatchClient cloudWatchClient, CloudWatchMetric cloudWatchMetric, String dimensionValue,
      String groupName, CloudWatchDataCollectionInfo dataCollectionInfo, String appId, long startTime, long endTime,
      ThirdPartyApiCallLog apiCallLog, boolean is247Task, Map<String, Long> hostStartTimeMap) {
    TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv = TreeBasedTable.create();
    switch (dataCollectionInfo.getAnalysisComparisonStrategy()) {
      case COMPARE_WITH_PREVIOUS:
        fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, dimensionValue, groupName,
            startTime, endTime, appId, dataCollectionInfo, rv, apiCallLog.copy(), COMPARE_WITH_PREVIOUS, is247Task,
            hostStartTimeMap);
        break;
      case COMPARE_WITH_CURRENT:
        switch (awsNameSpace) {
          case EC2:
            fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, dimensionValue, groupName,
                startTime, endTime, appId, dataCollectionInfo, rv, apiCallLog, COMPARE_WITH_CURRENT, is247Task,
                hostStartTimeMap);
            break;
          case ELB:
            for (int i = 0; i <= CANARY_DAYS_TO_COLLECT; i++) {
              String hostName = i == 0 ? TEST_HOST_NAME : CONTROL_HOST_NAME + "-" + i;
              endTime = endTime - TimeUnit.DAYS.toMillis(i);
              startTime = startTime - TimeUnit.DAYS.toMillis(i);
              hostStartTimeMap.put(hostName, startTime);
              fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, hostName, groupName,
                  startTime, endTime, appId, dataCollectionInfo, rv, apiCallLog, COMPARE_WITH_CURRENT, is247Task,
                  hostStartTimeMap);
            }
            break;
          case ECS:
            fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, dimensionValue, groupName,
                startTime, endTime, appId, dataCollectionInfo, rv, apiCallLog, COMPARE_WITH_CURRENT, is247Task,
                hostStartTimeMap);
            break;
          default:
            throw new WingsException("Invalid name space " + awsNameSpace);
        }
        break;
      case PREDICTIVE:
        if (is247Task) {
          long startTimeStamp = dataCollectionInfo.getStartTime();
          long endTimeStamp =
              dataCollectionInfo.getStartTime() + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionTime());

          fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, dimensionValue, groupName,
              startTimeStamp, endTimeStamp, appId, dataCollectionInfo, rv, apiCallLog, PREDICTIVE, is247Task,
              hostStartTimeMap);
        } else {
          fetchMetrics(awsNameSpace, cloudWatchClient, cloudWatchMetric, dimensionValue, dimensionValue, groupName,
              endTime - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES + DURATION_TO_ASK_MINUTES), endTime, appId,
              dataCollectionInfo, rv, apiCallLog, PREDICTIVE, is247Task, hostStartTimeMap);
        }
        break;
      default:
        throw new WingsException("Invalid strategy " + dataCollectionInfo.getAnalysisComparisonStrategy());
    }
    return rv;
  }

  private void fetchMetrics(AwsNameSpace awsNameSpace, AmazonCloudWatchClient cloudWatchClient,
      CloudWatchMetric cloudWatchMetric, String dimensionValue, String host, String groupName, long startTime,
      long endTime, String appId, CloudWatchDataCollectionInfo dataCollectionInfo,
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv, ThirdPartyApiCallLog apiCallLog,
      AnalysisComparisonStrategy analysisComparisonStrategy, boolean is247Task, Map<String, Long> hostStartTimeMap) {
    apiCallLog.setTitle("Fetching metric data from " + cloudWatchClient.getServiceName());
    apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    GetMetricStatisticsRequest metricStatisticsRequest = new GetMetricStatisticsRequest();
    metricStatisticsRequest.withNamespace(awsNameSpace.getNameSpace())
        .withMetricName(cloudWatchMetric.getMetricName())
        .withDimensions(new Dimension().withName(cloudWatchMetric.getDimension()).withValue(dimensionValue))
        .withStartTime(new Date(startTime))
        .withEndTime(new Date(endTime))
        .withPeriod(60)
        .withStatistics(cloudWatchMetric.getStatistics());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("body")
                                     .value(JsonUtils.asJson(metricStatisticsRequest))
                                     .type(FieldType.JSON)
                                     .build());

    GetMetricStatisticsResult metricStatistics;
    try {
      metricStatistics = cloudWatchClient.getMetricStatistics(metricStatisticsRequest);
    } catch (Exception e) {
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
      delegateLogService.save(dataCollectionInfo.getAwsConfig().getAccountId(), apiCallLog);
      throw new WingsException(
          "Unsuccessful response while fetching data from cloud watch. Error message: " + e.getMessage());
    }
    apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
    apiCallLog.addFieldToResponse(
        metricStatistics.getSdkHttpMetadata().getHttpStatusCode(), metricStatistics, FieldType.JSON);
    delegateLogService.save(dataCollectionInfo.getAwsConfig().getAccountId(), apiCallLog);
    List<Datapoint> datapoints = metricStatistics.getDatapoints();
    String metricName = awsNameSpace == AwsNameSpace.EC2 ? "EC2 Metrics/" + dimensionValue : dimensionValue;
    String hostNameForRecord = awsNameSpace == AwsNameSpace.LAMBDA ? CVConstants.LAMBDA_HOST_NAME : host;
    datapoints.forEach(datapoint -> {
      NewRelicMetricDataRecord newRelicMetricDataRecord =
          NewRelicMetricDataRecord.builder()
              .stateType(DelegateStateType.CLOUD_WATCH)
              .appId(appId)
              .name(metricName)
              .workflowId(dataCollectionInfo.getWorkflowId())
              .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
              .serviceId(dataCollectionInfo.getServiceId())
              .cvConfigId(dataCollectionInfo.getCvConfigId())
              .stateExecutionId(dataCollectionInfo.getStateExecutionId())
              .timeStamp(datapoint.getTimestamp().getTime())
              .dataCollectionMinute(getCollectionMinute(datapoint.getTimestamp().getTime(), analysisComparisonStrategy,
                  false, is247Task, startTime, dataCollectionInfo.getDataCollectionMinute(),
                  dataCollectionInfo.getCollectionTime(), host, hostStartTimeMap))
              .host(hostNameForRecord)
              .groupName(groupName)
              .tag(awsNameSpace.name())
              .values(new HashMap<>())
              .build();
      double value = getDataPointValue(cloudWatchMetric, datapoint);

      newRelicMetricDataRecord.getValues().put(cloudWatchMetric.getMetricName(), value);

      rv.put(dimensionValue, datapoint.getTimestamp().getTime(), newRelicMetricDataRecord);
    });
  }

  private double getDataPointValue(CloudWatchMetric cloudWatchMetric, Datapoint datapoint) {
    double value = 0;
    switch (cloudWatchMetric.getStatistics()) {
      case "Sum":
        value = datapoint.getSum();
        break;
      case "Average":
        value = datapoint.getAverage();
        break;
      default:
        unhandled(cloudWatchMetric.getStatistics());
    }

    if (!cloudWatchMetric.getUnit().name().equals(datapoint.getUnit())) {
      switch (StandardUnit.valueOf(datapoint.getUnit())) {
        case Bytes:
          switch (cloudWatchMetric.getUnit()) {
            case Kilobytes:
              value = value / CVConstants.KB;
              break;
            case Megabytes:
              value = value / CVConstants.MB;
              break;
            case Gigabytes:
              value = value / CVConstants.GB;
              break;
            default:
              unhandled(cloudWatchMetric.getUnit());
          }
          break;
        case Seconds:
          switch (cloudWatchMetric.getUnit()) {
            case Microseconds:
              value = value * TimeUnit.SECONDS.toMicros(1);
              break;
            case Milliseconds:
              value = value * TimeUnit.SECONDS.toMillis(1);
              break;
            default:
              unhandled(cloudWatchMetric.getUnit());
          }
          break;
        default:
          unhandled(StandardUnit.valueOf(datapoint.getUnit()));
      }
    }
    return value;
  }

  public int getCollectionMinute(final long metricTimeStamp, AnalysisComparisonStrategy analysisComparisonStrategy,
      boolean isHeartbeat, boolean is247Task, long startTime, int dataCollectionMinute, int collectionTime, String host,
      Map<String, Long> hostStartTimeMap) {
    boolean isPredictiveAnalysis = analysisComparisonStrategy == PREDICTIVE;
    int collectionMinute;
    if (isHeartbeat) {
      if (is247Task) {
        collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(startTime) + collectionTime;
      } else if (isPredictiveAnalysis) {
        collectionMinute = dataCollectionMinute + PREDECTIVE_HISTORY_MINUTES + DURATION_TO_ASK_MINUTES;
      } else {
        collectionMinute = dataCollectionMinute;
      }
    } else {
      if (is247Task) {
        collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp);
      } else {
        long collectionStartTime;
        if (isPredictiveAnalysis) {
          collectionStartTime = startTime - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES);
        } else {
          // This condition is needed as in case of COMPARE_WITH_CURRENT we keep track of startTime for each host.
          if (hostStartTimeMap.containsKey(host)) {
            collectionStartTime = hostStartTimeMap.get(host);
          } else {
            return dataCollectionMinute;
          }
        }
        collectionMinute = (int) (TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp - collectionStartTime));
      }
    }
    return collectionMinute;
  }
}
