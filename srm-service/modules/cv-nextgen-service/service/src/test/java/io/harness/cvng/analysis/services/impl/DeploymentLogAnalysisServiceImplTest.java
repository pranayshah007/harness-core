/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.BASELINE;
import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.KNOWN_EVENT;
import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.UNEXPECTED_FREQUENCY;
import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.UNKNOWN_EVENT;
import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.cvng.beans.DataSourceType.ERROR_TRACKING;
import static io.harness.cvng.beans.DataSourceType.SPLUNK;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.BGROVES;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NEMANJA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.LogsAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.Cluster;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterCoordinates;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ControlClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO.EventCount;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListWithCountDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.data.structure.CollectionUtils;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DeploymentLogAnalysisServiceImplTest extends CvNextGenTestBase {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  private String accountId;
  private String cvConfigId;
  private String verificationJobInstanceId;
  private String cvConfigId2;
  BuilderFactory builderFactory;
  private String radarChartVerificationJobInstanceId;
  private String verificationTaskId;

  @Before
  public void setUp() throws IOException {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    cvConfigId =
        verificationJobInstance.getCvConfigMap().values().stream().collect(Collectors.toList()).get(0).getUuid();
    cvConfigId2 =
        verificationJobInstance.getCvConfigMap().values().stream().collect(Collectors.toList()).get(1).getUuid();
    verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);

    VerificationJobInstance radarChartVerificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    radarChartVerificationJobInstanceId = verificationJobInstanceService.create(radarChartVerificationJobInstance);
    setUpDummyDeploymentLogAnalysis();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClusters() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOlist =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);

    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClustersWithClusterTypeFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                                                  .hostName(null)
                                                                  .build();

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOlist =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(List.of(UNKNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClustersWithHealthIdentifierFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                                                  .hostName(null)
                                                                  .build();

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOlist =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);

    String cvConfigIdentifier = verificationJobInstanceService.get(Arrays.asList(verificationJobInstanceId))
                                    .get(0)
                                    .getCvConfigMap()
                                    .values()
                                    .stream()
                                    .collect(Collectors.toList())
                                    .get(0)
                                    .getIdentifier();

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList(cvConfigIdentifier))
                                      .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList("some-random-identifier"))
                                      .clusterTypes(Arrays.asList(UNKNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList(cvConfigIdentifier))
                                      .clusterTypes(null)
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList(cvConfigIdentifier))
                                      .clusterTypes(Arrays.asList(UNKNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClusters_WithHostNameFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(null)
                                                                  .hostNames(Collections.singletonList("node2"))
                                                                  .build();

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOlist =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getText()).isEqualTo("Error in cluster 2");
    assertThat(logAnalysisClusterChartDTOlist.get(0).getLabel()).isEqualTo(2);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getX()).isEqualTo(0.4525);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getY()).isEqualTo(0.542524);
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClustersErrorTracking() {
    String verificationTaskIdErrorTracking = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId2, verificationJobInstanceId, ERROR_TRACKING);
    DeploymentLogAnalysis deploymentLogAnalysisErrorTracking =
        createDeploymentLogAnalysis(verificationTaskIdErrorTracking, "ET", "ErrorTracking", "ErrorTracking");
    deploymentLogAnalysisService.save(deploymentLogAnalysisErrorTracking);

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).build();
    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOlist =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("ErrorTracking"))
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(2);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getHostName()).isEqualTo("ErrorTracking");
    assertThat(logAnalysisClusterChartDTOlist.get(1).getHostName()).isEqualTo("ErrorTracking");

    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getHostName()).isEqualTo("node1");
    assertThat(logAnalysisClusterChartDTOlist.get(1).getHostName()).isEqualTo("node2");
    assertThat(logAnalysisClusterChartDTOlist.get(2).getHostName()).isEqualTo("node3");

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("node1"))
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(1);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getHostName()).isEqualTo("node1");

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("ErrorTracking"))
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist).isNotNull();
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(2);
    assertThat(logAnalysisClusterChartDTOlist.get(0).getHostName()).isEqualTo("ErrorTracking");
    assertThat(logAnalysisClusterChartDTOlist.get(1).getHostName()).isEqualTo("ErrorTracking");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();

    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultWithClusterFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                                                  .hostName(null)
                                                                  .build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(1);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(Arrays.asList(ClusterType.UNEXPECTED_FREQUENCY))
                                      .hostName(null)
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultWithHealthSourceIdentifierFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigIdentifier = verificationJobInstanceService.get(Arrays.asList(verificationJobInstanceId))
                                    .get(0)
                                    .getCvConfigMap()
                                    .values()
                                    .stream()
                                    .collect(Collectors.toList())
                                    .get(0)
                                    .getIdentifier();

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder()
            .healthSourceIdentifiers(Arrays.asList(cvConfigIdentifier))
            .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
            .hostName(null)
            .build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(1);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList("some-identifier"))
                                      .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisClusters_withNoDeploymentLogAnalysis() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();

    List<LogAnalysisClusterChartDTO> logAnalysisClusterChartDTOList =
        deploymentLogAnalysisService.getLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOList).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withLabelFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, 1, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getScore()).isEqualTo(0.7);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withWrongLabel() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();

    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, 15, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withHostNameFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(null)
                                                                  .hostName("node2")
                                                                  .build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultErrorTracking() {
    String verificationTaskIdErrorTracking = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId2, verificationJobInstanceId, ERROR_TRACKING);
    DeploymentLogAnalysis deploymentLogAnalysisErrorTracking =
        createDeploymentLogAnalysis(verificationTaskIdErrorTracking, "ET", "ErrorTracking", "ErrorTracking");
    deploymentLogAnalysisService.save(deploymentLogAnalysisErrorTracking);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("ErrorTracking"))
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(6);

    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);

    pageParams = PageParams.builder().page(0).size(10).build();
    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("node1"))
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(3);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostNames(Collections.singletonList("ErrorTracking"))
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(6);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withWrongHostNameFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(null)
                                                                  .hostName(generateUuid())
                                                                  .build();

    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withMultipleLogAnalyses() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    DeploymentLogAnalysis deploymentLogAnalysis2 = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysis2.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS));
    deploymentLogAnalysis2.setClusters(Arrays.asList(createCluster("Error in cluster 4", 4)));
    ClusterSummary clusterSummary = createClusterSummary(0, 0, 0, 4, null, ClusterType.KNOWN_EVENT);
    deploymentLogAnalysis2.setResultSummary(createResultSummary(0, 0, Arrays.asList(clusterSummary), null));
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    deploymentLogAnalysisService.save(deploymentLogAnalysis2);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.getContent()).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(4);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withMultiplePages() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    List<Cluster> clusters = new ArrayList();
    List<ClusterSummary> clusterSummaries = new ArrayList();
    for (int i = 0; i < 25; i++) {
      clusters.add(createCluster("Cluster " + i, i));
      clusterSummaries.add(createClusterSummary(0, 0, 0, i, null, ClusterType.KNOWN_EVENT));
    }
    deploymentLogAnalysis.setClusters(clusters);
    deploymentLogAnalysis.setResultSummary(createResultSummary(0, 0, clusterSummaries, null));
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse1 = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse1.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse1.getTotalPages()).isEqualTo(3);
    assertThat(pageResponse1.getContent()).isNotNull();
    assertThat(pageResponse1.getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(1).size(10).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse2 = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse2.getPageIndex()).isEqualTo(1);
    assertThat(pageResponse2.getTotalPages()).isEqualTo(3);
    assertThat(pageResponse2.getContent()).isNotNull();
    assertThat(pageResponse2.getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(2).size(10).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse3 = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse3.getPageIndex()).isEqualTo(2);
    assertThat(pageResponse3.getTotalPages()).isEqualTo(3);
    assertThat(pageResponse3.getContent()).isNotNull();
    assertThat(pageResponse3.getContent().size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withoutDeploymentLogAnalysis() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getPageIndex()).isEqualTo(0);
    assertThat(pageResponse.getTotalPages()).isEqualTo(0);
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResult_withWrongVerificationJobInstanceId() {
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    PageResponse<LogAnalysisClusterDTO> pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, generateUuid(), null, deploymentLogAnalysisFilter, pageParams);
    assertThat(pageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withoutFilters() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, DeploymentLogAnalysisFilter.builder().build(), pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getTotalClusters()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getClusterType())
        .isEqualTo(ClusterType.KNOWN_EVENT);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getDisplayName()).isEqualTo("Known");
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(1);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getLabel()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withClusterTypeFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder()
            .clusterTypes(Arrays.asList(UNKNOWN_EVENT, ClusterType.UNEXPECTED_FREQUENCY))
            .build();

    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getTotalClusters()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().size()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(2);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT)).build();
    logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getTotalClusters()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_WithHealthSourceIdentifierFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    String cvConfigIdentifier = verificationJobInstanceService.get(Arrays.asList(verificationJobInstanceId))
                                    .get(0)
                                    .getCvConfigMap()
                                    .values()
                                    .stream()
                                    .collect(Collectors.toList())
                                    .get(0)
                                    .getIdentifier();

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder()
            .healthSourceIdentifiers(Arrays.asList(cvConfigIdentifier))
            .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
            .hostName(null)
            .build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getLabel()).isEqualTo(1);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(Arrays.asList("some-identifier"))
                                      .clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withLabelFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, 1, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getLabel()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getScore()).isEqualTo(0.7);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withWrongLabel() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();

    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, 15, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withHostNameFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(null)
                                                                  .hostName("node2")
                                                                  .build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withWrongHostNameFilter() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                                                  .healthSourceIdentifiers(null)
                                                                  .clusterTypes(null)
                                                                  .hostName(generateUuid())
                                                                  .build();

    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withMultipleLogAnalyses() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    DeploymentLogAnalysis deploymentLogAnalysis2 = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysis2.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS));
    deploymentLogAnalysis2.setClusters(Arrays.asList(createCluster("Error in cluster 4", 4)));
    ClusterSummary clusterSummary = createClusterSummary(0, 0, 0, 4, null, ClusterType.KNOWN_EVENT);
    deploymentLogAnalysis2.setResultSummary(createResultSummary(0, 0, Arrays.asList(clusterSummary), null));
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    deploymentLogAnalysisService.save(deploymentLogAnalysis2);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getLabel()).isEqualTo(4);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withMultiplePages() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    List<Cluster> clusters = new ArrayList();
    List<ClusterSummary> clusterSummaries = new ArrayList();
    for (int i = 0; i < 25; i++) {
      clusters.add(createCluster("Cluster " + i, i));
      clusterSummaries.add(createClusterSummary(0, 0, 0, i, null, ClusterType.KNOWN_EVENT));
    }
    deploymentLogAnalysis.setClusters(clusters);
    deploymentLogAnalysis.setResultSummary(createResultSummary(0, 0, clusterSummaries, null));
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO1 =
        deploymentLogAnalysisService.getLogAnalysisResultV2(
            accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO1.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO1.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO1.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO1.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(1).size(10).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO2 =
        deploymentLogAnalysisService.getLogAnalysisResultV2(
            accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO2.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO2.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO2.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO2.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(2).size(10).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO3 =
        deploymentLogAnalysisService.getLogAnalysisResultV2(
            accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO3.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(2);
    assertThat(logAnalysisClusterWithCountDTO3.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO3.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO3.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withoutDeploymentLogAnalysis() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getTotalClusters()).isEqualTo(0);
    for (EventCount eventCount : logAnalysisClusterWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(0);
    }
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogAnalysisResultV2_withWrongVerificationJobInstanceId() {
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisClusterWithCountDTO logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, generateUuid(), null, deploymentLogAnalysisFilter, pageParams);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_noData() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    assertThat(deploymentLogAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId))
        .isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_verificationTaskIdsDoesNotExists() {
    assertThatThrownBy(
        () -> deploymentLogAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_getLatestData() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    List<ClusterSummary> clusterSummaries = new ArrayList();
    List<Cluster> clusters = new ArrayList();
    for (int i = 0; i < 25; i++) {
      clusterSummaries.add(createClusterSummary(0, 0, 0, i, null, ClusterType.KNOWN_EVENT));
    }
    deploymentLogAnalysis.setClusters(clusters);
    deploymentLogAnalysis.setResultSummary(createResultSummary(0, .7654, clusterSummaries, null));
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    assertThat(deploymentLogAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId).get())
        .isEqualTo(Risk.HEALTHY);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogsAnalysisSummary summary =
        deploymentLogAnalysisService.getAnalysisSummary(accountId, Arrays.asList(verificationJobInstanceId));
    assertThat(summary).isNotNull();
    assertThat(summary.getAnomalousClusterCount()).isEqualTo(2);
    assertThat(summary.getTotalClusterCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary_noAnalysisYet() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    LogsAnalysisSummary summary =
        deploymentLogAnalysisService.getAnalysisSummary(accountId, Arrays.asList(verificationJobInstanceId));
    assertThat(summary).isNotNull();
    assertThat(summary.getAnomalousClusterCount()).isEqualTo(0);
    assertThat(summary.getTotalClusterCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary_nullInput() {
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    assertThatThrownBy(() -> deploymentLogAnalysisService.getAnalysisSummary(accountId, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Missing verificationJobInstanceIds when looking for summary");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    List<LogAnalysisRadarChartClusterDTO> summary = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build());
    assertThat(summary.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_sortingOrder() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
            accountId, verificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build());

    assertThat(logAnalysisRadarChartClusterDTOS.get(0).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartClusterDTOS.get(0).getAngle()).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_filterWithEventType() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNKNOWN_EVENT)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNEXPECTED_FREQUENCY)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(KNOWN_EVENT)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_filterWithHostName() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("host")).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(3);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().size()).isEqualTo(4);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(3);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getPageItemCount()).isEqualTo(3);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getPageSize()).isEqualTo(20);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);

    LogAnalysisRadarChartListDTO content1 =
        logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(0);
    assertThat(content1.getFeedback()).isNotNull();
    assertThat(content1.getFeedbackApplied()).isNotNull();

    LogAnalysisRadarChartListDTO content2 =
        logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(1);
    assertThat(content2.getFeedback()).isNotNull();
    assertThat(content2.getFeedbackApplied()).isNotNull();

    LogAnalysisRadarChartListDTO content3 =
        logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(2);
    assertThat(content3.getFeedback()).isNotNull();
    assertThat(content3.getFeedbackApplied()).isNotNull();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_pagination() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(2).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
    Map<ClusterType, Integer> expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 1);
    expectedCount.put(KNOWN_EVENT, 1);
    expectedCount.put(UNKNOWN_EVENT, 1);
    expectedCount.put(BASELINE, 2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getPageItemCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_sortingOrder() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(3);
    List<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDTOS =
        logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent();

    assertThat(logAnalysisRadarChartListDTOS.get(0).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartListDTOS.get(0).getAngle()).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_eventCountsWithBaseline() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(3);
    List<EventCount> eventCounts = logAnalysisRadarChartListWithCountDTO.getEventCounts();
    assertThat(eventCounts.get(0).getClusterType()).isEqualTo(KNOWN_EVENT);
    assertThat(eventCounts.get(0).getCount()).isEqualTo(1);
    assertThat(eventCounts.get(1).getClusterType()).isEqualTo(UNEXPECTED_FREQUENCY);
    assertThat(eventCounts.get(1).getCount()).isEqualTo(1);
    assertThat(eventCounts.get(2).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(eventCounts.get(2).getCount()).isEqualTo(1);
    assertThat(eventCounts.get(3).getClusterType()).isEqualTo(BASELINE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByEventType() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNEXPECTED_FREQUENCY)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(KNOWN_EVENT)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNKNOWN_EVENT)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      if (eventCount.getClusterType() == UNKNOWN_EVENT) {
        assertThat(eventCount.getCount()).isEqualTo(0);
      } else {
        assertThat(eventCount.getCount()).isEqualTo(0);
      }
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByHost() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("host")).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(3);
    Map<ClusterType, Integer> expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 1);
    expectedCount.put(KNOWN_EVENT, 1);
    expectedCount.put(UNKNOWN_EVENT, 1);
    expectedCount.put(BASELINE, 1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(3);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByAngle() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().minAngle(10.0).maxAngle(200.0).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(0);

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterClusterIdZeroResults() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterId("clusterId").build(),
            PageParams.builder().page(0).size(20).build());
    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByClusterId() {
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, verificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder()
                .clusterId(UUID.nameUUIDFromBytes((verificationTaskId + ":" + 1).getBytes(Charsets.UTF_8)).toString())
                .build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent()).hasSize(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNodeNames() {
    assertThat(deploymentLogAnalysisService.getNodeNames(accountId, verificationJobInstanceId)).isEmpty();
    assertThat(deploymentLogAnalysisService.getNodeNames(accountId, radarChartVerificationJobInstanceId))
        .isEqualTo(Sets.newHashSet("host1", "host2", "host3", "host4"));
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClustersErrorTracking() {
    String verificationTaskIdErrorTracking = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId2, verificationJobInstanceId, ERROR_TRACKING);
    DeploymentLogAnalysis deploymentLogAnalysisErrorTracking =
        createDeploymentLogAnalysis(verificationTaskIdErrorTracking, "ET", "ErrorTracking", "ErrorTracking");
    deploymentLogAnalysisService.save(deploymentLogAnalysisErrorTracking);

    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    List<LogAnalysisRadarChartClusterDTO> radarChartLogAnalysisClusters =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isZero();

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisClusters = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isZero();
  }

  @Test
  @Owner(developers = BGROVES)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisResultErrorTracking() {
    String verificationTaskIdErrorTracking = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId2, verificationJobInstanceId, ERROR_TRACKING);
    DeploymentLogAnalysis deploymentLogAnalysisErrorTracking =
        createDeploymentLogAnalysis(verificationTaskIdErrorTracking, "ET", "ErrorTracking", "ErrorTracking");
    deploymentLogAnalysisService.save(deploymentLogAnalysisErrorTracking);

    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    LogAnalysisRadarChartListWithCountDTO radarChartLogAnalysisResult =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
            accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isZero();

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isZero();

    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(0).getMessage())
        .doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(1).getMessage())
        .doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(2).getMessage())
        .doesNotStartWith("ET");

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("node1")
                                      .build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);
  }

  private DeploymentLogAnalysis createDeploymentLogAnalysis(String verificationTaskId) {
    return createDeploymentLogAnalysis(verificationTaskId, "", "node1", "node2");
  }

  private DeploymentLogAnalysis createDeploymentLogAnalysis(
      String verificationTaskId, String msgPrefix, String hostname1, String hostname2) {
    Cluster cluster1 = createCluster(msgPrefix + "Error in cluster 1", 1);
    Cluster cluster2 = createCluster(msgPrefix + "Error in cluster 2", 2);
    Cluster cluster3 = createCluster(msgPrefix + "Error in cluster 3", 3);
    List<Cluster> clusters = Arrays.asList(cluster1, cluster2, cluster3);

    ClusterCoordinates clusterCoordinates1 = createClusterCoordinates(hostname1, 1, 0.6464, 0.717171);
    ClusterCoordinates clusterCoordinates2 = createClusterCoordinates(hostname2, 2, 0.4525, 0.542524);
    ClusterCoordinates clusterCoordinates3 = createClusterCoordinates("node3", 3, 0.2131, 0.4151);
    List<ClusterCoordinates> clusterCoordinatesList =
        Arrays.asList(clusterCoordinates1, clusterCoordinates2, clusterCoordinates3);

    ClusterSummary clusterSummary1 = createClusterSummary(1, 0.7, 36, 1, Arrays.asList(2D), ClusterType.KNOWN_EVENT);
    ClusterSummary clusterSummary2 = createClusterSummary(0, 0, 3, 2, Arrays.asList(2D), UNKNOWN_EVENT);
    ClusterSummary clusterSummary3 = createClusterSummary(2, 2.2, 55, 3, Arrays.asList(4D), UNEXPECTED_FREQUENCY);

    ResultSummary resultSummary = createResultSummary(
        1, 1, Arrays.asList(clusterSummary1, clusterSummary2, clusterSummary3), getControlClusterSummaries(3));

    ClusterSummary clusterSummary4 = createClusterSummary(2, 0.7, 36, 1, Arrays.asList(2D), KNOWN_EVENT);
    ClusterSummary clusterSummary5 = createClusterSummary(2, 0, 3, 2, Arrays.asList(2D), UNKNOWN_EVENT);
    ClusterSummary clusterSummary6 = createClusterSummary(2, 2.2, 55, 3, Arrays.asList(4D), UNEXPECTED_FREQUENCY);

    ResultSummary resultSummary2 = createResultSummary(
        2, 1, Arrays.asList(clusterSummary4, clusterSummary5, clusterSummary6), getControlClusterSummaries(3));

    HostSummary hostSummary1 = createHostSummary(hostname1, resultSummary);
    HostSummary hostSummary2 = createHostSummary(hostname2, resultSummary2);
    return DeploymentLogAnalysis.builder()
        .accountId(accountId)
        .clusters(clusters)
        .clusterCoordinates(clusterCoordinatesList)
        .verificationTaskId(verificationTaskId)
        .resultSummary(resultSummary)
        .hostSummaries(Arrays.asList(hostSummary1, hostSummary2))
        .startTime(Instant.now())
        .endTime(Instant.now().plus(10, ChronoUnit.MINUTES))
        .build();
  }

  private ResultSummary createResultSummary(int risk, double score, List<ClusterSummary> testClusterSummaries,
      List<ControlClusterSummary> controlClusterSummaries) {
    return ResultSummary.builder()
        .risk(risk)
        .score(score)
        .controlClusterHostFrequencies(generateClusterHostFrequencyData(controlClusterSummaries))
        .controlClusterSummaries(controlClusterSummaries)
        .testClusterSummaries(testClusterSummaries)
        .build();
  }

  private List<DeploymentLogAnalysisDTO.ClusterHostFrequencyData> generateClusterHostFrequencyData(
      List<ControlClusterSummary> testClusterSummaries) {
    return CollectionUtils.emptyIfNull(testClusterSummaries)
        .stream()
        .map(clusterSummary
            -> DeploymentLogAnalysisDTO.ClusterHostFrequencyData.builder()
                   .frequencyData(Collections.singletonList(
                       DeploymentLogAnalysisDTO.HostFrequencyData.builder()
                           .frequencies(clusterSummary.getControlFrequencyData()
                                            .stream()
                                            .map(frequency
                                                -> DeploymentLogAnalysisDTO.TimestampFrequencyCount.builder()
                                                       .count(frequency)
                                                       .timeStamp(1L)
                                                       .build())
                                            .collect(Collectors.toList()))
                           .host("host")
                           .build()))
                   .label(clusterSummary.getLabel())
                   .build())
        .collect(Collectors.toList());
  }

  private ClusterCoordinates createClusterCoordinates(String hostName, int label, double x, double y) {
    return ClusterCoordinates.builder().host(hostName).label(label).x(x).y(y).build();
  }

  private Cluster createCluster(String text, int label) {
    return Cluster.builder().text(text).label(label).build();
  }

  private ClusterSummary createClusterSummary(
      int risk, double score, int count, int label, List<Double> testFrequencyData, ClusterType clusterType) {
    return ClusterSummary.builder()
        .risk(risk)
        .clusterType(clusterType)
        .score(score)
        .count(count)
        .label(label)
        .testFrequencyData(testFrequencyData)
        .feedback(LogFeedback.builder().build())
        .feedbackApplied(LogFeedback.builder().build())
        .frequencyData(Collections.singletonList(
            DeploymentLogAnalysisDTO.HostFrequencyData.builder()
                .frequencies(CollectionUtils.emptyIfNull(testFrequencyData)
                                 .stream()
                                 .map(frequency
                                     -> DeploymentLogAnalysisDTO.TimestampFrequencyCount.builder()
                                            .count(frequency)
                                            .timeStamp(26459162L)
                                            .build())
                                 .collect(Collectors.toList()))
                .host("host")
                .build()))
        .build();
  }

  private List<ControlClusterSummary> getControlClusterSummaries(int labelCount) {
    return IntStream.range(0, labelCount)
        .boxed()
        .map(label
            -> ControlClusterSummary.builder().label(label).controlFrequencyData(Arrays.asList(0.0, 0.2, 0.9)).build())
        .collect(Collectors.toList());
  }

  private HostSummary createHostSummary(String host, ResultSummary resultSummary) {
    return HostSummary.builder().host(host).resultSummary(resultSummary).build();
  }

  private void setUpDummyDeploymentLogAnalysis() throws IOException {
    String textLoad = Resources.toString(
        DeploymentLogAnalysisServiceImplTest.class.getResource("/deployment/deployment-log-analysis.json"),
        Charsets.UTF_8);
    DeploymentLogAnalysis deploymentLogAnalysis = JsonUtils.asObject(textLoad, DeploymentLogAnalysis.class);
    verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, "cvConfigId", radarChartVerificationJobInstanceId, SPLUNK);
    deploymentLogAnalysis.setVerificationTaskId(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
  }
}
