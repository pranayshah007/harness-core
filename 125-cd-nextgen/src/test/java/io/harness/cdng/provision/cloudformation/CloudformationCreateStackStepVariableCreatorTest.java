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
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationCreateStepVariableCreator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationCreateStackStepVariableCreatorTest extends CategoryTest {
  private final CloudformationCreateStepVariableCreator createStepVariableCreator =
      new CloudformationCreateStepVariableCreator();

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(createStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(createStepVariableCreator.getFieldClass()).isEqualTo(CloudformationCreateStackStepNode.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2CreateS3URL() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineCreateStackS3URL.json", createStepVariableCreator,
        CloudformationCreateStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.capabilities",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.timeout",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.templateFile.spec.templateUrl",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.skipOnStackStatuses",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStackId.spec.configuration.connectorRef");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2CreateS3URLWithGitParameters() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineCreateStackS3URLWithGitParameters.json", createStepVariableCreator,
        CloudformationCreateStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.capabilities",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.timeout",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.templateUrl",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.skipOnStackStatuses",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.branch",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.paths",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.commitId",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.folderPath",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.repoName");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2CreateS3URLWithS3Parameters() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineCreateStackS3URLWithS3Parameters.json", createStepVariableCreator,
        CloudformationCreateStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.capabilities",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.timeout",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.templateUrl",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.skipOnStackStatuses",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.urls");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2CreateRemote() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineCreateStackRemote.json", createStepVariableCreator,
        CloudformationCreateStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.capabilities",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.timeout",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.branch",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.paths",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.commitId",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.folderPath",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.store.spec.repoName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.skipOnStackStatuses",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.urls");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2CreateInline() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineCreateStackInline.json", createStepVariableCreator,
        CloudformationCreateStackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.capabilities",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.timeout",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.name",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.description",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.delegateSelectors",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.provisionerIdentifier",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.roleArn",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.templateFile.spec.templateBody",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.stackName",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.skipOnStackStatuses",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.connectorRef",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.region",
            "pipeline.stages.foo.spec.infrastructure.infrastructureDefinition.provisioner.steps.createStack.spec.configuration.parameters.parametersid1.store.spec.urls");
  }
}
