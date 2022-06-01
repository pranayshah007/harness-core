/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.serializer.morphia.CECommonsMorphiaRegistrar;
import io.harness.morphia.CgOrchestrationBeansMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CgOrchestrationBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateAgentBeansKryoRegister;
import io.harness.serializer.kryo.DelegateAgentKryoRegister;
import io.harness.serializer.kryo.DelegateServiceKryoRegister;
import io.harness.serializer.kryo.EventEntitiesKryoRegistrar;
import io.harness.serializer.kryo.ManagerKryoRegistrar;
import io.harness.serializer.kryo.NgAuthenticationServiceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.NotificationDelegateTasksKryoRegistrar;
import io.harness.serializer.kryo.ProjectAndOrgKryoRegistrar;
import io.harness.serializer.kryo.WatcherBeansKryoRegister;
import io.harness.serializer.morphia.CommonEntitiesMorphiaRegister;
import io.harness.serializer.morphia.DelegateServiceBeansMorphiaRegistrar;
import io.harness.serializer.morphia.DelegateServiceMorphiaRegistrar;
import io.harness.serializer.morphia.EventEntitiesMorphiaRegister;
import io.harness.serializer.morphia.EventMorphiaRegistrar;
import io.harness.serializer.morphia.LimitsMorphiaRegistrar;
import io.harness.serializer.morphia.ManagerMorphiaRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.ProjectAndOrgMorphiaRegistrar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(PL)
public class ManagerRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CvNextGenBeansRegistrars.kryoRegistrars)
          .addAll(CvNextGenCommonsRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(DelegateTasksBeansRegistrars.kryoRegistrars)
          .addAll(CgOrchestrationRegistrars.kryoRegistrars)
          .add(CgOrchestrationBeansKryoRegistrar.class)
          .add(ManagerKryoRegistrar.class)
          .add(ProjectAndOrgKryoRegistrar.class)
          .addAll(NGCommonsRegistrars.kryoRegistrars)
          .addAll(NGCoreRegistrars.kryoRegistrars)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(FileServiceCommonsRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          // temporary:
          .add(NotificationDelegateTasksKryoRegistrar.class)
          .add(DelegateAgentKryoRegister.class)
          .add(DelegateAgentBeansKryoRegister.class)
          .add(WatcherBeansKryoRegister.class)
          .add(DelegateServiceKryoRegister.class)
          .addAll(OutboxEventRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .add(EventEntitiesKryoRegistrar.class)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .add(NgAuthenticationServiceKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(CvNextGenCommonsRegistrars.morphiaRegistrars)
          .addAll(VerificationCommonsRegistrars.morphiaRegistrars)
          .addAll(DelegateTasksBeansRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .addAll(NGCommonsRegistrars.morphiaRegistrars)
          .addAll(NGCoreRegistrars.morphiaRegistrars)
          .addAll(CgOrchestrationRegistrars.morphiaRegistrars)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .addAll(SMCoreRegistrars.morphiaRegistrars)
          .addAll(FileServiceCommonsRegistrars.morphiaRegistrars)
          .add(CECommonsMorphiaRegistrar.class)
          .add(CommonEntitiesMorphiaRegister.class)
          .add(DelegateServiceBeansMorphiaRegistrar.class)
          .add(DelegateServiceMorphiaRegistrar.class)
          .add(EventMorphiaRegistrar.class)
          .add(LimitsMorphiaRegistrar.class)
          .add(ManagerMorphiaRegistrar.class)
          .add(ProjectAndOrgMorphiaRegistrar.class)
          .addAll(ViewsModuleRegistrars.morphiaRegistrars)
          .add(CgOrchestrationBeansMorphiaRegistrar.class)
          .add(NotificationBeansMorphiaRegistrar.class)
          .addAll(ConnectorBeansRegistrars.morphiaRegistrars)
          .addAll(NGAuditCommonsRegistrars.morphiaRegistrars)
          .addAll(OutboxEventRegistrars.morphiaRegistrars)
          .add(EventEntitiesMorphiaRegister.class)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder()
          .addAll(PersistenceRegistrars.morphiaConverters)
          .addAll(DelegateTasksBeansRegistrars.morphiaConverters)
          .build();

  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
}
