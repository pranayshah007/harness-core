/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.ssca.cd.beans.orchestration.CdSscaOrchestrationStepNode;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import java.util.Collections;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaBeansRegistrar {
  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.CD_SSCA_ORCHESTRATION)
                   .clazz(CdSscaOrchestrationStepNode.class)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.PMS)
                                           .modulesSupported(ImmutableList.of(ModuleType.CD))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .featureFlags(Collections.singletonList(FeatureName.SSCA_ENABLED.name()))
                                           .build())
                   .build())
          .build();
}
