/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.ScopeInfo;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import org.glassfish.hk2.api.Factory;

public class ScopeInfoFactory implements Factory<ScopeInfo> {
  private final ContainerRequestContext context;

  @Inject
  public ScopeInfoFactory(ContainerRequestContext context) {
    this.context = context;
  }

  @Override
  public ScopeInfo provide() {
    return (ScopeInfo) context.getProperty("scopeInfo");
  }

  @Override
  public void dispose(ScopeInfo t) {}
}
