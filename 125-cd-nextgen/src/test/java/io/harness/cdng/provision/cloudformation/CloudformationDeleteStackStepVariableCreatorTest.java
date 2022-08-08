/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.variables.StepVariableCreatorTestUtils;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationDeleteStepVariableCreator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationDeleteStackStepVariableCreatorTest extends CategoryTest {
  private final CloudformationDeleteStepVariableCreator deleteStepVariableCreator =
      new CloudformationDeleteStepVariableCreator();

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(deleteStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(deleteStepVariableCreator.getFieldClass()).isEqualTo(CloudformationDeleteStackStepNode.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2DeleteInline() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineDeleteStackInline.json", deleteStepVariableCreator,
        CloudformationDeleteStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.configuration.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.configuration.spec.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.configuration.spec.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.configuration.spec.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.timeout");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2DeleteInherit() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineDeleteStackInherit.json", deleteStepVariableCreator,
        CloudformationDeleteStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.configuration.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.cfdeletestack.timeout");
  }
}
