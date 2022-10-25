/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.logging.logcontext.AutoLogContext;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class FacilitatorEventUtils {
  public AutoLogContext obtainLogContext(FacilitatorEvent event) {
    return new AutoLogContext(logContextMap(event), OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap(FacilitatorEvent event) {
    Map<String, String> logContext = new HashMap<>(AmbianceUtils.logContextMap(event.getAmbiance()));
    logContext.put("nodeExecutionId", event.getNodeExecutionId());
    logContext.put("notifyId", event.getNotifyId());
    return logContext;
  }
}
