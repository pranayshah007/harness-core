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
import io.harness.cf.pipeline.FeatureFlagStageNode;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.plancreator.steps.email.EmailStepNode;
import io.harness.plancreator.steps.http.HttpStepNode;
import io.harness.plancreator.steps.internal.FlagConfigurationStepNode;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.plancreator.steps.resourceconstraint.QueueStepNode;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateServiceBeansKryoRegistrar;
import io.harness.serializer.kryo.NGCoreBeansKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationStepsContractKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationStepsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationStepsContractMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationStepsMorphiaRegistrar;
import io.harness.ssca.cd.CdSscaBeansRegistrar;
import io.harness.steps.approval.stage.ApprovalStageNode;
import io.harness.steps.approval.step.custom.CustomApprovalStepNode;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.jira.JiraApprovalStepNode;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepNode;
import io.harness.steps.customstage.CustomStageNode;
import io.harness.steps.jira.create.JiraCreateStepNode;
import io.harness.steps.jira.update.JiraUpdateStepNode;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.steps.plugin.ContainerStepNode;
import io.harness.steps.policy.PolicyStepNode;
import io.harness.steps.servicenow.create.ServiceNowCreateStepNode;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStepNode;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepNode;
import io.harness.steps.shellscript.ShellScriptStepNode;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.steps.wait.WaitStepNode;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import java.util.Arrays;
import java.util.Collections;
import lombok.experimental.UtilityClass;
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
          .add(OrchestrationStepsContractKryoRegistrar.class)
          .addAll(PmsCommonsModuleRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .addAll(NGCoreClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .addAll(ContainerRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(OrchestrationStepsMorphiaRegistrar.class)
          .add(NotificationBeansMorphiaRegistrar.class)
          .add(OrchestrationStepsContractMorphiaRegistrar.class)
          .addAll(OrchestrationRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
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
          .addAll(CdSscaBeansRegistrar.yamlSchemaRegistrars)
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
                   .entityType(EntityType.PIPELINE_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(PipelineStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PIPELINE)
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
                   .entityType(EntityType.EMAIL_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(EmailStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.QUEUE_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(QueueStepNode.class)
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
                   .entityType(EntityType.CUSTOM_APPROVAL_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(CustomApprovalStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.APPROVAL)
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
                                               ModuleType.CF, ModuleType.CI, ModuleType.STO))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.TEMPLATE_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(TemplateStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Collections.singletonList(ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STAGE.name()).build())
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
                   .entityType(EntityType.SERVICENOW_IMPORT_SET_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ServiceNowImportSetStepNode.class)
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
          .add(
              YamlSchemaRootClass.builder()
                  .entityType(EntityType.POLICY_STEP)
                  .availableAtProjectLevel(true)
                  .availableAtOrgLevel(false)
                  .availableAtAccountLevel(false)
                  .clazz(PolicyStepNode.class)
                  .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                          .namespace(SchemaNamespaceConstants.PMS)
                                          .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.CI, ModuleType.CF,
                                              ModuleType.CE, ModuleType.PMS, ModuleType.STO, ModuleType.TEMPLATESERVICE,
                                              ModuleType.CV, ModuleType.CHAOS, ModuleType.GOVERNANCE))
                                          .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                          .build())
                  .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.STRATEGY_NODE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(StrategyConfig.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STRATEGY.name()).build())
                                           .build())
                   .build())
          .add(
              YamlSchemaRootClass.builder()
                  .entityType(EntityType.WAIT_STEP)
                  .availableAtProjectLevel(true)
                  .availableAtOrgLevel(false)
                  .availableAtAccountLevel(false)
                  .clazz(WaitStepNode.class)
                  .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                          .namespace(SchemaNamespaceConstants.PMS)
                                          .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS, ModuleType.CI))
                                          .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                          .build())
                  .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.CONTAINER_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(ContainerStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(Arrays.asList(ModuleType.CD, ModuleType.PMS))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .build();
}
