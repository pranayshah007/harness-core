/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.stackdriver;

import software.wings.beans.GcpConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackDriverNameSpace;
import software.wings.service.impl.stackdriver.StackDriverSetupTestNodeData;
import software.wings.service.impl.stackdriver.StackdriverGcpConfigTaskParams;
import software.wings.service.impl.stackdriver.StackdriverLogGcpConfigTaskParams;

import com.google.api.services.logging.v2.model.LogEntry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

/**
 * Created by Pranjal on 11/27/2018
 */
public interface StackDriverDelegateService {
  @DelegateTaskType(TaskType.STACKDRIVER_METRIC_DATA_FOR_NODE)
  VerificationNodeDataSetupResponse getMetricsWithDataForNode(@NotNull StackdriverGcpConfigTaskParams taskParams,
      StackDriverSetupTestNodeData setupTestNodeData, String hostName, ThirdPartyApiCallLog apiCallLog)
      throws IOException, CloneNotSupportedException;

  @DelegateTaskType(TaskType.STACKDRIVER_LIST_REGIONS)
  List<String> listRegions(@NotNull StackdriverGcpConfigTaskParams taskParams);

  @DelegateTaskType(TaskType.STACKDRIVER_LIST_FORWARDING_RULES)
  Map<String, String> listForwardingRules(@NotNull StackdriverGcpConfigTaskParams taskParams, String region)
      throws IOException;

  String createFilter(StackDriverNameSpace nameSpace, String metric, String dimensionValue);

  String getProjectId(GcpConfig gcpConfig);

  long getTimeStamp(String data);

  String getDateFormatTime(long time);

  List<LogEntry> fetchLogs(StackDriverLogDataCollectionInfo dataCollectionInfo, long collectionStartTime,
      long collectionEndTime, boolean is24x7Task, boolean fetchNextPage);

  @DelegateTaskType(TaskType.STACKDRIVER_LOG_DATA_FOR_NODE)
  VerificationNodeDataSetupResponse getLogWithDataForNode(StackdriverLogGcpConfigTaskParams taskParams,
      String stateExecutionId, String hostName, StackDriverSetupTestNodeData setupTestNodeData);

  @DelegateTaskType(TaskType.STACKDRIVER_GET_LOG_SAMPLE)
  Object getLogSample(
      StackdriverLogGcpConfigTaskParams taskParams, String guid, String query, long startTime, long endTime);
}
