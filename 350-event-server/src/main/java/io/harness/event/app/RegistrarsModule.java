/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import io.harness.kryo.KryoRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.EventsServerRegistrars;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Set;

public class RegistrarsModule extends AbstractModule {
  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return EventsServerRegistrars.kryoRegistrars;
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return EventsServerRegistrars.morphiaRegistrars;
  }
}
