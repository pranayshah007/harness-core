/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.kryo.KryoRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.VerificationKryoRegistrar;
import io.harness.serializer.morphia.PrimaryVersionManagerMorphiaRegistrar;
import io.harness.serializer.morphia.VerificationMorphiaRegistrar;
import io.harness.serializer.morphia.VerificationMorphiaRegistrars;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(VerificationKryoRegistrar.class)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(VerificationMorphiaRegistrar.class)
          .add(PrimaryVersionManagerMorphiaRegistrar.class)
          .add(VerificationMorphiaRegistrars.class)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .build();
}
