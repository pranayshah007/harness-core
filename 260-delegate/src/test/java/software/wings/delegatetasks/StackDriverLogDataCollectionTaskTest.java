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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackDriverNameSpace;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;

import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ArrayMap;
import com.google.api.services.logging.v2.model.ListLogEntriesResponse;
import com.google.api.services.logging.v2.model.LogEntry;
import com.google.api.services.logging.v2.model.MonitoredResource;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class StackDriverLogDataCollectionTaskTest extends WingsBaseTest {
  @Mock private DataCollectionExecutorService dataCollectionService;
  @Mock private StackDriverDelegateService stackDriverDelegateService;
  @Mock private DelegateLogService delegateLogService;
  @Mock private GcpHelperService gcpHelperService;
  @Mock private EncryptionService encryptionService;
  @Mock private LogAnalysisStoreService logAnalysisStoreService;
  @Mock private ScheduledFuture future;
  private StackDriverLogDataCollectionTask dataCollectionTask;
  private StackDriverLogDataCollectionInfo dataCollectionInfo;

  private String delegateId = UUID.randomUUID().toString();
  private String appId = UUID.randomUUID().toString();
  private String envId = UUID.randomUUID().toString();
  private String waitId = UUID.randomUUID().toString();
  private String accountId = UUID.randomUUID().toString();
  private String stateExecutionId = UUID.randomUUID().toString();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(stackDriverDelegateService.createFilter(
             StackDriverNameSpace.POD_NAME, "kubernetes.io/container/memory/request_utilization", "dummyHost"))
        .thenReturn("testFilter dummyHost");
    when(stackDriverDelegateService.createFilter(
             StackDriverNameSpace.POD_NAME, "kubernetes.io/container/memory/request_utilization", "secondHost"))
        .thenReturn("testFilter secondHost");
    String infrastructureMappingId = UUID.randomUUID().toString();
    String timeDuration = "10";
    dataCollectionInfo = buildDataCollectionInfo();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.STACKDRIVER_COLLECT_METRIC_DATA.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
                            .build();

    dataCollectionTask = new StackDriverLogDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(delegateId).data(taskData).build(), null, null, null);
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    setupFields();
  }

  private void setupFields() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(dataCollectionTask, "stackDriverDelegateService", stackDriverDelegateService, true);
    FieldUtils.writeField(dataCollectionTask, "gcpHelperService", gcpHelperService, true);
    FieldUtils.writeField(dataCollectionTask, "delegateLogService", delegateLogService, true);
    FieldUtils.writeField(dataCollectionTask, "logAnalysisStoreService", logAnalysisStoreService, true);
    FieldUtils.writeField(dataCollectionTask, "future", future, true);
  }

  private ListLogEntriesResponse getResponse() {
    ListLogEntriesResponse response = new ListLogEntriesResponse();
    LogEntry logEntry = new LogEntry();
    logEntry.set("insertId", UUID.randomUUID().toString());

    logEntry.set("insertId", "xb9o9sg1ezc5yn");
    Map<String, Object> jsonPayload = new HashMap<>();
    jsonPayload.put("message", "Exception encountered while running feedback analysis task for 7MYO8ajFTgCutcDxiMVnzA");
    jsonPayload.put("version", "1.0.46300");
    jsonPayload.put("timestamp", "2019-12-20 05:25:29.644 +0000");
    jsonPayload.put("logger", "io.harness.jobs.workflow.logs.WorkflowFeedbackAnalysisJob");
    jsonPayload.put("thread", "verification_scheduler_Worker-8");
    logEntry.setJsonPayload(jsonPayload);
    Map<String, Object> labels = new ArrayMap<>();
    labels.put("compute.googleapis.com/resource_name", "gke-qa-private-pool-1-87f31900-jq7m");
    labels.put("container.googleapis.com/pod_name", "verification-svc-canary-5c56fd89c6-5dm2r");
    labels.put("container.googleapis.com/stream", "stdout");
    labels.put("container.googleapis.com/namespace_name", "harness");
    logEntry.put("labels", labels);
    logEntry.set("logName", "projects/playground-243019/logs/node-problem-detector");
    logEntry.set("receiveTimestamp", "2019-12-20T06:31:32.997067486Z");
    Map<String, String> resourceLabels = new ArrayMap<>();
    MonitoredResource monitoredResource = new MonitoredResource();

    resourceLabels.put("zone", "us-west1-b");
    resourceLabels.put("pod_id", "verification-svc-canary-5c56fd89c6-5dm2r");
    resourceLabels.put("project_id", "qa-setup");
    resourceLabels.put("cluster_name", "qa-private");
    resourceLabels.put("container_name", "verification-svc");
    resourceLabels.put("namespace_name", "harness");
    resourceLabels.put("instance_id", "4826274342064220385");
    monitoredResource.setLabels(resourceLabels);
    monitoredResource.put("type", "container");
    logEntry.set("resource", monitoredResource);
    logEntry.set("timestamp", "2019-12-20T06:31:28.004490Z");
    logEntry.setFactory(new JacksonFactory());
    response.setEntries(Lists.newArrayList(logEntry));
    return response;
  }

  private StackDriverLogDataCollectionInfo buildDataCollectionInfo() {
    Set<String> hosts = new HashSet<>();
    hosts.add("host1");
    hosts.add("host2");
    return StackDriverLogDataCollectionInfo.builder()
        .collectionTime(10)
        .hosts(hosts)
        .applicationId(appId)
        .stateExecutionId(stateExecutionId)
        .initialDelayMinutes(0)
        .logMessageField("jsonPayload.message")
        .hostnameField("resource.labels.pod_id")
        .gcpConfig(GcpConfig.builder().accountId(accountId).build())
        .startTime(Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(2))
        .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogs_withParsingCorrectMessageAndHost() throws Exception {
    when(stackDriverDelegateService.fetchLogs(
             any(StackDriverLogDataCollectionInfo.class), anyLong(), anyLong(), anyBoolean(), anyBoolean()))
        .thenReturn(getResponse().getEntries());
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    when(logAnalysisStoreService.save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);
    Runnable runnable = dataCollectionTask.getDataCollector(taskResult);
    runnable.run();
    ArgumentCaptor<List> taskCaptor = ArgumentCaptor.forClass(List.class);
    verify(logAnalysisStoreService, times(1))
        .save(any(), any(), any(), any(), any(), any(), any(), any(), any(), taskCaptor.capture());
    assertThat(3).isEqualTo(taskCaptor.getValue().size());

    List<LogElement> results = taskCaptor.getValue();
    assertThat(results.get(0).getClusterLabel()).isEqualTo("-3");
    assertThat(results.get(0).getHost()).isEqualTo("host1");
    assertThat(results.get(1).getClusterLabel()).isEqualTo("-3");
    assertThat(results.get(1).getHost()).isEqualTo("host2");
    assertThat(results.get(2).getClusterLabel()).isEqualTo("0");
    assertThat(results.get(2).getLogMessage())
        .isEqualTo("Exception encountered while running feedback analysis task for 7MYO8ajFTgCutcDxiMVnzA");
    assertThat(results.get(2).getHost()).isEqualTo("verification-svc-canary-5c56fd89c6-5dm2r");
  }
}
