/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.serializer.KryoSerializer;
import io.harness.waiter.NotifyCallback;

import com.esotericsoftware.kryo.KryoException;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
public class OrchestrationComponentTester {
  public static void testKryoRegistration(Provider<KryoSerializer> kryoSerializerProvider) {
    KryoSerializer kryoSerializer = kryoSerializerProvider.get();
    Reflections reflections = new Reflections("io.harness", "software.wings");
    Set<Class<? extends NotifyCallback>> callbacks = reflections.getSubTypesOf(NotifyCallback.class)
                                                         .stream()
                                                         .filter(clazz -> !clazz.isInterface())
                                                         .collect(Collectors.toSet());
    List<Class<? extends NotifyCallback>> unregisteredClasses = new ArrayList<>();
    for (Class<? extends NotifyCallback> callback : callbacks) {
      if (!kryoSerializer.isRegistered(callback)) {
        log.error("Class should be registered with kryo : {}", callback.getName());
        unregisteredClasses.add(callback);
      }
    }

    if (!isEmpty(unregisteredClasses)) {
      throw new KryoException("Some Classes missing Kryo Registration");
    }
  }
}
