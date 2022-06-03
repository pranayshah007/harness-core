/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.TAG_DATA_SOURCE;
import static io.harness.cvng.beans.MonitoredServiceDataSourceType.ERROR_TRACKING;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.ErrorAnalysisSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.LogsAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.Cluster;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
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
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.VerificationTaskKeys;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
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
import java.io.IOException;
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
import org.mongodb.morphia.query.Sort;

@Slf4j
public class DeploymentLogAnalysisServiceImpl implements DeploymentLogAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
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
          clusterSummary -> { clusterSummaryMap.put(clusterSummary.getLabel(), clusterSummary); });

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
            .map(logAnalysisClusterDTO -> logAnalysisClusterDTO.getClusterType())
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
          Comparator.comparingDouble(logAnalysis -> logAnalysis.getResultSummary().getScore()));
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

  @Override
  public void addDemoAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, String demoTemplatePath) {
    try {
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
        deploymentLogAnalysis.setEndTime(deploymentLogAnalysis.getStartTime().plus(Duration.ofMinutes(1)));
      }
      hPersistence.save(deploymentLogAnalyses);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
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
                   .map(DeploymentLogAnalysisDTO.HostSummary::getHost)
                   .filter(host -> !DataSourceType.ERROR_TRACKING.getDisplayName().equals(host)))
        .collect(Collectors.toSet());
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
                                        .map(logAnalysisClusterDTO -> logAnalysisClusterDTO.getClusterType())
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

    return LogAnalysisRadarChartListWithCountDTO.builder()
        .totalClusters(totalClusters)
        .eventCounts(eventCounts)
        .logAnalysisRadarCharts(logAnalysisRadarChartListDTOPageResponse)
        .build();
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
      for (DeploymentLogAnalysisDTO.HostSummary hostSummary : deploymentLogAnalysis.getHostSummaries()) {
        if (deploymentLogAnalysisFilter.getHostNames().contains(hostSummary.getHost())) {
          resultSummary = hostSummary.getResultSummary();
          break;
        }
      }
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

    Map<Integer, DeploymentLogAnalysisDTO.ControlClusterSummary> controlClusters = new HashMap<>();

    resultSummary.getControlClusterSummaries().forEach(
        controlClusterSummary -> controlClusters.put(controlClusterSummary.getLabel(), controlClusterSummary));

    for (ClusterSummary testClusterSummary : resultSummary.getTestClusterSummaries()) {
      if (!deploymentLogAnalysisFilter.filterByClusterType()
          || deploymentLogAnalysisFilter.getClusterTypes().contains(testClusterSummary.getClusterType())) {
        LogAnalysisRadarChartListDTOBuilder logAnalysisRadarChartListDTOBuilder =
            LogAnalysisRadarChartListDTO.builder()
                .clusterId(UUID.nameUUIDFromBytes(
                                   (deploymentLogAnalysis.getVerificationTaskId() + ":" + testClusterSummary.getLabel())
                                       .getBytes(Charsets.UTF_8))
                               .toString())
                .label(testClusterSummary.getLabel())
                .message(labelToClusterMap.get(testClusterSummary.getLabel()).getText())
                .clusterType(testClusterSummary.getClusterType())
                .risk(testClusterSummary.getRiskLevel())
                .frequencyData(testClusterSummary.getTestFrequencyData())
                .count(testClusterSummary.getCount());
        if (testClusterSummary.getClusterType().equals(ClusterType.KNOWN_EVENT)
            || testClusterSummary.getClusterType().equals(ClusterType.UNEXPECTED_FREQUENCY)) {
          LogAnalysisRadarChartListDTOBuilder controlLogAnalysisChartListDTOBuilder =
              LogAnalysisRadarChartListDTO.builder()
                  .label(testClusterSummary.getLabel())
                  .message(labelToClusterMap.get(testClusterSummary.getLabel()).getText())
                  .clusterType(ClusterType.BASELINE)
                  .risk(Risk.NO_ANALYSIS);
          // In some scenerio's the LE is not saving the control data for host specific data. Throwing warning in case
          // if the control data is not present
          if (controlClusters.containsKey(testClusterSummary.getLabel())) {
            controlLogAnalysisChartListDTOBuilder.frequencyData(
                controlClusters.get(testClusterSummary.getLabel()).getControlFrequencyData());
          } else {
            log.warn("control data is not present for verificationTaskId: %s",
                deploymentLogAnalysis.getVerificationTaskId());
          }
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
    return logAnalysisRadarChartListDTOList;
  }
}
