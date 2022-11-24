/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.serializer.registrars;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.common.CommonsRegistrars;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.RecasterKryoRegistrar;

import com.google.common.collect.ImmutableSet;
import io.serializer.morphia.NGCommonsMorphiaRegistrar;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NGCommonsRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CommonsRegistrars.kryoRegistrars)
          .add(NGCommonsKryoRegistrar.class)
          .add(RecasterKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(NGCommonsMorphiaRegistrar.class).build();
}
