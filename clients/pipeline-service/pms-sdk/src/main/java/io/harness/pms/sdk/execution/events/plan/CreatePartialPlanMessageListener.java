/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.plan;

import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.plan.CreatePartialPlanEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class CreatePartialPlanMessageListener
    extends PmsAbstractMessageListener<CreatePartialPlanEvent, CreatePartialPlanEventHandler> {
  @Inject
  public CreatePartialPlanMessageListener(
      @Named(SDK_SERVICE_NAME) String serviceName, CreatePartialPlanEventHandler createPartialPlanEventHandler) {
    super(serviceName, CreatePartialPlanEvent.class, createPartialPlanEventHandler);
  }

  @Override
  protected CreatePartialPlanEvent extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return CreatePartialPlanEvent.parseFrom(message);
  }
}
