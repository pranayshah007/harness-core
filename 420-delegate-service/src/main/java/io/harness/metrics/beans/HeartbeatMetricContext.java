/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.beans;

import io.harness.metrics.AutoMetricContext;

public class HeartbeatMetricContext extends AutoMetricContext {
  public HeartbeatMetricContext(String time, String accountId, String accountName, String companyName, String ringName,
      String delegateImageTag, String upgraderImageTag, String watcherVersion, String watcherJREVersion,
      String delegateJREVersion, String orgId, String projectId, String delegateName, String delegateId,
      String delegateVersion, String delegateConnectionStatus, String delegateEventType, boolean isNg,
      boolean isImmutable) {
    put("time", time);
    put("accountId", accountId);
    put("accountName", accountName);
    put("companyName", companyName);
    put("ringName", ringName);
    put("delegateImageTag", delegateImageTag);
    put("upgraderImageTag", upgraderImageTag);
    put("watcherVersion", watcherVersion);
    put("watcherJREVersion", watcherJREVersion);
    put("delegateJREVersion", delegateJREVersion);
    put("orgId", orgId);
    put("projectId", projectId);
    put("delegateName", delegateName);
    put("delegateId", delegateId);
    put("delegateVersion", delegateVersion);
    put("delegateConnectionStatus", delegateConnectionStatus);
    put("delegateEventType", delegateEventType);
    put("isNg", String.valueOf(isNg));
    put("isImmutable", String.valueOf(isImmutable));
  }
}
