/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

@OwnedBy(IDP)
public class BackstageResourceClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(BackstageResourceClient.class).toProvider(BackstageResourceClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
