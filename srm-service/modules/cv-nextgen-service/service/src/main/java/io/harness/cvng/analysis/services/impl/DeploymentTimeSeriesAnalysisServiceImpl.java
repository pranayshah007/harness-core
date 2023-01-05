/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getFilteredAnalysedTestDataNodes;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getMetricTypeFromCvConfigAndMetricDefinition;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isAnalysisResultExcluded;
import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.isTransactionGroupExcluded;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.TimeSeriesAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostData;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData;
import io.harness.cvng.analysis.beans.NodeRiskCountDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TransactionMetricInfo;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis.DeploymentTimeSeriesAnalysisKeys;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
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
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
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

public class DeploymentTimeSeriesAnalysisServiceImpl implements DeploymentTimeSeriesAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private NextGenService nextGenService;

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
    List<DeploymentTimeSeriesAnalysis> latestDeploymentTimeSeriesAnalysis =
        getLatestDeploymentTimeSeriesAnalysis(accountId, verifyStepExecutionId, deploymentTimeSeriesAnalysisFilter);
    Set<String> requestedHealthSources = new HashSet<>(deploymentTimeSeriesAnalysisFilter.getHealthSourceIdentifiers());
    Set<String> requestedTransactionGroups = new HashSet<>(deploymentTimeSeriesAnalysisFilter.getTransactionNames());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verifyStepExecutionId);

    List<CVConfig> cvConfigs = verificationJobInstance.getResolvedJob().getCvConfigs();
    Map<String, CVConfig> cvConfigMap =
        cvConfigs.stream().collect(Collectors.toMap(CVConfig::getUuid, cvConfig -> cvConfig, (u, v) -> v));

    List<MetricsAnalysis> metricsAnalyses = new ArrayList<>();

    for (DeploymentTimeSeriesAnalysis timeSeriesAnalysis : latestDeploymentTimeSeriesAnalysis) {
      VerificationTask verificationTask = verificationTaskService.get(timeSeriesAnalysis.getVerificationTaskId());
      Preconditions.checkArgument(verificationTask.getTaskInfo().getTaskType() == TaskType.DEPLOYMENT,
          "VerificationTask should be of Deployment type");

      MetricCVConfig<? extends AnalysisInfo> cvConfig = (MetricCVConfig<? extends AnalysisInfo>) cvConfigMap.get(
          ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId());
      String healthSourceIdentifier = cvConfig.getFullyQualifiedIdentifier();
      if (deploymentTimeSeriesAnalysisFilter.filterByHealthSourceIdentifiers()
          && !requestedHealthSources.contains(healthSourceIdentifier)) {
        continue;
      }
      Set<MetricDefinition> metricDefinitions = cvConfig.getMetricPack().getMetrics();
      Map<String, MetricDefinition> metricDefinitionMap = metricDefinitions.stream().collect(
          Collectors.toMap(MetricDefinition::getName, metricDefinition -> metricDefinition, (u, v) -> v));
      for (TransactionMetricHostData transactionMetricHostData : timeSeriesAnalysis.getTransactionMetricSummaries()) {
        String metricName = transactionMetricHostData.getMetricName();
        MetricDefinition metricDefinition = metricDefinitionMap.get(metricName);
        AnalysisResult analysisResult = AnalysisResult.fromRisk(transactionMetricHostData.getRisk());
        String transactionGroup = transactionMetricHostData.getTransactionName();
        if (isAnalysisResultExcluded(deploymentTimeSeriesAnalysisFilter, analysisResult)
            || isTransactionGroupExcluded(
                deploymentTimeSeriesAnalysisFilter, requestedTransactionGroups, transactionGroup)) {
          continue;
        }
        MetricsAnalysis metricsAnalysis =
            MetricsAnalysis.builder()
                .metricName(metricName)
                .metricIdentifier(metricDefinition.getIdentifier())
                .healthSourceIdentifier(healthSourceIdentifier)
                .metricType(getMetricTypeFromCvConfigAndMetricDefinition(cvConfig, metricDefinition))
                .transactionGroup(transactionGroup)
                .thresholds(metricDefinition.getThresholds()
                                .stream()
                                .map(VerifyStepMetricsAnalysisUtils::getMetricThresholdFromTimeSeriesThreshold)
                                .collect(Collectors.toList()))
                .analysisResult(analysisResult)
                .testDataNodes(
                    getFilteredAnalysedTestDataNodes(transactionMetricHostData, deploymentTimeSeriesAnalysisFilter))
                .build();

        metricsAnalyses.add(metricsAnalysis);
      }
    }
    return metricsAnalyses;
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
