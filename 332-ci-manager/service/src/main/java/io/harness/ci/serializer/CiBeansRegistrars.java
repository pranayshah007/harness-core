/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.nodes.ActionStepNode;
import io.harness.beans.steps.nodes.ArtifactoryUploadNode;
import io.harness.beans.steps.nodes.BackgroundStepNode;
import io.harness.beans.steps.nodes.BitriseStepNode;
import io.harness.beans.steps.nodes.BuildAndPushACRNode;
import io.harness.beans.steps.nodes.BuildAndPushDockerNode;
import io.harness.beans.steps.nodes.BuildAndPushECRNode;
import io.harness.beans.steps.nodes.BuildAndPushGCRNode;
import io.harness.beans.steps.nodes.GCSUploadNode;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.RestoreCacheGCSNode;
import io.harness.beans.steps.nodes.RestoreCacheS3Node;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.S3UploadNode;
import io.harness.beans.steps.nodes.SaveCacheGCSNode;
import io.harness.beans.steps.nodes.SaveCacheS3Node;
import io.harness.beans.steps.nodes.SecurityNode;
import io.harness.ci.serializer.morphia.CIExecutionMorphiaRegistrar;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.cimanager.serializer.CIContractsMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.serializer.common.CommonsRegistrars;
import io.harness.serializer.kryo.CIBeansKryoRegistrar;
import io.harness.serializer.kryo.NgPersistenceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.CIBeansMorphiaRegistrar;
import io.harness.serializer.morphia.NgPersistenceMorphiaRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.YamlMorphiaRegistrar;
import io.harness.ssca.SscaBeansRegistrar;
import io.harness.sto.STOStepType;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.Collections;

@OwnedBy(HarnessTeam.CI)
public class CiBeansRegistrars {
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
          .add(CIBeansKryoRegistrar.class)
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

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .addAll(SscaBeansRegistrar.yamlSchemaRegistrars)
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.INTEGRATION_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(IntegrationStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CI)
                                           .modulesSupported(ImmutableList.of(ModuleType.CI, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.INTEGRATION_STEPS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(CIStepInfo.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RUN_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .modulesSupported(Lists.newArrayList(ModuleType.CI, ModuleType.CD, ModuleType.PMS))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .namespace(SchemaNamespaceConstants.CI)

                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RunStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BACKGROUND_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .modulesSupported(Lists.newArrayList(ModuleType.CI, ModuleType.CD, ModuleType.PMS))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .namespace(SchemaNamespaceConstants.CI)
                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BackgroundStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RUN_TEST)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RunTestStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.ARTIFACTORY_UPLOAD)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(ArtifactoryUploadNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.S3_UPLOAD)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(S3UploadNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.GCS_UPLOAD)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(GCSUploadNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BUILD_AND_PUSH_DOCKER_REGISTRY)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BuildAndPushDockerNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BUILD_AND_PUSH_ECR)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BuildAndPushECRNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BUILD_AND_PUSH_GCR)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BuildAndPushGCRNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BUILD_AND_PUSH_ACR)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BuildAndPushACRNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.PLUGIN)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .modulesSupported(Lists.newArrayList(ModuleType.CI, ModuleType.CD, ModuleType.PMS))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .namespace(SchemaNamespaceConstants.CI)

                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(PluginStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RESTORE_CACHE_GCS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RestoreCacheGCSNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.RESTORE_CACHE_S3)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(RestoreCacheS3Node.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SAVE_CACHE_GCS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(SaveCacheGCSNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SAVE_CACHE_S3)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(SaveCacheS3Node.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SECURITY)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(SecurityNode.class)
                   .build())
          .addAll(STOStepType.createSecurityStepYamlDefinitions())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.GIT_CLONE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .modulesSupported(Lists.newArrayList(ModuleType.CI, ModuleType.CD, ModuleType.PMS))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .namespace(SchemaNamespaceConstants.CI)
                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(GitCloneStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.ACTION_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .modulesSupported(Lists.newArrayList(ModuleType.CI, ModuleType.CD, ModuleType.PMS))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .namespace(SchemaNamespaceConstants.CI)

                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(ActionStepNode.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BITRISE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .modulesSupported(Collections.singletonList(ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(BitriseStepNode.class)
                   .build())
          .build();
}
