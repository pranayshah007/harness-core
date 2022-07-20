/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.plan;

import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.pms.contracts.plan.CreatePartialPlanEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

public class CreatePartialPlanMessageListener
    extends PmsAbstractMessageListener<CreatePartialPlanEvent, CreatePartialPlanEventHandler> {
  @Inject
  public CreatePartialPlanMessageListener(@Named(SDK_SERVICE_NAME) String serviceName,
      CreatePartialPlanEventHandler createPartialPlanEventHandler,
      @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(serviceName, CreatePartialPlanEvent.class, createPartialPlanEventHandler, executorService);
  }
}
