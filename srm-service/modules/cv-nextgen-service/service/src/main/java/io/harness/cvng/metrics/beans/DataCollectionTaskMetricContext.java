/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import io.harness.metrics.AutoMetricContext;

public class DataCollectionTaskMetricContext extends AutoMetricContext {
  public DataCollectionTaskMetricContext(
      String accountId, String dataCollectionTaskType, String provider, long retryCount) {
    put("accountId", accountId);
    put("dataCollectionTaskType", dataCollectionTaskType);
    put("provider", provider);
    put("retryCount", String.valueOf(retryCount));
  }
}
