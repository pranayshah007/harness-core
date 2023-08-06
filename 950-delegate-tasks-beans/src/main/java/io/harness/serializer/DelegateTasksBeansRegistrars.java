/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;
import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.morphia.CgOrchestrationBeansMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CgOrchestrationBeansKryoRegistrar;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.serializer.morphia.CommonEntitiesMorphiaRegister;
import io.harness.serializer.morphia.DelegateTasksBeansMorphiaRegistrar;
import io.harness.serializer.morphia.DelegateTasksCommonsBeansMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(DEL)
@UtilityClass
public class DelegateTasksBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(ApiServiceBeansRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .addAll(ScmJavaClientRegistrars.kryoRegistrars)
          .addAll(PersistenceRegistrars.kryoRegistrars)
          .addAll(FileServiceCommonsRegistrars.kryoRegistrars)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .add(DelegateTasksBeansKryoRegister.class)
          .add(CgOrchestrationBeansKryoRegistrar.class)
          .add(CommonEntitiesKryoRegistrar.class)
          .add(DelegateBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(ApiServiceBeansRegistrars.morphiaRegistrars)
          .addAll(PersistenceRegistrars.morphiaRegistrars)
          .add(DelegateTasksBeansMorphiaRegistrar.class)
          .add(DelegateTasksCommonsBeansMorphiaRegistrar.class)
          .addAll(ConnectorBeansRegistrars.morphiaRegistrars)
          .addAll(FileServiceCommonsRegistrars.morphiaRegistrars)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .add(CommonEntitiesMorphiaRegister.class)
          .add(CgOrchestrationBeansMorphiaRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder().build();
}
