/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.analysis.CVAnalysisConstants.AFTER;
import static io.harness.cvng.analysis.CVAnalysisConstants.BEFORE;
import static io.harness.cvng.analysis.CVAnalysisConstants.CANARY;
import static io.harness.cvng.analysis.CVAnalysisConstants.PRIMARY;
import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NEMANJA;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.HostSummaryInfo;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.Cluster;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterCoordinates;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ControlClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostData;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostInfo;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.HostRecordDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.SimpleVerificationAdditionalInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.beans.AdditionalInfo;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VerificationJobInstanceAnalysisServiceImplTest extends CvNextGenTestBase {
  @Inject HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private HostRecordService hostRecordService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private VerificationJobInstanceAnalysisServiceImpl verificationJobInstanceAnalysisService;
  @Inject private ActivityService activityService;
  @Mock private NextGenService nextGenService;

  private String accountId;
  private String cvConfigId;
  private String identifier;
  private String serviceIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private String envIdentifier;
  private BuilderFactory builderFactory;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    cvConfigId = generateUuid();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    identifier = generateUuid();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    FieldUtils.writeField(deploymentTimeSeriesAnalysisService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(verificationJobInstanceAnalysisService, "deploymentTimeSeriesAnalysisService",
        deploymentTimeSeriesAnalysisService, true);
    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(ConnectorInfoDTO.builder().name("AppDynamics Connector").build()));
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withBothTimeSeriesAndLogsAnalyses() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);

    hostRecordService.save(hostRecordDTO);

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));
    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(PRIMARY);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(1);
    List<HostSummaryInfo> primaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getPrimary());
    assertThat(primaryHosts.get(0).getHostName()).contains("node1");
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(2);
    List<HostSummaryInfo> canaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getCanary());
    assertThat(canaryHosts.get(0).getHostName()).isEqualTo("node1");
    assertThat(canaryHosts.get(0).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(0).getAnomalousMetricsCount()).isEqualTo(0);
    assertThat(canaryHosts.get(0).getAnomalousLogClustersCount()).isEqualTo(2);
    assertThat(canaryHosts.get(1).getHostName()).isEqualTo("node2");
    assertThat(canaryHosts.get(1).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(1).getAnomalousMetricsCount()).isEqualTo(2);
    assertThat(canaryHosts.get(1).getAnomalousLogClustersCount()).isEqualTo(3);
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withVerificationJobInstanceInQueuedState() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    verificationJobInstanceService.create(verificationJobInstance);
    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isEmpty();
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withoutAnalysesAndHostRecords() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEmpty();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isEmpty();
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withTimeSeriesAnalysisOnly() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);

    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);
    hostRecordService.save(hostRecordDTO);

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(PRIMARY);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(0);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(1);
    List<HostSummaryInfo> canaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getCanary());
    assertThat(canaryHosts.get(0).getHostName()).isEqualTo("node2");
    assertThat(canaryHosts.get(0).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(0).getAnomalousMetricsCount()).isEqualTo(2);
    assertThat(canaryHosts.get(0).getAnomalousLogClustersCount()).isEqualTo(0);
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withLogAnalysisOnly() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);

    hostRecordService.save(hostRecordDTO);

    deploymentLogAnalysisService.save(createDeploymentLogAnalysis(verificationTaskId));

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(PRIMARY);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(1);
    List<HostSummaryInfo> primaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getPrimary());
    assertThat(primaryHosts.get(0).getHostName()).contains("node1");
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(2);
    List<HostSummaryInfo> canaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getCanary());
    assertThat(canaryHosts.get(0).getHostName()).isEqualTo("node1");
    assertThat(canaryHosts.get(0).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(0).getAnomalousMetricsCount()).isEqualTo(0);
    assertThat(canaryHosts.get(0).getAnomalousLogClustersCount()).isEqualTo(2);
    assertThat(canaryHosts.get(1).getHostName()).isEqualTo("node2");
    assertThat(canaryHosts.get(1).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(1).getAnomalousMetricsCount()).isEqualTo(0);
    assertThat(canaryHosts.get(1).getAnomalousLogClustersCount()).isEqualTo(3);
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withImprovisedCanary() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);

    hostRecordService.save(hostRecordDTO);

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);

    HostInfo hostInfo1 = createHostInfo("node1", 1, 1.1, true, true);
    HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysis.setHostSummaries(new ArrayList<>());

    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEqualTo(canaryBlueGreenAdditionalInfo.getCanary());
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withImprovisedCanaryAndDuplicateNodes() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstanceWithMultipleCVConfigs(verificationJob);
    List<CVConfig> cvConfigList = new ArrayList<>();
    for (String uuid : verificationJobInstance.getCvConfigMap().keySet()) {
      cvConfigList.add(verificationJobInstance.getCvConfigMap().get(uuid));
    }
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigList.get(0).getUuid(), verificationJobInstanceId, APP_DYNAMICS);
    String verificationTaskId2 = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigList.get(1).getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);

    hostRecordService.save(hostRecordDTO);

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);

    HostInfo hostInfo1 = createHostInfo("node1", 1, 1.1, true, true);
    HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysis.setHostSummaries(new ArrayList<>());

    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId2);

    hostInfo1 = createHostInfo("node1", 2, 1.1, true, true);
    hostInfo2 = createHostInfo("node2", 1, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEqualTo(canaryBlueGreenAdditionalInfo.getCanary());
    canaryBlueGreenAdditionalInfo.getCanary().forEach(
        node -> assertThat(node.getRisk()).isEqualByComparingTo(Risk.UNHEALTHY));
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetBlueGreenDeploymentAdditionalInfo_withoutAnalysesAndHostRecords() {
    VerificationJob verificationJob =
        builderFactory.blueGreenVerificationJobBuilder().trafficSplitPercentage(null).build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.BLUE_GREEN);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEmpty();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isEmpty();
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetBlueGreenDeploymentAdditionalInfo_withTimeSeriesAnalysisOnly() {
    VerificationJob verificationJob =
        builderFactory.blueGreenVerificationJobBuilder().trafficSplitPercentage(null).build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);
    hostRecordService.save(hostRecordDTO);

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.BLUE_GREEN);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(0);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary()).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(1);
    List<HostSummaryInfo> canaryHosts = new ArrayList<>(canaryBlueGreenAdditionalInfo.getCanary());
    assertThat(canaryHosts.get(0).getHostName()).isEqualTo("node2");
    assertThat(canaryHosts.get(0).getRisk()).isEqualTo(Risk.UNHEALTHY);
    assertThat(canaryHosts.get(0).getAnomalousMetricsCount()).isEqualTo(2);
    assertThat(canaryHosts.get(0).getAnomalousLogClustersCount()).isEqualTo(0);
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetBlueGreenDeploymentAdditionalInfo_withDuplicateNodes() {
    VerificationJob verificationJob =
        builderFactory.blueGreenVerificationJobBuilder().trafficSplitPercentage(null).build();
    VerificationJobInstance verificationJobInstance =
        createVerificationJobInstanceWithMultipleCVConfigs(verificationJob);
    List<CVConfig> cvConfigList = new ArrayList<>();
    for (String uuid : verificationJobInstance.getCvConfigMap().keySet()) {
      cvConfigList.add(verificationJobInstance.getCvConfigMap().get(uuid));
    }
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigList.get(0).getUuid(), verificationJobInstanceId, APP_DYNAMICS);
    String verificationTaskId2 = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigList.get(1).getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);

    HostInfo hostInfo1 = createHostInfo("node1", 1, 1.1, true, true);
    HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));

    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);

    deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId2);

    hostInfo1 = createHostInfo("node1", 2, 1.1, true, true);
    hostInfo2 = createHostInfo("node2", 1, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.BLUE_GREEN);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(2);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary()).isEqualTo(canaryBlueGreenAdditionalInfo.getCanary());
    canaryBlueGreenAdditionalInfo.getCanary().forEach(
        node -> assertThat(node.getRisk()).isEqualByComparingTo(Risk.UNHEALTHY));
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNull();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetCanaryDeploymentAdditionalInfo_withImprovisedCanaryAndAdditionalPreDeploymentHost() {
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().trafficSplitPercentage(40).build();
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance(verificationJob);
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, APP_DYNAMICS);

    Set<String> preDeploymentHosts = new HashSet<>();
    preDeploymentHosts.add("node1");
    preDeploymentHosts.add("node2");
    preDeploymentHosts.add("node3");

    HostRecordDTO hostRecordDTO = createHostRecordDTO(preDeploymentHosts, verificationTaskId);

    hostRecordService.save(hostRecordDTO);

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);

    HostInfo hostInfo1 = createHostInfo("node1", 1, 1.1, true, false);
    HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, true, true);

    deploymentTimeSeriesAnalysis.setHostSummaries(Arrays.asList(hostInfo1, hostInfo2));

    DeploymentLogAnalysis deploymentLogAnalysis = createDeploymentLogAnalysis(verificationTaskId);
    deploymentLogAnalysis.setHostSummaries(new ArrayList<>());

    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);
    deploymentLogAnalysisService.save(deploymentLogAnalysis);

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstanceAnalysisService.getCanaryBlueGreenAdditionalInfo(accountId, verificationJobInstance);

    assertThat(canaryBlueGreenAdditionalInfo).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getType()).isEqualTo(VerificationJobType.CANARY);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimaryInstancesLabel()).isEqualTo(BEFORE);
    assertThat(canaryBlueGreenAdditionalInfo.getCanaryInstancesLabel()).isEqualTo(AFTER);
    assertThat(canaryBlueGreenAdditionalInfo.getPrimary().size()).isEqualTo(3);
    assertThat(canaryBlueGreenAdditionalInfo.getCanary().size()).isEqualTo(1);
    // verifies that primary nodes no longer contain riskScore
    canaryBlueGreenAdditionalInfo.getPrimary().forEach(
        hostSummaryInfo -> assertThat(hostSummaryInfo.getRisk()).isEqualTo(Risk.NO_ANALYSIS));
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage()).isNotNull();
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage().getPreDeploymentPercentage()).isEqualTo(60);
    assertThat(canaryBlueGreenAdditionalInfo.getTrafficSplitPercentage().getPostDeploymentPercentage()).isEqualTo(40);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLoadTestAdditionalInfo_withBaselineVerificationJobInstanceId() {
    Instant baselineStartTIme = Instant.now().minus(10, ChronoUnit.MINUTES);
    VerificationJobInstance baselineVerificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .startTime(baselineStartTIme)
            .resolvedJob(builderFactory.testVerificationJobBuilder().baselineVerificationJobInstanceId(null).build())
            .build();
    String baselineVerificationJobInstanceId = verificationJobInstanceService.create(baselineVerificationJobInstance);
    DeploymentActivity deploymentActivity = DeploymentActivity.builder()
                                                .verificationStartTime(baselineStartTIme.toEpochMilli())
                                                .deploymentTag("Build1")
                                                .build();
    Activity activity = deploymentActivity;
    activity.setActivityName("baselineActivity");
    activity.setActivitySourceId("ActivitySourceId");
    activity.setAccountId(accountId);
    activity.setVerificationJobInstanceIds(Arrays.asList(baselineVerificationJobInstanceId));
    activity.setType(ActivityType.DEPLOYMENT);
    activityService.createActivity(activity);
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.testVerificationJobBuilder()
                             .baselineVerificationJobInstanceId(baselineVerificationJobInstanceId)
                             .build())
            .build();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    VerificationJobInstance currentVerificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    Instant currentTime = Instant.now();
    currentVerificationJobInstance.setStartTimeFromTest(currentTime);
    hPersistence.save(currentVerificationJobInstance);

    DeploymentActivity currentDeploymentActivity =
        DeploymentActivity.builder().verificationStartTime(currentTime.toEpochMilli()).deploymentTag("Build2").build();
    Activity currentActivity = currentDeploymentActivity;
    currentActivity.setActivityName("currentActivity");
    currentActivity.setAccountId(accountId);
    currentActivity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
    currentActivity.setType(ActivityType.DEPLOYMENT);
    activityService.createActivity(currentActivity);
    LoadTestAdditionalInfo loadTestAdditionalInfo =
        verificationJobInstanceAnalysisService.getLoadTestAdditionalInfo(accountId, currentVerificationJobInstance);

    assertThat(loadTestAdditionalInfo).isNotNull();
    assertThat(loadTestAdditionalInfo.getBaselineDeploymentTag()).isEqualTo("Build1");
    assertThat(loadTestAdditionalInfo.getBaselineStartTime())
        .isEqualTo(baselineVerificationJobInstance.getStartTime().toEpochMilli());
    assertThat(loadTestAdditionalInfo.getCurrentDeploymentTag()).isEqualTo("Build2");
    assertThat(loadTestAdditionalInfo.getCurrentStartTime())
        .isEqualTo(currentVerificationJobInstance.getStartTime().toEpochMilli());
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLoadTestAdditionalInfo_withoutBaselineVerificationJobInstanceId() {
    VerificationJobInstance baselineVerificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.testVerificationJobBuilder().baselineVerificationJobInstanceId(null).build())
            .build();
    String baselineVerificationJobInstanceId = verificationJobInstanceService.create(baselineVerificationJobInstance);
    TestVerificationJob verificationJob = (TestVerificationJob) baselineVerificationJobInstance.getResolvedJob();

    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.testVerificationJobBuilder()
                             .baselineVerificationJobInstanceId(baselineVerificationJobInstanceId)
                             .build())
            .build();

    Instant currentTime = Instant.now();
    verificationJobInstance.setStartTimeFromTest(currentTime);
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    DeploymentActivity deploymentActivity =
        DeploymentActivity.builder().verificationStartTime(currentTime.toEpochMilli()).deploymentTag("Build0").build();
    Activity activity = deploymentActivity;
    activity.setActivityName("activity");
    activity.setAccountId(accountId);
    activity.setVerificationJobInstanceIds(Arrays.asList(baselineVerificationJobInstanceId));
    activity.setType(ActivityType.DEPLOYMENT);
    activityService.createActivity(activity);
    deploymentActivity =
        DeploymentActivity.builder().verificationStartTime(currentTime.toEpochMilli()).deploymentTag("Build1").build();
    activity = deploymentActivity;
    activity.setActivityName("activity");
    activity.setAccountId(accountId);
    activity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
    activity.setType(ActivityType.DEPLOYMENT);
    activityService.createActivity(activity);

    LoadTestAdditionalInfo loadTestAdditionalInfo =
        verificationJobInstanceAnalysisService.getLoadTestAdditionalInfo(accountId, verificationJobInstance);

    assertThat(loadTestAdditionalInfo).isNotNull();
    assertThat(loadTestAdditionalInfo.getBaselineDeploymentTag()).isEqualTo("Build0");
    assertThat(loadTestAdditionalInfo.getCurrentDeploymentTag()).isEqualTo("Build1");
    assertThat(loadTestAdditionalInfo.getCurrentStartTime()).isEqualTo(currentTime.toEpochMilli());
  }
  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetLoadTestAdditionalInfo_withoutActivityForVerificationJobInstanceId() {
    VerificationJobInstance baselineVerificationJobInstance = createVerificationJobInstance();
    String verificationJobInstanceId = verificationJobInstanceService.create(baselineVerificationJobInstance);
    VerificationJob verificationJob = baselineVerificationJobInstance.getResolvedJob();
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    verificationJobInstance.setResolvedJob(verificationJob);
    Instant currentTime = Instant.now();
    verificationJobInstance.setStartTimeFromTest(currentTime);
    hPersistence.save(verificationJobInstance);

    DeploymentActivity deploymentActivity =
        DeploymentActivity.builder()
            .verificationStartTime(verificationJobInstance.getStartTime().toEpochMilli())
            .deploymentTag("Build1")
            .build();
    Activity activity = deploymentActivity;
    activity.setActivityName("randomActivity");
    activity.setAccountId(accountId);
    activity.setVerificationJobInstanceIds(Arrays.asList(generateUuid()));
    activity.setType(ActivityType.DEPLOYMENT);
    activityService.createActivity(activity);

    assertThatThrownBy(
        () -> verificationJobInstanceAnalysisService.getLoadTestAdditionalInfo(accountId, verificationJobInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Activity not found for verificationJobInstanceId: " + verificationJobInstanceId);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetSimpleVerificationAdditionalInfo() {
    AdditionalInfo additionalInfo =
        verificationJobInstanceAnalysisService.getSimpleVerificationAdditionalInfo(accountId, null);
    assertThat(additionalInfo).isInstanceOf(SimpleVerificationAdditionalInfo.class);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testAddDemoAnalysisData() {
    CVConfig cvConfig = builderFactory.splunkCVConfigBuilder().uuid(cvConfigId).build();
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setExecutionStatus(VerificationJobInstance.ExecutionStatus.SUCCESS);
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.VERIFICATION_PASSED);
    verificationJobInstanceService.create(verificationJobInstance);
    verificationJobInstanceAnalysisService.addDemoAnalysisData(
        verificationTaskService.createDeploymentVerificationTask(
            accountId, cvConfig.getUuid(), verificationJobInstance.getUuid(), cvConfig.getType()),
        cvConfig, verificationJobInstance);
    assertThat(
        verificationJobInstanceAnalysisService.getLatestRiskScore(accountId, verificationJobInstance.getUuid()).get())
        .isEqualTo(Risk.HEALTHY);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDemoTemplatePath_allProviders() throws IOException {
    for (DataSourceType dataSourceType : DataSourceType.values()) {
      String demoData =
          Resources.toString(this.getClass().getResource(verificationJobInstanceAnalysisService.getDemoTemplatePath(
                                 ActivityVerificationStatus.VERIFICATION_PASSED, dataSourceType)),
              Charsets.UTF_8);
      // use existing demo data (One of splunk, prometheus or appdynamics based on similarity with the new provider.
      // creating a new file should be rare.
      assertThat(demoData).isNotNull();
    }
  }

  private HostRecordDTO createHostRecordDTO(Set<String> preDeploymentHosts, String verificationTaskId) {
    return HostRecordDTO.builder()
        .hosts(preDeploymentHosts)
        .verificationTaskId(verificationTaskId)
        .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
        .endTime(Instant.now().minus(2, ChronoUnit.MINUTES))
        .build();
  }

  private VerificationJobInstance createVerificationJobInstance(VerificationJob verificationJob) {
    return builderFactory.verificationJobInstanceBuilder()
        .deploymentStartTime(Instant.now())
        .startTime(Instant.now())
        .resolvedJob(verificationJob)
        .build();
  }

  private VerificationJobInstance createVerificationJobInstance() {
    return builderFactory.verificationJobInstanceBuilder().build();
  }

  private VerificationJobInstance createVerificationJobInstanceWithMultipleCVConfigs(VerificationJob verificationJob) {
    CVConfig cvConfig1 = builderFactory.appDynamicsCVConfigBuilder().uuid(generateUuid()).build();
    CVConfig cvConfig2 = builderFactory.appDynamicsCVConfigBuilder().uuid(generateUuid()).build();
    Map<String, CVConfig> cvConfigMap = new HashMap<String, CVConfig>() {
      {
        put(cvConfig1.getUuid(), cvConfig1);
        put(cvConfig2.getUuid(), cvConfig2);
      }
    };
    return builderFactory.verificationJobInstanceBuilder()
        .resolvedJob(verificationJob)
        .cvConfigMap(cvConfigMap)
        .build();
  }

  private HostInfo createHostInfo(String hostName, int risk, Double score, boolean primary, boolean canary) {
    return HostInfo.builder().hostName(hostName).risk(risk).score(score).primary(primary).canary(canary).build();
  }

  private HostData createHostData(
      String hostName, int risk, Double score, List<Double> controlData, List<Double> testData) {
    return HostData.builder()
        .hostName(hostName)
        .risk(risk)
        .score(score)
        .controlData(controlData)
        .testData(testData)
        .build();
  }

  private TransactionMetricHostData createTransactionMetricHostData(
      String transactionName, String metricName, int risk, Double score, List<HostData> hostDataList) {
    return TransactionMetricHostData.builder()
        .transactionName(transactionName)
        .metricName(metricName)
        .risk(risk)
        .score(score)
        .hostData(hostDataList)
        .build();
  }

  private DeploymentTimeSeriesAnalysis createDeploymentTimeSeriesAnalysis(String verificationTaskId) {
    HostInfo hostInfo1 = createHostInfo("node1", 1, 1.1, false, false);
    HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, false, true);
    HostData hostData1 = createHostData("node1", 0, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    HostData hostData2 = createHostData("node2", 2, 2.0, Arrays.asList(1D), Arrays.asList(1D));

    TransactionMetricHostData transactionMetricHostData1 = createTransactionMetricHostData(
        "/todolist/inside", "Errors per Minute", 0, 0.5, Arrays.asList(hostData1, hostData2));

    HostData hostData3 = createHostData("node1", 0, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    HostData hostData4 = createHostData("node2", 2, 2.0, Arrays.asList(1D), Arrays.asList(1D));

    TransactionMetricHostData transactionMetricHostData2 = createTransactionMetricHostData(
        "/todolist/exception", "Calls per Minute", 2, 2.5, Arrays.asList(hostData3, hostData4));
    return DeploymentTimeSeriesAnalysis.builder()
        .accountId(accountId)
        .score(1.0)
        .risk(Risk.OBSERVE)
        .verificationTaskId(verificationTaskId)
        .transactionMetricSummaries(Arrays.asList(transactionMetricHostData1, transactionMetricHostData2))
        .hostSummaries(Arrays.asList(hostInfo1, hostInfo2))
        .startTime(Instant.now())
        .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
        .build();
  }

  private DeploymentLogAnalysis createDeploymentLogAnalysis(String verificationTaskId) {
    Cluster cluster1 = createCluster("Error in cluster 1", 1);
    Cluster cluster2 = createCluster("Error in cluster 2", 2);
    Cluster cluster3 = createCluster("Error in cluster 3", 3);
    List<Cluster> clusters = Arrays.asList(cluster1, cluster2, cluster3);

    ClusterCoordinates clusterCoordinates1 = createClusterCoordinates("node1", 1, 0.6464, 0.717171);
    ClusterCoordinates clusterCoordinates2 = createClusterCoordinates("node2", 2, 0.4525, 0.542524);
    ClusterCoordinates clusterCoordinates3 = createClusterCoordinates("node3", 3, 0.4525, 0.542524);
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

    HostSummary hostSummary1 = createHostSummary("node1", resultSummary);
    HostSummary hostSummary2 = createHostSummary("node2", resultSummary2);
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
}
