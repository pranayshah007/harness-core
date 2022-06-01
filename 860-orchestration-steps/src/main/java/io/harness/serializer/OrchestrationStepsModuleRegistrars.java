/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cf.pipeline.FeatureFlagStageNode;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.plancreator.steps.http.HttpStepNode;
import io.harness.plancreator.steps.internal.FlagConfigurationStepNode;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateServiceBeansKryoRegistrar;
import io.harness.serializer.kryo.NGCoreBeansKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationStepsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationStepsMorphiaRegistrar;
import io.harness.steps.approval.stage.ApprovalStageNode;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.jira.JiraApprovalStepNode;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepNode;
import io.harness.steps.customstage.CustomStageNode;
import io.harness.steps.jira.create.JiraCreateStepNode;
import io.harness.steps.jira.update.JiraUpdateStepNode;
import io.harness.steps.policy.PolicyStepNode;
import io.harness.steps.servicenow.create.ServiceNowCreateStepNode;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepNode;
import io.harness.steps.shellscript.ShellScriptStepNode;
import io.harness.steps.template.TemplateStepNode;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(PIPELINE)
public class OrchestrationStepsModuleRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(OrchestrationRegistrars.kryoRegistrars)
          .add(OrchestrationStepsKryoRegistrar.class)
          .add(YamlKryoRegistrar.class)
          .add(NGCoreBeansKryoRegistrar.class)
          .add(DelegateServiceBeansKryoRegistrar.class)
          .add(CommonEntitiesKryoRegistrar.class)
          .addAll(PmsCommonsModuleRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .addAll(NGCoreClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(OrchestrationRegistrars.morphiaRegistrars)
          .add(OrchestrationStepsMorphiaRegistrar.class)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .add(NotificationBeansMorphiaRegistrar.class)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder()
          .addAll(OrchestrationBeansRegistrars.morphiaConverters)
          .build();

  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(OrchestrationRegistrars.springConverters)
          .addAll(NGCommonModuleRegistrars.springConverters)
          .build();

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.PIPELINES)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(PipelineConfig.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.PIPELINE_STEPS)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(PMSStepInfo.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.APPROVAL_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ApprovalStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
                                           .modulesSupported(ImmutableList.of(ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.CUSTOM_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(CustomStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CUSTOM)
                                           .modulesSupported(ImmutableList.of(ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.FEATURE_FLAG_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(FeatureFlagStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CF)
                                           .modulesSupported(ImmutableList.of(ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.HTTP_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(HttpStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SHELL_SCRIPT_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ShellScriptStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.JIRA_CREATE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(JiraCreateStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.JIRA_UPDATE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(JiraUpdateStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.TEMPLATE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(TemplateStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS, ModuleType.CE,
                                               ModuleType.CF, ModuleType.CI))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SERVICENOW_APPROVAL_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ServiceNowApprovalStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.JIRA_APPROVAL_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(JiraApprovalStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.HARNESS_APPROVAL_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(HarnessApprovalStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.BARRIER_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(BarrierStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SERVICENOW_CREATE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ServiceNowCreateStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SERVICENOW_UPDATE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ServiceNowUpdateStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.FLAG_CONFIGURATION)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(FlagConfigurationStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CF)
                                           .modulesSupported(Arrays.asList(ModuleType.CF, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.POLICY_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(PolicyStepNode.class)
                   .yamlSchemaMetadata(
                       YamlSchemaMetadata.builder()
                           .namespace(SchemaNamespaceConstants.PMS)
                           .modulesSupported(Arrays.asList(ModuleType.values()))
                           .featureFlags(Collections.singletonList(FeatureName.CUSTOM_POLICY_STEP.name()))
                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                           .build())
                   .build())
          .build();
}
