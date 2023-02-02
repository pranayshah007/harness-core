/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.serializer;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.DelegateServiceDriverRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.SecretManagerClientRegistrars;

import com.google.common.collect.ImmutableSet;

public class AuditEventStreamingRegistrar {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(SecretManagerClientRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(DelegateServiceDriverRegistrars.morphiaRegistrars)
          .build();
}
