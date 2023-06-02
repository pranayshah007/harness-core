/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.module;

import io.harness.govern.ProviderModule;
import io.harness.serializer.DelegateServiceRegistrars;
import io.harness.serializer.KryoRegistrar;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Set;

public class DelegateServiceKryoModule extends ProviderModule {
  @Provides
  @Singleton
  Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(DelegateServiceRegistrars.kryoRegistrars)
        .build();
  }
}
