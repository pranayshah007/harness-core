package io.harness.serializer;

import io.harness.serializer.kryo.ContainerKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;

import com.google.common.collect.ImmutableSet;

public class ContainerRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(DelegateTasksBeansRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .add(ContainerKryoRegistrar.class)
          .build();
}
