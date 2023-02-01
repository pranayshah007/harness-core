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
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getFilteredAnalysedTestDataNodes;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getFilteredMetricCVConfigs;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getHealthSourceFromCVConfig;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getMetricTypeFromCvConfigAndMetricDefinition;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getThresholdsFromDefinition;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getTransactionGroupFromCVConfig;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isAnalysisResultExcluded;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isTransactionGroupExcluded;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.parseTestNodeIdentifiersFromDeploymentTimeSeriesAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.populateRawMetricDataInMetricAnalysis;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.removeMetricFromResult;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.setDeeplinkURLWithRange;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.TimeSeriesAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostData;
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
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
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
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

public class DeploymentTimeSeriesAnalysisServiceImpl implements DeploymentTimeSeriesAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  private static final String STEP_INPUT_IN_SECONDS = "60";
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private NextGenService nextGenService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;

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

      if (areMetricsFromCVConfigFilteredOut(metricsForThisAnalysis)) {
        continue;
      }

      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType =
          getAppliedDeploymentAnalysisType(verificationJobInstance.getUuid(), verificationTaskId);

      Map<String, Map<String, List<TimeSeriesRecordDTO>>> controlNodesRawData = getControlNodesRawData(
          appliedDeploymentAnalysisType, verificationJobInstance, verificationTaskId, timeSeriesAnalysis);
      Map<String, Map<String, List<TimeSeriesRecordDTO>>> testNodesRawData = getTestNodesRawData(
          appliedDeploymentAnalysisType, verificationJobInstance, verificationTaskId, timeSeriesAnalysis);

      for (TransactionMetricHostData transactionMetricHostData : timeSeriesAnalysis.getTransactionMetricSummaries()) {
        // LE metricName is BE metricIdentifier
        String metricIdentifier = transactionMetricHostData.getMetricName();
        String transactionGroup = transactionMetricHostData.getTransactionName();
        AnalysisResult analysisResult = AnalysisResult.fromRisk(transactionMetricHostData.getRisk());
        if (isAnalysisResultExcluded(deploymentTimeSeriesAnalysisFilter, analysisResult)
            || isTransactionGroupExcluded(deploymentTimeSeriesAnalysisFilter, transactionGroup)) {
          removeMetricFromResult(metricsForThisAnalysis, metricIdentifier);
        } else {
          MetricsAnalysis metricsAnalysis = metricsForThisAnalysis.get(metricIdentifier);
          // Setting timeranges for the deeplink URL
          if (StringUtils.isNotEmpty(metricsAnalysis.getDeeplinkURL())) {
            String deeplinkURLWithRange = setDeeplinkURLWithRange(timeSeriesAnalysis, metricsAnalysis.getDeeplinkURL());
            metricsAnalysis.setDeeplinkURL(deeplinkURLWithRange);
          }
          metricsAnalysis.setTransactionGroup(transactionGroup);
          metricsAnalysis.setAnalysisResult(analysisResult);
          metricsAnalysis.setTestDataNodes(getFilteredAnalysedTestDataNodes(
              transactionMetricHostData, deploymentTimeSeriesAnalysisFilter, metricsAnalysis.getThresholds()));
          populateRawMetricDataInMetricAnalysis(appliedDeploymentAnalysisType,
              controlNodesRawData.get(metricIdentifier), testNodesRawData.get(metricIdentifier), metricsAnalysis);
        }
      }
    }
    List<MetricsAnalysis> metricsAnalyses = new ArrayList<>();
    for (Map<String, MetricsAnalysis> map : mapOfCvConfigIdAndFilteredMetrics.values()) {
      metricsAnalyses.addAll(map.values());
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
    List<MetricsAnalysis> metricsAnalyses;
    if (CollectionUtils.isEmpty(metricCVConfig.getMetricInfos())) {
      metricsAnalyses = getMetricAnalysesFromMetricDefinitions(metricCVConfig, healthSource, transactionGroup);
    } else {
      metricsAnalyses = getMetricAnalysesFromMetricInfos(metricCVConfig, healthSource, transactionGroup);
    }
    return metricsAnalyses.stream().collect(Collectors.toMap(
        MetricsAnalysis::getMetricIdentifier, metricsAnalysis -> metricsAnalysis, (existing, current) -> current));
  }

  private List<MetricsAnalysis> getMetricAnalysesFromMetricDefinitions(
      MetricCVConfig<? extends AnalysisInfo> metricCVConfig, HealthSource healthSource, String transactionGroup) {
    return metricCVConfig.getMetricPack()
        .getMetrics()
        .stream()
        .filter(MetricDefinition::isIncluded)
        .map(metricDefinition
            -> MetricsAnalysis.builder()
                   .metricName(metricDefinition.getName())
                   .metricIdentifier(metricDefinition.getIdentifier())
                   .healthSource(healthSource)
                   .transactionGroup(transactionGroup)
                   .metricType(getMetricTypeFromCvConfigAndMetricDefinition(metricCVConfig, metricDefinition))
                   .thresholds(getThresholdsFromDefinition(metricDefinition))
                   .build())
        .collect(Collectors.toList());
  }

  private List<MetricsAnalysis> getMetricAnalysesFromMetricInfos(
      MetricCVConfig<? extends AnalysisInfo> metricCVConfig, HealthSource healthSource, String transactionGroup) {
    Map<String, MetricDefinition> metricDefinitions = metricCVConfig.getMetricPack().getMetrics().stream().collect(
        Collectors.toMap(MetricDefinition::getIdentifier, metricDefinition -> metricDefinition, (u, v) -> v));
    return metricCVConfig.getMetricInfos()
        .stream()
        .filter(VerifyStepMetricsAnalysisUtils::isDeploymentVerificationEnabledForThisMetric)
        .map(metricInfo -> {
          MetricsAnalysis metricsAnalysis =
              MetricsAnalysis.builder()
                  .metricName(metricInfo.getMetricName())
                  .metricIdentifier(metricInfo.getIdentifier())
                  .healthSource(healthSource)
                  .transactionGroup(transactionGroup)
                  .metricType(getMetricTypeFromCvConfigAndMetricDefinition(
                      metricCVConfig, metricDefinitions.get(metricInfo.getIdentifier())))
                  .thresholds(getThresholdsFromDefinition(metricDefinitions.get(metricInfo.getIdentifier())))
                  .build();

          Optional<String> deeplinkURL = buildDeepLinkURL(metricCVConfig, metricInfo);
          deeplinkURL.ifPresent(metricsAnalysis::setDeeplinkURL);

          return metricsAnalysis;
        })
        .collect(Collectors.toList());
  }

  private Optional<String> buildDeepLinkURL(MetricCVConfig metricCVConfig, AnalysisInfo metric) {
    Optional<String> deeplinkURL = Optional.empty();
    if (metric instanceof PrometheusCVConfig.MetricInfo) {
      PrometheusCVConfig prometheusCVConfig = (PrometheusCVConfig) metricCVConfig;
      PrometheusCVConfig.MetricInfo metricInfo = (PrometheusCVConfig.MetricInfo) metric;
      Optional<ConnectorInfoDTO> connectorInfoDTO =
          nextGenService.get(prometheusCVConfig.getAccountId(), prometheusCVConfig.getConnectorIdentifier(),
              prometheusCVConfig.getOrgIdentifier(), prometheusCVConfig.getProjectIdentifier());
      if (connectorInfoDTO.isPresent()) {
        PrometheusConnectorDTO connectorConfigDTO =
            (PrometheusConnectorDTO) connectorInfoDTO.get().getConnectorConfig();

        try {
          URIBuilder uriBuilder;
          uriBuilder = new URIBuilder(connectorConfigDTO.getUrl() + "/graph");
          uriBuilder.addParameter("g0.step_input", STEP_INPUT_IN_SECONDS);
          uriBuilder.addParameter("g0.expr", metricInfo.getQuery());
          deeplinkURL = Optional.ofNullable(uriBuilder.build().toString());
        } catch (URISyntaxException ignored) {
        }
      }
    }
    return deeplinkURL;
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawData(
      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType, VerificationJobInstance verificationJobInstance,
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST) {
      return getControlNodesRawDataForLoadTestAnalysis(verificationJobInstance, verificationTaskId);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
      return getControlNodesRawDataForCanaryAnalysis(verificationJobInstance, verificationTaskId, timeSeriesAnalysis);
    } else if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.ROLLING) {
      return getControlNodesRawDataForRollingAnalysis(verificationJobInstance, verificationTaskId, timeSeriesAnalysis);
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawData(
      AppliedDeploymentAnalysisType appliedDeploymentAnalysisType, VerificationJobInstance verificationJobInstance,
      String verificationTaskId, DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST) {
      return getTestNodesRawDataForLoadTestAnalysis(verificationJobInstance, verificationTaskId, timeSeriesAnalysis);
    } else {
      return getTestNodesRawDataForCanaryAndRollingAnalysis(
          verificationJobInstance, verificationTaskId, timeSeriesAnalysis);
    }
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawDataForCanaryAndRollingAnalysis(
      VerificationJobInstance verificationJobInstance, String verificationTaskId,
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    Set<String> testNodes = parseTestNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, verificationJobInstance.getStartTime(), timeSeriesAnalysis.getEndTime(), testNodes);

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getTestNodesRawDataForLoadTestAnalysis(
      VerificationJobInstance verificationJobInstance, String verificationTaskId,
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, verificationJobInstance.getStartTime(), timeSeriesAnalysis.getEndTime(), null);

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
      VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = null;
    TestVerificationJob verificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = verificationJob.getBaselineVerificationJobInstanceId();
    if (StringUtils.isNotBlank(baselineVerificationJobInstanceId)) {
      VerificationJobInstance baselineVerificationJobInstance =
          verificationJobInstanceService.getVerificationJobInstance(baselineVerificationJobInstanceId);
      Optional<String> baselineVerificationTaskId =
          verificationTaskService.findBaselineVerificationTaskId(verificationTaskId, verificationJobInstance);
      if (baselineVerificationTaskId.isPresent()) {
        timeSeriesRecordDtos =
            timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(baselineVerificationTaskId.get(),
                baselineVerificationJobInstance.getStartTime(), baselineVerificationJobInstance.getEndTime(), null);
      }
    }

    CollectionUtils.emptyIfNull(timeSeriesRecordDtos)
        .forEach(timeSeriesRecordDTO -> timeSeriesRecordDTO.setHost(LOAD_TEST_BASELINE_NODE_IDENTIFIER));

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawDataForCanaryAnalysis(
      VerificationJobInstance verificationJobInstance, String verificationTaskId,
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos;
    Set<String> controlNodes = parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
    timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, verificationJobInstance.getStartTime(), timeSeriesAnalysis.getEndTime(), controlNodes);

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
  }

  private Map<String, Map<String, List<TimeSeriesRecordDTO>>> getControlNodesRawDataForRollingAnalysis(
      VerificationJobInstance verificationJobInstance, String verificationTaskId,
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = null;

    Optional<TimeRange> dataCollectionTimeRange = verificationJobInstance.getResolvedJob().getPreActivityTimeRange(
        verificationJobInstance.getDeploymentStartTime());

    if (dataCollectionTimeRange.isPresent()) {
      Set<String> controlNodes = parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis(timeSeriesAnalysis);
      timeSeriesRecordDtos = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(verificationTaskId,
          dataCollectionTimeRange.get().getStartTime(), dataCollectionTimeRange.get().getEndTime(), controlNodes);
    }

    return convertTimeSeriesRecordDtosListToMap(timeSeriesRecordDtos);
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
