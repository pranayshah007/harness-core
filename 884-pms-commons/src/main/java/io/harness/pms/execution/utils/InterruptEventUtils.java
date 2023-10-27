/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse.ResponseCase;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptEvent.Builder;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class InterruptEventUtils {
  public AutoLogContext obtainLogContext(InterruptEvent event) {
    return new AutoLogContext(logContextMap(event), OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap(InterruptEvent event) {
    Map<String, String> logContext = new HashMap<>(AmbianceUtils.logContextMap(event.getAmbiance()));
    logContext.put("interruptType", event.getType().name());
    logContext.put("interruptUuid", event.getInterruptUuid());
    logContext.put("notifyId", event.getNotifyId());
    return logContext;
  }

  public InterruptEvent buildInterruptEvent(Builder builder, ExecutableResponse executableResponse) {
    ResponseCase responseCase = executableResponse.getResponseCase();
    switch (responseCase) {
      case ASYNC:
        return builder.setAsync(executableResponse.getAsync()).build();
      case ASYNCCHAIN:
        return builder.setAsyncChain(executableResponse.getAsyncChain()).build();
      case TASK:
        return builder.setTask(executableResponse.getTask()).build();
      case TASKCHAIN:
        return builder.setTaskChain(executableResponse.getTaskChain()).build();
      case CHILD:
      case CHILDREN:
      case CHILDCHAIN:
        log.error("Only Leaf Nodes are supposed to be Abortable : {}", responseCase);
        throw new IllegalStateException("Not an abortable node");
      case RESPONSE_NOT_SET:
      default:
        log.warn("No Handling present for Executable Response of type : {}", responseCase);
        return builder.build();
    }
  }
}
