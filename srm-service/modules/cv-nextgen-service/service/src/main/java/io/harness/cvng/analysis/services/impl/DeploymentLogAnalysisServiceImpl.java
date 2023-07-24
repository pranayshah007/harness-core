/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.beans.FeatureName.SRM_ENABLE_BASELINE_BASED_VERIFICATION;
import static io.harness.cvng.CVConstants.TAG_DATA_SOURCE;
import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.NO_BASELINE_AVAILABLE;
import static io.harness.cvng.beans.MonitoredServiceDataSourceType.ERROR_TRACKING;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.joda.time.DateTimeConstants.SECONDS_PER_MINUTE;

import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.ErrorAnalysisSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.LogsAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.Cluster;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterHostFrequencyData;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterHostFrequencyData.ClusterHostFrequencyDataBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary.ClusterSummaryBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ControlClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostFrequencyData;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostFrequencyData.HostFrequencyDataBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary.HostSummaryBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary.ResultSummaryBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.TimestampFrequencyCount;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.TimestampFrequencyCount.TimestampFrequencyCountBuilder;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO.EventCount;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListDTO.LogAnalysisRadarChartListDTOBuilder;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListWithCountDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis.DeploymentLogAnalysisKeys;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.cdng.beans.v2.ClusterAnalysisOverview;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.VerificationTaskKeys;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.ticket.beans.TicketResponseDto;
import io.harness.cvng.ticket.services.TicketService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.ng.beans.PageResponse;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class DeploymentLogAnalysisServiceImpl implements DeploymentLogAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private TicketService ticketService;

  @Override
  public void save(DeploymentLogAnalysis deploymentLogAnalysis) {
    hPersistence.save(deploymentLogAnalysis);
  }

  @Override
  public List<DeploymentLogAnalysis> getAnalysisResults(String verificationTaskId) {
    return hPersistence.createQuery(DeploymentLogAnalysis.class)
        .filter(DeploymentLogAnalysisKeys.verificationTaskId, verificationTaskId)
        .asList();
  }

  @Override
  public List<LogAnalysisClusterChartDTO> getLogAnalysisClusters(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<DeploymentLogAnalysis> latestDeploymentLogAnalysis =
        getLatestDeploymentLogAnalysis(accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    if (isEmpty(latestDeploymentLogAnalysis)) {
      return Collections.emptyList();
    }

    List<LogAnalysisClusterChartDTO> allClusters = new ArrayList<>();
    for (DeploymentLogAnalysis deploymentLogAnalysis : latestDeploymentLogAnalysis) {
      List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOList =
          getLogAnalysisClusterChartList(deploymentLogAnalysis, deploymentLogAnalysisFilter.getHostNames());

      Map<Integer, ClusterSummary> clusterSummaryMap = new HashMap<>();
      deploymentLogAnalysis.getResultSummary().getTestClusterSummaries().forEach(
          clusterSummary -> clusterSummaryMap.put(clusterSummary.getLabel(), clusterSummary));

      logAnalysisClusterChartDTOList.forEach(logAnalysisClusterChartDTO -> {
        if (clusterSummaryMap.containsKey(logAnalysisClusterChartDTO.getLabel())
            && (!deploymentLogAnalysisFilter.filterByClusterType()
                || deploymentLogAnalysisFilter.getClusterTypes().contains(
                    clusterSummaryMap.get(logAnalysisClusterChartDTO.getLabel()).getClusterType()))) {
          logAnalysisClusterChartDTO.setRisk(
              clusterSummaryMap.get(logAnalysisClusterChartDTO.getLabel()).getRiskLevel());
          logAnalysisClusterChartDTO.setClusterType(
              clusterSummaryMap.get(logAnalysisClusterChartDTO.getLabel()).getClusterType());
          allClusters.add(logAnalysisClusterChartDTO);
        }
      });
    }
    return allClusters;
  }

  @Override
  public PageResponse<LogAnalysisClusterDTO> getLogAnalysisResult(String accountId, String verificationJobInstanceId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    List<LogAnalysisClusterDTO> logAnalysisClusters =
        getLogAnalysisResult(accountId, verificationJobInstanceId, label, deploymentLogAnalysisFilter);

    return PageUtils.offsetAndLimit(logAnalysisClusters, pageParams.getPage(), pageParams.getSize());
  }

  @Override
  public LogAnalysisClusterWithCountDTO getLogAnalysisResultV2(String accountId, String verificationJobInstanceId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    Integer totalClusters = getTotalClusters(accountId, verificationJobInstanceId);
    List<LogAnalysisClusterDTO> logAnalysisClusters =
        getLogAnalysisResult(accountId, verificationJobInstanceId, label, deploymentLogAnalysisFilter);
    PageResponse<LogAnalysisClusterDTO> paginatedLogAnalysisClusterDTO =
        PageUtils.offsetAndLimit(logAnalysisClusters, pageParams.getPage(), pageParams.getSize());

    List<LogAnalysisClusterDTO> paginatedLogAnalysisClusters = paginatedLogAnalysisClusterDTO.getContent();
    Map<ClusterType, Long> eventCountByEventTypeMap =
        paginatedLogAnalysisClusters.stream()
            .map(LogAnalysisClusterDTO::getClusterType)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    return LogAnalysisClusterWithCountDTO.builder()
        .totalClusters(totalClusters)
        .eventCounts(ClusterType.getNonBaselineValues()
                         .stream()
                         .map(clusterType
                             -> EventCount.builder()
                                    .clusterType(clusterType)
                                    .count(eventCountByEventTypeMap.getOrDefault(clusterType, 0L).intValue())
                                    .build())
                         .collect(Collectors.toList()))
        .logAnalysisClusterDTO(paginatedLogAnalysisClusterDTO)
        .build();
  }

  private List<LogAnalysisClusterDTO> getLogAnalysisResult(String accountId, String verificationJobInstanceId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<DeploymentLogAnalysis> latestDeploymentLogAnalysis =
        getLatestDeploymentLogAnalysis(accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    if (isEmpty(latestDeploymentLogAnalysis)) {
      return Collections.emptyList();
    }
    List<LogAnalysisClusterDTO> logAnalysisClusters = new ArrayList<>();

    for (DeploymentLogAnalysis deploymentLogAnalysis : latestDeploymentLogAnalysis) {
      deploymentLogAnalysis.getResultSummary().setLabelToControlDataMap();
      if (deploymentLogAnalysisFilter.filterByHostNames()) {
        logAnalysisClusters.addAll(getHostSpecificLogAnalysisClusters(
            deploymentLogAnalysis, label, deploymentLogAnalysisFilter.getHostNames()));
      } else {
        logAnalysisClusters.addAll(getOverallLogAnalysisClusters(deploymentLogAnalysis, label));
      }
    }
    if (deploymentLogAnalysisFilter.filterByClusterType()) {
      logAnalysisClusters =
          logAnalysisClusters.stream()
              .filter(
                  logAnalysis -> deploymentLogAnalysisFilter.getClusterTypes().contains(logAnalysis.getClusterType()))
              .collect(Collectors.toList());
    }
    logAnalysisClusters.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    return logAnalysisClusters;
  }

  private Integer getTotalClusters(String accountId, String verificationJobInstanceId) {
    List<DeploymentLogAnalysis> latestDeploymentLogAnalysis = getLatestDeploymentLogAnalysis(
        accountId, verificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build());
    Integer[] totalClusters = {0};
    latestDeploymentLogAnalysis.forEach(
        deploymentLogAnalysis -> totalClusters[0] += deploymentLogAnalysis.getClusters().size());
    return totalClusters[0];
  }

  @Override
  public LogsAnalysisSummary getAnalysisSummary(String accountId, List<String> verificationJobInstanceIds) {
    List<Integer> anomClusterCounts = new ArrayList<>();
    List<Integer> totalClusterCounts = new ArrayList<>();

    Preconditions.checkNotNull(
        verificationJobInstanceIds, "Missing verificationJobInstanceIds when looking for summary");
    verificationJobInstanceIds.forEach(verificationJobInstanceId -> {
      List<LogAnalysisClusterDTO> logAnalysisClusters = getLogAnalysisResult(
          accountId, verificationJobInstanceId, null, DeploymentLogAnalysisFilter.builder().build());
      int anomClusters = 0, totalClusters = 0;
      for (LogAnalysisClusterDTO logAnalysisClusterDTO : logAnalysisClusters) {
        if (logAnalysisClusterDTO.getRisk().isGreaterThan(Risk.HEALTHY)) {
          anomClusters++;
        }
        totalClusters++;
      }
      anomClusterCounts.add(anomClusters);
      totalClusterCounts.add(totalClusters);
    });
    return LogsAnalysisSummary.builder()
        .anomalousClusterCount(anomClusterCounts.stream().mapToInt(Integer::intValue).sum())
        .totalClusterCount(totalClusterCounts.stream().mapToInt(Integer::intValue).sum())
        .build();
  }

  @Override
  public ErrorAnalysisSummary getErrorAnalysisSummary(String accountId, List<String> verificationJobInstanceIds) {
    List<Integer> anomClusterCounts = new ArrayList<>();
    List<Integer> totalClusterCounts = new ArrayList<>();

    Preconditions.checkNotNull(
        verificationJobInstanceIds, "Missing verificationJobInstanceIds when looking for summary");
    DeploymentLogAnalysisFilter filter =
        DeploymentLogAnalysisFilter.builder()
            .hostNames(Collections.singletonList(DataSourceType.ERROR_TRACKING.getDisplayName()))
            .build();

    verificationJobInstanceIds.forEach(verificationJobInstanceId -> {
      List<LogAnalysisClusterDTO> logAnalysisClusters =
          getLogAnalysisResult(accountId, verificationJobInstanceId, null, filter);
      int anomClusters = 0, totalClusters = 0;
      for (LogAnalysisClusterDTO logAnalysisClusterDTO : logAnalysisClusters) {
        if (logAnalysisClusterDTO.getRisk().isGreaterThan(Risk.HEALTHY)) {
          anomClusters++;
        }
        totalClusters++;
      }
      anomClusterCounts.add(anomClusters);
      totalClusterCounts.add(totalClusters);
    });

    return ErrorAnalysisSummary.builder()
        .anomalousClusterCount(anomClusterCounts.stream().mapToInt(Integer::intValue).sum())
        .totalClusterCount(totalClusterCounts.stream().mapToInt(Integer::intValue).sum())
        .build();
  }

  @Override
  public Optional<Risk> getRecentHighestRiskScore(String accountId, String verificationJobInstanceId) {
    DeploymentLogAnalysis recentHighestDeploymentLogAnalysis =
        getRecentHighestDeploymentLogAnalysis(accountId, verificationJobInstanceId);
    if (recentHighestDeploymentLogAnalysis == null) {
      return Optional.empty();
    } else {
      return Optional.of(recentHighestDeploymentLogAnalysis.getResultSummary().getRiskLevel());
    }
  }

  @Override
  @Nullable
  public DeploymentLogAnalysis getRecentHighestDeploymentLogAnalysis(
      String accountId, String verificationJobInstanceId) {
    Set<String> verificationTaskIds =
        verificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId);
    DeploymentLogAnalysis max = null;
    for (String verificationTaskId : verificationTaskIds) {
      DeploymentLogAnalysis deploymentLogAnalysis =
          hPersistence.createQuery(DeploymentLogAnalysis.class)
              .filter(DeploymentLogAnalysisKeys.verificationTaskId, verificationTaskId)
              .order(Sort.descending(DeploymentLogAnalysisKeys.startTime))
              .get();
      max = CVNGObjectUtils.max(max, deploymentLogAnalysis,
          Comparator.comparingDouble(logAnalysis -> logAnalysis.getResultSummary().getRisk()));
    }
    return max;
  }

  @Override
  public List<DeploymentLogAnalysis> getLatestDeploymentLogAnalysis(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    Set<String> verificationTaskIds =
        verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstanceId);
    if (deploymentLogAnalysisFilter.filterByHealthSourceIdentifiers()) {
      List<String> cvConfigIds = verificationJobInstanceService.getCVConfigIdsForVerificationJobInstance(
          verificationJobInstanceId, deploymentLogAnalysisFilter.getHealthSourceIdentifiers());
      verificationTaskIds =
          verificationTaskIds.stream()
              .filter(
                  verificationTaskId -> cvConfigIds.contains(verificationTaskService.getCVConfigId(verificationTaskId)))
              .collect(Collectors.toSet());
    }

    List<DeploymentLogAnalysis> deploymentLogAnalyses = new ArrayList<>();
    for (String taskId : verificationTaskIds) {
      DeploymentLogAnalysis logAnalysis = hPersistence.createQuery(DeploymentLogAnalysis.class)
                                              .filter(DeploymentLogAnalysisKeys.verificationTaskId, taskId)
                                              .order(Sort.descending(DeploymentLogAnalysisKeys.startTime))
                                              .get();
      if (logAnalysis != null) {
        deploymentLogAnalyses.add(logAnalysis);
      }
    }
    return deploymentLogAnalyses;
  }

  @Override
  public String getLogDemoTemplate(String verificationTaskId) {
    List<DeploymentLogAnalysis> deploymentLogAnalyses =
        hPersistence.createQuery(DeploymentLogAnalysis.class)
            .filter(DeploymentLogAnalysisKeys.verificationTaskId, verificationTaskId)
            .asList();
    return JsonUtils.asJson(deploymentLogAnalyses);
  }

  private List<TimestampFrequencyCount> updateTimeStamps(
      List<TimestampFrequencyCount> timestampFrequencyCounts, long startTimeInMinutes) {
    List<TimestampFrequencyCount> newTimeStamps = new ArrayList<>();
    for (TimestampFrequencyCount timestampFrequencyCount : timestampFrequencyCounts) {
      TimestampFrequencyCountBuilder timestampFrequencyCountBuilder = timestampFrequencyCount.toBuilder();
      timestampFrequencyCountBuilder.timeStamp(startTimeInMinutes);
      startTimeInMinutes++;
      newTimeStamps.add(timestampFrequencyCountBuilder.build());
    }
    return newTimeStamps;
  }

  @Override
  public void addDemoAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, String demoTemplatePath) {
    try {
      VerificationJobType verificationJobType = verificationJobInstance.getResolvedJob().getType();
      String template = Resources.toString(this.getClass().getResource(demoTemplatePath), Charsets.UTF_8);
      List<DeploymentLogAnalysis> deploymentLogAnalyses =
          JsonUtils.asObject(template, new TypeReference<List<DeploymentLogAnalysis>>() {});
      deploymentLogAnalyses.sort(Comparator.comparing(DeploymentLogAnalysis::getStartTime));
      Instant lastStartTime = deploymentLogAnalyses.get(0).getStartTime();
      int minute = 0;
      for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalyses) {
        deploymentLogAnalysis.setVerificationTaskId(verificationTaskId);
        if (!lastStartTime.equals(deploymentLogAnalysis.getStartTime())) {
          lastStartTime = deploymentLogAnalysis.getStartTime();
          minute++;
        }
        deploymentLogAnalysis.setStartTime(verificationJobInstance.getStartTime().plus(Duration.ofMinutes(minute)));
        deploymentLogAnalysis.setEndTime(deploymentLogAnalysis.getStartTime().plus(Duration.ofMinutes(10)));
        Instant instantStart = verificationJobInstance.getStartTime();
        long startTimeInMinutes = instantStart.getEpochSecond() / SECONDS_PER_MINUTE;
        List<ClusterSummary> updatedTestClusterSummary = getUpdatedTestClusterSummary(
            deploymentLogAnalysis.getResultSummary().getTestClusterSummaries(), startTimeInMinutes);
        ResultSummaryBuilder resultSummaryBuilder = deploymentLogAnalysis.getResultSummary().toBuilder();
        resultSummaryBuilder.testClusterSummaries(updatedTestClusterSummary);
        deploymentLogAnalysis.setResultSummary(resultSummaryBuilder.build());

        List<ClusterHostFrequencyData> controlHostFrequencyData =
            deploymentLogAnalysis.getResultSummary().getControlClusterHostFrequencies();
        List<ClusterHostFrequencyData> updatedControlHostFrequencyData =
            getUpdatedControlHostFrequencyData(controlHostFrequencyData, startTimeInMinutes, verificationJobType);
        ResultSummaryBuilder updatedResultSummary = deploymentLogAnalysis.getResultSummary().toBuilder();
        updatedResultSummary.controlClusterHostFrequencies(updatedControlHostFrequencyData);
        deploymentLogAnalysis.setResultSummary(updatedResultSummary.build());

        List<HostSummary> hostSummaries = deploymentLogAnalysis.getHostSummaries();
        List<HostSummary> updatedHostSummaries = new ArrayList<>();
        for (HostSummary hostSummary : hostSummaries) {
          ResultSummary resultSummary = hostSummary.getResultSummary();
          List<ClusterHostFrequencyData> updatedClusterHostFrequencyData = getUpdatedControlHostFrequencyData(
              resultSummary.getControlClusterHostFrequencies(), startTimeInMinutes, verificationJobType);
          List<ClusterSummary> testClusterSummaryUpdated =
              getUpdatedTestClusterSummary(resultSummary.getTestClusterSummaries(), startTimeInMinutes);
          ResultSummaryBuilder updatedResultSummaryBuilder = resultSummary.toBuilder();
          updatedResultSummaryBuilder.controlClusterHostFrequencies(updatedClusterHostFrequencyData)
              .testClusterSummaries(testClusterSummaryUpdated);
          HostSummaryBuilder hostSummaryBuilder = hostSummary.toBuilder();
          hostSummaryBuilder.resultSummary(updatedResultSummaryBuilder.build());
          updatedHostSummaries.add(hostSummaryBuilder.build());
        }
        deploymentLogAnalysis.setHostSummaries(updatedHostSummaries);
      }
      hPersistence.save(deploymentLogAnalyses);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<ClusterSummary> getUpdatedTestClusterSummary(
      List<ClusterSummary> testClusterSummary, long startTimeInMinutes) {
    List<ClusterSummary> updatedTestClusterSummary = new ArrayList<>();
    for (ClusterSummary clusterSummary : testClusterSummary) {
      List<HostFrequencyData> hostFrequencyDataList = clusterSummary.getFrequencyData();
      List<HostFrequencyData> updatedHostFrequencyDataList = new ArrayList<>();
      for (HostFrequencyData frequencyData : hostFrequencyDataList) {
        List<TimestampFrequencyCount> updatedTimeStamps =
            updateTimeStamps(frequencyData.getFrequencies(), startTimeInMinutes);
        HostFrequencyDataBuilder hostFrequencyDataBuilder = frequencyData.toBuilder();
        hostFrequencyDataBuilder.frequencies(updatedTimeStamps);
        updatedHostFrequencyDataList.add(hostFrequencyDataBuilder.build());
      }
      ClusterSummaryBuilder newClusterSummaryBuilder = clusterSummary.toBuilder();
      newClusterSummaryBuilder.frequencyData(updatedHostFrequencyDataList);
      updatedTestClusterSummary.add(newClusterSummaryBuilder.build());
    }
    return updatedTestClusterSummary;
  }

  private List<ClusterHostFrequencyData> getUpdatedControlHostFrequencyData(
      List<ClusterHostFrequencyData> controlHostFrequencyData, long startTimeInMinutes,
      VerificationJobType verificationJobType) {
    if (!verificationJobType.equals(VerificationJobType.CANARY)) {
      startTimeInMinutes = startTimeInMinutes - 10;
    }
    List<ClusterHostFrequencyData> hostFrequencyData = new ArrayList<>();
    if (controlHostFrequencyData == null) {
      return hostFrequencyData;
    }
    for (ClusterHostFrequencyData clusterHostFrequencyData : controlHostFrequencyData) {
      List<HostFrequencyData> hostFrequencyDataList = clusterHostFrequencyData.getFrequencyData();
      List<HostFrequencyData> updatedHostFrequencyDataList = new ArrayList<>();
      for (HostFrequencyData frequencyData : hostFrequencyDataList) {
        List<TimestampFrequencyCount> updatedTimeStamps =
            updateTimeStamps(frequencyData.getFrequencies(), startTimeInMinutes);
        HostFrequencyDataBuilder hostFrequencyDataBuilder = frequencyData.toBuilder();
        hostFrequencyDataBuilder.frequencies(updatedTimeStamps);
        updatedHostFrequencyDataList.add(hostFrequencyDataBuilder.build());
      }
      ClusterHostFrequencyDataBuilder clusterHostFrequencyDataBuilder = clusterHostFrequencyData.toBuilder();
      clusterHostFrequencyDataBuilder.frequencyData(updatedHostFrequencyDataList);
      hostFrequencyData.add(clusterHostFrequencyDataBuilder.build());
    }
    return hostFrequencyData;
  }

  @Override
  public Set<String> getNodeNames(String accountId, String verificationJobInstanceId) {
    return getLatestDeploymentLogAnalysis(
        accountId, verificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build())
        .stream()
        .flatMap(deploymentLogAnalysis
            -> deploymentLogAnalysis.getHostSummaries()
                   .stream()
                   .filter(hostSummary -> hostSummary.getHost() != null)
                   .map(HostSummary::getHost)
                   .filter(host -> !DataSourceType.ERROR_TRACKING.getDisplayName().equals(host)))
        .collect(Collectors.toSet());
  }

  @Override
  public ClusterAnalysisOverview getLogsAnalysisOverview(String accountId, String verifyStepExecutionId) {
    return getFilteredClusterAnalysisOverview(
        accountId, verifyStepExecutionId, DeploymentLogAnalysisFilter.builder().build());
  }

  @Override
  public ClusterAnalysisOverview getErrorsAnalysisOverview(String accountId, String verifyStepExecutionId) {
    DeploymentLogAnalysisFilter filter =
        DeploymentLogAnalysisFilter.builder()
            .hostNames(Collections.singletonList(DataSourceType.ERROR_TRACKING.getDisplayName()))
            .build();
    return getFilteredClusterAnalysisOverview(accountId, verifyStepExecutionId, filter);
  }

  private ClusterAnalysisOverview getFilteredClusterAnalysisOverview(
      String accountId, String verifyStepExecutionId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<LogAnalysisClusterDTO> logAnalysisClusters =
        getLogAnalysisResult(accountId, verifyStepExecutionId, null, deploymentLogAnalysisFilter);
    int knownClusters = getClusterCount(logAnalysisClusters, ClusterType.KNOWN_EVENT);
    int unknownClusters = getClusterCount(logAnalysisClusters, ClusterType.UNKNOWN_EVENT);
    int unexpectedFrequencyClusters = getClusterCount(logAnalysisClusters, ClusterType.UNEXPECTED_FREQUENCY);

    return ClusterAnalysisOverview.builder()
        .knownClustersCount(knownClusters)
        .unknownClustersCount(unknownClusters)
        .unexpectedFrequencyClustersCount(unexpectedFrequencyClusters)
        .build();
  }

  private static int getClusterCount(List<LogAnalysisClusterDTO> logAnalysisClusters, ClusterType clusterType) {
    int count = 0;
    for (LogAnalysisClusterDTO logAnalysisClusterDTO : logAnalysisClusters) {
      if (clusterType == logAnalysisClusterDTO.getClusterType()) {
        count++;
      }
    }
    return count;
  }

  private List<LogAnalysisClusterChartDTO> getLogAnalysisClusterChartList(
      DeploymentLogAnalysis deploymentLogAnalysis, List<String> hostNames) {
    Map<Integer, Cluster> labelToClusterMap = new HashMap<>();
    deploymentLogAnalysis.getClusters().forEach(cluster -> labelToClusterMap.put(cluster.getLabel(), cluster));

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOList = new ArrayList<>();

    VerificationTask verificationTask =
        hPersistence.createQuery(VerificationTask.class)
            .filter(VerificationTaskKeys.uuid, deploymentLogAnalysis.getVerificationTaskId())
            .get();
    if (isNotEmpty(hostNames) || !ERROR_TRACKING.name().equals(verificationTask.getTags().get(TAG_DATA_SOURCE))) {
      deploymentLogAnalysis.getClusterCoordinates()
          .stream()
          .filter(clusterCoordinate -> isEmpty(hostNames) || hostNames.contains(clusterCoordinate.getHost()))
          .forEach(clusterCoordinate -> {
            Cluster cluster = labelToClusterMap.get(clusterCoordinate.getLabel());
            logAnalysisClusterChartDTOList.add(LogAnalysisClusterChartDTO.builder()
                                                   .label(cluster.getLabel())
                                                   .text(cluster.getText())
                                                   .hostName(clusterCoordinate.getHost())
                                                   .x(clusterCoordinate.getX())
                                                   .y(clusterCoordinate.getY())
                                                   .build());
          });
    }

    return logAnalysisClusterChartDTOList;
  }

  private void addLogAnalysisClusterDTO(
      List<LogAnalysisClusterDTO> logAnalysisClusters, ResultSummary resultSummary, Cluster cluster) {
    resultSummary.getTestClusterSummaries()
        .stream()
        .filter(clusterSummary -> clusterSummary.getLabel() == cluster.getLabel())
        .forEach(clusterSummary
            -> logAnalysisClusters.add(LogAnalysisClusterDTO.builder()
                                           .label(cluster.getLabel())
                                           .message(cluster.getText())
                                           .risk(clusterSummary.getRiskLevel())
                                           .score(clusterSummary.getScore())
                                           .count(clusterSummary.getCount())
                                           .clusterType(clusterSummary.getClusterType())
                                           .controlFrequencyData(resultSummary.getControlData(cluster.getLabel()))
                                           .testFrequencyData(clusterSummary.getTestFrequencyData())
                                           .build()));
  }

  private List<LogAnalysisClusterDTO> getHostSpecificLogAnalysisClusters(
      DeploymentLogAnalysis deploymentLogAnalysis, Integer label, List<String> hostNames) {
    List<LogAnalysisClusterDTO> logAnalysisClusters = new ArrayList<>();
    deploymentLogAnalysis.getClusters()
        .stream()
        .filter(cluster -> label != null ? cluster.getLabel() == label : Boolean.TRUE)
        .forEach(cluster
            -> deploymentLogAnalysis.getHostSummaries()
                   .stream()
                   .filter(hostSummary -> hostNames.contains(hostSummary.getHost()))
                   .forEach(hostSummary
                       -> addLogAnalysisClusterDTO(logAnalysisClusters, hostSummary.getResultSummary(), cluster)));
    return logAnalysisClusters;
  }

  private List<LogAnalysisClusterDTO> getOverallLogAnalysisClusters(
      DeploymentLogAnalysis deploymentLogAnalysis, Integer label) {
    List<LogAnalysisClusterDTO> logAnalysisClusters = new ArrayList<>();
    VerificationTask verificationTask = verificationTaskService.get(deploymentLogAnalysis.getVerificationTaskId());
    if (!ERROR_TRACKING.name().equals(verificationTask.getTags().get(TAG_DATA_SOURCE))) {
      deploymentLogAnalysis.getClusters()
          .stream()
          .filter(cluster -> label != null ? cluster.getLabel() == label : Boolean.TRUE)
          .forEach(cluster
              -> addLogAnalysisClusterDTO(logAnalysisClusters, deploymentLogAnalysis.getResultSummary(), cluster));
    }
    return logAnalysisClusters;
  }

  @Override
  public LogAnalysisRadarChartListWithCountDTO getRadarChartLogAnalysisResult(String accountId,
      String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter,
      PageParams pageParams) {
    List<LogAnalysisRadarChartListDTO> logAnalysisResults =
        getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    logAnalysisResults = filterByAngle(logAnalysisResults, deploymentLogAnalysisFilter);
    Integer totalClusters = logAnalysisResults.size();
    PageResponse<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDTOPageResponse =
        PageUtils.offsetAndLimit(logAnalysisResults, pageParams.getPage(), pageParams.getSize());

    Map<ClusterType, Long> eventCountByEventTypeMap = new HashMap<>();

    Long baselineCount =
        logAnalysisResults.stream()
            .filter(logAnalysisRadarChartListDTO -> !Objects.isNull(logAnalysisRadarChartListDTO.getBaseline()))
            .count();

    eventCountByEventTypeMap.put(ClusterType.BASELINE, baselineCount);
    eventCountByEventTypeMap.putAll(logAnalysisResults.stream()
                                        .map(LogAnalysisRadarChartListDTO::getClusterType)
                                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
    List<EventCount> eventCounts =
        ClusterType.getNonBaselineValues()
            .stream()
            .map(clusterType
                -> EventCount.builder()
                       .clusterType(clusterType)
                       .count(eventCountByEventTypeMap.getOrDefault(clusterType, 0L).intValue())
                       .build())
            .collect(Collectors.toList());

    eventCounts.add(EventCount.builder()
                        .clusterType(ClusterType.BASELINE)
                        .count(eventCountByEventTypeMap.getOrDefault(ClusterType.BASELINE, 0L).intValue())
                        .build());

    // Filter the eventCount based on NO_BASELINE_AVAILABLE, if it's true then only NO_BASELINE_AVAILABLE should be
    // present, else it shouldn't be part of the eventCount
    Optional<EventCount> result =
        eventCounts.stream().filter(event -> NO_BASELINE_AVAILABLE.equals(event.getClusterType())).findFirst();
    if (featureFlagService.isFeatureFlagEnabled(accountId, SRM_ENABLE_BASELINE_BASED_VERIFICATION.name())
        && result.isPresent() && result.get().getCount() > 0) {
      eventCounts.clear();
      eventCounts.add(result.get());
    } else {
      eventCounts = eventCounts.stream()
                        .filter(eventCount -> !eventCount.getClusterType().equals(NO_BASELINE_AVAILABLE))
                        .collect(Collectors.toList());
    }

    populateTicketsForLogFeedbacks(logAnalysisRadarChartListDTOPageResponse.getContent());
    return LogAnalysisRadarChartListWithCountDTO.builder()
        .totalClusters(totalClusters)
        .eventCounts(eventCounts)
        .logAnalysisRadarCharts(logAnalysisRadarChartListDTOPageResponse)
        .build();
  }

  private void populateTicketsForLogFeedbacks(List<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDtos) {
    log.info("Populating ticket details for logFeedbacks.");
    logAnalysisRadarChartListDtos.stream()
        .filter(logAnalysis -> Objects.nonNull(logAnalysis.getFeedback()))
        .forEach(logAnalysis -> logAnalysis.setFeedback(getLogFeedbackWithTicket(logAnalysis.getFeedback())));
  }

  private LogFeedback getLogFeedbackWithTicket(LogFeedback logFeedback) {
    log.info("Populating ticket details for logFeedback with feedbackId {}.", logFeedback.getFeedbackId());
    TicketResponseDto ticketResponseDto = ticketService.getTicketForFeedbackId(logFeedback.getFeedbackId());
    return logFeedback.toBuilder().ticket(ticketResponseDto).build();
  }

  @Override
  public List<LogAnalysisRadarChartClusterDTO> getRadarChartLogAnalysisClusters(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<LogAnalysisRadarChartListDTO> logAnalysisResults =
        getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    logAnalysisResults = filterByAngle(logAnalysisResults, deploymentLogAnalysisFilter);
    return logAnalysisResults.stream()
        .map(key -> LogAnalysisRadarChartClusterDTO.buildWithLogAnalysisRadarChartListDTO(key))
        .collect(Collectors.toList());
  }

  private List<LogAnalysisRadarChartListDTO> filterByAngle(
      List<LogAnalysisRadarChartListDTO> logAnalysisResults, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<LogAnalysisRadarChartListDTO> filteredLogAnalysisResult = logAnalysisResults;
    if (deploymentLogAnalysisFilter.filterByAngle()) {
      Preconditions.checkArgument(
          deploymentLogAnalysisFilter.getMinAngle() >= 0 && deploymentLogAnalysisFilter.getMaxAngle() <= 360,
          "Angle filter range should be between 0 and 360");
      filteredLogAnalysisResult = logAnalysisResults.stream()
                                      .filter(result
                                          -> result.getAngle() >= deploymentLogAnalysisFilter.getMinAngle()
                                              && result.getAngle() <= deploymentLogAnalysisFilter.getMaxAngle())
                                      .collect(Collectors.toList());
    }
    return filteredLogAnalysisResult;
  }

  private List<LogAnalysisRadarChartListDTO> getRadarChartLogAnalysisResult(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<DeploymentLogAnalysis> latestDeploymentLogAnalysis =
        getLatestDeploymentLogAnalysis(accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    List<LogAnalysisRadarChartListDTO> allLogAnalysisRadarChartListDTOs = new ArrayList<>();
    for (DeploymentLogAnalysis deploymentLogAnalysis : latestDeploymentLogAnalysis) {
      allLogAnalysisRadarChartListDTOs.addAll(
          getRadarChartLogAnalysisResult(deploymentLogAnalysis, deploymentLogAnalysisFilter));
    }
    Collections.sort(allLogAnalysisRadarChartListDTOs);
    if (allLogAnalysisRadarChartListDTOs.size() > 0) {
      setAngleAndRadiusForRadarChart(allLogAnalysisRadarChartListDTOs);
    }
    return allLogAnalysisRadarChartListDTOs;
  }

  private void setAngleAndRadiusForRadarChart(List<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDTOS) {
    int totalSize = logAnalysisRadarChartListDTOS.size();
    Preconditions.checkArgument(totalSize != 0, "Radar CHart List size cannot be 0 for the angle calculation");
    double angleDifference = (double) 360 / totalSize;
    double angle = 0;
    Random random = new Random(123456789);
    for (int i = 0; i < logAnalysisRadarChartListDTOS.size(); i++) {
      LogAnalysisRadarChartListDTO logAnalysisRadarChartListDTO = logAnalysisRadarChartListDTOS.get(i);
      logAnalysisRadarChartListDTO.setAngle(angle);
      logAnalysisRadarChartListDTO.setRadius(
          getRandomRadiusInExpectedRange(logAnalysisRadarChartListDTO.getClusterType(), random));
      if (logAnalysisRadarChartListDTO.hasControlData()) {
        logAnalysisRadarChartListDTO.getBaseline().setAngle(angle);
        logAnalysisRadarChartListDTO.getBaseline().setRadius(
            getRandomRadiusInExpectedRange(logAnalysisRadarChartListDTO.getBaseline().getClusterType(), random));
      }
      angle += angleDifference;
      angle = Math.min(angle, 360);
    }
  }

  private double getRandomRadiusInExpectedRange(ClusterType clusterType, Random random) {
    if (clusterType.equals(ClusterType.BASELINE)) {
      return random.nextDouble() * 0.5 + 0.5;
    } else if (clusterType.equals(ClusterType.KNOWN_EVENT) || clusterType.equals(ClusterType.UNEXPECTED_FREQUENCY)) {
      return random.nextDouble() + 1;
    } else {
      return random.nextDouble() + 2;
    }
  }

  private List<LogAnalysisRadarChartListDTO> getRadarChartLogAnalysisResult(
      DeploymentLogAnalysis deploymentLogAnalysis, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDTOList = new ArrayList<>();
    Map<Integer, Cluster> labelToClusterMap = new HashMap<>();
    deploymentLogAnalysis.getClusters().forEach(cluster -> labelToClusterMap.put(cluster.getLabel(), cluster));

    ResultSummary resultSummary = null;
    if (deploymentLogAnalysisFilter.filterByHostNames()) {
      List<ClusterSummary> testClusterSummaryList =
          getFilteredTestClusterSummary(deploymentLogAnalysis.getResultSummary().getTestClusterSummaries(),
              deploymentLogAnalysisFilter.getHostNames());
      List<ClusterHostFrequencyData> filteredClusterClusterHostFrequencies = getFilteredControlClusterHostFrequencies(
          deploymentLogAnalysis.getResultSummary().getControlClusterHostFrequencies(),
          deploymentLogAnalysisFilter.getHostNames());
      ResultSummaryBuilder filteredResultSummaryBuilder = deploymentLogAnalysis.getResultSummary().toBuilder();
      filteredResultSummaryBuilder.testClusterSummaries(testClusterSummaryList)
          .controlClusterHostFrequencies(filteredClusterClusterHostFrequencies)
          .build();
      resultSummary = filteredResultSummaryBuilder.build();
    } else {
      // Make sure Error Tracking entries are filtered. Error Tracking for the time being is extending the use of Logs
      // until it gets its own type
      VerificationTask verificationTask = verificationTaskService.get(deploymentLogAnalysis.getVerificationTaskId());
      if (!ERROR_TRACKING.name().equals(verificationTask.getTags().get(TAG_DATA_SOURCE))) {
        resultSummary = deploymentLogAnalysis.getResultSummary();
      }
    }

    if (resultSummary == null) {
      return new ArrayList<>();
    }

    Map<Integer, ControlClusterSummary> controlClusters = new HashMap<>();

    resultSummary.getControlClusterSummaries().forEach(
        controlClusterSummary -> controlClusters.put(controlClusterSummary.getLabel(), controlClusterSummary));

    Map<Integer, ClusterHostFrequencyData> controlClusterFrequencyDataMap =
        CollectionUtils.emptyIfNull(deploymentLogAnalysis.getResultSummary().getControlClusterHostFrequencies())
            .stream()
            .collect(Collectors.toMap(
                ClusterHostFrequencyData::getLabel, clusterHostFrequencyData -> clusterHostFrequencyData));

    for (ClusterSummary testClusterSummary : resultSummary.getTestClusterSummaries()) {
      if (!deploymentLogAnalysisFilter.filterByClusterType()
          || deploymentLogAnalysisFilter.getClusterTypes().contains(testClusterSummary.getClusterType())) {
        List<TimestampFrequencyCount> totalTestFrequencyData =
            getTotalTestFrequencyData(testClusterSummary, deploymentLogAnalysis.getVerificationTaskId());
        List<TimestampFrequencyCount> averageControlFrequencyData = new ArrayList<>();
        if (controlClusterFrequencyDataMap.get(testClusterSummary.getLabel()) != null) {
          averageControlFrequencyData =
              getAverageControlFrequencyData(controlClusterFrequencyDataMap.get(testClusterSummary.getLabel()),
                  deploymentLogAnalysis.getVerificationTaskId());
        }
        List<HostFrequencyData> testHostFrequencyData = getTestHostFrequencyData(
            testClusterSummary.getFrequencyData(), deploymentLogAnalysis.getVerificationTaskId());
        LogAnalysisRadarChartListDTOBuilder logAnalysisRadarChartListDTOBuilder =
            LogAnalysisRadarChartListDTO.builder()
                .clusterId(UUID.nameUUIDFromBytes(
                                   (deploymentLogAnalysis.getVerificationTaskId() + ":" + testClusterSummary.getLabel())
                                       .getBytes(StandardCharsets.UTF_8))
                               .toString())
                .message(labelToClusterMap.get(testClusterSummary.getLabel()).getText())
                .clusterType(testClusterSummary.getClusterType())
                .previousClusterType(testClusterSummary.getPreviousClusterType())
                .risk(testClusterSummary.getRiskLevel())
                .previousRisk(testClusterSummary.getPreviousRiskLevel())
                .totalTestFrequencyData(totalTestFrequencyData)
                .testHostFrequencyData(testHostFrequencyData)
                .count(getCountFromTotalTestFrequencyData(totalTestFrequencyData))
                .feedback(testClusterSummary.getFeedback())
                .feedbackApplied(testClusterSummary.getFeedbackApplied())
                .averageControlFrequencyData(averageControlFrequencyData);
        if (testClusterSummary.getClusterType().equals(ClusterType.KNOWN_EVENT)
            || testClusterSummary.getClusterType().equals(ClusterType.UNEXPECTED_FREQUENCY)) {
          LogAnalysisRadarChartListDTOBuilder controlLogAnalysisChartListDTOBuilder =
              LogAnalysisRadarChartListDTO.builder()
                  .label(testClusterSummary.getLabel())
                  .message(labelToClusterMap.get(testClusterSummary.getLabel()).getText())
                  .clusterType(ClusterType.BASELINE)
                  .risk(Risk.NO_ANALYSIS);
          logAnalysisRadarChartListDTOBuilder.baseline(controlLogAnalysisChartListDTOBuilder.build());
        }
        logAnalysisRadarChartListDTOList.add(logAnalysisRadarChartListDTOBuilder.build());
      }
    }
    if (deploymentLogAnalysisFilter.hasClusterIdFilter()) {
      logAnalysisRadarChartListDTOList =
          logAnalysisRadarChartListDTOList.stream()
              .filter(logAnalysisRadarChartListDTO
                  -> logAnalysisRadarChartListDTO.getClusterId().equals(deploymentLogAnalysisFilter.getClusterId()))
              .collect(Collectors.toList());
      Preconditions.checkState(
          logAnalysisRadarChartListDTOList.size() <= 1, "clusterId filter should result in one or zero cluster");
    }
    // remove clusters with 0 count
    logAnalysisRadarChartListDTOList =
        logAnalysisRadarChartListDTOList.stream()
            .filter(logAnalysisRadarChartListDTO -> logAnalysisRadarChartListDTO.getCount() != 0)
            .collect(Collectors.toList());
    return logAnalysisRadarChartListDTOList;
  }

  private List<ClusterHostFrequencyData> getFilteredControlClusterHostFrequencies(
      List<ClusterHostFrequencyData> controlClusterHostFrequencies, List<String> hostNames) {
    List<ClusterHostFrequencyData> filteredClusterHostFrequencyData = new ArrayList<>();
    if (controlClusterHostFrequencies == null) {
      return filteredClusterHostFrequencyData;
    }
    for (ClusterHostFrequencyData clusterHostFrequencyData : controlClusterHostFrequencies) {
      List<HostFrequencyData> hostFrequencyDataList =
          getFilteredHostFrequencyDataList(clusterHostFrequencyData.getFrequencyData(), hostNames);
      ClusterHostFrequencyDataBuilder clusterHostFrequencyDataBuilder = clusterHostFrequencyData.toBuilder();
      clusterHostFrequencyDataBuilder.frequencyData(hostFrequencyDataList);
      filteredClusterHostFrequencyData.add(clusterHostFrequencyDataBuilder.build());
    }
    return filteredClusterHostFrequencyData;
  }

  private List<ClusterSummary> getFilteredTestClusterSummary(
      List<ClusterSummary> originalClusterSummaryList, List<String> hostNames) {
    List<ClusterSummary> filteredClusterSummary = new ArrayList<>();
    for (ClusterSummary c : originalClusterSummaryList) {
      List<HostFrequencyData> hostFrequencyDataList = getFilteredHostFrequencyDataList(c.getFrequencyData(), hostNames);
      ClusterSummaryBuilder clusterSummaryBuilder = c.toBuilder();
      clusterSummaryBuilder.frequencyData(hostFrequencyDataList);
      filteredClusterSummary.add(clusterSummaryBuilder.build());
    }
    return filteredClusterSummary;
  }

  private List<HostFrequencyData> getFilteredHostFrequencyDataList(
      List<HostFrequencyData> frequencyDataList, List<String> hostNames) {
    List<HostFrequencyData> filteredHostNames = new ArrayList<>();
    if (frequencyDataList == null) {
      return filteredHostNames;
    }
    for (HostFrequencyData hostFrequencyData : frequencyDataList) {
      if (hostNames.contains(hostFrequencyData.getHost())) {
        filteredHostNames.add(hostFrequencyData);
      }
    }
    return filteredHostNames;
  }

  private int getCountFromTotalTestFrequencyData(List<TimestampFrequencyCount> totalTestFrequencyData) {
    return totalTestFrequencyData.stream().map(TimestampFrequencyCount::getCount).mapToInt(Double::intValue).sum();
  }

  private List<HostFrequencyData> getTestHostFrequencyData(
      List<HostFrequencyData> frequencyData, String verificationTaskId) {
    List<HostFrequencyData> testHostFrequencyData = new ArrayList<>();
    if (frequencyData == null) {
      return testHostFrequencyData;
    }
    String verificationJobInstanceId = verificationTaskService.getVerificationJobInstanceId(verificationTaskId);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    long startTimeMinutes = verificationJobInstance.getStartTime().getEpochSecond() / SECONDS_PER_MINUTE;
    long endTimeInMinutes = startTimeMinutes + verificationJobInstance.getResolvedJob().getDuration().toMinutes();
    for (HostFrequencyData hostFrequencyData : frequencyData) {
      Map<Long, Double> timeStampFrequencyCountMap = new HashMap<>();
      for (TimestampFrequencyCount timestampFrequencyCount : hostFrequencyData.getFrequencies()) {
        timeStampFrequencyCountMap.put(timestampFrequencyCount.getTimeStamp(), timestampFrequencyCount.getCount());
      }
      List<TimestampFrequencyCount> timestampFrequencyCountList = new ArrayList<>();
      for (Long time = startTimeMinutes; time < endTimeInMinutes; time++) {
        TimestampFrequencyCount timestampFrequencyCount = TimestampFrequencyCount.builder()
                                                              .count(timeStampFrequencyCountMap.getOrDefault(time, 0.0))
                                                              .timeStamp(time * SECONDS_PER_MINUTE)
                                                              .build();
        timestampFrequencyCountList.add(timestampFrequencyCount);
      }
      HostFrequencyData updatedHostFrequencyData = HostFrequencyData.builder()
                                                       .frequencies(timestampFrequencyCountList)
                                                       .host(hostFrequencyData.getHost())
                                                       .build();
      testHostFrequencyData.add(updatedHostFrequencyData);
    }
    return testHostFrequencyData;
  }

  private List<TimestampFrequencyCount> getAverageControlFrequencyData(
      ClusterHostFrequencyData clusterHostFrequencyData, String verificationTaskId) {
    List<TimestampFrequencyCount> timestampFrequencyCountList = new ArrayList<>();
    Map<Long, List<Double>> timeStampFrequencyCountMap = new HashMap<>();
    for (HostFrequencyData hostFrequencyData : clusterHostFrequencyData.getFrequencyData()) {
      for (TimestampFrequencyCount timestampFrequencyCount : hostFrequencyData.getFrequencies()) {
        List<Double> countList =
            timeStampFrequencyCountMap.getOrDefault(timestampFrequencyCount.getTimeStamp(), new ArrayList<>());
        if (timestampFrequencyCount.getCount() != null) {
          countList.add(timestampFrequencyCount.getCount());
          timeStampFrequencyCountMap.put(timestampFrequencyCount.getTimeStamp(), countList);
        }
      }
    }

    String verificationJobInstanceId = verificationTaskService.getVerificationJobInstanceId(verificationTaskId);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    long startTimeMinutes;
    long endTimeMinutes;
    if (verificationJobInstance.getResolvedJob().getType() == VerificationJobType.CANARY) {
      Instant startTime = verificationJobInstance.getStartTime();
      Instant endTime = verificationJobInstance.getEndTime();

      startTimeMinutes = startTime.getEpochSecond() / SECONDS_PER_MINUTE;
      endTimeMinutes = endTime.getEpochSecond() / SECONDS_PER_MINUTE;
    } else {
      Instant endTime = verificationJobInstance.getDeploymentStartTime();
      Instant startTime = endTime.minus(verificationJobInstance.getResolvedJob().getDuration());

      startTimeMinutes = startTime.getEpochSecond() / SECONDS_PER_MINUTE;
      endTimeMinutes = endTime.getEpochSecond() / SECONDS_PER_MINUTE;
    }

    for (Long time = startTimeMinutes; time < endTimeMinutes; time++) {
      List<Double> countList = timeStampFrequencyCountMap.get(time);
      double avg;
      if (isEmpty(countList)) {
        avg = 0.0;
      } else {
        avg = countList.stream().mapToDouble(i -> i).sum() / countList.size();
      }
      avg = Math.round(avg * 100) / 100.00;
      TimestampFrequencyCount timestampFrequencyCount =
          TimestampFrequencyCount.builder().count(avg).timeStamp(time * SECONDS_PER_MINUTE).build();
      timestampFrequencyCountList.add(timestampFrequencyCount);
    }
    return timestampFrequencyCountList;
  }

  private List<TimestampFrequencyCount> getTotalTestFrequencyData(
      ClusterSummary testClusterSummary, String verificationTaskId) {
    List<TimestampFrequencyCount> timestampFrequencyCountList = new ArrayList<>();
    // In case of old verification job, host frequency data won't be available so return empty
    // timeStampFrequencyCountList in that case.
    if (testClusterSummary.getFrequencyData() == null) {
      return timestampFrequencyCountList;
    }

    Map<Long, List<Double>> timeStampFrequencyCountMap = new HashMap<>();
    for (HostFrequencyData hostFrequencyData : testClusterSummary.getFrequencyData()) {
      for (TimestampFrequencyCount timestampFrequencyCount : hostFrequencyData.getFrequencies()) {
        List<Double> countList =
            timeStampFrequencyCountMap.getOrDefault(timestampFrequencyCount.getTimeStamp(), new ArrayList<>());
        if (timestampFrequencyCount.getCount() != null) {
          countList.add(timestampFrequencyCount.getCount());
          timeStampFrequencyCountMap.put(timestampFrequencyCount.getTimeStamp(), countList);
        }
      }
    }

    String verificationJobInstanceId = verificationTaskService.getVerificationJobInstanceId(verificationTaskId);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    long startTimeMinutes = verificationJobInstance.getStartTime().getEpochSecond() / SECONDS_PER_MINUTE;
    long endTimeInMinutes = startTimeMinutes + verificationJobInstance.getResolvedJob().getDuration().toMinutes();
    for (Long time = startTimeMinutes; time < endTimeInMinutes; time++) {
      List<Double> countList = timeStampFrequencyCountMap.get(time);
      double sum;
      if (isEmpty(countList)) {
        sum = 0.0;
      } else {
        sum = countList.stream().mapToDouble(i -> i).sum();
      }
      TimestampFrequencyCount timestampFrequencyCount =
          TimestampFrequencyCount.builder().count(sum).timeStamp(time * SECONDS_PER_MINUTE).build();
      timestampFrequencyCountList.add(timestampFrequencyCount);
    }
    return timestampFrequencyCountList;
  }
}
