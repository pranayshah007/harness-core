/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.servicelevelobjective.services.impl.SLOHealthIndicatorServiceImpl;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SLIMetricAnalysisStateExecutorTest extends CvNextGenTestBase {
  @Inject Map<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLOHealthIndicatorServiceImpl sloHealthIndicatorService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Mock TimeSeriesRecordService timeSeriesRecordService;
  @Mock VerificationTaskService verificationTaskService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private ServiceLevelIndicator serviceLevelIndicator;
  AnalysisStateExecutor sliMetricAnalysisStateExecutor;
  private SLIMetricAnalysisState sliMetricAnalysisState;
  ServiceLevelObjectiveDTO serviceLevelObjective;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    sliMetricAnalysisStateExecutor = stateTypeAnalysisStateExecutorMap.get(StateType.SLI_METRIC_ANALYSIS);
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceLevelObjective = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                .monitoredServiceRef(monitoredServiceDTO.getIdentifier())
                                .healthSourceRef(generateUuid())
                                .build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjective);
    serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), serviceLevelObjective.getServiceLevelIndicators().get(0).getIdentifier());
    verificationTaskId = serviceLevelIndicator.getUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "timeSeriesRecordService", timeSeriesRecordService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.getSliId(any())).thenReturn(verificationTaskId);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(generateTimeSeriesRecord());
    sliMetricAnalysisState = SLIMetricAnalysisState.builder().build();
    sliMetricAnalysisState.setInputs(input);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExecute() {
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100);
  }

  private List<TimeSeriesRecordDTO> generateTimeSeriesRecord() {
    List<TimeSeriesRecordDTO> timeSeriesDataCollectionRecordList = new ArrayList<>();
    String host = generateUuid();
    String metric1 = "metric1";
    String metric2 = "metric2";
    String metricName1 = "metricName1";
    String metricName2 = "metricName2";
    String group1 = "group1";
    String group2 = "group2";
    Double value1 = 20.0;
    Double value2 = 50.0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      TimeSeriesRecordDTO timeSeriesDataCollectionRecord1 =
          TimeSeriesRecordDTO.builder()
              .verificationTaskId(verificationTaskId)
              .host(host)
              .groupName(group1)
              .metricIdentifier(metric1)
              .metricName(metricName1)
              .metricValue(value1)
              .epochMinute(TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli()))
              .build();
      timeSeriesDataCollectionRecordList.add(timeSeriesDataCollectionRecord1);
      TimeSeriesRecordDTO timeSeriesDataCollectionRecord2 =
          TimeSeriesRecordDTO.builder()
              .verificationTaskId(verificationTaskId)
              .host(host)
              .groupName(group2)
              .metricIdentifier(metric2)
              .metricName(metricName2)
              .metricValue(value2)
              .epochMinute(TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli()))
              .build();
      timeSeriesDataCollectionRecordList.add(timeSeriesDataCollectionRecord2);
    }
    return timeSeriesDataCollectionRecordList;
  }
}
