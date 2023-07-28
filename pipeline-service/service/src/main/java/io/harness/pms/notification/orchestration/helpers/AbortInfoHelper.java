/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.helpers;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.AbortedBy;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.interrupts.ManualIssuer;

import com.google.inject.Inject;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class AbortInfoHelper {
  @Inject private InterruptService interruptService;
  private static final String SYSTEM_USER = "systemUser";

  public AbortedBy fetchAbortedByInfoFromInterrupts(String planExecutionId) {
    AbortedBy abortedBy = null;
    List<Interrupt> interruptsList = interruptService.fetchAbortAllPlanLevelInterrupt(planExecutionId);
    if (isNotEmpty(interruptsList)) {
      Long createdAt = interruptsList.get(0).getCreatedAt();
      ManualIssuer manualIssuer = interruptsList.get(0).getInterruptConfig().getIssuedBy().getManualIssuer();
      if (isEmpty(manualIssuer.getUserId())) {
        abortedBy = AbortedBy.builder().userName(SYSTEM_USER).createdAt(createdAt).build();
      } else {
        abortedBy = AbortedBy.builder()
                        .email(manualIssuer.getEmailId())
                        .userName(manualIssuer.getUserId())
                        .createdAt(createdAt)
                        .build();
      }
    }
    // In case of pipeline stage, if a child pipeline is aborted, interrupt won't be registered with parent pipeline's
    // planExecutionId
    else {
      abortedBy = AbortedBy.builder().userName(SYSTEM_USER).build();
    }
    return abortedBy;
  }
}
