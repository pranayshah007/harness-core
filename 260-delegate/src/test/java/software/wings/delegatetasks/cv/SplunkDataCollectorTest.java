/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SplunkConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;

import com.google.common.collect.Lists;
import com.splunk.Job;
import com.splunk.Service;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class SplunkDataCollectorTest extends CategoryTest {
  private SplunkConfig config;
  private SplunkDataCollector splunkDataCollector;

  @Before
  public void setUp() throws IllegalAccessException {
    initMocks(this);
    config = SplunkConfig.builder()
                 .accountId("123")
                 .splunkUrl("https://splunk-test-host.com:8089")
                 .username("123")
                 .password("123".toCharArray())
                 .build();
    splunkDataCollector = spy(new SplunkDataCollector());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testInitSplunkService() {
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 = createDataCollectionInfo();
    splunkDataCollector.init(dataCollectionExecutionContext, splunkDataCollectionInfoV2);
  }
  // TODO: test creating job object with right arguments

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogsWithoutHost() {
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    when(dataCollectionExecutionContext.getActivityLogger()).thenReturn(mock(Logger.class));
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 = createDataCollectionInfo();
    splunkDataCollector.init(dataCollectionExecutionContext, splunkDataCollectionInfoV2);
    Service service = mock(Service.class);
    doReturn(service).when(splunkDataCollector).initSplunkServiceWithToken(config);
    Job job = mock(Job.class);
    doReturn(job).when(splunkDataCollector).createSearchJob(any(), any(), any(), any());
    when(job.getResults(any())).thenReturn(getSplunkJsonResponseInputStream());
    ThirdPartyApiCallLog thirdPartyApiCallLog = mock(ThirdPartyApiCallLog.class);
    when(dataCollectionExecutionContext.createApiCallLog()).thenReturn(thirdPartyApiCallLog);
    List<LogElement> logElements = splunkDataCollector.fetchLogs();
    verify(splunkDataCollector)
        .createSearchJob(any(),
            eq("search *exception* | bin _time span=1m | cluster t=0.9999 showcount=t labelonly=t| table _time, _raw,cluster_label, host | stats latest(_raw) as _raw count as cluster_count by _time,cluster_label,host"),
            any(), any());
    assertThat(logElements.size()).isEqualTo(2);
    assertThat(logElements.get(0).getHost()).isEqualTo("todo-app-with-verification-todo-app-qa-3-5b7d99ccf9-2mr9x");
    assertThat(logElements.get(1).getHost()).isEqualTo("todo-app-with-verification-todo-app-qa-4-5b7d99ccf9-2mr9x");
    assertThat(logElements.get(0).getClusterLabel()).isEqualTo("1");
    assertThat(logElements.get(1).getClusterLabel()).isEqualTo("2");
    assertThat(logElements.get(0).getLogMessage()).isEqualTo("test log message with exception cluster 1");
    assertThat(logElements.get(1).getLogMessage()).isEqualTo("test log message with exception cluster 2");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withHost() {
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    when(dataCollectionExecutionContext.getActivityLogger()).thenReturn(mock(Logger.class));
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 = createDataCollectionInfo();
    splunkDataCollector.init(dataCollectionExecutionContext, splunkDataCollectionInfoV2);
    Service service = mock(Service.class);
    doReturn(service).when(splunkDataCollector).initSplunkServiceWithToken(config);
    Job job = mock(Job.class);
    doReturn(job).when(splunkDataCollector).createSearchJob(any(), any(), any(), any());
    when(job.getResults(any())).thenReturn(getSplunkJsonResponseInputStream());
    ThirdPartyApiCallLog thirdPartyApiCallLog = mock(ThirdPartyApiCallLog.class);
    when(dataCollectionExecutionContext.createApiCallLog()).thenReturn(thirdPartyApiCallLog);
    List<LogElement> logElements =
        splunkDataCollector.fetchLogs(Lists.newArrayList("todo-app-with-verification-todo-app-qa-3-5b7d99ccf9-2mr9x",
            "todo-app-with-verification-todo-app-qa-4-5b7d99ccf9-2mr9x"));
    verify(splunkDataCollector)
        .createSearchJob(any(),
            eq("search *exception* host=todo-app-with-verification-todo-app-qa-3-5b7d99ccf9-2mr9x OR host=todo-app-with-verification-todo-app-qa-4-5b7d99ccf9-2mr9x| bin _time span=1m | cluster t=0.9999 showcount=t labelonly=t| table _time, _raw,cluster_label, host | stats latest(_raw) as _raw count as cluster_count by _time,cluster_label,host"),
            any(), any());
    assertThat(logElements.size()).isEqualTo(2);
    assertThat(logElements.get(0).getHost()).isEqualTo("todo-app-with-verification-todo-app-qa-3-5b7d99ccf9-2mr9x");
    assertThat(logElements.get(1).getHost()).isEqualTo("todo-app-with-verification-todo-app-qa-4-5b7d99ccf9-2mr9x");
    assertThat(logElements.get(0).getClusterLabel()).isEqualTo("1");
    assertThat(logElements.get(1).getClusterLabel()).isEqualTo("2");
    assertThat(logElements.get(0).getLogMessage()).isEqualTo("test log message with exception cluster 1");
    assertThat(logElements.get(1).getLogMessage()).isEqualTo("test log message with exception cluster 2");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withHostWhenBatchSizeIdGreaterThen5() {
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    when(dataCollectionExecutionContext.getActivityLogger()).thenReturn(mock(Logger.class));
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 = createDataCollectionInfo();
    splunkDataCollector.init(dataCollectionExecutionContext, splunkDataCollectionInfoV2);
    assertThatThrownBy(() -> splunkDataCollector.fetchLogs(Lists.newArrayList("1", "2", "3", "4", "5", "6")))
        .hasMessage("hostBatch size can not be greater than 5");
  }

  private InputStream getSplunkJsonResponseInputStream() {
    @Language("JSON")
    String splunkResponse = "{\n"
        + "  \"preview\": false,\n"
        + "  \"init_offset\": 0,\n"
        + "  \"messages\": [],\n"
        + "  \"fields\": [\n"
        + "    {\n"
        + "      \"name\": \"_time\",\n"
        + "      \"groupby_rank\": \"0\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"cluster_label\",\n"
        + "      \"groupby_rank\": \"1\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"host\",\n"
        + "      \"groupby_rank\": \"2\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"_raw\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"cluster_count\"\n"
        + "    }\n"
        + "  ],\n"
        + "  \"results\": [\n"
        + "    {\n"
        + "      \"_time\": \"2019-08-20T10:25:00.000+00:00\",\n"
        + "      \"cluster_label\": \"1\",\n"
        + "      \"host\": \"todo-app-with-verification-todo-app-qa-3-5b7d99ccf9-2mr9x\",\n"
        + "      \"_raw\": \"test log message with exception cluster 1\",\n"
        + "      \"cluster_count\": \"1\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"_time\": \"2019-08-20T10:25:00.000+00:00\",\n"
        + "      \"cluster_label\": \"2\",\n"
        + "      \"host\": \"todo-app-with-verification-todo-app-qa-4-5b7d99ccf9-2mr9x\",\n"
        + "      \"_raw\": \"test log message with exception cluster 2\",\n"
        + "      \"cluster_count\": \"1\"\n"
        + "    }\n"
        + "  ],\n"
        + "  \"highlighted\": {}\n"
        + "}";
    return IOUtils.toInputStream(splunkResponse, Charset.defaultCharset());
  }

  private SplunkDataCollectionInfoV2 createDataCollectionInfo() {
    return SplunkDataCollectionInfoV2.builder()
        .startTime(Instant.now())
        .endTime(Instant.now())
        .splunkConfig(config)
        .query("*exception*")
        .hostnameField("host")
        .isAdvancedQuery(false)
        .build();
  }
}
