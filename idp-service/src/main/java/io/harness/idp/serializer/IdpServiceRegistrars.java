/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.serializer;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.idp.pipeline.stages.node.IDPStageNode;
import io.harness.idp.serializer.kryo.IdpServiceKryoRegistrar;
import io.harness.idp.serializer.morphia.IdpServiceMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.serializer.*;
import io.harness.serializer.kryo.*;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import lombok.experimental.UtilityClass;
@UtilityClass
@OwnedBy(IDP)
public class IdpServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(IdpServiceKryoRegistrar.class)
          .add(DelegateTasksBeansKryoRegister.class)
          .add(NGCommonsKryoRegistrar.class)
          .add(CommonsKryoRegistrar.class)
          .add(ApiServiceBeansKryoRegister.class)
          .add(DelegateBeansKryoRegistrar.class)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          //

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
          .add(CIContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .build();
  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(IdpServiceMorphiaRegistrar.class).build();

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.IDP_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(IDPStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.IDP)
                                           .modulesSupported(ImmutableList.of(ModuleType.IDP))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          //                  .add(YamlSchemaRootClass.builder()
          //                          .entityType(EntityType.PLUGIN)
          //                          .availableAtProjectLevel(true)
          //                          .availableAtOrgLevel(false)
          //                          .yamlSchemaMetadata(YamlSchemaMetadata.builder()
          //                                  .modulesSupported(ImmutableList.of(ModuleType.IACM))
          //                                  .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
          //                                  .build())
          //                          .availableAtAccountLevel(false)
          //                          .clazz(PluginStepNode.class)
          //                          .build())
          //                  .add(YamlSchemaRootClass.builder()
          //                          .entityType(EntityType.ACTION_STEP)
          //                          .availableAtProjectLevel(true)
          //                          .availableAtOrgLevel(false)
          //                          .yamlSchemaMetadata(YamlSchemaMetadata.builder()
          //                                  .modulesSupported(Collections.singletonList(ModuleType.IACM))
          //                                  .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
          //                                  .build())
          //                          .availableAtAccountLevel(false)
          //                          .clazz(ActionStepNode.class)
          //                          .build())
          //                  .add(YamlSchemaRootClass.builder()
          //                          .entityType(EntityType.IACM_STEPS)
          //                          .availableAtProjectLevel(true)
          //                          .availableAtOrgLevel(false)
          //                          .availableAtAccountLevel(false)
          //                          .clazz(IACMStepInfo.class)
          //                          .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RUN_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.IDP))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RunStepNode.class)
                   .build())
          .build();
}
