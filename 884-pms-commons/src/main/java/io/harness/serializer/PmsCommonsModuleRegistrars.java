/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.serializer.GitSyncSdkRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.serializer.kryo.PmsContractsKryoRegistrar;
import io.harness.serializer.kryo.PmsCommonsKryoRegistrar;
import io.harness.serializer.kryo.RecasterKryoRegistrar;
import io.harness.serializer.morphia.PmsCommonsMorphiaRegistrar;
import io.harness.serializer.spring.converters.ambiance.AmbianceReadConverter;
import io.harness.serializer.spring.converters.ambiance.AmbianceWriteConverter;
import io.harness.serializer.spring.converters.facilitators.response.FacilitatorResponseReadConverter;
import io.harness.serializer.spring.converters.facilitators.response.FacilitatorResponseWriteConverter;
import io.harness.serializer.spring.converters.nodeexecution.NodeExecutionReadConverter;
import io.harness.serializer.spring.converters.nodeexecution.NodeExecutionWriteConverter;
import io.harness.serializer.spring.converters.orchestrationMap.OrchestrationMapReadConverter;
import io.harness.serializer.spring.converters.orchestrationMap.OrchestrationMapWriteConverter;
import io.harness.serializer.spring.converters.outcomes.PmsOutcomeReadConverter;
import io.harness.serializer.spring.converters.outcomes.PmsOutcomeWriteConverter;
import io.harness.serializer.spring.converters.plannode.PlanNodeProtoReadConverter;
import io.harness.serializer.spring.converters.plannode.PlanNodeProtoWriteConverter;
import io.harness.serializer.spring.converters.retryInfo.RetryInfoReadConverter;
import io.harness.serializer.spring.converters.retryInfo.RetryInfoWriteConverter;
import io.harness.serializer.spring.converters.sdk.SdkModuleInfoReadConverter;
import io.harness.serializer.spring.converters.sdk.SdkModuleInfoWriteConverter;
import io.harness.serializer.spring.converters.steptype.StepTypeReadConverter;
import io.harness.serializer.spring.converters.steptype.StepTypeWriteConverter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;
import org.springframework.core.convert.converter.Converter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class PmsCommonsModuleRegistrars {
  public final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(PmsCommonsKryoRegistrar.class)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .addAll(NGCommonsRegistrars.kryoRegistrars)
          .add(PmsContractsKryoRegistrar.class)
          .add(RecasterKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(PmsCommonsMorphiaRegistrar.class)
          .addAll(NGCommonsRegistrars.morphiaRegistrars)
          .addAll(GitSyncSdkRegistrar.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .build();

  public final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.of(StepTypeReadConverter.class, StepTypeWriteConverter.class, AmbianceWriteConverter.class,
          AmbianceReadConverter.class, FacilitatorResponseReadConverter.class, FacilitatorResponseWriteConverter.class,
          PlanNodeProtoReadConverter.class, PlanNodeProtoWriteConverter.class, NodeExecutionReadConverter.class,
          NodeExecutionWriteConverter.class, SdkModuleInfoReadConverter.class, SdkModuleInfoWriteConverter.class,
          OrchestrationMapReadConverter.class, OrchestrationMapWriteConverter.class, PmsOutcomeReadConverter.class,
          PmsOutcomeWriteConverter.class, RetryInfoReadConverter.class, RetryInfoWriteConverter.class);
}
