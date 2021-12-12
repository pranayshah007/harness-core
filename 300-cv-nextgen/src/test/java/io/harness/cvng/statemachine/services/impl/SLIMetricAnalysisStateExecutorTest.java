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
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
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
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  @Inject Map<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap;
  AnalysisStateExecutor sliMetricAnalysisStateExecutor;
  private SLIMetricAnalysisState sliMetricAnalysisState;
  @Mock TimeSeriesRecordService timeSeriesRecordService;
  BuilderFactory builderFactory;
  @Mock ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Mock VerificationTaskService verificationTaskService;
  private ServiceLevelIndicator serviceLevelIndicator;
  @Inject HPersistence hPersistence;
  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    sliMetricAnalysisStateExecutor = stateTypeAnalysisStateExecutorMap.get(StateType.SLI_METRIC_ANALYSIS);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);
    serviceLevelIndicator = ThresholdServiceLevelIndicator.builder()
                                .metric1("metric1")
                                .thresholdType(ThresholdType.GREATER_THAN)
                                .thresholdValue(50.0)
                                .sliMissingDataType(SLIMissingDataType.GOOD)
                                .accountId(builderFactory.getContext().getAccountId())
                                .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                .sliMetricType(SLIMetricType.THRESHOLD)
                                .healthSourceIdentifier(generateUuid())
                                .monitoredServiceIdentifier(generateUuid())
                                .uuid(generateUuid())
                                .build();
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();
    FieldUtils.writeField(
        sliMetricAnalysisStateExecutor, "serviceLevelIndicatorService", serviceLevelIndicatorService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "timeSeriesRecordService", timeSeriesRecordService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.getSliId(any())).thenReturn(verificationTaskId);
    when(serviceLevelIndicatorService.get(any())).thenReturn(serviceLevelIndicator);
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
  }

  private List<TimeSeriesRecordDTO> generateTimeSeriesRecord() {
    List<TimeSeriesRecordDTO> timeSeriesDataCollectionRecordList = new ArrayList<>();
    String host = generateUuid();
    String metric1 = "metric1";
    String group1 = "group1";
    Double value1 = 90.0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      TimeSeriesRecordDTO timeSeriesDataCollectionRecord =
          TimeSeriesRecordDTO.builder()
              .verificationTaskId(verificationTaskId)
              .host(host)
              .groupName(group1)
              .metricName(metric1)
              .metricValue(value1)
              .epochMinute(TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli()))
              .build();
      timeSeriesDataCollectionRecordList.add(timeSeriesDataCollectionRecord);
    }
    return timeSeriesDataCollectionRecordList;
  }
}
