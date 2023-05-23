/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.http.modules;

import io.harness.http.HttpService;
import io.harness.http.HttpServiceImpl;

import com.google.inject.AbstractModule;

public class HttpTaskNGModule extends AbstractModule {
  @Override
  protected void configure() {
    bindRequestHandlers();
  }

  private void bindRequestHandlers() {
    bind(HttpService.class).to(HttpServiceImpl.class);
  }
}
