/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.platform;

import com.google.common.collect.ImmutableSet;
import io.harness.serializer.AccessControlClientRegistrars;
import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.CvNextGenBeansRegistrars;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.DelegateTasksBeansRegistrars;
import io.harness.serializer.FileServiceCommonsRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.LicenseBeanRegistrar;
import io.harness.serializer.RbacCoreRegistrars;
import io.harness.serializer.SMDelegateRegistrars;
import io.harness.serializer.kryo.CgOrchestrationBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateAgentBeansKryoRegister;
import io.harness.serializer.kryo.NgAuthenticationServiceKryoRegistrar;
import io.harness.serializer.kryo.NotificationDelegateTasksKryoRegistrar;
import io.harness.serializer.kryo.WatcherBeansKryoRegister;
import io.serializer.registrars.NGCommonsRegistrars;


public class DelegatePlatformRegistrars {
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
