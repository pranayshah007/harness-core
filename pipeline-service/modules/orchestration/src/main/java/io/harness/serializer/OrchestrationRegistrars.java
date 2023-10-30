/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.serializer.FiltersRegistrars;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateServiceBeansKryoRegistrar;
import io.harness.serializer.kryo.NGCoreKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationKryoRegistrar;
import io.harness.serializer.kryo.SecretManagerClientKryoRegistrar;
import io.harness.serializer.morphia.OrchestrationMorphiaRegistrar;
import io.harness.serializer.morphia.converters.AdviserObtainmentMorphiaConverter;
import io.harness.serializer.morphia.converters.AdviserTypeMorphiaConverter;
import io.harness.serializer.morphia.converters.AmbianceMorphiaConverter;
import io.harness.serializer.morphia.converters.ExecutableResponseMorphiaConverter;
import io.harness.serializer.morphia.converters.ExecutionMetadataMorphiaConverter;
import io.harness.serializer.morphia.converters.ExecutionPrincipalInfoMorphiaConverter;
import io.harness.serializer.morphia.converters.ExecutionTriggerInfoMorphiaConverter;
import io.harness.serializer.morphia.converters.FacilitatorObtainmentMorphiaConverter;
import io.harness.serializer.morphia.converters.FacilitatorTypeMorphiaConverter;
import io.harness.serializer.morphia.converters.FailureInfoMorphiaConverter;
import io.harness.serializer.morphia.converters.GovernanceMetadataMorphiaConverter;
import io.harness.serializer.morphia.converters.InterruptConfigMorphiaConverter;
import io.harness.serializer.morphia.converters.InterruptEffectMorphiaConverter;
import io.harness.serializer.morphia.converters.LevelMorphiaConverter;
import io.harness.serializer.morphia.converters.PolicyMetadataMorphiaConverter;
import io.harness.serializer.morphia.converters.PolicySetMetadataMorphiaConverter;
import io.harness.serializer.morphia.converters.RefObjectMorphiaConverter;
import io.harness.serializer.morphia.converters.RefTypeMorphiaConverter;
import io.harness.serializer.morphia.converters.RetryExecutionInfoMorphiaConverter;
import io.harness.serializer.morphia.converters.SdkModuleInfoMorphiaConverter;
import io.harness.serializer.morphia.converters.StepTypeMorphiaConverter;
import io.harness.serializer.morphia.converters.TriggerPayloadMorphiaConverter;
import io.harness.serializer.morphia.converters.TriggeredByMorphiaConverter;
import io.harness.serializer.spring.converters.MatrixMetadataReadConverter;
import io.harness.serializer.spring.converters.MatrixMetadataWriteConverter;
import io.harness.serializer.spring.converters.StrategyMetadataReadConverter;
import io.harness.serializer.spring.converters.StrategyMetadataWriteConverter;
import io.harness.serializer.spring.converters.advisers.obtainment.AdviserObtainmentReadConverter;
import io.harness.serializer.spring.converters.advisers.obtainment.AdviserObtainmentWriteConverter;
import io.harness.serializer.spring.converters.advisers.response.AdviserResponseReadConverter;
import io.harness.serializer.spring.converters.advisers.response.AdviserResponseWriteConverter;
import io.harness.serializer.spring.converters.advisers.type.AdviserTypeReadConverter;
import io.harness.serializer.spring.converters.advisers.type.AdviserTypeWriteConverter;
import io.harness.serializer.spring.converters.ambiance.AmbianceReadConverter;
import io.harness.serializer.spring.converters.ambiance.AmbianceWriteConverter;
import io.harness.serializer.spring.converters.consumerconfig.ConsumerConfigReadConverter;
import io.harness.serializer.spring.converters.consumerconfig.ConsumerConfigWriteConverter;
import io.harness.serializer.spring.converters.errorInfo.ExecutionErrorInfoReadConverter;
import io.harness.serializer.spring.converters.errorInfo.ExecutionErrorInfoWriteConverter;
import io.harness.serializer.spring.converters.executableresponse.ExecutableResponseReadConverter;
import io.harness.serializer.spring.converters.executableresponse.ExecutableResponseWriteConverter;
import io.harness.serializer.spring.converters.executionmetadata.ExecutionMetadataReadConverter;
import io.harness.serializer.spring.converters.executionmetadata.ExecutionMetadataWriteConverter;
import io.harness.serializer.spring.converters.executionmetadata.TriggerPayloadReadConverter;
import io.harness.serializer.spring.converters.executionmetadata.TriggerPayloadWriteConverter;
import io.harness.serializer.spring.converters.expansionhandler.JsonExpansionInfoReadConverter;
import io.harness.serializer.spring.converters.expansionhandler.JsonExpansionInfoWriteConverter;
import io.harness.serializer.spring.converters.facilitators.obtainment.FacilitatorObtainmentReadConverter;
import io.harness.serializer.spring.converters.facilitators.obtainment.FacilitatorObtainmentWriteConverter;
import io.harness.serializer.spring.converters.facilitators.response.FacilitatorResponseReadConverter;
import io.harness.serializer.spring.converters.facilitators.response.FacilitatorResponseWriteConverter;
import io.harness.serializer.spring.converters.facilitators.type.FacilitatorTypeReadConverter;
import io.harness.serializer.spring.converters.facilitators.type.FacilitatorTypeWriteConverter;
import io.harness.serializer.spring.converters.failureinfo.FailureInfoReadConverter;
import io.harness.serializer.spring.converters.failureinfo.FailureInfoWriteConverter;
import io.harness.serializer.spring.converters.governancemetadata.GovernanceMetadataReadConverter;
import io.harness.serializer.spring.converters.governancemetadata.GovernanceMetadataWriteConverter;
import io.harness.serializer.spring.converters.governancemetadata.PolicyMetadataReadConverter;
import io.harness.serializer.spring.converters.governancemetadata.PolicyMetadataWriteConverter;
import io.harness.serializer.spring.converters.governancemetadata.PolicySetMetadataReadConverter;
import io.harness.serializer.spring.converters.governancemetadata.PolicySetMetadataWriteConverter;
import io.harness.serializer.spring.converters.graphlayout.GraphLayoutNodeWriteConverter;
import io.harness.serializer.spring.converters.graphlayout.GraphLayoutReadConverter;
import io.harness.serializer.spring.converters.graphlayout.LayoutNodeInfoReadConverter;
import io.harness.serializer.spring.converters.graphlayout.LayoutNodeInfoWriteConverter;
import io.harness.serializer.spring.converters.interrupt.InterruptConfigReadConverter;
import io.harness.serializer.spring.converters.interrupt.InterruptConfigWriteConverter;
import io.harness.serializer.spring.converters.interrupt.InterruptEffectReadConverter;
import io.harness.serializer.spring.converters.interrupt.InterruptEffectWriteConverter;
import io.harness.serializer.spring.converters.level.LevelReadConverter;
import io.harness.serializer.spring.converters.level.LevelWriteConverter;
import io.harness.serializer.spring.converters.logging.UnitProgressReadConverter;
import io.harness.serializer.spring.converters.logging.UnitProgressWriteConverter;
import io.harness.serializer.spring.converters.nodeexecution.NodeExecutionReadConverter;
import io.harness.serializer.spring.converters.nodeexecution.NodeExecutionWriteConverter;
import io.harness.serializer.spring.converters.orchestrationMap.OrchestrationMapReadConverter;
import io.harness.serializer.spring.converters.orchestrationMap.OrchestrationMapWriteConverter;
import io.harness.serializer.spring.converters.outcomes.PmsOutcomeReadConverter;
import io.harness.serializer.spring.converters.outcomes.PmsOutcomeWriteConverter;
import io.harness.serializer.spring.converters.pipelinechaining.PipelineStageInfoReadConverter;
import io.harness.serializer.spring.converters.pipelinechaining.PipelineStageInfoWriteConverter;
import io.harness.serializer.spring.converters.plannode.PlanNodeProtoReadConverter;
import io.harness.serializer.spring.converters.plannode.PlanNodeProtoWriteConverter;
import io.harness.serializer.spring.converters.principal.ExecutionPrincipalInfoReadConverter;
import io.harness.serializer.spring.converters.principal.ExecutionPrincipalInfoWriteConverter;
import io.harness.serializer.spring.converters.refobject.RefObjectReadConverter;
import io.harness.serializer.spring.converters.refobject.RefObjectWriteConverter;
import io.harness.serializer.spring.converters.reftype.RefTypeReadConverter;
import io.harness.serializer.spring.converters.reftype.RefTypeWriteConverter;
import io.harness.serializer.spring.converters.retryInfo.RetryInfoReadConverter;
import io.harness.serializer.spring.converters.retryInfo.RetryInfoWriteConverter;
import io.harness.serializer.spring.converters.run.NodeRunInfoReadConverter;
import io.harness.serializer.spring.converters.run.NodeRunInfoWriteConverter;
import io.harness.serializer.spring.converters.sdk.SdkModuleInfoReadConverter;
import io.harness.serializer.spring.converters.sdk.SdkModuleInfoWriteConverter;
import io.harness.serializer.spring.converters.skip.SkipInfoReadConverter;
import io.harness.serializer.spring.converters.skip.SkipInfoWriteConverter;
import io.harness.serializer.spring.converters.stepoutcomeref.StepOutcomeRefReadConverter;
import io.harness.serializer.spring.converters.stepoutcomeref.StepOutcomeRefWriteConverter;
import io.harness.serializer.spring.converters.stepparameters.PmsStepParametersReadConverter;
import io.harness.serializer.spring.converters.stepparameters.PmsStepParametersWriteConverter;
import io.harness.serializer.spring.converters.steps.SdkStepReadConverter;
import io.harness.serializer.spring.converters.steps.SdkStepWriteConverter;
import io.harness.serializer.spring.converters.steps.StepInfoReadConverter;
import io.harness.serializer.spring.converters.steps.StepInfoWriteConverter;
import io.harness.serializer.spring.converters.steptype.StepTypeReadConverter;
import io.harness.serializer.spring.converters.steptype.StepTypeWriteConverter;
import io.harness.serializer.spring.converters.sweepingoutput.SweepingOutputReadMongoConverter;
import io.harness.serializer.spring.converters.sweepingoutput.SweepingOutputWriteMongoConverter;
import io.harness.serializer.spring.converters.timeout.obtainment.TimeoutObtainmentReadConverter;
import io.harness.serializer.spring.converters.timeout.obtainment.TimeoutObtainmentWriteConverter;
import io.harness.serializer.spring.converters.triggers.ExecutionTriggerInfoReadConverter;
import io.harness.serializer.spring.converters.triggers.ExecutionTriggerInfoWriteConverter;
import io.harness.serializer.spring.converters.triggers.TriggeredByReadConverter;
import io.harness.serializer.spring.converters.triggers.TriggeredByWriteConverter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.core.convert.converter.Converter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class OrchestrationRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .addAll(OrchestrationBeansRegistrars.kryoRegistrars)
          .addAll(OrchestrationDelayRegistrars.kryoRegistrars)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          .add(NGCoreKryoRegistrar.class)
          .add(SecretManagerClientKryoRegistrar.class)
          .add(OrchestrationKryoRegistrar.class)
          .add(DelegateServiceBeansKryoRegistrar.class)
          .add(CommonEntitiesKryoRegistrar.class)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(FiltersRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(WaitEngineRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceDriverRegistrars.morphiaRegistrars)
          .addAll(OrchestrationBeansRegistrars.morphiaRegistrars)
          .addAll(OrchestrationDelayRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .add(OrchestrationMorphiaRegistrar.class)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(FiltersRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder()
          .addAll(OrchestrationBeansRegistrars.morphiaConverters)
          .add(AmbianceMorphiaConverter.class)
          .add(SdkModuleInfoMorphiaConverter.class)
          .add(LevelMorphiaConverter.class)
          .add(StepTypeMorphiaConverter.class)
          .add(RefObjectMorphiaConverter.class)
          .add(AdviserTypeMorphiaConverter.class)
          .add(AdviserObtainmentMorphiaConverter.class)
          .add(FacilitatorTypeMorphiaConverter.class)
          .add(FacilitatorObtainmentMorphiaConverter.class)
          .add(RefTypeMorphiaConverter.class)
          .add(FailureInfoMorphiaConverter.class)
          .add(ExecutableResponseMorphiaConverter.class)
          .add(ExecutionTriggerInfoMorphiaConverter.class)
          .add(ExecutionPrincipalInfoMorphiaConverter.class)
          .add(InterruptEffectMorphiaConverter.class)
          .add(TriggeredByMorphiaConverter.class)
          .add(ExecutionMetadataMorphiaConverter.class)
          .add(TriggerPayloadMorphiaConverter.class)
          .add(InterruptConfigMorphiaConverter.class)
          .add(PolicyMetadataMorphiaConverter.class)
          .add(PolicySetMetadataMorphiaConverter.class)
          .add(GovernanceMetadataMorphiaConverter.class)
          .add(RetryExecutionInfoMorphiaConverter.class)
          .build();

  public static final List<Class<? extends Converter<?, ?>>> orchestrationConverters = ImmutableList.of(
      SweepingOutputReadMongoConverter.class, SweepingOutputWriteMongoConverter.class, AmbianceReadConverter.class,
      AmbianceWriteConverter.class, LevelReadConverter.class, LevelWriteConverter.class, AdviserTypeReadConverter.class,
      AdviserTypeWriteConverter.class, AdviserObtainmentReadConverter.class, AdviserObtainmentWriteConverter.class,
      FacilitatorTypeReadConverter.class, FacilitatorTypeWriteConverter.class, StepTypeReadConverter.class,
      StepTypeWriteConverter.class, FacilitatorObtainmentReadConverter.class, FacilitatorObtainmentWriteConverter.class,
      RefTypeReadConverter.class, RefTypeWriteConverter.class, RefObjectReadConverter.class,
      RefObjectWriteConverter.class, StepInfoReadConverter.class, StepInfoWriteConverter.class,
      FailureInfoReadConverter.class, FailureInfoWriteConverter.class, PlanNodeProtoReadConverter.class,
      PlanNodeProtoWriteConverter.class, StepOutcomeRefReadConverter.class, StepOutcomeRefWriteConverter.class,
      ExecutableResponseReadConverter.class, ExecutableResponseWriteConverter.class, NodeExecutionReadConverter.class,
      NodeExecutionWriteConverter.class, FacilitatorResponseReadConverter.class,
      FacilitatorResponseWriteConverter.class, GraphLayoutReadConverter.class, GraphLayoutNodeWriteConverter.class,
      ExecutionErrorInfoReadConverter.class, ExecutionErrorInfoWriteConverter.class, LayoutNodeInfoReadConverter.class,
      LayoutNodeInfoWriteConverter.class, ExecutionTriggerInfoReadConverter.class,
      ExecutionTriggerInfoWriteConverter.class, TriggeredByReadConverter.class, TriggeredByWriteConverter.class,
      ExecutionMetadataReadConverter.class, ExecutionMetadataWriteConverter.class, TriggerPayloadReadConverter.class,
      TriggerPayloadWriteConverter.class, SkipInfoReadConverter.class, SkipInfoWriteConverter.class,
      TimeoutObtainmentReadConverter.class, TimeoutObtainmentWriteConverter.class, AdviserResponseReadConverter.class,
      AdviserResponseWriteConverter.class, UnitProgressReadConverter.class, UnitProgressWriteConverter.class,
      InterruptConfigReadConverter.class, InterruptConfigWriteConverter.class, NodeRunInfoReadConverter.class,
      NodeRunInfoWriteConverter.class, ExecutionPrincipalInfoReadConverter.class,
      ExecutionPrincipalInfoWriteConverter.class, InterruptEffectReadConverter.class,
      InterruptEffectWriteConverter.class, SdkModuleInfoReadConverter.class, SdkModuleInfoWriteConverter.class,
      ConsumerConfigReadConverter.class, ConsumerConfigWriteConverter.class, OrchestrationMapReadConverter.class,
      OrchestrationMapWriteConverter.class, SdkStepWriteConverter.class, SdkStepReadConverter.class,
      PmsOutcomeReadConverter.class, PmsOutcomeWriteConverter.class, PmsStepParametersReadConverter.class,
      PmsStepParametersWriteConverter.class, PolicyMetadataReadConverter.class, PolicyMetadataWriteConverter.class,
      PolicySetMetadataReadConverter.class, PolicySetMetadataWriteConverter.class,
      GovernanceMetadataReadConverter.class, GovernanceMetadataWriteConverter.class,
      JsonExpansionInfoReadConverter.class, JsonExpansionInfoWriteConverter.class, StrategyMetadataReadConverter.class,
      MatrixMetadataReadConverter.class, StrategyMetadataWriteConverter.class, MatrixMetadataWriteConverter.class,
      PipelineStageInfoReadConverter.class, PipelineStageInfoWriteConverter.class, RetryInfoReadConverter.class,
      RetryInfoWriteConverter.class);

  public static final List<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(orchestrationConverters)
          .addAll(OrchestrationBeansRegistrars.springConverters)
          .addAll(WaitEngineRegistrars.springConverters)
          .build();
}
