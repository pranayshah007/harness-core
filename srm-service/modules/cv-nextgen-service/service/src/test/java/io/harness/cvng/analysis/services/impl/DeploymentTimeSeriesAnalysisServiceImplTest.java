/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NEMANJA;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.TimeSeriesAnalysisSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.beans.TransactionMetricInfo;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.cdng.beans.v2.AnalysisReason;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.ControlDataType;
import io.harness.cvng.cdng.beans.v2.MetricType;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class DeploymentTimeSeriesAnalysisServiceImplTest extends CvNextGenTestBase {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private CVConfigService cvConfigService;
  @Mock private NextGenService nextGenService;
  @Mock private TimeSeriesRecordService mockedTimeSeriesRecordService;

  private String accountId;
  private String identifier;
  private String serviceIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private String envIdentifier;
  private BuilderFactory builderFactory;
  private List<TimeSeriesRecordDTO> timeSeriesRecordDtos;

  @Before
  public void setUp() throws IllegalAccessException {
    accountId = generateUuid();
    serviceIdentifier = generateUuid();
    identifier = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    envIdentifier = generateUuid();
    builderFactory = BuilderFactory.builder()
                         .context(BuilderFactory.Context.builder()
                                      .projectParams(ProjectParams.builder()
                                                         .accountIdentifier(accountId)
                                                         .orgIdentifier(orgIdentifier)
                                                         .projectIdentifier(projectIdentifier)
                                                         .build())
                                      .envIdentifier(envIdentifier)
                                      .serviceIdentifier(serviceIdentifier)
                                      .build())
                         .build();

    FieldUtils.writeField(
        deploymentTimeSeriesAnalysisService, "timeSeriesRecordService", mockedTimeSeriesRecordService, true);
    FieldUtils.writeField(deploymentTimeSeriesAnalysisService, "nextGenService", nextGenService, true);
    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(ConnectorInfoDTO.builder().name("AppDynamics Connector").build()));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    TimeSeriesAnalysisSummary summary =
        deploymentTimeSeriesAnalysisService.getAnalysisSummary(Arrays.asList(verificationJobInstanceId));
    assertThat(summary).isNotNull();
    assertThat(summary.getNumAnomMetrics()).isEqualTo(1);
    assertThat(summary.getTotalNumMetrics()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary_badRequest() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    TimeSeriesAnalysisSummary summary =
        deploymentTimeSeriesAnalysisService.getAnalysisSummary(Arrays.asList(verificationJobInstanceId));

    assertThatThrownBy(() -> deploymentTimeSeriesAnalysisService.getAnalysisSummary(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Missing verificationJobInstanceIds when looking for summary");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisSummary_noAnalysisYet() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    TimeSeriesAnalysisSummary summary =
        deploymentTimeSeriesAnalysisService.getAnalysisSummary(Arrays.asList(verificationJobInstanceId));

    assertThat(summary).isNotNull();
    assertThat(summary.getNumAnomMetrics()).isEqualTo(0);
    assertThat(summary.getTotalNumMetrics()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getTransactionMetric()
                   .getTransactionName())
        .isEqualTo("/todolist/exception");
    assertThat(
        transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getTransactionMetric().getScore())
        .isEqualTo(2.5); // ensures that sorting based on score from transaction works
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(3);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodes()
                   .first()
                   .getHostName()
                   .get())
        .isEqualTo("node3");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().first().getScore())
        .isEqualTo(2.0); // checks that sorting per node works correctly
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetrics_withNoVerificationTaskMapping() {
    String verificationJobInstanceId = verificationJobInstanceService.create(createVerificationJobInstance());
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withHostNameFilter() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(Arrays.asList("node1"))
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodes()
                   .first()
                   .getHostName()
                   .get())
        .isEqualTo("node1");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetrics_withDataSourceTypeFilter() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    List<String> healthSourceIdentifiersFilter = Arrays.asList(cvConfig.getIdentifier());
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIdentifiersFilter)
            .filter(null)
            .anomalousMetricsOnly(false)
            .anomalousNodesOnly(false)
            .hostNames(null)
            .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(3);

    deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                             .healthSourceIdentifiers(Arrays.asList("some-identifier"))
                                             .filter(null)
                                             .anomalousMetricsOnly(false)
                                             .anomalousNodesOnly(false)
                                             .hostNames(null)
                                             .build();

    transactionMetricInfoSummaryPageDTO = deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetrics_withHostNameFilterWithOnlyFewTxn() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(Arrays.asList("node3"))
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodes()
                   .first()
                   .getHostName()
                   .get())
        .isEqualTo("node3");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withWrongHostName() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(Arrays.asList("randomnode"))
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO summaryPageDTO = deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);
    assertThat(summaryPageDTO).isNotNull();
    assertThat(summaryPageDTO.getPageResponse().getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withAnomalousMetricsAndAnomalousNodesFilterAsTrue() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(true)
                                                                                .anomalousNodesOnly(true)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(1);
    assertThat(
        transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getTransactionMetric().getRisk())
        .isNotEqualTo(Risk.HEALTHY);
    assertThat(
        transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getTransactionMetric().getScore())
        .isEqualTo(2.5);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(2);
    for (DeploymentTimeSeriesAnalysisDTO.HostData hostData :
        transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes()) {
      assertThat(hostData.getRisk()).isNotEqualTo(Risk.HEALTHY);
    }
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMetrics_withAnomalousMetricsAndAnomalousNodesFilterAsFalse() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(3);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(1).getNodes().size())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withHostNameAndAnomalousMetricsFilter() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(true)
                                                                                .anomalousNodesOnly(true)
                                                                                .hostNames(Arrays.asList("node2"))
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();
    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    List<TransactionMetricInfo> content = transactionMetricInfoSummaryPageDTO.getPageResponse().getContent();
    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(2);
    assertThat(content.get(0).getTransactionMetric().getScore()).isEqualTo(2.5);
    assertThat(content.get(0).getConnectorName()).isEqualTo("AppDynamics Connector");
    assertThat(content.get(0).getNodes().size()).isEqualTo(1);
    assertThat(content.get(0).getNodes().first().getHostName().get()).isEqualTo("node2");

    assertThat(content.get(0).getTransactionMetric().getTransactionName()).isEqualTo("/todolist/exception");
    assertThat(content.get(0).getTransactionMetric().getMetricName()).isEqualTo("Calls per Minute");
    assertThat(content.get(1).getTransactionMetric().getTransactionName()).isEqualTo("/todolist/inside");
    assertThat(content.get(1).getTransactionMetric().getMetricName()).isEqualTo("Errors per Minute");
  }
  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withMultipleDeploymentTimeSeriesAnalyses() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);
    deploymentTimeSeriesAnalysis.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS));
    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData =
        createTransactionMetricHostData("newTransaction", "newMetric", 2, 5.0,
            deploymentTimeSeriesAnalysis.getTransactionMetricSummaries().get(0).getHostData());
    deploymentTimeSeriesAnalysis.setTransactionMetricSummaries(Arrays.asList(transactionMetricHostData));
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isNotNull();
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getTransactionMetric()
                   .getTransactionName())
        .isEqualTo("newTransaction");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getConnectorName())
        .isEqualTo("AppDynamics Connector");
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withMultiplePages() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);
    List<DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData> transactionSummaries = new ArrayList();
    for (int i = 0; i < 25; i++) {
      transactionSummaries.add(createTransactionMetricHostData("transaction " + i, "metric", 0, 0.0,
          deploymentTimeSeriesAnalysis.getTransactionMetricSummaries().get(0).getHostData()));
    }
    deploymentTimeSeriesAnalysis.setTransactionMetricSummaries(transactionSummaries);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO page1 = deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(page1.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(page1.getPageResponse().getTotalPages()).isEqualTo(3);
    assertThat(page1.getPageResponse().getContent()).isNotNull();
    assertThat(page1.getPageResponse().getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(1).size(10).build();
    TransactionMetricInfoSummaryPageDTO page2 = deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(page2.getPageResponse().getPageIndex()).isEqualTo(1);
    assertThat(page2.getPageResponse().getTotalPages()).isEqualTo(3);
    assertThat(page2.getPageResponse().getContent()).isNotNull();
    assertThat(page2.getPageResponse().getContent().size()).isEqualTo(10);

    pageParams = PageParams.builder().page(2).size(10).build();
    TransactionMetricInfoSummaryPageDTO page3 = deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(page3.getPageResponse().getPageIndex()).isEqualTo(2);
    assertThat(page3.getPageResponse().getTotalPages()).isEqualTo(3);
    assertThat(page3.getPageResponse().getContent()).isNotNull();
    assertThat(page3.getPageResponse().getContent().size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetMetrics_withoutDeploymentTimeSeriesAnalysis() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getPageIndex()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getTotalPages()).isEqualTo(0);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_noData() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    assertThat(deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId))
        .isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_verificationTaskIdDoesNotExists() {
    String verificationJobInstanceId = verificationJobInstanceService.create(createVerificationJobInstance());
    assertThatThrownBy(
        () -> deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_getLatest() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis = createDeploymentTimeSeriesAnalysis(verificationTaskId);
    List<DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData> transactionSummaries = new ArrayList();
    for (int i = 0; i < 25; i++) {
      transactionSummaries.add(createTransactionMetricHostData("transaction " + i, "metric", 0, 0.0,
          deploymentTimeSeriesAnalysis.getTransactionMetricSummaries().get(0).getHostData()));
    }
    deploymentTimeSeriesAnalysis.setTransactionMetricSummaries(transactionSummaries);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);
    assertThat(
        deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId).get())
        .isEqualTo(Risk.OBSERVE);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecentHighestRiskScore_getRecentHighest() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId1 = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    cvConfig = createCVConfig();
    String verificationTaskId2 = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis1 =
        createDeploymentTimeSeriesAnalysis(verificationTaskId1);
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis2 =
        createDeploymentTimeSeriesAnalysis(verificationTaskId2);
    deploymentTimeSeriesAnalysis2.setStartTime(
        deploymentTimeSeriesAnalysis1.getStartTime().minus(Duration.ofMinutes(2)));
    deploymentTimeSeriesAnalysis2.setEndTime(deploymentTimeSeriesAnalysis1.getStartTime().minus(Duration.ofMinutes(1)));
    deploymentTimeSeriesAnalysis2.setScore(.9);
    List<DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData> transactionSummaries = new ArrayList();
    for (int i = 0; i < 25; i++) {
      transactionSummaries.add(createTransactionMetricHostData("transaction " + i, "metric", 0, 0.0,
          deploymentTimeSeriesAnalysis1.getTransactionMetricSummaries().get(0).getHostData()));
    }
    deploymentTimeSeriesAnalysis1.setTransactionMetricSummaries(transactionSummaries);
    deploymentTimeSeriesAnalysis2.setTransactionMetricSummaries(transactionSummaries);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis1);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis2);
    assertThat(
        deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId).get())
        .isEqualTo(Risk.OBSERVE);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMetrics_withNodeCountByRiskStatusMap() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter = DeploymentTimeSeriesAnalysisFilter.builder()
                                                                                .healthSourceIdentifiers(null)
                                                                                .filter(null)
                                                                                .anomalousMetricsOnly(false)
                                                                                .anomalousNodesOnly(false)
                                                                                .hostNames(null)
                                                                                .transactionNames(null)
                                                                                .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodeRiskCountDTO()
                   .getTotalNodeCount())
        .isEqualTo(3);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodeRiskCountDTO()
                   .getAnomalousNodeCount())
        .isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodeRiskCountDTO()
                   .getTotalNodeCount())
        .isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodeRiskCountDTO()
                   .getAnomalousNodeCount())
        .isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(1)
                   .getCount())
        .isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(0)
                   .getCount())
        .isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(1)
                   .getDisplayName())
        .isEqualTo("Healthy");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(0)
                   .getCount())
        .isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(1)
                   .getCount())
        .isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodeRiskCountDTO()
                   .getNodeRiskCounts()
                   .get(0)
                   .getDisplayName())
        .isEqualTo("Unhealthy");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetControlDataTimeRange_verificationTypeIsCanary_analysisIsNull() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    Optional<TimeRange> maybeControlDataTimeRange = deploymentTimeSeriesAnalysisService.getControlDataTimeRange(
        AppliedDeploymentAnalysisType.CANARY, verificationJobInstance, null);
    assertThat(maybeControlDataTimeRange).isPresent();
    assertThat(maybeControlDataTimeRange.get().getStartTime()).isEqualTo(verificationJobInstance.getStartTime());
    assertThat(maybeControlDataTimeRange.get().getEndTime()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetControlDataTimeRange_verificationTypeIsCanary() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    DeploymentTimeSeriesAnalysis timeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder().endTime(Instant.ofEpochMilli(999999999)).build();
    Optional<TimeRange> maybeControlDataTimeRange = deploymentTimeSeriesAnalysisService.getControlDataTimeRange(
        AppliedDeploymentAnalysisType.CANARY, verificationJobInstance, timeSeriesAnalysis);
    assertThat(maybeControlDataTimeRange).isPresent();
    assertThat(maybeControlDataTimeRange.get().getStartTime()).isEqualTo(verificationJobInstance.getStartTime());
    assertThat(maybeControlDataTimeRange.get().getEndTime()).isEqualTo(timeSeriesAnalysis.getEndTime());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetControlDataTimeRange_verificationTypeIsSimple() {
    Optional<TimeRange> maybeControlDataTimeRange =
        deploymentTimeSeriesAnalysisService.getControlDataTimeRange(AppliedDeploymentAnalysisType.SIMPLE, null, null);
    assertThat(maybeControlDataTimeRange).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetControlDataTimeRange_verificationTypeIsRolling() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setResolvedJob(BlueGreenVerificationJob.builder()
                                               .serviceIdentifier(RuntimeParameter.builder().value("s").build())
                                               .envIdentifier(RuntimeParameter.builder().value("v").build())
                                               .duration(RuntimeParameter.builder().value("5m").build())
                                               .sensitivity(RuntimeParameter.builder().value("HIGH").build())
                                               .identifier("id")
                                               .build());
    DeploymentTimeSeriesAnalysis timeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder().endTime(Instant.ofEpochMilli(999999999)).build();
    Optional<TimeRange> maybeControlDataTimeRange = deploymentTimeSeriesAnalysisService.getControlDataTimeRange(
        AppliedDeploymentAnalysisType.ROLLING, verificationJobInstance, timeSeriesAnalysis);
    assertThat(maybeControlDataTimeRange).isPresent();
    assertThat(maybeControlDataTimeRange.get().getStartTime())
        .isEqualTo(verificationJobInstance.getResolvedJob()
                       .getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime())
                       .get()
                       .getStartTime());
    assertThat(maybeControlDataTimeRange.get().getEndTime())
        .isEqualTo(verificationJobInstance.getResolvedJob()
                       .getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime())
                       .get()
                       .getEndTime());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetTestDataTimeRange() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    DeploymentTimeSeriesAnalysis timeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder().endTime(Instant.ofEpochMilli(999999999)).build();
    TimeRange testlDataTimeRange =
        deploymentTimeSeriesAnalysisService.getTestDataTimeRange(verificationJobInstance, timeSeriesAnalysis);
    assertThat(testlDataTimeRange.getStartTime()).isEqualTo(verificationJobInstance.getStartTime());
    assertThat(testlDataTimeRange.getEndTime()).isEqualTo(timeSeriesAnalysis.getEndTime());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetTestDataTimeRange_analysisIsNull() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    TimeRange testlDataTimeRange =
        deploymentTimeSeriesAnalysisService.getTestDataTimeRange(verificationJobInstance, null);
    assertThat(testlDataTimeRange.getStartTime()).isEqualTo(verificationJobInstance.getStartTime());
    assertThat(testlDataTimeRange.getEndTime()).isNull();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetTransactionNames() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));
    List<String> transactionNameList =
        deploymentTimeSeriesAnalysisService.getTransactionNames(accountId, verificationJobInstanceId);

    assertThat(transactionNameList.size()).isEqualTo(2);
    assertThat(transactionNameList.get(0)).isEqualTo("/todolist/inside");
    assertThat(transactionNameList.get(1)).isEqualTo("/todolist/exception");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNodeNames() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));
    Set<String> nodeNameSet = deploymentTimeSeriesAnalysisService.getNodeNames(accountId, verificationJobInstanceId);

    assertThat(nodeNameSet.size()).isEqualTo(3);
    assertThat(nodeNameSet).isEqualTo(Sets.newHashSet("node1", "node2", "node3"));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNodeNames_withoutHostNames() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());
    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysisWithoutHostNames(verificationTaskId));
    Set<String> nodeNameSet = deploymentTimeSeriesAnalysisService.getNodeNames(accountId, verificationJobInstanceId);

    assertThat(nodeNameSet.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMetrics_withTransactionNameFilters() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder()
            .healthSourceIdentifiers(null)
            .filter(null)
            .anomalousMetricsOnly(false)
            .anomalousNodesOnly(false)
            .hostNames(null)
            .transactionNames(Arrays.asList("/todolist/inside"))
            .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(1);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getTransactionMetric()
                   .getTransactionName())
        .isEqualTo("/todolist/inside");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMetrics_withHostNamesFilters() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentTimeSeriesAnalysis(verificationTaskId));

    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder()
            .healthSourceIdentifiers(null)
            .filter(null)
            .anomalousMetricsOnly(false)
            .anomalousNodesOnly(false)
            .hostNames(Arrays.asList("node1", "node2"))
            .transactionNames(null)
            .build();
    PageParams pageParams = PageParams.builder().page(0).size(10).build();

    TransactionMetricInfoSummaryPageDTO transactionMetricInfoSummaryPageDTO =
        deploymentTimeSeriesAnalysisService.getMetrics(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams);

    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().size()).isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(0).getNodes().size())
        .isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse().getContent().get(1).getNodes().size())
        .isEqualTo(2);
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(0)
                   .getNodes()
                   .first()
                   .getHostName()
                   .get())
        .isEqualTo("node2");
    assertThat(transactionMetricInfoSummaryPageDTO.getPageResponse()
                   .getContent()
                   .get(1)
                   .getNodes()
                   .first()
                   .getHostName()
                   .get())
        .isEqualTo("node2");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testIsAnalysisFailFastForLatestTimeRange() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getCvConfigMap().values().iterator().next();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    boolean isAnalysisFailFast =
        deploymentTimeSeriesAnalysisService.isAnalysisFailFastForLatestTimeRange(verificationTaskId);
    assertThat(isAnalysisFailFast).isFalse();

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysisOne =
        DeploymentTimeSeriesAnalysis.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .startTime(Instant.now())
            .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
            .build();
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysisTwo =
        DeploymentTimeSeriesAnalysis.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .startTime(Instant.now().plus(1, ChronoUnit.MINUTES))
            .endTime(Instant.now().plus(2, ChronoUnit.MINUTES))
            .build();
    deploymentTimeSeriesAnalysisTwo.setFailFast(true);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysisOne);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysisTwo);

    isAnalysisFailFast = deploymentTimeSeriesAnalysisService.isAnalysisFailFastForLatestTimeRange(verificationTaskId);
    assertThat(isAnalysisFailFast).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetFilteredMetricAnalysesForVerifyStepExecutionId() {
    timeSeriesRecordDtos = getTimeSeriesRecordDtos();
    when(mockedTimeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(any(), any(), any(), any()))
        .thenReturn(timeSeriesRecordDtos);

    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getResolvedJob()
                            .getCvConfigs()
                            .stream()
                            .filter(cvConfig1 -> cvConfig1.getVerificationType() == VerificationType.TIME_SERIES)
                            .collect(Collectors.toList())
                            .get(0);
    AppDynamicsCVConfig metricCVConfig = (AppDynamicsCVConfig) cvConfig;
    metricCVConfig.setGroupName("txn");
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentMetricAnalysis(verificationTaskId));
    verificationJobInstanceService.updateAppliedDeploymentAnalysisTypeForVerificationTaskId(
        verificationJobInstance.getUuid(), verificationTaskId, AppliedDeploymentAnalysisType.CANARY);
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().build();
    List<MetricsAnalysis> metricsAnalyses =
        deploymentTimeSeriesAnalysisService.getFilteredMetricAnalysesForVerifyStepExecutionId(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    assertThat(metricsAnalyses).hasSize(2);
    assertThat(metricsAnalyses.get(0).getMetricName()).isEqualTo("name");
    assertThat(metricsAnalyses.get(0).getMetricIdentifier()).isEqualTo("identifier");
    assertThat(metricsAnalyses.get(0).getTransactionGroup()).isEqualTo("txn");
    assertThat(metricsAnalyses.get(0).getHealthSource().getIdentifier())
        .isEqualTo(cvConfig.getFullyQualifiedIdentifier());
    assertThat(metricsAnalyses.get(0).getAnalysisResult()).isEqualTo(AnalysisResult.UNHEALTHY);
    assertThat(metricsAnalyses.get(0).getMetricType()).isEqualTo(MetricType.PERFORMANCE_OTHER);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getAnalysisResult())
        .isEqualTo(AnalysisResult.NO_ANALYSIS);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getAnalysisReason())
        .isEqualTo(AnalysisReason.NO_CONTROL_DATA);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getAppliedThresholds()).contains("thresholdId");
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getControlNodeIdentifier()).isEqualTo("node3");
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getControlDataType()).isNull();
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedControlData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedControlData().get(0).getValue())
        .isEqualTo(1.0);
    assertThat(
        metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedControlData().get(0).getTimestampInMillis())
        .isEqualTo(1587549810000L);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedTestData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedTestData().get(0).getValue())
        .isEqualTo(1.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getNormalisedTestData().get(0).getTimestampInMillis())
        .isEqualTo(1587549810000L);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getTestData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getTestData().get(0).getValue()).isEqualTo(22.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getTestData().get(0).getTimestampInMillis())
        .isEqualTo(1980000);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getControlData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getControlData().get(0).getValue()).isEqualTo(9.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(1).getControlData().get(0).getTimestampInMillis())
        .isEqualTo(14040000);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getAnalysisResult())
        .isEqualTo(AnalysisResult.UNHEALTHY);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getAnalysisReason())
        .isEqualTo(AnalysisReason.ML_ANALYSIS);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getAppliedThresholds())
        .isEqualTo(List.of("thresholdId"));
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getControlNodeIdentifier()).isEqualTo("node3");
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getControlDataType())
        .isEqualTo(ControlDataType.MINIMUM_DEVIATION);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedControlData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedControlData().get(0).getValue())
        .isEqualTo(1.0);
    assertThat(
        metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedControlData().get(0).getTimestampInMillis())
        .isEqualTo(1587549810000L);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedTestData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedTestData().get(0).getValue())
        .isEqualTo(1.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNormalisedTestData().get(0).getTimestampInMillis())
        .isEqualTo(1587549810000L);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getTestData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getTestData().get(0).getValue()).isEqualTo(2332.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getTestData().get(0).getTimestampInMillis())
        .isEqualTo(265980000);

    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getControlData()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getControlData().get(0).getValue()).isEqualTo(9.0);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getControlData().get(0).getTimestampInMillis())
        .isEqualTo(14040000);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetFilteredMetricAnalysesForVerifyStepExecutionId_filteredNodeNames() {
    timeSeriesRecordDtos = getTimeSeriesRecordDtos();
    when(mockedTimeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(any(), any(), any(), any()))
        .thenReturn(timeSeriesRecordDtos);
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getResolvedJob()
                            .getCvConfigs()
                            .stream()
                            .filter(cvConfig1 -> cvConfig1.getVerificationType() == VerificationType.TIME_SERIES)
                            .collect(Collectors.toList())
                            .get(0);
    AppDynamicsCVConfig metricCVConfig = (AppDynamicsCVConfig) cvConfig;
    metricCVConfig.setGroupName("txn");
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentMetricAnalysis(verificationTaskId));
    verificationJobInstanceService.updateAppliedDeploymentAnalysisTypeForVerificationTaskId(
        verificationJobInstance.getUuid(), verificationTaskId, AppliedDeploymentAnalysisType.CANARY);
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().hostNames(List.of("node1")).build();
    List<MetricsAnalysis> metricsAnalyses =
        deploymentTimeSeriesAnalysisService.getFilteredMetricAnalysesForVerifyStepExecutionId(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    assertThat(metricsAnalyses).hasSize(2);
    assertThat(metricsAnalyses.get(0).getMetricName()).isEqualTo("name");
    assertThat(metricsAnalyses.get(0).getMetricIdentifier()).isNotBlank();
    assertThat(metricsAnalyses.get(0).getTransactionGroup()).isEqualTo("txn");
    assertThat(metricsAnalyses.get(0).getHealthSource().getIdentifier())
        .isEqualTo(cvConfig.getFullyQualifiedIdentifier());
    assertThat(metricsAnalyses.get(0).getAnalysisResult()).isEqualTo(AnalysisResult.UNHEALTHY);
    assertThat(metricsAnalyses.get(0).getMetricType()).isEqualTo(MetricType.PERFORMANCE_OTHER);
    assertThat(metricsAnalyses.get(0).getTestDataNodes()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNodeIdentifier()).isEqualTo("node1");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetFilteredMetricAnalysesForVerifyStepExecutionId_filteredAnalysisResult() {
    timeSeriesRecordDtos = getTimeSeriesRecordDtos();
    when(mockedTimeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(any(), any(), any(), any()))
        .thenReturn(timeSeriesRecordDtos);
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getResolvedJob()
                            .getCvConfigs()
                            .stream()
                            .filter(cvConfig1 -> cvConfig1.getVerificationType() == VerificationType.TIME_SERIES)
                            .collect(Collectors.toList())
                            .get(0);
    AppDynamicsCVConfig metricCVConfig = (AppDynamicsCVConfig) cvConfig;
    metricCVConfig.setGroupName("txn");
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentMetricAnalysis(verificationTaskId));
    verificationJobInstanceService.updateAppliedDeploymentAnalysisTypeForVerificationTaskId(
        verificationJobInstance.getUuid(), verificationTaskId, AppliedDeploymentAnalysisType.CANARY);
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().anomalousNodesOnly(true).anomalousMetricsOnly(true).build();
    List<MetricsAnalysis> metricsAnalyses =
        deploymentTimeSeriesAnalysisService.getFilteredMetricAnalysesForVerifyStepExecutionId(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    assertThat(metricsAnalyses).hasSize(1);
    assertThat(metricsAnalyses.get(0).getMetricName()).isEqualTo("name");
    assertThat(metricsAnalyses.get(0).getMetricIdentifier()).isNotBlank();
    assertThat(metricsAnalyses.get(0).getTransactionGroup()).isEqualTo("txn");
    assertThat(metricsAnalyses.get(0).getHealthSource().getIdentifier())
        .isEqualTo(cvConfig.getFullyQualifiedIdentifier());
    assertThat(metricsAnalyses.get(0).getAnalysisResult()).isEqualTo(AnalysisResult.UNHEALTHY);
    assertThat(metricsAnalyses.get(0).getMetricType()).isEqualTo(MetricType.PERFORMANCE_OTHER);
    assertThat(metricsAnalyses.get(0).getTestDataNodes()).hasSize(1);
    assertThat(metricsAnalyses.get(0).getTestDataNodes().get(0).getNodeIdentifier()).isEqualTo("node2");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetFilteredMetricAnalysesForVerifyStepExecutionId_filteredTransactionNames() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    CVConfig cvConfig = verificationJobInstance.getResolvedJob()
                            .getCvConfigs()
                            .stream()
                            .filter(cvConfig1 -> cvConfig1.getVerificationType() == VerificationType.TIME_SERIES)
                            .collect(Collectors.toList())
                            .get(0);
    AppDynamicsCVConfig metricCVConfig = (AppDynamicsCVConfig) cvConfig;
    metricCVConfig.setGroupName("node2");
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig.getUuid(), verificationJobInstanceId, cvConfig.getType());

    deploymentTimeSeriesAnalysisService.save(createDeploymentMetricAnalysis(verificationTaskId));
    verificationJobInstanceService.updateAppliedDeploymentAnalysisTypeForVerificationTaskId(
        verificationJobInstance.getUuid(), verificationTaskId, AppliedDeploymentAnalysisType.CANARY);
    DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter =
        DeploymentTimeSeriesAnalysisFilter.builder().transactionNames(List.of("node1")).build();
    List<MetricsAnalysis> metricsAnalyses =
        deploymentTimeSeriesAnalysisService.getFilteredMetricAnalysesForVerifyStepExecutionId(
            accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter);
    assertThat(metricsAnalyses).isEmpty();
  }

  private VerificationJobInstance createVerificationJobInstance() {
    VerificationJobInstance jobInstance = builderFactory.verificationJobInstanceBuilder().build();
    jobInstance.setAccountId(accountId);
    return jobInstance;
  }

  private DeploymentTimeSeriesAnalysisDTO.HostInfo createHostInfo(
      String hostName, int risk, Double score, boolean primary, boolean canary) {
    return DeploymentTimeSeriesAnalysisDTO.HostInfo.builder()
        .hostName(hostName)
        .risk(risk)
        .score(score)
        .primary(primary)
        .canary(canary)
        .build();
  }

  private DeploymentTimeSeriesAnalysisDTO.HostData createHostData(
      String hostName, int risk, Double score, List<Double> controlData, List<Double> testData) {
    return createHostData(hostName, risk, score, controlData, testData, Collections.EMPTY_LIST);
  }

  private DeploymentTimeSeriesAnalysisDTO.HostData createHostData(String hostName, int risk, Double score,
      List<Double> controlData, List<Double> testData, List<String> appliedThresholdIds) {
    return createHostData(hostName, risk, score, controlData, testData, appliedThresholdIds, null);
  }

  private DeploymentTimeSeriesAnalysisDTO.HostData createHostData(String hostName, int risk, Double score,
      List<Double> controlData, List<Double> testData, List<String> appliedThresholdIds, String controlNode) {
    return DeploymentTimeSeriesAnalysisDTO.HostData.builder()
        .hostName(hostName)
        .risk(risk)
        .score(score)
        .controlData(controlData)
        .testData(testData)
        .appliedThresholdIds(appliedThresholdIds)
        .nearestControlHost(controlNode)
        .build();
  }

  private DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData createTransactionMetricHostData(
      String transactionName, String metricName, int risk, Double score,
      List<DeploymentTimeSeriesAnalysisDTO.HostData> hostDataList) {
    return DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData.builder()
        .transactionName(transactionName)
        .metricName(metricName)
        .risk(risk)
        .score(score)
        .hostData(hostDataList)
        .build();
  }

  private DeploymentTimeSeriesAnalysis createDeploymentTimeSeriesAnalysis(String verificationTaskId) {
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo1 = createHostInfo("node1", -1, 0.0, false, true);
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, false, true);
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo3 = createHostInfo("node3", 2, 2.2, false, true);
    DeploymentTimeSeriesAnalysisDTO.HostData hostData1 =
        createHostData("node1", -1, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    DeploymentTimeSeriesAnalysisDTO.HostData hostData2 =
        createHostData("node2", 2, 2.0, Arrays.asList(1D), Arrays.asList(1D));

    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData1 =
        createTransactionMetricHostData(
            "/todolist/inside", "Errors per Minute", 0, 0.5, Arrays.asList(hostData1, hostData2));

    DeploymentTimeSeriesAnalysisDTO.HostData hostData3 =
        createHostData("node1", 0, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    DeploymentTimeSeriesAnalysisDTO.HostData hostData4 =
        createHostData("node2", 2, 2.0, Arrays.asList(1D), Arrays.asList(1D));
    DeploymentTimeSeriesAnalysisDTO.HostData hostData5 =
        createHostData("node3", 2, 2.0, Arrays.asList(1D), Arrays.asList(1D));

    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData2 =
        createTransactionMetricHostData(
            "/todolist/exception", "Calls per Minute", 2, 2.5, Arrays.asList(hostData3, hostData4, hostData5));
    return DeploymentTimeSeriesAnalysis.builder()
        .accountId(accountId)
        .score(.7)
        .risk(Risk.OBSERVE)
        .verificationTaskId(verificationTaskId)
        .transactionMetricSummaries(Arrays.asList(transactionMetricHostData1, transactionMetricHostData2))
        .hostSummaries(Arrays.asList(hostInfo1, hostInfo2, hostInfo3))
        .startTime(Instant.now())
        .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
        .build();
  }

  private DeploymentTimeSeriesAnalysis createDeploymentTimeSeriesAnalysisWithoutHostNames(String verificationTaskId) {
    DeploymentTimeSeriesAnalysisDTO.HostData hostData1 =
        createHostData(null, 0, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData1 =
        createTransactionMetricHostData("/todolist/inside", "Errors per Minute", 0, 0.5, Arrays.asList(hostData1));
    DeploymentTimeSeriesAnalysisDTO.HostData hostData3 =
        createHostData(null, 0, 0.0, Arrays.asList(1D), Arrays.asList(1D));
    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData2 =
        createTransactionMetricHostData("/todolist/exception", "Calls per Minute", 2, 2.5, Arrays.asList(hostData3));
    return DeploymentTimeSeriesAnalysis.builder()
        .accountId(accountId)
        .score(.7)
        .risk(Risk.OBSERVE)
        .verificationTaskId(verificationTaskId)
        .transactionMetricSummaries(Arrays.asList(transactionMetricHostData1, transactionMetricHostData2))
        .startTime(Instant.now())
        .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
        .build();
  }

  private DeploymentTimeSeriesAnalysis createDeploymentMetricAnalysis(String verificationTaskId) {
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo1 = createHostInfo("node1", -1, 0.0, false, true);
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo2 = createHostInfo("node2", 2, 2.2, false, true);
    DeploymentTimeSeriesAnalysisDTO.HostInfo hostInfo3 = createHostInfo("node3", 2, 2.2, true, false);

    DeploymentTimeSeriesAnalysisDTO.HostData hostData1 =
        createHostData("node1", -1, 0.0, List.of(1D), List.of(1D), List.of("thresholdId"), "node3");
    DeploymentTimeSeriesAnalysisDTO.HostData hostData2 =
        createHostData("node2", 2, 2.0, List.of(1D), List.of(1D), List.of("thresholdId"), "node3");
    DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData1 =
        createTransactionMetricHostData("txn", "identifier", 2, 0.5, Arrays.asList(hostData1, hostData2));

    return DeploymentTimeSeriesAnalysis.builder()
        .accountId(accountId)
        .score(.7)
        .risk(Risk.UNHEALTHY)
        .verificationTaskId(verificationTaskId)
        .transactionMetricSummaries(List.of(transactionMetricHostData1))
        .hostSummaries(Arrays.asList(hostInfo1, hostInfo2, hostInfo3))
        .startTime(Instant.now())
        .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
        .build();
  }

  private CVConfig createCVConfig() {
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    return cvConfigService.save(cvConfig);
  }

  private List<TimeSeriesRecordDTO> getTimeSeriesRecordDtos() {
    List<TimeSeriesRecordDTO> timeSeriesRecordDtos = new ArrayList<>();
    timeSeriesRecordDtos.add(TimeSeriesRecordDTO.builder()
                                 .metricIdentifier("identifier")
                                 .metricValue(22.0)
                                 .epochMinute(33L)
                                 .host("node1")
                                 .groupName("txn")
                                 .build());
    timeSeriesRecordDtos.add(TimeSeriesRecordDTO.builder()
                                 .metricIdentifier("identifier")
                                 .metricValue(2332.0)
                                 .epochMinute(4433L)
                                 .host("node2")
                                 .groupName("txn")
                                 .build());
    timeSeriesRecordDtos.add(TimeSeriesRecordDTO.builder()
                                 .metricIdentifier("identifier")
                                 .metricValue(9.0)
                                 .epochMinute(234L)
                                 .host("node3")
                                 .groupName("txn")
                                 .build());
    return timeSeriesRecordDtos;
  }
}
