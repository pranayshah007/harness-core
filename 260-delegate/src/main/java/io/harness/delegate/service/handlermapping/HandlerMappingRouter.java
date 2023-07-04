/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping;

import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.delegate.beans.SchedulingTaskEvent.Method;
import io.harness.delegate.service.handlermapping.handlers.Handler;

import jauter.Router;

public class HandlerMappingRouter
    extends Router<SchedulingTaskEvent.Method, Class<? extends Handler>, HandlerMappingRouter> {
  @Override
  protected HandlerMappingRouter getThis() {
    return this;
  }

  @Override
  protected Method CONNECT() {
    return Method.OTHER;
  }

  @Override
  protected Method DELETE() {
    return Method.DELETE;
  }

  @Override
  protected Method GET() {
    return Method.GET;
  }

  @Override
  protected Method HEAD() {
    return Method.OTHER;
  }

  @Override
  protected Method OPTIONS() {
    return Method.OTHER;
  }

  @Override
  protected Method PATCH() {
    return Method.OTHER;
  }

  @Override
  protected Method POST() {
    return Method.POST;
  }

  @Override
  protected Method PUT() {
    return Method.OTHER;
  }

  @Override
  protected Method TRACE() {
    return Method.OTHER;
  }
}
