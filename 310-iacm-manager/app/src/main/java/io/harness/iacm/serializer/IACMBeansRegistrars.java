/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.serializer;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IACMStageNode;
import io.harness.beans.steps.IACMStepInfo;
import io.harness.beans.steps.nodes.ActionStepNode;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.ci.serializer.morphia.CIExecutionMorphiaRegistrar;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.cimanager.serializer.CIContractsMorphiaRegistrar;
import io.harness.iacm.IACMStepType;
import io.harness.iacm.serializer.kryo.IACMBeansKryoRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.serializer.AccessControlClientRegistrars;
import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.ContainerRegistrars;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.FeatureFlagBeansRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.LicenseManagerRegistrars;
import io.harness.serializer.NGCommonModuleRegistrars;
import io.harness.serializer.NGCoreBeansRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.serializer.ProjectAndOrgRegistrars;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.SecretManagerClientRegistrars;
import io.harness.serializer.WaitEngineRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.serializer.common.CommonsRegistrars;
import io.harness.serializer.kryo.NgPersistenceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.CIBeansMorphiaRegistrar;
import io.harness.serializer.morphia.NgPersistenceMorphiaRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.YamlMorphiaRegistrar;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IACM)
@UtilityClass
public class IACMBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .addAll(SecretManagerClientRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
          .addAll(LicenseManagerRegistrars.kryoRegistrars)
          .addAll(ContainerRegistrars.kryoRegistrars)
          .add(NgPersistenceKryoRegistrar.class)
          .add(IACMBeansKryoRegistrar.class)
          .add(CIContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.morphiaRegistrars)
          .addAll(NGCoreBeansRegistrars.morphiaRegistrars)
          .addAll(SecretManagerClientRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(WaitEngineRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceBeansRegistrars.morphiaRegistrars)
          .addAll(AccessControlClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(CommonsRegistrars.morphiaRegistrars)
          .addAll(ConnectorNextGenRegistrars.morphiaRegistrars)
          .addAll(LicenseManagerRegistrars.morphiaRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .addAll(ContainerRegistrars.morphiaRegistrars)
          .add(NotificationBeansMorphiaRegistrar.class)
          .add(CIBeansMorphiaRegistrar.class)
          .add(CIContractsMorphiaRegistrar.class)
          .add(CIExecutionMorphiaRegistrar.class)
          .add(YamlMorphiaRegistrar.class)
          .add(NgPersistenceMorphiaRegistrar.class)
          .build();
  private YamlSchemaRootClass createStepYaml(IACMStepType stepType) {
    return YamlSchemaRootClass.builder()
        .entityType(stepType.getEntityType())
        .availableAtProjectLevel(true)
        .availableAtOrgLevel(false)
        .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                .modulesSupported(Collections.singletonList(ModuleType.IACM))
                                .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                .build())
        .availableAtAccountLevel(false)
        .clazz(stepType.getNode())
        .build();
  }
  private ImmutableList<YamlSchemaRootClass> createIACMStepYamlDefinitions() {
    ImmutableList.Builder<YamlSchemaRootClass> stepPaletteListBuilder = ImmutableList.<YamlSchemaRootClass>builder();

    Arrays.asList(IACMStepType.values()).forEach(e -> stepPaletteListBuilder.add(createStepYaml(e)));

    return stepPaletteListBuilder.build();
  }

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.IACM_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(IACMStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.IACM)
                                           .modulesSupported(ImmutableList.of(ModuleType.IACM, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.PLUGIN)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(ImmutableList.of(ModuleType.IACM))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(PluginStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.ACTION_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.IACM))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(ActionStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.IACM_STEPS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(IACMStepInfo.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RUN_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.IACM))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RunStepNode.class)
                   .build())
          .addAll(createIACMStepYamlDefinitions())
          .build();
}
