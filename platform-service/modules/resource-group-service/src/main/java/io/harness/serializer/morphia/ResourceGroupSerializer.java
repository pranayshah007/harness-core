/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.serializer.GitSyncSdkRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.AccessControlClientRegistrars;
import io.harness.serializer.CvNextGenBeansRegistrars;
import io.harness.serializer.DelegateServiceDriverRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.FeatureFlagBeansRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGCoreClientRegistrars;
import io.harness.serializer.OutboxEventRegistrars;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.WaitEngineRegistrars;
import io.harness.serializer.kryo.DelegateServiceBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.serializer.kryo.DelegateTasksKryoRegistrar;
import io.harness.serializer.kryo.NGCoreKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.PmsCommonsKryoRegistrar;
import io.harness.serializer.kryo.SecretConfigKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class ResourceGroupSerializer {
  public final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(DelegateTasksBeansKryoRegister.class)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .add(NGCoreKryoRegistrar.class)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .add(DelegateTasksKryoRegistrar.class)
          .add(SecretConfigKryoRegistrar.class)
          .addAll(CvNextGenBeansRegistrars.kryoRegistrars)
          .add(DelegateServiceBeansKryoRegistrar.class)
          .add(YamlKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .add(PmsCommonsKryoRegistrar.class)
          .addAll(FeatureFlagBeansRegistrars.kryoRegistrars)
          .build();

  public final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(OutboxEventRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceDriverRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(SMCoreRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(GitSyncSdkRegistrar.morphiaRegistrars)
          .add(ResourceGroupMorphiaRegistrar.class)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .build();
}
