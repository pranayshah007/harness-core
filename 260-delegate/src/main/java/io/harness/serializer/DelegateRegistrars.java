/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.kryo.CgOrchestrationBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateAgentBeansKryoRegister;
import io.harness.serializer.kryo.NgAuthenticationServiceKryoRegistrar;
import io.harness.serializer.kryo.NotificationDelegateTasksKryoRegistrar;
import io.harness.serializer.kryo.WatcherBeansKryoRegister;

import com.google.common.collect.ImmutableSet;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;

@UtilityClass
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class DelegateRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CvNextGenBeansRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(DelegateTasksBeansRegistrars.kryoRegistrars)
          .add(CgOrchestrationBeansKryoRegistrar.class)
          .addAll(NGCommonsRegistrars.kryoRegistrars)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .addAll(FileServiceCommonsRegistrars.kryoRegistrars)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          // temporary:
          .add(NotificationDelegateTasksKryoRegistrar.class)
          .add(DelegateAgentBeansKryoRegister.class)
          .add(WatcherBeansKryoRegister.class)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .add(NgAuthenticationServiceKryoRegistrar.class)
          .addAll(SMDelegateRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .build();
}
