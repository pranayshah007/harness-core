/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.testing.TestExecution;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
public class KryoModule extends AbstractModule {
  private static volatile KryoModule instance;

  public static KryoModule getInstance() {
    if (instance == null) {
      instance = new KryoModule();
    }
    return instance;
  }

  public void testAutomaticSearch(Provider<Set<Class<? extends KryoRegistrar>>> registrarsProvider) {
    Reflections reflections = new Reflections("io.harness.serializer.kryo");

    // Reflections have race issue and rarely but form time to time returns less.
    // We are checking here only if we missed something, not exact match on purpose
    Set<Class<? extends KryoRegistrar>> reflectionRegistrars = reflections.getSubTypesOf(KryoRegistrar.class);

    Set<Class<? extends KryoRegistrar>> registrars = registrarsProvider.get();

    reflectionRegistrars.removeAll(registrars);
    if (isNotEmpty(reflectionRegistrars)) {
      throw new IllegalStateException(String.format("You are missing %s", reflectionRegistrars));
    }
  }

  @Override
  protected void configure() {
    if (!binder().currentStage().name().equals("TOOL")) {
      Provider<Set<Class<? extends KryoRegistrar>>> provider =
          getProvider(Key.get(new TypeLiteral<Set<Class<? extends KryoRegistrar>>>() {}));
      MapBinder<String, TestExecution> testExecutionMapBinder =
          MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
      testExecutionMapBinder.addBinding("Kryo test registration").toInstance(() -> testAutomaticSearch(provider));
    }
  }
}
