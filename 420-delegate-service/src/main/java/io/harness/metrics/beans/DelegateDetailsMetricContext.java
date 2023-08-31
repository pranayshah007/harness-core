/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.beans;

import io.harness.metrics.AutoMetricContext;

public class DelegateDetailsMetricContext extends AutoMetricContext {
  public DelegateDetailsMetricContext(String accountId, String orgId, String projectId, String delegateName,
      String delegateId, String delegateVersion, boolean isNg, boolean isImmutable) {
    put("accountId", accountId);
    put("orgId", orgId);
    put("projectId", projectId);
    put("delegateName", delegateName);
    put("delegateId", delegateId);
    put("delegateVersion", delegateVersion);
    put("isNg", String.valueOf(isNg));
    put("isImmutable", String.valueOf(isImmutable));
  }
}
