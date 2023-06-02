/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.WingsBaseTest;
import software.wings.beans.ElkConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.elk.ElkDataCollectionInfo;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.impl.elk.ElkQueryType;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ElkLogzDataCollectionTaskTest extends WingsBaseTest {
  @Mock private DataCollectionExecutorService dataCollectionService;
  @Mock private EncryptionService encryptionService;
  @Mock private ElkDelegateService elkDelegateService;
  @Mock private LogAnalysisStoreService logAnalysisStoreService;
  @Mock private ScheduledFuture future;
  private ElkLogzDataCollectionTask dataCollectionTask;
  private ElkDataCollectionInfo dataCollectionInfo;

  private String delegateId = UUID.randomUUID().toString();
  private String appId = UUID.randomUUID().toString();
  private String envId = UUID.randomUUID().toString();
  private String waitId = UUID.randomUUID().toString();
  private String accountId = UUID.randomUUID().toString();
  private String stateExecutionId = UUID.randomUUID().toString();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    String timeDuration = "10";
    dataCollectionInfo = buildDataCollectionInfo();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.ELK_COLLECT_LOG_DATA.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
                            .build();

    dataCollectionTask = new ElkLogzDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(delegateId).data(taskData).build(), null, null, null);
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    setupFields();
  }

  private void setupFields() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(dataCollectionTask, "elkDelegateService", elkDelegateService, true);
    FieldUtils.writeField(dataCollectionTask, "logAnalysisStoreService", logAnalysisStoreService, true);
    FieldUtils.writeField(dataCollectionTask, "future", future, true);
  }

  private ElkDataCollectionInfo buildDataCollectionInfo() {
    Set<String> hosts = new HashSet<>();
    hosts.add("harness-example-deployment-canary-5f65dcf968-6slrm");
    long startTime = Timestamp.currentMinuteBoundary();
    return ElkDataCollectionInfo.builder()
        .accountId(accountId)
        .applicationId(appId)
        .stateExecutionId(stateExecutionId)
        .hosts(hosts)
        .startMinute((int) TimeUnit.MILLISECONDS.toMinutes(startTime))
        .startTime(startTime)
        .collectionTime(5)
        .queryType(ElkQueryType.MATCH)
        .query("exception")
        .hostnameField("hostname")
        .messageField("message")
        .timestampField("@timestamp")
        .timestampFieldFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        .elkConfig(ElkConfig.builder()
                       .elkConnector(ElkConnector.ELASTIC_SEARCH_SERVER)
                       .accountId(accountId)
                       .elkUrl("https://elk-test.com/")
                       .build())
        .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogsWithCorrectDataCollectionMinuteOnMultipleCalls() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);

    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(searchResponse());
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable runnable = dataCollectionTask.getDataCollector(taskResult);
    runnable.run();
    runnable.run();
    ArgumentCaptor<List> taskCaptor = ArgumentCaptor.forClass(List.class);
    verify(logAnalysisStoreService, times(2))
        .save(any(), any(), any(), any(), any(), any(), any(), any(), any(), taskCaptor.capture());
    assertThat(taskCaptor.getValue().size()).isEqualTo(2);

    List<List> results = taskCaptor.getAllValues();
    assertThat(results.size()).isEqualTo(2);
    List<LogElement> first = results.get(0);
    assertThat(first.get(0).getLogCollectionMinute()).isEqualTo(dataCollectionInfo.getStartMinute());
    List<LogElement> second = results.get(1);
    assertThat(second.get(0).getLogCollectionMinute()).isEqualTo(dataCollectionInfo.getStartMinute() + 1);
    assertThat(first.get(0).getClusterLabel()).isEqualTo("-3");
    assertThat(first.get(1).getLogMessage())
        .isEqualTo("java.lang.RuntimeException: Method throws runtime exception java.lang.Thread.run(Thread.java:748)");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogsWithCorrectDataCollectionMinuteOnMultipleCallsVersion7() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);

    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(searchResponseVersion7());
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable runnable = dataCollectionTask.getDataCollector(taskResult);
    runnable.run();
    runnable.run();
    ArgumentCaptor<List> taskCaptor = ArgumentCaptor.forClass(List.class);
    verify(logAnalysisStoreService, times(2))
        .save(any(), any(), any(), any(), any(), any(), any(), any(), any(), taskCaptor.capture());
    assertThat(taskCaptor.getValue().size()).isEqualTo(2);

    List<List> results = taskCaptor.getAllValues();
    assertThat(results.size()).isEqualTo(2);
    List<LogElement> first = results.get(0);
    assertThat(first.get(0).getLogCollectionMinute()).isEqualTo(dataCollectionInfo.getStartMinute());
    List<LogElement> second = results.get(1);
    assertThat(second.get(0).getLogCollectionMinute()).isEqualTo(dataCollectionInfo.getStartMinute() + 1);
    assertThat(first.get(0).getClusterLabel()).isEqualTo("-3");
    assertThat(first.get(1).getLogMessage())
        .isEqualTo("java.lang.RuntimeException: Method throws runtime exception java.lang.Thread.run(Thread.java:748)");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogsWithCorrectParamsInSearch() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);

    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(searchResponse());
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable runnable = dataCollectionTask.getDataCollector(taskResult);
    runnable.run();
    runnable.run();
    ArgumentCaptor<ElkLogFetchRequest> taskCaptor = ArgumentCaptor.forClass(ElkLogFetchRequest.class);
    verify(elkDelegateService, times(2)).search(any(), any(), taskCaptor.capture(), any(), anyInt());

    List<ElkLogFetchRequest> results = taskCaptor.getAllValues();
    assertThat(results.size()).isEqualTo(2);

    assertThat(results.get(0).getEndTime())
        .isEqualTo(Instant.ofEpochMilli(dataCollectionInfo.getStartTime()).plus(Duration.ofMinutes(1)).toEpochMilli());
    assertThat(results.get(1).getEndTime())
        .isEqualTo(Instant.ofEpochMilli(dataCollectionInfo.getStartTime()).plus(Duration.ofMinutes(2)).toEpochMilli());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogsWithCorrectParamsInSearchVersion7() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);

    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(searchResponseVersion7());
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable runnable = dataCollectionTask.getDataCollector(taskResult);
    runnable.run();
    runnable.run();
    ArgumentCaptor<ElkLogFetchRequest> taskCaptor = ArgumentCaptor.forClass(ElkLogFetchRequest.class);
    verify(elkDelegateService, times(2)).search(any(), any(), taskCaptor.capture(), any(), anyInt());

    List<ElkLogFetchRequest> results = taskCaptor.getAllValues();
    assertThat(results.size()).isEqualTo(2);

    assertThat(results.get(0).getEndTime())
        .isEqualTo(Instant.ofEpochMilli(dataCollectionInfo.getStartTime()).plus(Duration.ofMinutes(1)).toEpochMilli());
    assertThat(results.get(1).getEndTime())
        .isEqualTo(Instant.ofEpochMilli(dataCollectionInfo.getStartTime()).plus(Duration.ofMinutes(2)).toEpochMilli());
  }

  private Object searchResponse() throws IOException {
    String json = "{\n"
        + "  \"took\" : 4,\n"
        + "  \"timed_out\" : false,\n"
        + "  \"_shards\" : {\n"
        + "    \"total\" : 5,\n"
        + "    \"successful\" : 5,\n"
        + "    \"skipped\" : 0,\n"
        + "    \"failed\" : 0\n"
        + "  },\n"
        + "  \"hits\" : {\n"
        + "    \"total\" : 1,\n"
        + "    \"max_score\" : 0.0,\n"
        + "    \"hits\" : [ {\n"
        + "      \"_index\" : \"integration-test\",\n"
        + "      \"_type\" : \"_doc\",\n"
        + "      \"_id\" : \"-_R4_W0BhJ3XTYaV0z5L\",\n"
        + "      \"_score\" : 0.0,\n"
        + "      \"_source\" : {\n"
        + "        \"hostname\" : \"harness-example-deployment-canary-5f65dcf968-6slrm\",\n"
        + "        \"level\" : \"WARN\",\n"
        + "        \"message\" : \"java.lang.RuntimeException: Method throws runtime exception java.lang.Thread.run(Thread.java:748)\",\n"
        + "        \"@timestamp\" : \"2019-10-24T11:13:20.492Z\"\n"
        + "      }\n"
        + "    }]\n"
        + "  }\n"
        + "}";
    return new ObjectMapper().readValue(json, HashMap.class);
  }

  private Object searchResponseVersion7() throws IOException {
    String json = "{\n"
        + "  \"took\" : 4,\n"
        + "  \"timed_out\" : false,\n"
        + "  \"_shards\" : {\n"
        + "    \"total\" : 5,\n"
        + "    \"successful\" : 5,\n"
        + "    \"skipped\" : 0,\n"
        + "    \"failed\" : 0\n"
        + "  },\n"
        + "  \"hits\" : {\n"
        + "    \"total\" : {\n"
        + "    \"value\" : 1,\n"
        + "    \"relation\" : \"eq\"\n"
        + "  },\n"
        + "    \"max_score\" : 0.0,\n"
        + "    \"hits\" : [ {\n"
        + "      \"_index\" : \"integration-test\",\n"
        + "      \"_type\" : \"_doc\",\n"
        + "      \"_id\" : \"-_R4_W0BhJ3XTYaV0z5L\",\n"
        + "      \"_score\" : 0.0,\n"
        + "      \"_source\" : {\n"
        + "        \"hostname\" : \"harness-example-deployment-canary-5f65dcf968-6slrm\",\n"
        + "        \"level\" : \"WARN\",\n"
        + "        \"message\" : \"java.lang.RuntimeException: Method throws runtime exception java.lang.Thread.run(Thread.java:748)\",\n"
        + "        \"@timestamp\" : \"2019-10-24T11:13:20.492Z\"\n"
        + "      }\n"
        + "    }]\n"
        + "  }\n"
        + "}";
    return new ObjectMapper().readValue(json, HashMap.class);
  }
}
