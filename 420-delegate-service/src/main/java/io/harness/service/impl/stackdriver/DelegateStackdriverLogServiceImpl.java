/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl.stackdriver;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.delegate.resources.DelegateHackLog;
import io.harness.delegate.resources.DelegateStackDriverLog;
import io.harness.logging.StackdriverLoggerFactory;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.service.intfc.DelegateStackdriverLogService;

import software.wings.service.impl.infra.InfraDownloadService;

import com.google.api.gax.paging.Page;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class DelegateStackdriverLogServiceImpl implements DelegateStackdriverLogService {
  private final InfraDownloadService infraDownloadService;

  List<String> exceptionTypes;

  @Override
  public PageResponse<DelegateStackDriverLog> fetchPageLogs(
      String accountId, List<String> taskIds, PageRequest request, long start, long end) {
    final Logging logging = StackdriverLoggerFactory.get(infraDownloadService.getStackdriverLoggingToken());

    Page<LogEntry> entries = logging.listLogEntries(Logging.EntryListOption.pageSize(request.getPageSize()),
        Logging.EntryListOption.pageToken(request.getPageToken()),
        Logging.EntryListOption.sortOrder(Logging.SortingField.TIMESTAMP, Logging.SortingOrder.DESCENDING),
        Logging.EntryListOption.filter(QueryConstructor.getErrorLogQuery(accountId, start, end)));

    List<DelegateStackDriverLog> logLines = StreamSupport.stream(entries.iterateAll().spliterator(), false)
                                                .map(logEntry -> LogEntryToDelegateStackDriverLogMapper.map(logEntry))
                                                .collect(Collectors.toList());
    return PageResponse.<DelegateStackDriverLog>builder()
        .pageSize(request.getPageSize())
        .content(logLines)
        .pageToken(entries.getNextPageToken())
        .build();
  }

  @Override
  public List<DelegateHackLog> fetchPageLogs(String accountId, PageRequest request, long start, long end) {
    final Logging logging = StackdriverLoggerFactory.get(infraDownloadService.getStackdriverLoggingToken());

    Page<LogEntry> entries = logging.listLogEntries(Logging.EntryListOption.pageSize(request.getPageSize()),
        Logging.EntryListOption.pageToken(request.getPageToken()),
        Logging.EntryListOption.sortOrder(Logging.SortingField.TIMESTAMP, Logging.SortingOrder.DESCENDING),
        Logging.EntryListOption.filter(QueryConstructor.getErrorLogQuery(accountId, start, end)));

    String exceptionFromConfig = System.getenv("exceptionTypes");
    if (isBlank(exceptionFromConfig)) {
      exceptionTypes = Arrays.asList("Check your Azure credentials");
    } else {
      exceptionTypes = Arrays.asList(exceptionFromConfig.split(","));
      log.info("Exception types from config are: {}", exceptionTypes);
    }

    List<DelegateStackDriverLog> logLines = StreamSupport.stream(entries.iterateAll().spliterator(), false)
                                                .map(logEntry -> LogEntryToDelegateStackDriverLogMapper.map(logEntry))
                                                .collect(Collectors.toList());

    log.info("Checking logLines {}", logLines.size());

    return logLines.stream()
        .filter(this::containsException)
        .map(delegateStackDriverLog -> buildDelegateHackObject(delegateStackDriverLog))
        .collect(Collectors.toList());
  }

  private boolean containsException(DelegateStackDriverLog delegateStackDriverLog) {
    for (String errorMessage : exceptionTypes) {
      if (delegateStackDriverLog.getException().contains(errorMessage)) {
        return true;
      }
    }
    return false;
  }

  private DelegateHackLog buildDelegateHackObject(DelegateStackDriverLog delegateStackDriverLog) {
    //  ErrorHack foundError = null;
    String foundError = null;
    for (String errorMessage : exceptionTypes) {
      if (delegateStackDriverLog.getException().contains(errorMessage)) {
        foundError = errorMessage;
        break;
      }
    }

    return DelegateHackLog.builder()
        .delegateId(delegateStackDriverLog.getDelegateId())
        .accountId(delegateStackDriverLog.getAccountId())
        .exceptionType(foundError)
        .build();
  }
}
