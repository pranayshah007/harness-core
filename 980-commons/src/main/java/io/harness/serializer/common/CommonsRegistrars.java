/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.common;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.kryo.CommonsKryoRegistrar;
import io.harness.serializer.morphia.CommonMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommonsRegistrars {
  public static final com.google.common.collect.ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder().add(CommonsKryoRegistrar.class).build();

  public static final com.google.common.collect.ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(CommonMorphiaRegistrar.class).build();
}
