/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.NewRelicApplicationInstance;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicMetric;
import software.wings.service.impl.newrelic.NewRelicMetricData;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricSlice;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricTimeSlice;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

@Slf4j
public class NewRelicDataCollectionTaskTest extends WingsBaseTest {
  @Mock private NewRelicDelegateService newRelicDelegateService;
  @Mock private MetricDataStoreService metricDataStoreService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DataCollectionExecutorService dataCollectionService;

  private final NewRelicDataCollectionTask task =
      Mockito.spy(new NewRelicDataCollectionTask(DelegateTaskPackage.builder()
                                                     .delegateId("delegateId")
                                                     .data(TaskData.builder()
                                                               .async(true)
                                                               .taskType(TaskType.NEWRELIC_COLLECT_METRIC_DATA.name())
                                                               .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                               .build())
                                                     .build(),
          null, notifyResponseData -> {}, () -> true));
  private DataCollectionTaskResult dataCollectionTaskResult;
  private NewRelicDataCollectionInfo dataCollectionInfo;
  private final Set<NewRelicMetric> txnsToCollect =
      Sets.newHashSet(NewRelicMetric.builder().name("WebTransaction/txn1").build(),
          NewRelicMetric.builder().name("WebTransaction/txn2").build(),
          NewRelicMetric.builder().name("WebTransaction/txn3").build());
  private final List<NewRelicApplicationInstance> instances =
      Lists.newArrayList(NewRelicApplicationInstance.builder().host("host1").id(1).port(80).build(),
          NewRelicApplicationInstance.builder().host("host2").id(2).port(81).build());

