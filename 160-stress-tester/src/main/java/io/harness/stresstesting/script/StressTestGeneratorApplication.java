/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stresstesting.script;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.govern.ProviderModule;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class StressTestGeneratorApplication {
  public static void main(String... args) {
    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar> > registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar> >builder()
            .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
            .addAll(ManagerRegistrars.kryoRegistrars)
            .build();
      }
    });
    Injector injector = Guice.createInjector(modules);

    StressTestGenerator stressTestGenerator = injector.getInstance(CapabilityStressTestGenerator.class);
    log.info(stressTestGenerator.makeStressTest().toString());
  }
}
