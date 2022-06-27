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
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.junit.Before;
import org.junit.Ignore;
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
    radarChartVerificationJobInstanceId = "radarChartVerificationJobInstanceId";
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
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(Arrays.asList(UNKNOWN_EVENT))
                                      .hostName(null)
                                      .build();
    logAnalysisClusterChartDTOlist = deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(0);
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
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);

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
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(3);

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
    assertThat(logAnalysisClusterChartDTOlist.size()).isEqualTo(0);
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
    assertThat(pageResponse.getContent().size()).isEqualTo(3);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(3);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(Arrays.asList(ClusterType.UNEXPECTED_FREQUENCY))
                                      .hostName(null)
                                      .build();
    pageResponse = deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(pageResponse.getContent().size()).isEqualTo(0);
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
    assertThat(pageResponse.getContent().size()).isEqualTo(3);
    assertThat(pageResponse.getContent().get(0).getLabel()).isEqualTo(3);

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
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(0);

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
    for (EventCount eventCount : logAnalysisClusterWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(0);
    }
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(0);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(ClusterType.KNOWN_EVENT)).build();
    logAnalysisClusterWithCountDTO = deploymentLogAnalysisService.getLogAnalysisResultV2(
        accountId, verificationJobInstanceId, null, deploymentLogAnalysisFilter, pageParams);

    assertThat(logAnalysisClusterWithCountDTO.getTotalClusters()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getPageIndex()).isEqualTo(0);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent()).isNotNull();
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(3);
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
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().size()).isEqualTo(3);
    assertThat(logAnalysisClusterWithCountDTO.getLogAnalysisClusterDTO().getContent().get(0).getLabel()).isEqualTo(3);

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
      //      clusters.add(createCluster("Cluster " + i, i, 0, 0));
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
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
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
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    assertThatThrownBy(() -> deploymentLogAnalysisService.getAnalysisSummary(accountId, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Missing verificationJobInstanceIds when looking for summary");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters() throws IOException {
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
            accountId, radarChartVerificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build());

    List<LogAnalysisRadarChartClusterDTO> expectedLogClusterAnalysis =
        JsonUtils.asObject(Resources.toString(DeploymentLogAnalysisServiceImplTest.class.getResource(
                                                  "/deployment/deployment-log-analysis-cluster-output.json"),
                               Charsets.UTF_8),
            new TypeReference<List<LogAnalysisRadarChartClusterDTO>>() {});

    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(expectedLogClusterAnalysis.size());
    for (int i = 0; i < expectedLogClusterAnalysis.size(); i++) {
      // cluster id keep changing based on verificationTaskId.
      // expectedLogClusterAnalysis.get(i).setClusterId(logAnalysisRadarChartClusterDTOS.get(i).getClusterId());
      assertThat(logAnalysisRadarChartClusterDTOS.get(i))
          .isEqualToIgnoringGivenFields(expectedLogClusterAnalysis.get(i), "clusterId");
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_sortingOrder() {
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
            accountId, radarChartVerificationJobInstanceId, DeploymentLogAnalysisFilter.builder().build());

    assertThat(logAnalysisRadarChartClusterDTOS.get(0).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartClusterDTOS.get(0).getAngle()).isEqualTo(0.0);
    assertThat(logAnalysisRadarChartClusterDTOS.get(1).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartClusterDTOS.get(1).getAngle()).isEqualTo(90.0);
    assertThat(logAnalysisRadarChartClusterDTOS.get(2).getClusterType()).isEqualTo(UNEXPECTED_FREQUENCY);
    assertThat(logAnalysisRadarChartClusterDTOS.get(2).getAngle()).isEqualTo(180.0);
    assertThat(logAnalysisRadarChartClusterDTOS.get(3).getClusterType()).isEqualTo(KNOWN_EVENT);
    assertThat(logAnalysisRadarChartClusterDTOS.get(3).getAngle()).isEqualTo(270.0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_filterWithEventType() {
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNKNOWN_EVENT)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(2);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNEXPECTED_FREQUENCY)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(KNOWN_EVENT)).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartLogAnalysisClusters_filterWithHostName() {
    List<LogAnalysisRadarChartClusterDTO> logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostOne")).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostTwo")).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(2);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostThree")).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(1);

    logAnalysisRadarChartClusterDTOS =
        deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("randomHost")).build());
    assertThat(logAnalysisRadarChartClusterDTOS.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  @Ignore("Will be fixed later by Kamal")
  public void testGetRadarChartAnalysisResult() throws IOException {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(20).build());

    LogAnalysisRadarChartListWithCountDTO expectedLogAnalysis =
        JsonUtils.asObject(Resources.toString(DeploymentLogAnalysisServiceImplTest.class.getResource(
                                                  "/deployment/deployment-log-analysis-list-output.json"),
                               Charsets.UTF_8),
            LogAnalysisRadarChartListWithCountDTO.class);
    // JsonUtils.asPrettyJson(logAnalysisRadarChartListWithCountDTO);
    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters())
        .isEqualTo(expectedLogAnalysis.getTotalClusters());
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).isEqualTo(expectedLogAnalysis.getEventCounts());
    for (int i = 0; i < logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems(); i++) {
      LogAnalysisRadarChartListDTO logAnalysisRadarChartListDTO =
          logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(i);
      LogAnalysisRadarChartListDTO expectedLogAnalysisRadarChartListDTO =
          expectedLogAnalysis.getLogAnalysisRadarCharts().getContent().get(i);
      assertThat(logAnalysisRadarChartListDTO)
          .isEqualToIgnoringGivenFields(expectedLogAnalysisRadarChartListDTO, "clusterId", "radius", "angle");
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_pagination() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(2).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(4);
    Map<ClusterType, Integer> expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 1);
    expectedCount.put(KNOWN_EVENT, 1);
    expectedCount.put(UNKNOWN_EVENT, 2);
    expectedCount.put(BASELINE, 2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(expectedCount.get(eventCount.getClusterType()));
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(4);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getPageItemCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_sortingOrder() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(4);
    List<LogAnalysisRadarChartListDTO> logAnalysisRadarChartListDTOS =
        logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent();

    assertThat(logAnalysisRadarChartListDTOS.get(0).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartListDTOS.get(0).getAngle()).isEqualTo(0.0);
    assertThat(logAnalysisRadarChartListDTOS.get(1).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(logAnalysisRadarChartListDTOS.get(1).getAngle()).isEqualTo(90.0);
    assertThat(logAnalysisRadarChartListDTOS.get(2).getClusterType()).isEqualTo(UNEXPECTED_FREQUENCY);
    assertThat(logAnalysisRadarChartListDTOS.get(2).getAngle()).isEqualTo(180.0);
    assertThat(logAnalysisRadarChartListDTOS.get(3).getClusterType()).isEqualTo(KNOWN_EVENT);
    assertThat(logAnalysisRadarChartListDTOS.get(3).getAngle()).isEqualTo(270.0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_eventCountsWithBaseline() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().build(), PageParams.builder().page(0).size(10).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(4);
    List<EventCount> eventCounts = logAnalysisRadarChartListWithCountDTO.getEventCounts();
    assertThat(eventCounts.get(0).getClusterType()).isEqualTo(KNOWN_EVENT);
    assertThat(eventCounts.get(0).getCount()).isEqualTo(1);
    assertThat(eventCounts.get(1).getClusterType()).isEqualTo(UNEXPECTED_FREQUENCY);
    assertThat(eventCounts.get(1).getCount()).isEqualTo(1);
    assertThat(eventCounts.get(2).getClusterType()).isEqualTo(UNKNOWN_EVENT);
    assertThat(eventCounts.get(2).getCount()).isEqualTo(2);
    assertThat(eventCounts.get(3).getClusterType()).isEqualTo(BASELINE);
    assertThat(eventCounts.get(3).getCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByEventType() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNEXPECTED_FREQUENCY)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      if (eventCount.getClusterType() == UNEXPECTED_FREQUENCY || eventCount.getClusterType() == BASELINE) {
        assertThat(eventCount.getCount()).isEqualTo(1);
      } else {
        assertThat(eventCount.getCount()).isEqualTo(0);
      }
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(1);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(KNOWN_EVENT)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      if (eventCount.getClusterType() == KNOWN_EVENT || eventCount.getClusterType() == BASELINE) {
        assertThat(eventCount.getCount()).isEqualTo(1);
      } else {
        assertThat(eventCount.getCount()).isEqualTo(0);
      }
    }
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(1);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterTypes(Arrays.asList(UNKNOWN_EVENT)).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(2);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      if (eventCount.getClusterType() == UNKNOWN_EVENT) {
        assertThat(eventCount.getCount()).isEqualTo(2);
      } else {
        assertThat(eventCount.getCount()).isEqualTo(0);
      }
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByHost() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostOne")).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    Map<ClusterType, Integer> expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 0);
    expectedCount.put(KNOWN_EVENT, 1);
    expectedCount.put(UNKNOWN_EVENT, 0);
    expectedCount.put(BASELINE, 1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(expectedCount.get(eventCount.getClusterType()));
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(1);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostTwo")).build(),
            PageParams.builder().page(0).size(20).build());
    expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 1);
    expectedCount.put(KNOWN_EVENT, 0);
    expectedCount.put(UNKNOWN_EVENT, 1);
    expectedCount.put(BASELINE, 1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(expectedCount.get(eventCount.getClusterType()));
    }

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(2);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("hostThree")).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    expectedCount = new HashMap<>();
    expectedCount.put(UNEXPECTED_FREQUENCY, 0);
    expectedCount.put(KNOWN_EVENT, 0);
    expectedCount.put(UNKNOWN_EVENT, 1);
    expectedCount.put(BASELINE, 0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts()).hasSize(4);
    for (EventCount eventCount : logAnalysisRadarChartListWithCountDTO.getEventCounts()) {
      assertThat(eventCount.getCount()).isEqualTo(expectedCount.get(eventCount.getClusterType()));
    }
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(1);

    logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().hostNames(Collections.singletonList("randomHost")).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(0);

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByAngle() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().minAngle(10.0).maxAngle(200.0).build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(0).getCount()).isEqualTo(0);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(1).getCount()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getEventCounts().get(2).getCount()).isEqualTo(1);

    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalPages()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getTotalItems()).isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterClusterIdZeroResults() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder().clusterId("clusterId").build(),
            PageParams.builder().page(0).size(20).build());
    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRadarChartAnalysisResult_filterByClusterId() {
    LogAnalysisRadarChartListWithCountDTO logAnalysisRadarChartListWithCountDTO =
        deploymentLogAnalysisService.getRadarChartLogAnalysisResult(accountId, radarChartVerificationJobInstanceId,
            DeploymentLogAnalysisFilter.builder()
                .clusterId(UUID.nameUUIDFromBytes((verificationTaskId + ":" + 2).getBytes(Charsets.UTF_8)).toString())
                .build(),
            PageParams.builder().page(0).size(20).build());

    assertThat(logAnalysisRadarChartListWithCountDTO.getTotalClusters()).isEqualTo(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent()).hasSize(1);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(0).getLabel())
        .isEqualTo(2);
    assertThat(logAnalysisRadarChartListWithCountDTO.getLogAnalysisRadarCharts().getContent().get(0).getMessage())
        .isEqualTo("clusterTwo");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNodeNames() {
    assertThat(deploymentLogAnalysisService.getNodeNames(accountId, verificationJobInstanceId)).isEmpty();
    assertThat(deploymentLogAnalysisService.getNodeNames(accountId, radarChartVerificationJobInstanceId))
        .isEqualTo(Sets.newHashSet("hostOne", "hostTwo", "hostThree"));
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
    assertThat(radarChartLogAnalysisClusters.size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisClusters = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisClusters.get(0).getMessage()).startsWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(1).getMessage()).startsWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(2).getMessage()).startsWith("ET");

    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().healthSourceIdentifiers(null).clusterTypes(null).hostName(null).build();
    radarChartLogAnalysisClusters = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisClusters.get(0).getMessage()).doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(1).getMessage()).doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(2).getMessage()).doesNotStartWith("ET");

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("node1")
                                      .build();
    radarChartLogAnalysisClusters = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisClusters.get(0).getMessage()).doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(1).getMessage()).doesNotStartWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(2).getMessage()).doesNotStartWith("ET");

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisClusters = deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter);
    assertThat(radarChartLogAnalysisClusters).isNotNull();
    assertThat(radarChartLogAnalysisClusters.size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisClusters.get(0).getMessage()).startsWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(1).getMessage()).startsWith("ET");
    assertThat(radarChartLogAnalysisClusters.get(2).getMessage()).startsWith("ET");
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
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(0);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(0).getMessage())
        .startsWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(1).getMessage())
        .startsWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(2).getMessage())
        .startsWith("ET");

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
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);

    deploymentLogAnalysisFilter = DeploymentLogAnalysisFilter.builder()
                                      .healthSourceIdentifiers(null)
                                      .clusterTypes(null)
                                      .hostName("ErrorTracking")
                                      .build();
    radarChartLogAnalysisResult = deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, verificationJobInstanceId, deploymentLogAnalysisFilter, pageParams);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isNotNull();
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().size()).isEqualTo(3);
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(0).getMessage())
        .startsWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(1).getMessage())
        .startsWith("ET");
    assertThat(radarChartLogAnalysisResult.getLogAnalysisRadarCharts().getContent().get(1).getMessage())
        .startsWith("ET");
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
    ClusterSummary clusterSummary2 = createClusterSummary(0, 0, 3, 2, Arrays.asList(2D), ClusterType.KNOWN_EVENT);
    ClusterSummary clusterSummary3 = createClusterSummary(2, 2.2, 55, 3, Arrays.asList(4D), ClusterType.KNOWN_EVENT);

    ResultSummary resultSummary =
        createResultSummary(1, 1, Arrays.asList(clusterSummary1, clusterSummary2, clusterSummary3), null);

    ClusterSummary clusterSummary4 = createClusterSummary(2, 0.7, 36, 1, Arrays.asList(2D), ClusterType.KNOWN_EVENT);
    ClusterSummary clusterSummary5 = createClusterSummary(2, 0, 3, 2, Arrays.asList(2D), ClusterType.KNOWN_EVENT);
    ClusterSummary clusterSummary6 = createClusterSummary(2, 2.2, 55, 3, Arrays.asList(4D), ClusterType.KNOWN_EVENT);

    ResultSummary resultSummary2 =
        createResultSummary(2, 1, Arrays.asList(clusterSummary4, clusterSummary5, clusterSummary6), null);

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
        .controlClusterSummaries(controlClusterSummaries)
        .testClusterSummaries(testClusterSummaries)
        .build();
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
        .build();
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
