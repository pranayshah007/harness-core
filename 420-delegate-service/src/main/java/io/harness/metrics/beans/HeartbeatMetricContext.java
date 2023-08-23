/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.beans;

import io.harness.metrics.AutoMetricContext;

import java.text.SimpleDateFormat;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class HeartbeatMetricContext extends AutoMetricContext {
  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  public HeartbeatMetricContext(long time, String accountId, String orgId, String projectId, String delegateName,
      String delegateId, String delegateVersion, String delegateConnectionStatus, String delegateEventType,
      boolean isNg, boolean isImmutable, String cpuUsage, String memeUsage, long lastHB) {
    put("time", dateFormat.format(lastHB));
    put("accountId", accountId);
    put("orgId", orgId);
    put("projectId", projectId);
    put("delegateName", delegateName);
    put("delegateId", delegateId);
    put("delegateVersion", delegateVersion);
    put("delegateConnectionStatus", delegateConnectionStatus);
    put("delegateEventType", delegateEventType);
    put("isNg", String.valueOf(isNg));
    put("isImmutable", String.valueOf(isImmutable));
    put("cpuUsage", cpuUsage);
    put("memUsage", memeUsage);
    put("lastHB", dateFormat.format(lastHB));
  }
}
