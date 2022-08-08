/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.variables.StepVariableCreatorTestUtils;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationRollbackStepVariableCreator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationRollbackStackStepVariableCreatorTest extends CategoryTest {
  private final CloudformationRollbackStepVariableCreator rollbackStepVariableCreator =
      new CloudformationRollbackStepVariableCreator();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(rollbackStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(rollbackStepVariableCreator.getFieldClass()).isEqualTo(CloudformationRollbackStepNode.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2RollbackStack() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraRollbackFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineRollbackStack.json", rollbackStepVariableCreator, CloudformationRollbackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo2.spec.infrastructure.infrastructureDefinition.provisioner.rollbackSteps.tfrg.spec.configuration.provisionerIdentifier",
            "pipeline.stages.foo2.spec.infrastructure.infrastructureDefinition.provisioner.rollbackSteps.tfrg.spec.delegateSelectors",
            "pipeline.stages.foo2.spec.infrastructure.infrastructureDefinition.provisioner.rollbackSteps.tfrg.timeout",
            "pipeline.stages.foo2.spec.infrastructure.infrastructureDefinition.provisioner.rollbackSteps.tfrg.name",
            "pipeline.stages.foo2.spec.infrastructure.infrastructureDefinition.provisioner.rollbackSteps.tfrg.description");
  }
}
