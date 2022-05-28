/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import io.harness.govern.ProviderModule;
import io.harness.serializer.DelegateRegistrars;
import io.harness.serializer.KryoRegistrar;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Set;

public class DelegateKryoModule extends ProviderModule {
  @Provides
  @Singleton
  Set<Class<? extends KryoRegistrar> > registrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar> >builder().addAll(DelegateRegistrars.kryoRegistrars).build();
  }
}
