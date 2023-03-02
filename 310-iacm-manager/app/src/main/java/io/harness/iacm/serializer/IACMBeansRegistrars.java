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
import io.harness.iacm.IACMStepType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IACM)
@UtilityClass
public class IACMBeansRegistrars {
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
