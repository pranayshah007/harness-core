/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.metrics.RiskLevel.LOW;
import static software.wings.metrics.RiskLevel.MEDIUM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.newrelic.ExperimentalMetricRecord;
import software.wings.service.intfc.analysis.ExperimentalAnalysisService;
import software.wings.service.intfc.analysis.ExperimentalMetricAnalysisRecordService;
import software.wings.service.intfc.analysis.TimeSeriesMLAnalysisRecordService;
import software.wings.sm.StateType;
import software.wings.sm.states.NewRelicState;

import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExperimentalAnalysisServiceImplTest extends WingsBaseTest {
  @Inject private ExperimentalAnalysisService experimentalAnalysisService;
  @Mock private ExperimentalMetricAnalysisRecordService experimentalMetricAnalysisRecordService;
  @Mock private TimeSeriesMLAnalysisRecordService timeSeriesMLAnalysisRecordService;

  private String stateExecutionId = "stateExecutionId";
  private String workflowExecutionId = "workflowExecutionId";
  private StateType stateType = StateType.NEW_RELIC;
  private String expName = "ts";
  private String groupName = "default";

  private List<String> hosts = Lists.newArrayList("host1", "host2");
  private List<String> transactions = Lists.newArrayList("/getdata", "/postdata", "/process");
  private List<String> metrics = Lists.newArrayList("requestsPerMinute", "averageResponseTime", "error", "apdexScore");

  private ExperimentalMetricAnalysisRecord getExperimentalRecord(List<Integer> riskValues) {
    ExperimentalMetricAnalysisRecord analysisRecord = ExperimentalMetricAnalysisRecord.builder()
                                                          .mismatched(true)
                                                          .experimentName(expName)
                                                          .experimentStatus(ExperimentStatus.UNDETERMINED)
                                                          .build();
    analysisRecord.setStateType(stateType);
    analysisRecord.setWorkflowExecutionId(workflowExecutionId);
    analysisRecord.setStateExecutionId(stateExecutionId);
    analysisRecord.setAnalysisMinute(0);
    analysisRecord.setGroupName(groupName);
    analysisRecord.setAggregatedRisk(riskValues.get(0));
    analysisRecord.setTransactions(getTransactions(riskValues));
    analysisRecord.bundleAsJosnAndCompress();
    return analysisRecord;
  }

  private TimeSeriesMLAnalysisRecord getAnalysisRecord(List<Integer> riskValues) {
    TimeSeriesMLAnalysisRecord analysisRecord = new TimeSeriesMLAnalysisRecord();
    analysisRecord.setStateType(stateType);
    analysisRecord.setWorkflowExecutionId(workflowExecutionId);
    analysisRecord.setStateExecutionId(stateExecutionId);
    analysisRecord.setAnalysisMinute(0);
    analysisRecord.setGroupName(groupName);
    analysisRecord.setAggregatedRisk(riskValues.get(0));
    analysisRecord.setTransactions(getTransactions(riskValues));
    analysisRecord.bundleAsJosnAndCompress();
    return analysisRecord;
  }

  private Map<String, TimeSeriesMLTxnSummary> getTransactions(List<Integer> riskValues) {
    Map<String, TimeSeriesMLTxnSummary> txnSummary = new HashMap<>();
    int counter = 1;
    for (String txn : transactions) {
      Map<String, TimeSeriesMLMetricSummary> metricSummary = new HashMap<>();
      for (String metric : metrics) {
        TimeSeriesMLMetricSummary ms = new TimeSeriesMLMetricSummary();
        ms.setMetric_name(metric);
        ms.setMetric_type(NewRelicState.getMetricTypeForMetric(metric));
        ms.setMax_risk(riskValues.get(counter));
        counter++;
        metricSummary.put(metric, ms);
      }
      TimeSeriesMLTxnSummary ts = new TimeSeriesMLTxnSummary();
      ts.setTxn_name(txn);
      ts.setGroup_name(groupName);
      ts.setMetrics(metricSummary);
      txnSummary.put(txn, ts);
    }
    return txnSummary;
  }

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    FieldUtils.writeField(experimentalAnalysisService, "experimentalMetricAnalysisRecordService",
        experimentalMetricAnalysisRecordService, true);
    FieldUtils.writeField(
        experimentalAnalysisService, "timeSeriesMLAnalysisRecordService", timeSeriesMLAnalysisRecordService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExperimentalMetricAnalysisSummary_WithoutMismatchData() {
    when(experimentalMetricAnalysisRecordService.getLastAnalysisRecord(anyString(), anyString()))
        .thenReturn(getExperimentalRecord(Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
    when(timeSeriesMLAnalysisRecordService.getLastAnalysisRecord(anyString()))
        .thenReturn(getAnalysisRecord(Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));

    assertThatThrownBy(
        () -> experimentalAnalysisService.getExperimentalMetricAnalysisSummary(stateExecutionId, stateType, expName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExperimentalMetricAnalysisSummary_WithMismatchData() {
    when(experimentalMetricAnalysisRecordService.getLastAnalysisRecord(anyString(), anyString()))
        .thenReturn(getExperimentalRecord(Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)));
    when(timeSeriesMLAnalysisRecordService.getLastAnalysisRecord(anyString()))
        .thenReturn(getAnalysisRecord(Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));

    ExperimentalMetricRecord record =
        experimentalAnalysisService.getExperimentalMetricAnalysisSummary(stateExecutionId, stateType, expName);
    assertThat(record).isNotNull();
    assertThat(record.getRiskLevel()).isEqualByComparingTo(LOW);
    assertThat(record.getExperimentalRiskLevel()).isEqualByComparingTo(MEDIUM);
    assertThat(record.isMismatch()).isEqualTo(true);
    assertThat(record.getMetricAnalysis().size()).isEqualTo(1);
    assertThat(record.getMetricAnalysis().get(0).getRiskLevel()).isEqualByComparingTo(LOW);
    assertThat(record.getMetricAnalysis().get(0).getExperimentalRiskLevel()).isEqualByComparingTo(MEDIUM);
    assertThat(record.getMetricAnalysis().get(0).isMismatch()).isEqualTo(true);
    assertThat(record.getMetricAnalysis().get(0).getMetricValues().size()).isEqualTo(1);
    assertThat(record.getMetricAnalysis().get(0).getMetricValues().get(0).getRiskLevel()).isEqualByComparingTo(LOW);
    assertThat(record.getMetricAnalysis().get(0).getMetricValues().get(0).getExperimentalRiskLevel())
        .isEqualByComparingTo(MEDIUM);
    assertThat(record.getMetricAnalysis().get(0).getMetricValues().get(0).isMismatch()).isEqualTo(true);
  }
}
