/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import com.google.common.collect.ImmutableSet;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.PersistenceBeansKryoRegistrar;
import io.harness.serializer.mongo.PersistenceBeansMorphiaRegistrar;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PersistenceBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(PersistenceBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(PersistenceBeansMorphiaRegistrar.class)
          .build();
}
