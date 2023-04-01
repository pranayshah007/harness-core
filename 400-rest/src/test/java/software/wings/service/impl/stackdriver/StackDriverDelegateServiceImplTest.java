/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.common.VerificationConstants.CV_DATA_COLLECTION_INTERVAL_IN_MINUTE;
import static software.wings.service.impl.stackdriver.StackDriverDelegateServiceImpl.MAX_LOGS_PER_MINUTE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.google.api.services.logging.v2.Logging;
import com.google.api.services.logging.v2.model.ListLogEntriesRequest;
import com.google.api.services.logging.v2.model.ListLogEntriesResponse;
import com.google.api.services.logging.v2.model.LogEntry;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;

public class StackDriverDelegateServiceImplTest extends WingsBaseTest {
  private StackDriverDelegateServiceImpl spyServiceImpl;
  private StackDriverDelegateService stackDriverDelegateService;
  @Mock private GcpHelperService gcpHelperService;
  @Mock private DelegateCVActivityLogService delegateCVActivityLogService;
  @Mock private EncryptionService encryptionService;
  @Mock private DelegateLogService delegateLogService;
  @Mock private GcbService gcbService;

  private String accountId;
  private StackDriverLogDataCollectionInfo dataCollectionInfo;
  private StackdriverGcpConfigTaskParams taskParams;
  private StackDriverSetupTestNodeData setupTestNodeData;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setupTests() throws Exception {
    spyServiceImpl = Mockito.spy(new StackDriverDelegateServiceImpl());
    stackDriverDelegateService = spyServiceImpl;
    accountId = generateUuid();
    FieldUtils.writeField(stackDriverDelegateService, "gcpHelperService", gcpHelperService, true);
    FieldUtils.writeField(
        stackDriverDelegateService, "delegateCVActivityLogService", delegateCVActivityLogService, true);
    FieldUtils.writeField(stackDriverDelegateService, "encryptionService", encryptionService, true);
    FieldUtils.writeField(stackDriverDelegateService, "delegateLogService", delegateLogService, true);
    FieldUtils.writeField(stackDriverDelegateService, "gcbService", gcbService, true);
    Logging logging = mock(Logging.class);
    Logging.Entries entries = mock(Logging.Entries.class);
    when(logging.entries()).thenReturn(entries);
    Logging.Entries.List list = mock(Logging.Entries.List.class);
    when(entries.list(any(ListLogEntriesRequest.class))).thenReturn(list);

    ListLogEntriesResponse listLogEntriesResponse = new ListLogEntriesResponse();
    listLogEntriesResponse.setNextPageToken(generateUuid());
    listLogEntriesResponse.setEntries(Lists.newArrayList(new LogEntry(), new LogEntry()));

    when(list.execute()).thenReturn(listLogEntriesResponse);
    when(gcpHelperService.getLoggingResource(any(), anyString(), anyBoolean())).thenReturn(logging);
    DelegateCVActivityLogService.Logger logger = mock(DelegateCVActivityLogService.Logger.class);
    when(delegateCVActivityLogService.getLogger(
             anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
        .thenReturn(logger);

    dataCollectionInfo = StackDriverLogDataCollectionInfo.builder()
                             .gcpConfig(GcpConfig.builder()
                                            .accountId(accountId)
                                            .serviceAccountKeyFileContent("{\"project_id\":\"test\"}".toCharArray())
                                            .build())
                             .query(generateUuid())
                             .hostnameField(generateUuid())
                             .hosts(Sets.newHashSet(generateUuid()))
                             .stateExecutionId(generateUuid())
                             .build();
    when(gcbService.getProjectId(any(GcpConfig.class))).thenReturn("test");

    taskParams = StackdriverGcpConfigTaskParams.builder().gcpConfig(dataCollectionInfo.getGcpConfig()).build();

    setupTestNodeData = StackDriverSetupTestNodeData.builder()
                            .metricDefinitions(Collections.singletonList(StackDriverMetricDefinition.builder()
                                                                             .metricType("INFRA")
                                                                             .metricName("CPU")
                                                                             .txnName("Hardware")
                                                                             .build()))
                            .build();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFetchLogs_whenLogsLimitReachedForWorkflow() {
    assertThatThrownBy(()
                           -> stackDriverDelegateService.fetchLogs(
                               dataCollectionInfo, System.currentTimeMillis(), System.currentTimeMillis(), false, true))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Limit of " + MAX_LOGS_PER_MINUTE + " logs per minute reached. Please refine your query.");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testFetchLogs_whenLogsLimitReachedForServiceGuard() {
    final List<LogEntry> logEntries = stackDriverDelegateService.fetchLogs(
        dataCollectionInfo, System.currentTimeMillis(), System.currentTimeMillis(), true, true);
    assertThat(logEntries.size()).isEqualTo(MAX_LOGS_PER_MINUTE * CV_DATA_COLLECTION_INTERVAL_IN_MINUTE);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFetchLogs_whenNoNextPage() {
    final List<LogEntry> logEntries = stackDriverDelegateService.fetchLogs(
        dataCollectionInfo, System.currentTimeMillis(), System.currentTimeMillis(), false, false);
    assertThat(logEntries.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsWithDataForNode_validTimeSeriesReturned() throws IOException, CloneNotSupportedException {
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.builder().build();
    ListTimeSeriesResponse mockedResponse = new ListTimeSeriesResponse();
    mockedResponse.setTimeSeries(Collections.singletonList(new TimeSeries()));
    doReturn(mockedResponse)
        .when(spyServiceImpl)
        .getTimeSeriesResponse(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
    VerificationNodeDataSetupResponse response =
        stackDriverDelegateService.getMetricsWithDataForNode(taskParams, setupTestNodeData, "host", apiCallLog);
    assertThat(response.isProviderReachable()).isTrue();
    assertThat(response.getLoadResponse().isLoadPresent()).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsWithDataForNode_NoTimeSeriesReturned() throws IOException, CloneNotSupportedException {
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.builder().build();
    ListTimeSeriesResponse mockedResponse = new ListTimeSeriesResponse();
    mockedResponse.setTimeSeries(null);
    doReturn(mockedResponse)
        .when(spyServiceImpl)
        .getTimeSeriesResponse(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
    VerificationNodeDataSetupResponse response =
        stackDriverDelegateService.getMetricsWithDataForNode(taskParams, setupTestNodeData, "host", apiCallLog);
    assertThat(response.isProviderReachable()).isTrue();
    assertThat(response.getLoadResponse().isLoadPresent()).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsWithDataForNode_MultipleTimeSeriesReturned() {
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.builder().build();
    ListTimeSeriesResponse mockedResponse = new ListTimeSeriesResponse();
    mockedResponse.setTimeSeries(Lists.newArrayList(new TimeSeries(), new TimeSeries()));
    doReturn(mockedResponse)
        .when(spyServiceImpl)
        .getTimeSeriesResponse(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
    assertThatThrownBy(
        () -> stackDriverDelegateService.getMetricsWithDataForNode(taskParams, setupTestNodeData, "host", apiCallLog))
        .isInstanceOf(IllegalStateException.class);
  }
}
