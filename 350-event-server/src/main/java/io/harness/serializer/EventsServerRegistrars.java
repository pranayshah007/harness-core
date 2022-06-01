/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CvNextGenBeansKryoRegistrar;
import io.harness.serializer.kryo.CvNextGenCommonsKryoRegistrar;
import io.harness.serializer.kryo.DelegateAgentBeansKryoRegister;
import io.harness.serializer.kryo.DelegateAgentKryoRegister;
import io.harness.serializer.kryo.ManagerKryoRegistrar;
import io.harness.serializer.kryo.NgAuthenticationServiceKryoRegistrar;
import io.harness.serializer.kryo.ProjectAndOrgKryoRegistrar;
import io.harness.serializer.kryo.SecretManagerClientKryoRegistrar;
import io.harness.serializer.kryo.WatcherBeansKryoRegister;
import io.harness.serializer.morphia.CommonEntitiesMorphiaRegister;
import io.harness.serializer.morphia.ConnectorMorphiaClassesRegistrar;
import io.harness.serializer.morphia.EventMorphiaRegistrar;
import io.harness.serializer.morphia.LimitsMorphiaRegistrar;
import io.harness.serializer.morphia.ManagerMorphiaRegistrar;
import io.harness.serializer.morphia.NGCoreMorphiaClassesRegistrar;
import io.harness.serializer.morphia.ProjectAndOrgMorphiaRegistrar;
import io.harness.serializer.morphia.SMCoreMorphiaRegistrar;
import io.harness.serializer.morphia.SecretManagerClientMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EventsServerRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(DelegateTasksBeansRegistrars.kryoRegistrars)
          .addAll(CgOrchestrationRegistrars.kryoRegistrars)
          .add(ManagerKryoRegistrar.class)
          .add(NgAuthenticationServiceKryoRegistrar.class)
          .add(ProjectAndOrgKryoRegistrar.class)
          .addAll(NGCoreRegistrars.kryoRegistrars)
          .add(SecretManagerClientKryoRegistrar.class)
          .add(CvNextGenCommonsKryoRegistrar.class)
          .add(CvNextGenBeansKryoRegistrar.class)
          // temporary:
          .add(DelegateAgentKryoRegister.class)
          .add(DelegateAgentBeansKryoRegister.class)
          .add(WatcherBeansKryoRegister.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(DelegateTasksBeansRegistrars.morphiaRegistrars)
          .add(ManagerMorphiaRegistrar.class)
          .add(LimitsMorphiaRegistrar.class)
          .add(ProjectAndOrgMorphiaRegistrar.class)
          .add(CommonEntitiesMorphiaRegister.class)
          .addAll(NGCoreRegistrars.morphiaRegistrars)
          .add(SecretManagerClientMorphiaRegistrar.class)
          .add(NGCoreMorphiaClassesRegistrar.class)
          .add(ConnectorMorphiaClassesRegistrar.class)
          .add(SMCoreMorphiaRegistrar.class)
          .add(EventMorphiaRegistrar.class)
          .build();
}