  @Before
  public void setUp() throws IOException, CloneNotSupportedException {
    on(task).set("newRelicDelegateService", newRelicDelegateService);
    on(task).set("metricStoreService", metricDataStoreService);
    on(task).set("dataCollectionService", dataCollectionService);

    NewRelicConfig config = NewRelicConfig.builder()
                                .accountId(generateUuid())
                                .newRelicUrl("newRelicUrl")
                                .apiKey(generateUuid().toCharArray())
                                .build();
    dataCollectionInfo = NewRelicDataCollectionInfo.builder()
                             .newRelicConfig(config)
                             .applicationId(generateUuid())
                             .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + generateUuid())
                             .serviceId(generateUuid())
                             .startTime(Instant.now().minus(10, ChronoUnit.MINUTES).toEpochMilli())
                             .collectionTime(10)
                             .newRelicAppId(1)
                             .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                             .dataCollectionMinute(0)
                             .hosts(new HashMap<>())
                             .encryptedDataDetails(new ArrayList<>())
                             .settingAttributeId(generateUuid())
                             .checkNotAllowedStrings(Boolean.FALSE)
                             .build();
    dataCollectionInfo.getHosts().put("host1", "host1");
    dataCollectionInfo.getHosts().put("host2", "host2");
    dataCollectionTaskResult = task.initDataCollection(dataCollectionInfo);
    doReturn(txnsToCollect)
        .when(newRelicDelegateService)
        .getTxnsWithDataInLastHour(any(), any(), any(), anyLong(), anyBoolean(), any());
    doReturn(instances).when(newRelicDelegateService).getApplicationInstances(any(), any(), anyLong(), any());
  }

  @Data
  private class NewRelicTransactions {
    private double average_response_time;
    private long requests_per_minute;
    private long call_count;
    private long error_count;
    private double score;
  }

  private NewRelicMetricSlice getMetricData(String txnName, Double respTime, Integer callCount,
      Integer requestsPerMinute, Integer error_count, Double score) {
    NewRelicTransactions transactions = new NewRelicTransactions();
    if (respTime != null) {
      transactions.setAverage_response_time(respTime);
    }
    if (callCount != null) {
      transactions.setCall_count(callCount);
    }
    if (requestsPerMinute != null) {
      transactions.setRequests_per_minute(requestsPerMinute);
    }
    if (error_count != null) {
      transactions.setError_count(error_count);
    }
    if (score != null) {
      transactions.setScore(score);
    }
    NewRelicMetricTimeSlice newRelicMetricTimeSlice = new NewRelicMetricTimeSlice();
    newRelicMetricTimeSlice.setFrom(Instant.now().minusSeconds(120).toString());
    newRelicMetricTimeSlice.setTo(Instant.now().minusSeconds(60).toString());
    newRelicMetricTimeSlice.setValues(transactions);
    NewRelicMetricSlice webMetricSlice = new NewRelicMetricSlice();
    webMetricSlice.setName(txnName);
    webMetricSlice.setTimeslices(Lists.newArrayList(newRelicMetricTimeSlice));
    return webMetricSlice;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRun() throws IOException {
    NewRelicMetricSlice slice1 = getMetricData("WebTransaction/txn1", 1.2, 2, 2, 2, 0.8);
    NewRelicMetricSlice slice2 = getMetricData("WebTransaction/txn2", 1.2, 0, 0, 8, 1.0);

    NewRelicMetricData webMetricData = NewRelicMetricData.builder()
                                           .metrics_found(Sets.newHashSet("WebTransaction/txn1", "WebTransaction/txn2"))
                                           .metrics(Sets.newHashSet(slice1, slice2))
                                           .build();
    doReturn(webMetricData)
        .when(newRelicDelegateService)
        .getMetricDataApplicationInstance(any(), any(), anyLong(), anyLong(), any(), anyLong(), anyLong(), any());
    doReturn(Boolean.TRUE).when(task).saveMetrics(any(), any(), any(), any());
    task.getDataCollector(dataCollectionTaskResult).run();

    ArgumentCaptor<List> dataRecordCaptors = ArgumentCaptor.forClass(List.class);
    verify(task, times(3))
        .saveMetrics(eq(dataCollectionInfo.getNewRelicConfig().getAccountId()),
            eq(dataCollectionInfo.getApplicationId()), eq(dataCollectionInfo.getStateExecutionId()),
            dataRecordCaptors.capture());

    List<List> dataRecords = dataRecordCaptors.getAllValues();
    assertThat(dataRecords.size()).isEqualTo(3);
    assertThat(dataRecords.get(0).size()).isEqualTo(2);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(0).get(0)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(0).get(1)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(1).get(0)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(1).get(1)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(2).get(0)).getName()).isEqualTo("Harness heartbeat metric");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRun_without_web_transactions() throws IOException {
    NewRelicMetricSlice slice1 = getMetricData("WebTransaction/txn1", null, null, null, 2, 0.8);
    NewRelicMetricSlice slice2 = getMetricData("WebTransaction/txn2", null, null, null, 8, 1.0);

    NewRelicMetricData webMetricData = NewRelicMetricData.builder()
                                           .metrics_found(Sets.newHashSet("WebTransaction/txn1", "WebTransaction/txn2"))
                                           .metrics(Sets.newHashSet(slice1, slice2))
                                           .build();
    doReturn(webMetricData)
        .when(newRelicDelegateService)
        .getMetricDataApplicationInstance(any(), any(), anyLong(), anyLong(), any(), anyLong(), anyLong(), any());
    doReturn(Boolean.TRUE).when(task).saveMetrics(any(), any(), any(), any());
    task.getDataCollector(dataCollectionTaskResult).run();

    ArgumentCaptor<List> dataRecordCaptors = ArgumentCaptor.forClass(List.class);
    verify(task, times(3))
        .saveMetrics(eq(dataCollectionInfo.getNewRelicConfig().getAccountId()),
            eq(dataCollectionInfo.getApplicationId()), eq(dataCollectionInfo.getStateExecutionId()),
            dataRecordCaptors.capture());

    List<List> dataRecords = dataRecordCaptors.getAllValues();
    assertThat(dataRecords.size()).isEqualTo(3);
    assertThat(dataRecords.get(0).size()).isEqualTo(2);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(0).get(0)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(0).get(1)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(1).get(0)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(1).get(1)).getValues().size()).isEqualTo(5);
    assertThat(((NewRelicMetricDataRecord) dataRecords.get(2).get(0)).getName()).isEqualTo("Harness heartbeat metric");
  }
}
