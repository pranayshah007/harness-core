/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.serializer.kryo.PmsContractsKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationBeansKryoRegistrar;
import io.harness.serializer.kryo.RecasterKryoRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationBeansMorphiaRegistrar;
import io.harness.serializer.spring.converters.outputs.PmsSweepingOutputReadConverter;
import io.harness.serializer.spring.converters.outputs.PmsSweepingOutputWriteConverter;
import io.harness.serializer.spring.converters.stepdetails.PmsStepDetailsReadConverter;
import io.harness.serializer.spring.converters.stepdetails.PmsStepDetailsWriteConverter;
import io.harness.serializer.spring.converters.stepparameters.PmsStepParametersReadConverter;
import io.harness.serializer.spring.converters.stepparameters.PmsStepParametersWriteConverter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(PersistenceRegistrars.kryoRegistrars)
          .addAll(TimeoutEngineRegistrars.kryoRegistrars)
          .add(PmsContractsKryoRegistrar.class)
          .add(PmsSdkCoreKryoRegistrar.class)
          .addAll(PmsCommonsModuleRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .add(OrchestrationBeansKryoRegistrar.class)
          .add(RecasterKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(PersistenceRegistrars.morphiaRegistrars)
          .addAll(TimeoutEngineRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceBeansRegistrars.morphiaRegistrars)
          .addAll(PmsCommonsModuleRegistrars.morphiaRegistrars)
          .add(PmsSdkCoreMorphiaRegistrar.class)
          .addAll(WaitEngineRegistrars.morphiaRegistrars)
          .add(OrchestrationBeansMorphiaRegistrar.class)
          .add(NotificationBeansMorphiaRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder().addAll(PersistenceRegistrars.morphiaConverters).build();

  public final ImmutableList<Class<? extends Converter<?, ?>>> springConverters = ImmutableList.of(
      PmsStepParametersReadConverter.class, PmsStepParametersWriteConverter.class, PmsSweepingOutputReadConverter.class,
      PmsSweepingOutputWriteConverter.class, PmsStepDetailsReadConverter.class, PmsStepDetailsWriteConverter.class);
}
