/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.metrics.AutoMetricContext;

public class SLOMetricContext extends AutoMetricContext {
  public SLOMetricContext(ServiceLevelIndicator serviceLevelIndicator) {
    put("accountId", serviceLevelIndicator.getAccountId());
    put("verificationTaskId", serviceLevelIndicator.getUuid());
    put("sliUuid", serviceLevelIndicator.getUuid());
    put("sliIdentifier", serviceLevelIndicator.getIdentifier());
  }

  public SLOMetricContext(CompositeServiceLevelObjective serviceLevelObjective) {
    put("accountId", serviceLevelObjective.getAccountId());
    put("verificationTaskId", serviceLevelObjective.getUuid());
    put("sloUuid", serviceLevelObjective.getUuid());
    put("sloIdentifier", serviceLevelObjective.getIdentifier());
  }
}
