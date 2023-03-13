/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl.stackdriver;

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

  @Override
  public PageResponse<DelegateStackDriverLog> fetchPageLogs(
      String accountId, List<String> taskIds, PageRequest request, long start, long end) {
    final Logging logging = StackdriverLoggerFactory.get(infraDownloadService.getStackdriverLoggingToken());

    Page<LogEntry> entries = logging.listLogEntries(Logging.EntryListOption.pageSize(request.getPageSize()),
        Logging.EntryListOption.pageToken(request.getPageToken()),
        Logging.EntryListOption.sortOrder(Logging.SortingField.TIMESTAMP, Logging.SortingOrder.DESCENDING),
        Logging.EntryListOption.filter(QueryConstructor.getTasksLogQuery(accountId, taskIds, start, end)));

    List<DelegateStackDriverLog> logLines = StreamSupport.stream(entries.iterateAll().spliterator(), false)
                                                .map(logEntry -> LogEntryToDelegateStackDriverLogMapper.map(logEntry))
                                                .collect(Collectors.toList());
    return PageResponse.<DelegateStackDriverLog>builder()
        .pageSize(request.getPageSize())
        .content(logLines)
        .pageToken(entries.getNextPageToken())
        .build();
  }
}
