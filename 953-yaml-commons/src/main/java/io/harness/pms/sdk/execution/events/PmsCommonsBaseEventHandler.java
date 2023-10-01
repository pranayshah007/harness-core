/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;

import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@TargetModule(HarnessModule._884_PMS_COMMONS)
public interface PmsCommonsBaseEventHandler<T> {
  /*
   * messageTimeStamp: timeStamp when the message was inserted in the redis queue.
   * readTs: timeStamp when the message was read from the redis queue.
   */
  void handleEvent(T event, Map<String, String> metadataMap, long messageTimeStamp, long readTs);
}
