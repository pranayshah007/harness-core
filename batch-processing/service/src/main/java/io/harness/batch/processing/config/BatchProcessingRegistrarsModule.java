/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.govern.ProviderModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.SecretManagerClientRegistrars;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Set;

public class BatchProcessingRegistrarsModule extends ProviderModule {
  @Provides
  @Singleton
  Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(SecretManagerClientRegistrars.kryoRegistrars)
        // required due to 'KryoException: Encountered unregistered class ID: 7180'
        .addAll(SMCoreRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(ManagerRegistrars.morphiaRegistrars)
        .addAll(SecretManagerClientRegistrars.morphiaRegistrars)
        .build();
  }
}
