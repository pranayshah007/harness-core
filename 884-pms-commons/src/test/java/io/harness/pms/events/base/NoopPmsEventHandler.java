/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.sdk.execution.events.EventHandlerResult;

import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopPmsEventHandler extends PmsBaseEventHandler<InterruptEvent, Boolean> {
  @Override
  protected Map<String, String> extraLogProperties(InterruptEvent event) {
    return null;
  }

  @Override
  protected Ambiance extractAmbiance(InterruptEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, InterruptEvent message) {
    return Collections.emptyMap();
  }

  @Override
  protected String getMetricPrefix(InterruptEvent message) {
    return null;
  }

  @Override
  protected EventHandlerResult<Boolean> handleEventWithContext(InterruptEvent event) {
    return EventHandlerResult.<Boolean>builder().data(true).success(true).build();
  }
}
