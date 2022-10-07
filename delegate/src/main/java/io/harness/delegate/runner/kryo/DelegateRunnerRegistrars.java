/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.kryo;

import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
//import io.harness.serializer.DelegateTasksBeansRegistrars;
import io.harness.serializer.FileServiceCommonsRegistrars;
import io.harness.serializer.KryoRegistrar;
//import io.harness.serializer.SMDelegateRegistrars;
import io.harness.serializer.kryo.CgOrchestrationBeansKryoRegistrar;
//import io.harness.serializer.kryo.NotificationDelegateTasksKryoRegistrar;

import com.google.common.collect.ImmutableSet;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateRunnerRegistrars {
    public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
        ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(ConnectorBeansRegistrars.kryoRegistrars)
            .add(CgOrchestrationBeansKryoRegistrar.class)
            .addAll(NGCommonsRegistrars.kryoRegistrars)
            .addAll(FileServiceCommonsRegistrars.kryoRegistrars)
            // temporary:
            //.add(NotificationDelegateTasksKryoRegistrar.class)
            .addAll(DelegateTaskRegistrars.kryoRegistrars)
            //.addAll(SMDelegateRegistrars.kryoRegistrars)
            .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
            .build();
}
