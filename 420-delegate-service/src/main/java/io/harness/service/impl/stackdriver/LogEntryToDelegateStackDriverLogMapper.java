/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl.stackdriver;

import io.harness.delegate.resources.DelegateStackDriverLog;
import io.harness.delegate.resources.DelegateStackDriverLog.DelegateStackDriverLogBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * LogEntryToDelegateStackDriverLogMapper class converts a LogEntry (google api) to StackDriverLog.
 * This mapper works for manager and delegate stack driver logs.
 */
@UtilityClass
@Slf4j
public class LogEntryToDelegateStackDriverLogMapper {
  public DelegateStackDriverLog map(final LogEntry logEntry) {
    // fill basic log info
    var builder = DelegateStackDriverLog.builder()
                      .severity(logEntry.getSeverity().name())
                      .ISOTime(EpochToUTCConverter.fromEpoch(logEntry.getTimestamp() / 1000L));

    // fill label data
    fillLabelData(logEntry, builder);

    // fill payload data
    switch (logEntry.getPayload().getType()) {
      case STRING:
        String message = logEntry.getPayload().getData().toString();
        builder.message(message);
        break;
      case JSON:
        fillJsonPayloadData(logEntry, builder);
        break;
      case PROTO:
        log.warn("Received proto payload from stack driver");
        break;
      default:
        log.warn("Received unknown payload type from stack driver");
    }
    return builder.build();
  }

  private DelegateStackDriverLogBuilder fillLabelData(LogEntry logEntry, DelegateStackDriverLogBuilder builder) {
    // Delegate agent logs have these fields
    final Map<String, String> labels = logEntry.getLabels();
    return builder.app(labels.getOrDefault("app", ""))
        .accountId(labels.getOrDefault("accountId", ""))
        .delegateId(labels.getOrDefault("delegateId", ""))
        .managerHost(labels.getOrDefault("managerHost", ""))
        .processId(labels.getOrDefault("processId", ""))
        .source(labels.getOrDefault("source", ""))
        .version(labels.getOrDefault("version", ""));
  }

  private DelegateStackDriverLogBuilder fillJsonPayloadData(LogEntry logEntry, DelegateStackDriverLogBuilder builder) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode node = objectMapper.valueToTree(((Payload.JsonPayload) logEntry.getPayload()).getDataAsMap());
    if (Objects.isNull(node)) {
      return builder;
    }
    if (node.has("logger")) {
      builder.logger(node.get("logger").asText());
    }
    if (node.has("message")) {
      builder.message(node.get("message").asText());
    }
    if (node.has("thread")) {
      builder.thread(node.get("thread").asText());
    }
    if (node.has("exception")) {
      builder.exception(node.get("exception").asText());
    }
    if (node.has("harness")) {
      var harnessNode = node.get("harness");
      if (harnessNode.has("taskId")) {
        builder.taskId(harnessNode.get("taskId").asText());
      }
      // manager logs have the below fields in payload - "harness" section
      if (harnessNode.has("accountId")) {
        builder.accountId(harnessNode.get("accountId").asText());
      }
      if (harnessNode.has("delegateId")) {
        builder.delegateId(harnessNode.get("delegateId").asText());
      }
    }
    return builder;
  }
}
