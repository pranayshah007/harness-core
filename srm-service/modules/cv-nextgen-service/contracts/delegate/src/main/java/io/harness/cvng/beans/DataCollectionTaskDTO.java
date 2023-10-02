/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DataCollectionTaskDTO {
  String uuid;
  String accountId;
  String verificationTaskId;
  Map<String, String> dataCollectionMetadata;
  DataCollectionInfo dataCollectionInfo;
  Instant startTime;
  Instant endTime;

  @Value
  @Builder
  public static class DataCollectionTaskResult {
    String dataCollectionTaskId;
    DataCollectionExecutionStatus status;
    Map<String, String> dataCollectionMetadata;
    String exception;
    String stacktrace;
    List<ExecutionLog> executionLogs;

    public List<ExecutionLog> getExecutionLogs() {
      return Optional.ofNullable(executionLogs).orElse(Collections.emptyList());
    }

    @Value
    @Builder
    public static class ExecutionLog {
      String log;
      LogLevel logLevel;
    }
  }
}
