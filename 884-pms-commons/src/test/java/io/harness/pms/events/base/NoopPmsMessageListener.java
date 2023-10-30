/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.interrupts.InterruptEvent;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class NoopPmsMessageListener extends PmsAbstractMessageListener<InterruptEvent, NoopPmsEventHandler> {
  public NoopPmsMessageListener(String serviceName, NoopPmsEventHandler handler) {
    super(serviceName, InterruptEvent.class, handler);
  }

  @Override
  protected InterruptEvent extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return InterruptEvent.parseFrom(message);
  }
}
