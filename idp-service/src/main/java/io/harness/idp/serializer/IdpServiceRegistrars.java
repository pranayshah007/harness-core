/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.serializer;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.serializer.kryo.IdpServiceKryoRegistrar;
import io.harness.idp.serializer.morphia.IdpServiceMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.kryo.ApiServiceBeansKryoRegister;
import io.harness.serializer.kryo.CommonsKryoRegistrar;
import io.harness.serializer.kryo.DelegateBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(IDP)
public class IdpServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(IdpServiceKryoRegistrar.class)
          .add(DelegateTasksBeansKryoRegister.class)
          .add(NGCommonsKryoRegistrar.class)
          .add(CommonsKryoRegistrar.class)
          .add(ApiServiceBeansKryoRegister.class)
          .add(DelegateBeansKryoRegistrar.class)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .build();
  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(IdpServiceMorphiaRegistrar.class).build();
}
