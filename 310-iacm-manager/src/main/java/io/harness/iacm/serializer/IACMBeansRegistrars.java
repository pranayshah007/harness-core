/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.serializer;

import static io.harness.beans.FeatureName.IACM_ENABLED;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.iacm.stages.IACMStageNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IACM)
@UtilityClass
public class IACMBeansRegistrars {
  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.IACM_STAGE)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(IACMStageNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .featureRestrictions(ImmutableList.of(IACM_ENABLED.name()))
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
                                           .featureRestrictions(ImmutableList.of(IACM_ENABLED.name()))
                                           .modulesSupported(ImmutableList.of(ModuleType.IACM))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .availableAtAccountLevel(false)
                   .clazz(PluginStepNode.class)
                   .build())
          .build();
}
