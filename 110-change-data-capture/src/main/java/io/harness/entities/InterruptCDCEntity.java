/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.VerifyStepInterruptCDCHandler;
import io.harness.interrupts.Interrupt;

import com.google.inject.Inject;

public class InterruptCDCEntity implements CDCEntity<Interrupt> {
  @Inject private VerifyStepInterruptCDCHandler verifyStepInterruptCDCHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return verifyStepInterruptCDCHandler;
  }

  @Override
  public Class<Interrupt> getSubscriptionEntity() {
    return Interrupt.class;
  }
}
