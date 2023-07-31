/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan.consumers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.async.plan.PartialPlanCreatorResponseData;
import io.harness.pms.contracts.plan.PartialPlanResponse;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PartialPlanResponseEventHandler implements PmsCommonsBaseEventHandler<PartialPlanResponse> {
  @Inject WaitNotifyEngine waitNotifyEngine;

  @Override
  public void handleEvent(
      PartialPlanResponse event, Map<String, String> metadataMap, long messageTimeStamp, long readTs) {
    waitNotifyEngine.doneWith(
        event.getNotifyId(), PartialPlanCreatorResponseData.builder().partialPlanResponse(event).build());
  }
}
