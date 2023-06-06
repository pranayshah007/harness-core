/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.LOAD_TEST_BASELINE_NODE_IDENTIFIER;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.LOAD_TEST_CURRENT_NODE_IDENTIFIER;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.areMetricsFromCVConfigFilteredOut;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.convertTimeSeriesRecordDtosListToMap;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.filterUnhealthyMetricsAnalyses;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getFilteredAnalysedTestDataNodes;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getFilteredMetricCVConfigs;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getHealthSourceFromCVConfig;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getMetricTypeFromCvConfigAndMetricDefinition;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getThresholdsFromDefinition;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getTransactionGroupFromCVConfig;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isAnalysisResultIncluded;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isTransactionGroupIncluded;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.parseTestNodeIdentifiersFromDeploymentTimeSeriesAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.populateRawMetricDataInMetricAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.populateTimestampsForNormalisedData;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.sortMetricsAnalysisResults;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.transformAnalysisResultsAndReasonsforSimpleVerification;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.TimeSeriesAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostData;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostInfo;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData;
import io.harness.cvng.analysis.beans.NodeRiskCountDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.beans.TransactionMetricInfo;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis.DeploymentTimeSeriesAnalysisKeys;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordGroupValue;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord.TimeSeriesDataRecordMetricValue;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysisOverview;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.DeeplinkURLService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils;
import io.harness.cvng.verificationjob.entities.SimpleVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.persistence.HPersistence;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PageUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class DeploymentTimeSeriesAnalysisServiceImpl implements DeploymentTimeSeriesAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private NextGenService nextGenService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private DeeplinkURLService deeplinkURLService;

  @Override
  public void save(DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis) {
    hPersistence.save(deploymentTimeSeriesAnalysis);
  }

  @Override
  public TransactionMetricInfoSummaryPageDTO getMetrics(String accountId, String verificationJobInstanceId,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, PageParams pageParams) {
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalysis =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    if (isEmpty(latestDeploymentTimeSeriesAnalysis)) {
      return TransactionMetricInfoSummaryPageDTO.builder()
          .pageResponse(PageUtils.offsetAndLimit(Collections.emptyList(), pageParams.getPage(), pageParams.getSize()))
          .build();
    }
    TimeRange deploymentTimeRange = TimeRange.builder()
                                        .startTime(verificationJobInstance.getStartTime())
                                        .endTime(latestDeploymentTimeSeriesAnalysis.get(0).getEndTime())
                                        .build();
    List<TransactionMetricInfo> transactionMetricInfoList =
        getMetrics(accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);

    if (deploymentTimeSeriesAnalysisFilter.filterByFilter()) {
      transactionMetricInfoList =
          transactionMetricInfoList.stream()
              .filter(transactionMetricInfo
                  -> transactionMetricInfo.getTransactionMetric().getMetricName().toLowerCase().contains(
                         deploymentTimeSeriesAnalysisFilter.getFilter().toLowerCase())
                      || transactionMetricInfo.getTransactionMetric().getTransactionName().toLowerCase().contains(
                          deploymentTimeSeriesAnalysisFilter.getFilter().toLowerCase()))
              .collect(Collectors.toList());
    }
    if (deploymentTimeSeriesAnalysisFilter.filterByTransactionNames()) {
      transactionMetricInfoList = transactionMetricInfoList.stream()
                                      .filter(transactionMetricInfo
                                          -> deploymentTimeSeriesAnalysisFilter.getTransactionNames().contains(
                                              transactionMetricInfo.getTransactionMetric().getTransactionName()))
                                      .collect(Collectors.toList());
    }
    return TransactionMetricInfoSummaryPageDTO.builder()
        .pageResponse(PageUtils.offsetAndLimit(transactionMetricInfoList, pageParams.getPage(), pageParams.getSize()))
        .deploymentTimeRange(deploymentTimeRange)
        .deploymentStartTime(deploymentTimeRange.getStartTime().toEpochMilli())
        .deploymentEndTime(deploymentTimeRange.getEndTime().toEpochMilli())
        .build();
  }

  @Override
  public TimeSeriesAnalysisSummary getAnalysisSummary(List<String> verificationJobInstanceIds) {
    Preconditions.checkNotNull(
        verificationJobInstanceIds, "Missing verificationJobInstanceIds when looking for summary");
    List<Integer> anomMetricCounts = new ArrayList<>();
    List<Integer> totalMetricCounts = new ArrayList<>();
    verificationJobInstanceIds.forEach(verificationJobInstanceId -> {
      VerificationJobInstance verificationJobInstance =
          verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
      List<TransactionMetricInfo> transactionMetricInfoList = getMetrics(verificationJobInstance.getAccountId(),
          verificationJobInstanceId, DeploymentTimeSeriesAnalysisFilter.builder().build());
      int numAnomMetrics = 0, totalMetrics = 0;
      for (TransactionMetricInfo transactionMetricInfo : transactionMetricInfoList) {
        if (transactionMetricInfo.getTransactionMetric().getRisk().isGreaterThan(Risk.HEALTHY)) {
          numAnomMetrics++;
        }
        totalMetrics++;
      }
      anomMetricCounts.add(numAnomMetrics);
      totalMetricCounts.add(totalMetrics);
    });

    return TimeSeriesAnalysisSummary.builder()
        .numAnomMetrics(anomMetricCounts.stream().mapToInt(Integer::intValue).sum())
        .totalNumMetrics(totalMetricCounts.stream().mapToInt(Integer::intValue).sum())
        .build();
  }

  @Override
  public MetricsAnalysisOverview getMetricsAnalysisOverview(String verifyStepExecutionId) {
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verifyStepExecutionId);
    List<TransactionMetricInfo> transactionMetricInfos = getMetrics(verificationJobInstance.getAccountId(),
        verifyStepExecutionId, DeploymentTimeSeriesAnalysisFilter.builder().build());
    int noAnalysis = 0;
    int healthy = 0;
    int warning = 0;
    int unhealthy = 0;
    for (TransactionMetricInfo transactionMetricInfo : transactionMetricInfos) {
      if (Objects.isNull(transactionMetricInfo.getTransactionMetric().getRisk())) {
        noAnalysis++;
        continue;
      }
      switch (transactionMetricInfo.getTransactionMetric().getRisk()) {
        case NO_DATA:
          noAnalysis++;
          break;
        case NO_ANALYSIS:
          noAnalysis++;
          break;
        case HEALTHY:
          healthy++;
          break;
        case OBSERVE:
          warning++;
          break;
        case NEED_ATTENTION:
          warning++;
          break;
        case UNHEALTHY:
          unhealthy++;
          break;
        default:
          throw new IllegalArgumentException(
              "Unrecognised Risk " + transactionMetricInfo.getTransactionMetric().getRisk());
      }
    }

    return MetricsAnalysisOverview.builder()
        .noAnalysis(noAnalysis)
        .healthy(healthy)
        .warning(warning)
        .unhealthy(unhealthy)
        .build();
  }

  @Override
  public String getTimeSeriesDemoTemplate(String verificationTaskId) {
    List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalyses =
        hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class)
            .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, verificationTaskId)
            .asList();
    return JsonUtils.asJson(deploymentTimeSeriesAnalyses);
  }

  @Override
  public void addDemoAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, String demoTemplatePath) {
    try {
      String template = Resources.toString(this.getClass().getResource(demoTemplatePath), Charsets.UTF_8);
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalyses =
          JsonUtils.asObject(template, new TypeReference<List<DeploymentTimeSeriesAnalysis>>() {});
      Collections.sort(deploymentTimeSeriesAnalyses, Comparator.comparing(DeploymentTimeSeriesAnalysis::getStartTime));
      Instant lastStartTime = deploymentTimeSeriesAnalyses.get(0).getStartTime();
      int minute = 0;
      for (DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis : deploymentTimeSeriesAnalyses) {
        deploymentTimeSeriesAnalysis.setVerificationTaskId(verificationTaskId);
        if (!lastStartTime.equals(deploymentTimeSeriesAnalysis.getStartTime())) {
          lastStartTime = deploymentTimeSeriesAnalysis.getStartTime();
          minute++;
        }
        deploymentTimeSeriesAnalysis.setStartTime(
            verificationJobInstance.getStartTime().plus(Duration.ofMinutes(minute)));
        deploymentTimeSeriesAnalysis.setEndTime(
            deploymentTimeSeriesAnalysis.getStartTime().plus(Duration.ofMinutes(1)));
      }
      hPersistence.save(deploymentTimeSeriesAnalyses);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void addDemoMetricsAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, ActivityVerificationStatus activityVerificationStatus) {
    MetricCVConfig<? extends AnalysisInfo> metricCVConfig = (MetricCVConfig<? extends AnalysisInfo>) cvConfig;
    boolean isCustomMetric = CollectionUtils.isNotEmpty(metricCVConfig.getMetricInfos());
    Duration verificationDuration = verificationJobInstance.getResolvedJob().getDuration();
    Instant testDataCollectionStartTime = verificationJobInstance.getStartTime();
    Instant testDataCollectionEndTime = testDataCollectionStartTime.plus(verificationDuration);
    TimeRange testDataCollectionTimeRange =
        TimeRange.builder().startTime(testDataCollectionStartTime).endTime(testDataCollectionEndTime).build();
    TimeRange controlDataCollectionTimeRange = null;
    AppliedDeploymentAnalysisType appliedDeploymentAnalysisType =
        AppliedDeploymentAnalysisType.fromVerificationJobType(verificationJobInstance.getResolvedJob().getType());
    List<String> controlHosts = new ArrayList<>();
    List<String> testHosts = new ArrayList<>();
    List<String> metricIdentifiers = new ArrayList<>();
    List<String> metricNames = new ArrayList<>();
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
      controlHosts.add("control-host-1");
      controlHosts.add("control-host-2");
      testHosts.add("test-host-1");
      testHosts.add("test-host-2");
      controlDataCollectionTimeRange = testDataCollectionTimeRange;
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.ROLLING) {
      controlHosts.add("control-host-1");
      controlHosts.add("control-host-2");
      testHosts.add("test-host-1");
      testHosts.add("test-host-2");
      controlDataCollectionTimeRange =
          TimeRange.builder()
              .startTime(verificationJobInstance.getDeploymentStartTime().minus(verificationDuration))
              .endTime(verificationJobInstance.getDeploymentStartTime())
              .build();
    } else {
      controlHosts.add("control-host-1");
      testHosts.add("test-host-1");
      Instant controlDataCollectionStartTime = verificationJobInstance.getStartTime().minus(1, ChronoUnit.DAYS);
      controlDataCollectionTimeRange = TimeRange.builder()
                                           .startTime(controlDataCollectionStartTime)
                                           .endTime(controlDataCollectionStartTime.plus(verificationDuration))
                                           .build();
    }
    List<String> transactionGroups = new ArrayList<>();
    if (isCustomMetric) {
      transactionGroups.add(metricCVConfig.maybeGetGroupName().orElse("transaction/group/1"));
      Set<AnalysisInfo> metricInfos = metricCVConfig.getMetricInfos()
                                          .stream()
                                          .filter(info -> info.getDeploymentVerification().isEnabled())
                                          .collect(Collectors.toSet());
      for (AnalysisInfo info : metricInfos) {
        metricIdentifiers.add(info.getIdentifier());
        metricNames.add(info.getMetricName());
      }

    } else {
      transactionGroups.add("transaction/group/1");
      transactionGroups.add("transaction/group/2");
      Set<MetricDefinition> metricDefinitions = metricCVConfig.getMetricPack()
                                                    .getMetrics()
                                                    .stream()
                                                    .filter(MetricDefinition::isIncluded)
                                                    .collect(Collectors.toSet());
      for (MetricDefinition metricDefinition : metricDefinitions) {
        metricIdentifiers.add(metricDefinition.getIdentifier());
        metricNames.add(metricDefinition.getName());
      }
    }

    int controlDataLowerBound = 20;
    int testDataLowerBound = 25;
    int controlDataUpperBound = 40;
    int testDataUpperBound = 45;
    Risk risk = Risk.HEALTHY;
    Double score = 0.5;
    if (activityVerificationStatus == ActivityVerificationStatus.VERIFICATION_FAILED) {
      testDataUpperBound *= 5;
      testDataLowerBound *= 5;
      risk = Risk.UNHEALTHY;
    }
    int numberOfNormalisationBuckets = (int) Math.ceil(verificationDuration.toMinutes() / 3);
    saveTimeSeriesRecords(controlHosts, transactionGroups, metricNames, metricIdentifiers,
        controlDataCollectionTimeRange, controlDataLowerBound, controlDataUpperBound, verificationJobInstance,
        verificationTaskId);
    saveTimeSeriesRecords(testHosts, transactionGroups, metricNames, metricIdentifiers, testDataCollectionTimeRange,
        testDataLowerBound, testDataUpperBound, verificationJobInstance, verificationTaskId);
    List<HostInfo> hostSummaries = new ArrayList<>();
    List<TransactionMetricHostData> transactionMetricSummaries = new ArrayList<>();

    for (String controlHost : controlHosts) {
      hostSummaries.add(HostInfo.builder().hostName(controlHost).primary(true).score(score).build());
    }
    for (String testHost : testHosts) {
      hostSummaries.add(HostInfo.builder().hostName(testHost).canary(true).risk(risk.getValue()).score(score).build());
    }
    Random random = new Random();
    for (int i = 0; i < metricIdentifiers.size(); ++i) {
      for (String transactionGroup : transactionGroups) {
        List<HostData> hostDataList = new ArrayList<>();
        for (int j = 0; j < testHosts.size(); ++j) {
          List<Double> controlData = new ArrayList<>();
          List<Double> testData = new ArrayList<>();
          for (int k = 0; k <= numberOfNormalisationBuckets; ++k) {
            controlData.add((double) getRandomNumberUsingNextInt(random, controlDataLowerBound, controlDataUpperBound));
            testData.add((double) getRandomNumberUsingNextInt(random, testDataLowerBound, testDataUpperBound));
          }
          HostData hostData = HostData.builder()
                                  .hostName(testHosts.get(j))
                                  .nearestControlHost(controlHosts.get(j))
                                  .controlData(controlData)
                                  .testData(testData)
                                  .score(score)
                                  .risk(risk.getValue())
                                  .build();
          hostDataList.add(hostData);
        }
        TransactionMetricHostData transactionMetricHostData = TransactionMetricHostData.builder()
                                                                  .hostData(hostDataList)
                                                                  .metricName(metricIdentifiers.get(i))
                                                                  .transactionName(transactionGroup)
                                                                  .risk(risk.getValue())
                                                                  .score(score)
                                                                  .build();

        transactionMetricSummaries.add(transactionMetricHostData);
      }
    }
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder()
            .startTime(testDataCollectionStartTime)
            .endTime(testDataCollectionEndTime)
            .accountId(verificationJobInstance.getAccountId())
            .risk(risk)
            .failFast(false)
            .verificationTaskId(verificationTaskId)
            .hostSummaries(hostSummaries)
            .transactionMetricSummaries(transactionMetricSummaries)
            .build();
    hPersistence.save(deploymentTimeSeriesAnalysis);
  }

  private void saveTimeSeriesRecords(List<String> hosts, List<String> transactionGroups, List<String> metricNames,
      List<String> metricIdentifiers, TimeRange timeRange, int lowerBound, int upperBound,
      VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    Random randomGenerator = new Random();
    List<TimeSeriesDataCollectionRecord> timeSeriesDataCollectionRecords = new ArrayList<>();
    for (String controlHost : hosts) {
      for (long timestamp = timeRange.getStartTime().toEpochMilli(); timestamp < timeRange.getEndTime().toEpochMilli();
           timestamp += 60000) {
        Set<TimeSeriesDataRecordMetricValue> metricValues = new HashSet<>();
        for (int i = 0; i < metricNames.size(); ++i) {
          Set<TimeSeriesDataRecordGroupValue> timeSeriesValues = new HashSet<>();
          for (String transactionGroup : transactionGroups) {
            timeSeriesValues.add(TimeSeriesDataRecordGroupValue.builder()
                                     .value(getRandomNumberUsingNextInt(randomGenerator, lowerBound, upperBound))
                                     .groupName(transactionGroup)
                                     .build());
          }
          TimeSeriesDataRecordMetricValue timeSeriesDataRecordMetricValue =
              TimeSeriesDataRecordMetricValue.builder()
                  .metricName(metricNames.get(i))
                  .metricIdentifier(metricIdentifiers.get(i))
                  .timeSeriesValues(timeSeriesValues)
                  .build();
          metricValues.add(timeSeriesDataRecordMetricValue);
        }

        TimeSeriesDataCollectionRecord timeSeriesDataCollectionRecord =
            TimeSeriesDataCollectionRecord.builder()
                .timeStamp(timestamp)
                .host(controlHost)
                .accountId(verificationJobInstance.getAccountId())
                .verificationTaskId(verificationTaskId)
                .metricValues(metricValues)
                .build();
        timeSeriesDataCollectionRecords.add(timeSeriesDataCollectionRecord);
      }
    }
    timeSeriesRecordService.save(timeSeriesDataCollectionRecords);
  }

  private int getRandomNumberUsingNextInt(Random random, int min, int max) {
    return random.nextInt(max - min) + min;
  }

  @Override
  public List<String> getTransactionNames(String accountId, String verificationJobInstanceId) {
    Set<String> transactionNameSet = new HashSet<>();
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().build();
    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalysis =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    for (DeploymentTimeSeriesAnalysis timeSeriesAnalysis : latestDeploymentTimeSeriesAnalysis) {
      timeSeriesAnalysis.getTransactionMetricSummaries().forEach(
          transactionMetricHostData -> transactionNameSet.add(transactionMetricHostData.getTransactionName()));
    }

    return new ArrayList<>(transactionNameSet);
  }

  @Override
  public Set<String> getNodeNames(String accountId, String verificationJobInstanceId) {
    Set<String> nodeNameSet = new HashSet<>();
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().build();
    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalysis =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    for (DeploymentTimeSeriesAnalysis timeSeriesAnalysis : latestDeploymentTimeSeriesAnalysis) {
      timeSeriesAnalysis.getTransactionMetricSummaries().forEach(
          transactionMetricHostData -> transactionMetricHostData.getHostData().forEach(hostData -> {
            if (hostData.getHostName().isPresent()) {
              nodeNameSet.add(hostData.getHostName().get());
            }
          }));
    }

    return new HashSet<>(nodeNameSet);
  }

  @Override
  public List<TransactionMetricInfo> getTransactionMetricInfos(String accountId, String verificationJobInstanceId) {
    return getMetrics(accountId, verificationJobInstanceId, DeploymentTimeSeriesAnalysisFilter.builder().build());
  }

  private List<TransactionMetricInfo> getMetrics(String accountId, String verificationJobInstanceId,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalysis =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);

    if (isEmpty(latestDeploymentTimeSeriesAnalysis)) {
      return Collections.emptyList();
    }

    Set<TransactionMetricInfo> transactionMetricInfoSet = new HashSet();
    for (DeploymentTimeSeriesAnalysis timeSeriesAnalysis : latestDeploymentTimeSeriesAnalysis) {
      VerificationTask verificationTask = verificationTaskService.get(timeSeriesAnalysis.getVerificationTaskId());
      Preconditions.checkNotNull(verificationTask.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT),
          "VerificationTask should be of Deployment type");
      CVConfig cvConfig = verificationJobInstanceService.getEmbeddedCVConfig(
          ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId(),
          ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId());

      String connectorName = getConnectorName(cvConfig);
      DataSourceType dataSourceType = cvConfig.getType();

      timeSeriesAnalysis.getTransactionMetricSummaries()
          .stream()
          .filter(transactionMetricHostData
              -> filterAnomalousMetrics(transactionMetricHostData,
                  deploymentTimeSeriesAnalysisFilter.filterByHostNames(),
                  deploymentTimeSeriesAnalysisFilter.isAnomalousMetricsOnly()))
          .forEach(transactionMetricHostData -> {
            Map<Risk, Integer> nodeCountByRiskStatusMap = new HashMap<>();
            SortedSet<HostData> nodeDataSet = new TreeSet();
            transactionMetricHostData.getHostData()
                .stream()
                .filter(hostData
                    -> filterHostData(hostData, deploymentTimeSeriesAnalysisFilter.getHostNames(),
                        deploymentTimeSeriesAnalysisFilter.isAnomalousMetricsOnly()))
                .filter(hostData
                    -> filterAnomalousNodes(hostData, deploymentTimeSeriesAnalysisFilter.isAnomalousNodesOnly()))
                .forEach(hostData -> {
                  nodeDataSet.add(hostData);
                  Risk risk = hostData.getRisk();
                  nodeCountByRiskStatusMap.put(risk, nodeCountByRiskStatusMap.getOrDefault(risk, 0) + 1);
                });
            TransactionMetricInfo transactionMetricInfo =
                TransactionMetricInfo.builder()
                    .transactionMetric(createTransactionMetric(transactionMetricHostData))
                    .connectorName(connectorName)
                    .dataSourceType(dataSourceType)
                    .nodes(nodeDataSet)
                    .nodeRiskCountDTO(getNodeRiskCountDTO(nodeCountByRiskStatusMap))
                    .build();
            if (isNotEmpty(nodeDataSet)) {
              transactionMetricInfoSet.add(transactionMetricInfo);
            }
          });
    }

    List<TransactionMetricInfo> transactionMetricInfoList = new ArrayList<>(transactionMetricInfoSet);
    Collections.sort(transactionMetricInfoList);
    return transactionMetricInfoList;
  }

  @Override
  public List<MetricsAnalysis> getFilteredMetricAnalysesForVerifyStepExecutionId(String accountId,
      String verifyStepExecutionId, DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verifyStepExecutionId);

    Map<String, Map<String, MetricsAnalysis>> mapOfCvConfigIdAndFilteredMetrics =
        getMapOfCvConfigIdAndFilteredMetrics(verificationJobInstance, deploymentTimeSeriesAnalysisFilter);

    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalyses =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verifyStepExecutionId, deploymentTimeSeriesAnalysisFilter);
    for (DeploymentTimeSeriesAnalysis timeSeriesAnalysis : latestDeploymentTimeSeriesAnalyses) {
      String verificationTaskId = timeSeriesAnalysis.getVerificationTaskId();
      VerificationTask verificationTask = verificationTaskService.get(verificationTaskId);
      Preconditions.checkArgument(verificationTask.getTaskInfo().getTaskType() == TaskType.DEPLOYMENT,
          "VerificationTask should be of Deployment type");

      String cvConfigIdForThisAnalysis = ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId();
      Map<String, MetricsAnalysis> metricsForThisAnalysis =
          mapOfCvConfigIdAndFilteredMetrics.get(cvConfigIdForThisAnalysis);

      MetricCVConfig<? extends AnalysisInfo> cvConfig =
          (MetricCVConfig<? extends AnalysisInfo>) verificationJobInstance.getCvConfigMap().get(
              cvConfigIdForThisAnalysis);
      boolean isCustomMetricPackBasedCvConfig = CollectionUtils.isNotEmpty(cvConfig.getMetricInfos());

      if (isCustomMetricPackBasedCvConfig && areMetricsFromCVConfigFilteredOut(metricsForThisAnalysis)) {
        continue;
      }
      Map<String, MetricsAnalysis> metricAnalysisDefinitionsForDefaultMetrics =
          getMetricAnalysesFromMetricDefinitions(cvConfig);

      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType =
          getAppliedDeploymentAnalysisType(verificationJobInstance.getUuid(), verificationTaskId);

      Optional<TimeRange> controlDataTimeRange =
          getControlDataTimeRange(appliedDeploymentAnalysisType, verificationJobInstance, timeSeriesAnalysis);
      TimeRange testDataTimeRange = getTestDataTimeRange(verificationJobInstance, timeSeriesAnalysis);

      Map<String, Map<String, List<TimeSeriesRecordDTO>>> controlNodesRawData =
          getControlNodesRawData(appliedDeploymentAnalysisType, verificationJobInstance, verificationTaskId,
              timeSeriesAnalysis, controlDataTimeRange);

      Map<String, Map<String, List<TimeSeriesRecordDTO>>> testNodesRawData =
          getTestNodesRawData(appliedDeploymentAnalysisType, verificationTaskId, timeSeriesAnalysis, testDataTimeRange);

      for (TransactionMetricHostData transactionMetricHostData : timeSeriesAnalysis.getTransactionMetricSummaries()) {
        // LE metricName is BE metricIdentifier
        String metricIdentifier = transactionMetricHostData.getMetricName();
        String transactionGroup = transactionMetricHostData.getTransactionName();
        AnalysisResult analysisResult = AnalysisResult.fromRisk(transactionMetricHostData.getRisk());
        if (isAnalysisResultIncluded(deploymentTimeSeriesAnalysisFilter, analysisResult)) {
          MetricsAnalysis metricsAnalysis = metricsForThisAnalysis.get(metricIdentifier);
          if (!isCustomMetricPackBasedCvConfig
              && isTransactionGroupIncluded(deploymentTimeSeriesAnalysisFilter, transactionGroup)) {
            MetricsAnalysis metricAnalysisDefinition = metricAnalysisDefinitionsForDefaultMetrics.get(metricIdentifier);
            metricsAnalysis = MetricsAnalysis.builder()
                                  .metricIdentifier(metricIdentifier)
                                  .transactionGroup(transactionGroup)
                                  .metricName(metricAnalysisDefinition.getMetricName())
                                  .healthSource(metricAnalysisDefinition.getHealthSource())
                                  .metricType(metricAnalysisDefinition.getMetricType())
                                  .thresholds(metricAnalysisDefinition.getThresholds())
                                  .build();
            metricsForThisAnalysis.put(UUIDGenerator.generateUuid(), metricsAnalysis);
          }
          if (Objects.nonNull(metricsAnalysis)) {
            if (Objects.isNull(metricsAnalysis.getTransactionGroup())) {
              // Only applicable where the metric is neither custom nor default-metric-pack metric, ie dashboard based
              // metrics. eg Datadog , GCP
              metricsAnalysis.setTransactionGroup(transactionGroup);
            }
            Optional<String> deeplinkURL = deeplinkURLService.buildDeeplinkURLFromCVConfig(
                cvConfig, metricIdentifier, testDataTimeRange.getStartTime(), testDataTimeRange.getEndTime());
            deeplinkURL.ifPresent(metricsAnalysis::setDeeplinkURL);
            metricsAnalysis.setAnalysisResult(analysisResult);
            metricsAnalysis.setTestDataNodes(getFilteredAnalysedTestDataNodes(
                transactionMetricHostData, deploymentTimeSeriesAnalysisFilter, metricsAnalysis.getThresholds()));
            populateTimestampsForNormalisedData(metricsAnalysis, controlDataTimeRange, testDataTimeRange);
            populateRawMetricDataInMetricAnalysis(appliedDeploymentAnalysisType,
                controlNodesRawData.getOrDefault(metricIdentifier, Collections.emptyMap()),
                testNodesRawData.getOrDefault(metricIdentifier, Collections.emptyMap()), metricsAnalysis);
          }
        } else {
          metricsForThisAnalysis.remove(metricIdentifier);
        }
      }
    }
    List<MetricsAnalysis> metricsAnalyses = new ArrayList<>();
    for (Map<String, MetricsAnalysis> map : mapOfCvConfigIdAndFilteredMetrics.values()) {
      if (deploymentTimeSeriesAnalysisFilter.isAnomalousMetricsOnly()) {
        map = filterUnhealthyMetricsAnalyses(map);
      }
      metricsAnalyses.addAll(map.values());
    }
    sortMetricsAnalysisResults(metricsAnalyses);
    if (verificationJobInstance.getResolvedJob() instanceof SimpleVerificationJob) {
      transformAnalysisResultsAndReasonsforSimpleVerification(metricsAnalyses);
    }
    return metricsAnalyses;
  }

  public Map<String, Map<String, MetricsAnalysis>> getMapOfCvConfigIdAndFilteredMetrics(
      VerificationJobInstance verificationJobInstance,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    List<MetricCVConfig<? extends AnalysisInfo>> filteredMetricCVConfigs =
        getFilteredMetricCVConfigs(verificationJobInstance, deploymentTimeSeriesAnalysisFilter);
    Map<String, Map<String, MetricsAnalysis>> mapOfCvConfigIdAndFilteredMetrics = new HashMap<>();
    for (MetricCVConfig<? extends AnalysisInfo> metricCVConfig : filteredMetricCVConfigs) {
      Map<String, MetricsAnalysis> mapOfMetricIdAndMetricsAnalyses = getMetricsFromCvConfig(metricCVConfig);
      mapOfCvConfigIdAndFilteredMetrics.put(metricCVConfig.getUuid(), mapOfMetricIdAndMetricsAnalyses);
    }
    return mapOfCvConfigIdAndFilteredMetrics;
  }

  private Map<String, MetricsAnalysis> getMetricsFromCvConfig(MetricCVConfig<? extends AnalysisInfo> metricCVConfig) {
    HealthSource healthSource = getHealthSourceFromCVConfig(metricCVConfig);
    String transactionGroup = getTransactionGroupFromCVConfig(metricCVConfig);
    List<MetricsAnalysis> metricsAnalyses = null;
    if (CollectionUtils.isNotEmpty(metricCVConfig.getMetricInfos())) {
      metricsAnalyses = getMetricAnalysesFromMetricInfos(metricCVConfig, healthSource, transactionGroup);
    }
    return CollectionUtils.emptyIfNull(metricsAnalyses)
        .stream()
        .collect(Collectors.toMap(
            MetricsAnalysis::getMetricIdentifier, metricsAnalysis -> metricsAnalysis, (existing, current) -> current));
  }

  private Map<String, MetricsAnalysis> getMetricAnalysesFromMetricDefinitions(
      MetricCVConfig<? extends AnalysisInfo> metricCVConfig) {
    HealthSource healthSource = getHealthSourceFromCVConfig(metricCVConfig);
    return metricCVConfig.getMetricPack()
        .getMetrics()
        .stream()
        .filter(MetricDefinition::isIncluded)
        .map(metricDefinition
            -> MetricsAnalysis.builder()
                   .metricName(metricDefinition.getName())
                   .metricIdentifier(metricDefinition.getIdentifier())
                   .healthSource(healthSource)
                   .metricType(getMetricTypeFromCvConfigAndMetricDefinition(metricCVConfig, metricDefinition))
                   .thresholds(getThresholdsFromDefinition(metricDefinition))
                   .build())
        .collect(
            Collectors.toMap(MetricsAnalysis::getMetricIdentifier, metricsAnalysis -> metricsAnalysis, (u, v) -> v));
  }

  private List<MetricsAnalysis> getMetricAnalysesFromMetricInfos(
      MetricCVConfig<? extends AnalysisInfo> metricCVConfig, HealthSource healthSource, String transactionGroup) {
    Map<String, MetricDefinition> metricDefinitions = metricCVConfig.getMetricPack().getMetrics().stream().collect(
        Collectors.toMap(MetricDefinition::getIdentifier, metricDefinition -> metricDefinition, (u, v) -> v));
    return metricCVConfig.getMetricInfos()
        .stream()
        .filter(VerifyStepMetricsAnalysisUtils::isDeploymentVerificationEnabledForThisMetric)
        .map(metricInfo -> {
          return MetricsAnalysis.builder()
              .metricName(metricInfo.getMetricName())
              .metricIdentifier(metricInfo.getIdentifier())
              .healthSource(healthSource)
              .transactionGroup(transactionGroup)
              .metricType(getMetricTypeFromCvConfigAndMetricDefinition(
                  metricCVConfig, metricDefinitions.get(metricInfo.getIdentifier())))
              .thresholds(getThresholdsFromDefinition(metricDefinitions.get(metricInfo.getIdentifier())))
              .analysisResult(AnalysisResult.NO_ANALYSIS)
              .build();
        })
        .collect(Collectors.toList());
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawData(
      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType, VerificationJobInstance verificationJobInstance,
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis, Optional<TimeRange> timeRange) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST) {
      return getControlNodesRawDataForLoadTestAnalysis(verificationJobInstance, verificationTaskId, timeRange);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
      return getControlNodesRawDataForCanaryAnalysis(verificationTaskId, timeSeriesAnalysis, timeRange);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.ROLLING) {
      return getControlNodesRawDataForRollingAnalysis(verificationTaskId, timeSeriesAnalysis, timeRange);
    } else {
      return Collections.emptyMap();
    }
  }

  private Optional<TimeRange> getControlDataTimeRange(AppliedDeploymentAnalysisType appliedDeploymentAnalysisType,
      VerificationJobInstance verificationJobInstance, DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST) {
      return getControlDataTimeRangeForLoadTestAnalysis(verificationJobInstance);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
      return getControlDataTimeRangeForCanaryAnalysis(verificationJobInstance, timeSeriesAnalysis);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.ROLLING) {
      return getControlDataTimeRangeForRollingAnalysis(verificationJobInstance);
    } else {
      return Optional.empty();
    }
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawData(
      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType, String verificationTaskId,
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis, TimeRange testDataTimeRange) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST
        || appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.SIMPLE) {
      return getTestNodesRawDataForLoadTestAnalysis(verificationTaskId, testDataTimeRange);
    } else {
      return getTestNodesRawDataForCanaryAndRollingAnalysis(verificationTaskId, timeSeriesAnalysis, testDataTimeRange);
    }
  }

  private TimeRange getTestDataTimeRange(
      VerificationJobInstance verificationJobInstance, DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    return TimeRange.builder()
        .startTime(verificationJobInstance.getStartTime())
        .endTime(timeSeriesAnalysis.getEndTime())
        .build();
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawDataForCanaryAndRollingAnalysis(
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis, TimeRange testDataTimeRange) {
    Set<String> testNodes = parseTestNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, testDataTimeRange.getStartTime(), testDataTimeRange.getEndTime(), testNodes);

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawDataForLoadTestAnalysis(
      String verificationTaskId, TimeRange testDataTimeRange) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, testDataTimeRange.getStartTime(), testDataTimeRange.getEndTime(), null);

    CollectionUtils.emptyIfNull(timeSeriesRecordDtos)
        .forEach(timeSeriesRecordDTO -> timeSeriesRecordDTO.setHost(LOAD_TEST_CURRENT_NODE_IDENTIFIER));

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private AppliedDeploymentAnalysisType getAppliedDeploymentAnalysisType(
      String verificationJobInstanceId, String verificationTaskId) {
    return verificationJobInstanceService.getAppliedDeploymentAnalysisTypeByVerificationTaskId(
        verificationJobInstanceId, verificationTaskId);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawDataForLoadTestAnalysis(
      VerificationJobInstance verificationJobInstance, String verificationTaskId, Optional<TimeRange> timeRange) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = null;
    TestVerificationJob verificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = verificationJob.getBaselineVerificationJobInstanceId();
    if (StringUtils.isNotBlank(baselineVerificationJobInstanceId)) {
      Optional<String> baselineVerificationTaskId =
          verificationTaskService.findBaselineVerificationTaskId(verificationTaskId, verificationJobInstance);
      if (baselineVerificationTaskId.isPresent()) {
        Preconditions.checkState(timeRange.isPresent(), "Time range must not be null");
        timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
            baselineVerificationTaskId.get(), timeRange.get().getStartTime(), timeRange.get().getEndTime(), null);
      }
    }

    CollectionUtils.emptyIfNull(timeSeriesRecordDtos)
        .forEach(timeSeriesRecordDTO -> timeSeriesRecordDTO.setHost(LOAD_TEST_BASELINE_NODE_IDENTIFIER));

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Optional<TimeRange> getControlDataTimeRangeForLoadTestAnalysis(
      VerificationJobInstance verificationJobInstance) {
    TimeRange timeRange = null;
    TestVerificationJob verificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = verificationJob.getBaselineVerificationJobInstanceId();
    if (StringUtils.isNotBlank(baselineVerificationJobInstanceId)) {
      VerificationJobInstance baselineVerificationJobInstance =
          verificationJobInstanceService.getVerificationJobInstance(baselineVerificationJobInstanceId);
      timeRange = TimeRange.builder()
                      .startTime(baselineVerificationJobInstance.getStartTime())
                      .endTime(baselineVerificationJobInstance.getEndTime())
                      .build();
    }
    return Optional.ofNullable(timeRange);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawDataForCanaryAnalysis(
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis, Optional<TimeRange> timeRange) {
    Preconditions.checkState(timeRange.isPresent(), "Time range must not be null");
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos;
    Set<String> controlNodes = parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
    timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, timeRange.get().getStartTime(), timeRange.get().getEndTime(), controlNodes);

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Optional<TimeRange> getControlDataTimeRangeForCanaryAnalysis(
      VerificationJobInstance verificationJobInstance, DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    return Optional.of(TimeRange.builder()
                           .startTime(verificationJobInstance.getStartTime())
                           .endTime(timeSeriesAnalysis.getEndTime())
                           .build());
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawDataForRollingAnalysis(
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis, Optional<TimeRange> timeRange) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = null;
    if (timeRange.isPresent()) {
      Set<String> controlNodes = parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
      timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
          verificationTaskId, timeRange.get().getStartTime(), timeRange.get().getEndTime(), controlNodes);
    }

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Optional<TimeRange> getControlDataTimeRangeForRollingAnalysis(
      VerificationJobInstance verificationJobInstance) {
    return verificationJobInstance.getResolvedJob().getPreActivityTimeRange(
        verificationJobInstance.getDeploymentStartTime());
  }

  private NodeRiskCountDTO getNodeRiskCountDTO(Map<Risk, Integer> nodeCountByRiskStatusMap) {
    Integer totalNodeCount = 0;
    List<NodeRiskCountDTO.NodeRiskCount> nodeRiskCounts = new ArrayList<>();
    for (Risk risk : nodeCountByRiskStatusMap.keySet()) {
      totalNodeCount += nodeCountByRiskStatusMap.get(risk);
      nodeRiskCounts.add(
          NodeRiskCountDTO.NodeRiskCount.builder().risk(risk).count(nodeCountByRiskStatusMap.get(risk)).build());
    }
    nodeRiskCounts.sort((r1, r2) -> Integer.compare(r2.getRisk().getValue(), r1.getRisk().getValue()));
    long anomalousNodeCount = nodeCountByRiskStatusMap.entrySet()
                                  .stream()
                                  .filter(entry -> entry.getKey().isGreaterThan(Risk.HEALTHY))
                                  .mapToInt(entry -> entry.getValue())
                                  .sum();
    return NodeRiskCountDTO.builder()
        .totalNodeCount(totalNodeCount)
        .anomalousNodeCount((int) anomalousNodeCount)
        .nodeRiskCounts(nodeRiskCounts)
        .build();
  }

  private boolean filterHostData(HostData hostData, List<String> hostNames, boolean anomalousMetricsOnly) {
    if (isEmpty(hostNames)) {
      return true;
    } else if (hostData.getHostName().isPresent()) {
      return hostNames.contains(hostData.getHostName().get()) && (!anomalousMetricsOnly || hostData.isAnomalous());
    } else {
      return false;
    }
  }

  private boolean filterAnomalousMetrics(
      TransactionMetricHostData transactionMetricHostData, boolean hostFilterExists, boolean anomalousMetricsOnly) {
    if (hostFilterExists) {
      return true; // need to filter at host data level.
    }
    return !anomalousMetricsOnly || transactionMetricHostData.isAnomalous();
  }

  private boolean filterAnomalousNodes(HostData hostData, boolean anomalousNodesOnly) {
    if (!anomalousNodesOnly || (anomalousNodesOnly && hostData.isAnomalous())) {
      return true;
    }
    return false;
  }

  private TransactionMetricInfo.TransactionMetric createTransactionMetric(
      TransactionMetricHostData transactionMetricHostData) {
    return TransactionMetricInfo.TransactionMetric.builder()
        .transactionName(transactionMetricHostData.getTransactionName())
        .metricName(transactionMetricHostData.getMetricName())
        .score(transactionMetricHostData.getScore())
        .risk(transactionMetricHostData.getRisk())
        .build();
  }

  @Override
  public List<DeploymentTimeSeriesAnalysis> getAnalysisResults(String verificationTaskId) {
    return hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class, excludeAuthority)
        .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, verificationTaskId)
        .asList();
  }

  @Override
  public boolean isAnalysisFailFastForLatestTimeRange(String verificationTaskId) {
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class, excludeAuthority)
            .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, verificationTaskId)
            .order(Sort.descending(DeploymentTimeSeriesAnalysisKeys.startTime))
            .get();
    return deploymentTimeSeriesAnalysis != null && deploymentTimeSeriesAnalysis.isFailFast();
  }

  @Override
  public Optional<Risk> getRecentHighestRiskScore(String accountId, String verificationJobInstanceId) {
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        getRecentHighestDeploymentTimeSeriesAnalysis(accountId, verificationJobInstanceId);
    if (deploymentTimeSeriesAnalysis == null) {
      return Optional.empty();
    } else {
      return Optional.of(deploymentTimeSeriesAnalysis.getRisk());
    }
  }

  @Override
  @Nullable
  public DeploymentTimeSeriesAnalysis getRecentHighestDeploymentTimeSeriesAnalysis(
      String accountId, String verificationJobInstanceId) {
    Set<String> verificationTaskIds =
        verificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId);
    DeploymentTimeSeriesAnalysis max = null;
    for (String verificationTaskId : verificationTaskIds) {
      DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
          hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class)
              .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, verificationTaskId)
              .order(Sort.descending(DeploymentTimeSeriesAnalysisKeys.startTime))
              .get();
      max = CVNGObjectUtils.max(
          max, deploymentTimeSeriesAnalysis, Comparator.comparingDouble(dta -> dta.getRisk().getValue()));
    }
    return max;
  }

  @Override
  public List<DeploymentTimeSeriesAnalysis> getLatestDeploymentTimeSeriesAnalysis(String accountId,
      String verificationJobInstanceId, DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    Set<String> verificationTaskIds =
        verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstanceId);

    if (deploymentTimeSeriesAnalysisFilter.filterByHealthSourceIdentifiers()) {
      List<String> cvConfigIds = verificationJobInstanceService.getCVConfigIdsForVerificationJobInstance(
          verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter.getHealthSourceIdentifiers());
      verificationTaskIds =
          verificationTaskIds.stream()
              .filter(
                  verificationTaskId -> cvConfigIds.contains(verificationTaskService.getCVConfigId(verificationTaskId)))
              .collect(Collectors.toSet());
    }

    List<DeploymentTimeSeriesAnalysis> timeSeriesAnalyses = new ArrayList<>();
    verificationTaskIds.forEach(taskId -> {
      DeploymentTimeSeriesAnalysis analysis = hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class)
                                                  .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, taskId)
                                                  .order(Sort.descending(DeploymentTimeSeriesAnalysisKeys.startTime))
                                                  .get();
      if (analysis != null) {
        timeSeriesAnalyses.add(analysis);
      }
    });
    return timeSeriesAnalyses;
  }

  @Nullable
  private String getConnectorName(CVConfig cvConfig) {
    Preconditions.checkNotNull(cvConfig, "CVConfig should not be null");
    Optional<ConnectorInfoDTO> connectorInfoDTO = nextGenService.get(cvConfig.getAccountId(),
        cvConfig.getConnectorIdentifier(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier());
    return connectorInfoDTO.isPresent() ? connectorInfoDTO.get().getName() : null;
  }
}
