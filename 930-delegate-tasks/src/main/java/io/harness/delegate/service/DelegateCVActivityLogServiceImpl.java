/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.CVActivityLog.LogLevel;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateCVActivityLogServiceImpl implements DelegateCVActivityLogService {
  @Inject private DelegateLogService delegateLogService;
  @Override
  public Logger getLogger(String accountId, String cvConfigId, long dataCollectionMinute, String stateExecutionId,
      String prefix, long... prefixTimestampParams) {
    return new LoggerImpl(accountId, cvConfigId, dataCollectionMinute, stateExecutionId, prefix, prefixTimestampParams);
  }

  private class LoggerImpl implements Logger {
    private final String accountId;
    private final String prefix;
    private String cvConfigId;
    private long dataCollectionMinute;
    private String stateExecutionId;
    private List<Long> prefixTimestampParams;
    LoggerImpl(String accountId, String cvConfigId, long dataCollectionMinute, String stateExecutionId, String prefix,
        long... prefixTimestampParams) {
      this.cvConfigId = cvConfigId;
      this.dataCollectionMinute = dataCollectionMinute;
      this.accountId = accountId;
      this.stateExecutionId = stateExecutionId;
      this.prefix = prefix;
      this.prefixTimestampParams = Arrays.stream(prefixTimestampParams).boxed().collect(Collectors.toList());
    }

    @Override
    public void appendLog(LogLevel logLevel, String log, long... timestampParams) {
      List<Long> timestampParamList = new ArrayList<>();
      timestampParamList.addAll(prefixTimestampParams);
      timestampParamList.addAll(Arrays.stream(timestampParams).boxed().collect(Collectors.toList()));
      CVActivityLog activityLog = CVActivityLog.builder()
                                      .log("[Delegate] " + prefix + " " + log)
                                      .logLevel(logLevel)
                                      .cvConfigId(cvConfigId)
                                      .createdAt(Instant.now().toEpochMilli())
                                      .stateExecutionId(stateExecutionId)
                                      .dataCollectionMinute(dataCollectionMinute)
                                      .timestampParams(timestampParamList)
                                      .accountId(accountId)
                                      .build();
      delegateLogService.save(accountId, activityLog);
    }
  }
}
