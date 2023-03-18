/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.beans.DataCollectionExecutionStatus.QUEUED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.RUNNING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.PrometheusDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIDataCollectionTaskServiceTest extends CvNextGenTestBase {
  @Inject private DataCollectionTaskService dataCollectionTaskService;
  @Inject private HPersistence hPersistence;
  @Inject private CVConfigService cvConfigService;
  @Inject private MetricPackService metricPackService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Clock clock;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private Instant fakeNow;
  private String dataCollectionWorkerId;
  private String verificationTaskId;
  private CVConfig cvConfig;
  @Inject private PrometheusDataCollectionInfoMapper prometheusDataCollectionSLIInfoMapper;
  @Inject private SLIDataCollectionTaskServiceImpl sliDataCollectionTaskService;
  BuilderFactory builderFactory = BuilderFactory.getDefault();
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  ServiceLevelIndicator serviceLevelIndicator;

  @Before
  public void setupTests() throws IllegalAccessException {
    initMocks(this);
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    cvConfig = cvConfigService.save(createCVConfig());
    serviceLevelIndicator = createSLI();
    verificationTaskId = verificationTaskService.getSLIVerificationTaskId(accountId, serviceLevelIndicator.getUuid());
    dataCollectionTaskService = spy(dataCollectionTaskService);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    FieldUtils.writeField(
        dataCollectionTaskService, "verificationJobInstanceService", verificationJobInstanceService, true);
    fakeNow = clock.instant();
    dataCollectionWorkerId = monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(cvConfig.getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getIdentifier());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSave_dataCollectionTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTaskService.save(dataCollectionTask);
    assertThat(dataCollectionTask.getStatus()).isEqualTo(QUEUED);
    DataCollectionTask updatedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updatedDataCollectionTask.getStatus()).isEqualTo(QUEUED);
    assertThat(updatedDataCollectionTask.getVerificationTaskId()).isEqualTo(dataCollectionTask.getVerificationTaskId());
    assertThat(updatedDataCollectionTask.shouldQueueAnalysis()).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSave_handleCreateNextTask() {
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
    assertThat(savedTask.getDataCollectionInfo()).isInstanceOf(PrometheusDataCollectionInfo.class);
    assertThat(savedTask.getEndTime())
        .isEqualTo(serviceLevelIndicator.getFirstTimeDataCollectionTimeRange().getEndTime());
    assertThat(savedTask.getStartTime())
        .isEqualTo(serviceLevelIndicator.getFirstTimeDataCollectionTimeRange().getStartTime());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSave_handleCreateNextTaskAfter4HoursOfCreation() throws IllegalAccessException {
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    DataCollectionTask prevTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .get();
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    assertThat(hPersistence.createQuery(DataCollectionTask.class)
                   .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                   .count())
        .isEqualTo(1);
    Clock twoMinutesAheadClock =
        Clock.fixed(Instant.ofEpochMilli(prevTask.getCreatedAt()).plus(5, ChronoUnit.HOURS), clock.getZone());
    FieldUtils.writeField(sliDataCollectionTaskService, "clock", twoMinutesAheadClock, true);
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    assertThat(hPersistence.createQuery(DataCollectionTask.class)
                   .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                   .count())
        .isEqualTo(2);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .order(Sort.descending(DataCollectionTaskKeys.startTime))
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSave_handleCreateNextTaskAfterSuccess() throws IllegalAccessException {
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    DataCollectionTask prevTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .get();
    prevTask.setStatus(DataCollectionExecutionStatus.SUCCESS);
    hPersistence.save(prevTask);
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    assertThat(hPersistence.createQuery(DataCollectionTask.class)
                   .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                   .count())
        .isEqualTo(1);
    Clock twoMinutesAheadClock =
        Clock.fixed(Instant.ofEpochMilli(prevTask.getCreatedAt()).plus(3, ChronoUnit.MINUTES), clock.getZone());
    FieldUtils.writeField(sliDataCollectionTaskService, "clock", twoMinutesAheadClock, true);
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    assertThat(hPersistence.createQuery(DataCollectionTask.class)
                   .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                   .count())
        .isEqualTo(2);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .order(Sort.descending(DataCollectionTaskKeys.startTime))
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSave_createNextTask() {
    sliDataCollectionTaskService.handleCreateNextTask(serviceLevelIndicator);
    DataCollectionTask prevTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .get();
    sliDataCollectionTaskService.createNextTask(prevTask);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
    assertThat(savedTask.getDataCollectionInfo()).isInstanceOf(PrometheusDataCollectionInfo.class);
    assertThat(savedTask.getEndTime())
        .isEqualTo(serviceLevelIndicator.getFirstTimeDataCollectionTimeRange().getEndTime());
    assertThat(savedTask.getStartTime())
        .isEqualTo(serviceLevelIndicator.getFirstTimeDataCollectionTimeRange().getStartTime());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testDataSourceTypeDataCollectionInfoMapperMap() {
    for (DataSourceType dataSourceType : DataSourceType.values()) {
      VerificationType verificationType = dataSourceType.getVerificationType();
      if (verificationType == VerificationType.TIME_SERIES && !dataSourceType.equals(DataSourceType.STACKDRIVER)
          && !dataSourceType.equals(DataSourceType.DYNATRACE) && !dataSourceType.equals(DataSourceType.KUBERNETES)) {
        assertThat(dataSourceTypeDataCollectionInfoMapperMap.containsKey(dataSourceType)).isTrue();
      }
    }
  }

  private ServiceLevelIndicator createSLI() {
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    createMonitoredService();
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .projectIdentifier(projectIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    String identifier =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier();
    return hPersistence.createQuery(ServiceLevelIndicator.class)
        .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelIndicatorKeys.identifier, identifier)
        .get();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private DataCollectionTask getDataCollectionTask(String dataCollectionTaskId) {
    return hPersistence.get(DataCollectionTask.class, dataCollectionTaskId);
  }

  private DataCollectionTask create() {
    return create(QUEUED, DataCollectionTask.Type.SLI);
  }

  private DataCollectionTask create(DataCollectionExecutionStatus executionStatus, DataCollectionTask.Type type) {
    return SLIDataCollectionTask.builder()
        .verificationTaskId(verificationTaskId)
        .dataCollectionWorkerId(dataCollectionWorkerId)
        .type(DataCollectionTask.Type.SLI)
        .accountId(accountId)
        .startTime(fakeNow.minus(Duration.ofMinutes(7)))
        .endTime(fakeNow.minus(Duration.ofMinutes(2)))
        .status(executionStatus)
        .dataCollectionInfo(createSLIDataCollectionInfo())
        .lastPickedAt(executionStatus == RUNNING ? fakeNow.minus(Duration.ofMinutes(5)) : null)
        .build();
  }

  private DataCollectionInfo createSLIDataCollectionInfo() {
    PrometheusCVConfig cvConfig = (PrometheusCVConfig) createCVConfig();
    return prometheusDataCollectionSLIInfoMapper.toDataCollectionInfo(
        Collections.singletonList(cvConfig), serviceLevelIndicator);
  }

  private CVConfig createCVConfig() {
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().build();
    String identifier = HealthSourceService.getNameSpacedIdentifier(
        builderFactory.getContext().getServiceIdentifier() + "_" + builderFactory.getContext().getEnvIdentifier(),
        "healthSourceIdentifier");
    cvConfig.setVerificationType(VerificationType.TIME_SERIES);
    cvConfig.setAccountId(accountId);
    cvConfig.setEnabled(true);
    cvConfig.setConnectorIdentifier(generateUuid());
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setIdentifier(identifier);
    cvConfig.setMonitoringSourceName(identifier);
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(generateUuid());
    cvConfig.setGroupName("myGroupName");
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    cvConfig.setMetricPack(metricPack);
    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    return cvConfig;
  }
}
