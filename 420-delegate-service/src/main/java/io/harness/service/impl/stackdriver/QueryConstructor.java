/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl.stackdriver;

import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class QueryConstructor {
  private static final String TASKS_LOG_QUERY = "severity>=DEFAULT\n"
      + "resource.type=(\"k8s_container\" OR \"global\")\n"
      + "labels.app=\"delegate\"\n"
      + "timestamp >= \"%s\" AND timestamp <= \"%s\"\n"
      + "jsonPayload.harness.taskId=\"%s\"\n"
      + "jsonPayload.harness.accountId=\"%s\"";

  public static String getTasksLogQuery(String accountId, List<String> taskIds, long start, long end) {
    Instant endTime = Instant.ofEpochSecond(end);
    return String.format(TASKS_LOG_QUERY, EpochToUTCConverter.fromEpoch(start), EpochToUTCConverter.fromEpoch(end),
        StringUtils.join(taskIds, " OR "), accountId);
  }
}
