/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import io.harness.cvng.core.entities.VerificationTask;
import io.harness.metrics.AutoMetricContext;

import java.util.Map;
import java.util.Objects;

public class VerificationTaskMetricContext extends AutoMetricContext {
  public VerificationTaskMetricContext(VerificationTask verificationTask) {
    put("accountId", verificationTask.getAccountId());
    if (Objects.nonNull(verificationTask.getTags())) {
      for (Map.Entry<String, String> pair : verificationTask.getTags().entrySet()) {
        put(pair.getKey(), pair.getValue());
      }
    }
  }
}
