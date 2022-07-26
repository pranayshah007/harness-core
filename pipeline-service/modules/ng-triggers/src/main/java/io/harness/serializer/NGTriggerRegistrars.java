/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ngtriggers.beans.config.NgTriggerConfigSchemaWrapper;
import io.harness.serializer.kryo.NGTriggerKryoRegistrar;
import io.harness.serializer.kryo.ProjectAndOrgKryoRegistrar;
import io.harness.serializer.morphia.NGTriggerMorphiaRegistrar;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.serializer.kryo.PollingKryoRegistrar;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class NGTriggerRegistrars {
  public final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(ProjectAndOrgKryoRegistrar.class)
          .add(NGTriggerKryoRegistrar.class)
          .addAll(ApiServiceBeansRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .add(PollingKryoRegistrar.class)
          .build();

  public final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .add(NGTriggerMorphiaRegistrar.class)
          .build();

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.TRIGGERS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(NgTriggerConfigSchemaWrapper.class)
                   .build())
          .build();
}
