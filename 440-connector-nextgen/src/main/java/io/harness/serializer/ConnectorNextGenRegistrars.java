/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.filter.serializer.FiltersRegistrars;
import io.harness.gitsync.serializer.GitSyncSdkRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.ConnectorNextGenKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.ConnectorMorphiaClassesRegistrar;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(DX)
public class ConnectorNextGenRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(NGCoreRegistrars.kryoRegistrars)
          .addAll(DelegateServiceDriverRegistrars.kryoRegistrars)
          .addAll(NGCoreClientRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(DelegateAgentBeansRegistrars.kryoRegistrars)
          .add(ConnectorNextGenKryoRegistrar.class)
          .addAll(GitSyncSdkRegistrar.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(ProjectAndOrgRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(NGCoreRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceDriverRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(FiltersRegistrars.morphiaRegistrars)
          .addAll(SMCoreRegistrars.morphiaRegistrars)
          .add(ConnectorMorphiaClassesRegistrar.class)
          .addAll(ConnectorBeansRegistrars.morphiaRegistrars)
          .addAll(GitSyncSdkRegistrar.morphiaRegistrars)
          .addAll(NGAuditCommonsRegistrars.morphiaRegistrars)
          .addAll(OutboxEventRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(ProjectAndOrgRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .build();

  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(NGCommonModuleRegistrars.springConverters)
          .build();

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.CONNECTORS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(true)
                   .availableAtAccountLevel(true)
                   .clazz(ConnectorDTO.class)
                   .build())
          .build();
}
