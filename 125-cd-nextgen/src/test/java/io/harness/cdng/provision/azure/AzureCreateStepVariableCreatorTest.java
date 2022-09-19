/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.variables.StepVariableCreatorTestUtils;
import io.harness.cdng.provision.azure.variablecreator.AzureARMRollbackStepVariableCreator;
import io.harness.cdng.provision.azure.variablecreator.AzureCreateARMResourceStepVariableCreator;
import io.harness.cdng.provision.azure.variablecreator.AzureCreateBPStepVariableCreator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateStepVariableCreatorTest extends CategoryTest {
  private final AzureCreateARMResourceStepVariableCreator creator = new AzureCreateARMResourceStepVariableCreator();
  private final AzureCreateBPStepVariableCreator bpcreator = new AzureCreateBPStepVariableCreator();

  private final AzureARMRollbackStepVariableCreator rollbackStepVariableCreator =
      new AzureARMRollbackStepVariableCreator();

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(creator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(creator.getFieldClass()).isEqualTo(AzureCreateARMResourceStepNode.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMResourceGroup() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateARMResourceRemoteTemplateParametersRG.json", creator,
        AzureCreateARMResourceStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.paths",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.subscription",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.resourceGroup",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.paths");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMSubscription() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateARMResourceRemoteTemplateParametersSUS.json", creator,
        AzureCreateARMResourceStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.location",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.subscription",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.paths",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.paths");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMManagement() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateARMResourceRemoteTemplateParametersMNG.json", creator,
        AzureCreateARMResourceStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.location",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.managementGroupId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.paths",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.paths");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMTenant() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateARMResourceRemoteTemplateParametersTenant.json", creator,
        AzureCreateARMResourceStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.location",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.paths",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.paths");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureBP() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateBPRemoteTemplateTemplate.json", bpcreator, AzureCreateBPStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(

            "pipeline.stages.aaa.spec.execution.steps.aaa.name", "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.paths",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.folderPath",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.branch",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.repoName",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.commitId",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.assignmentName");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMRollback() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureARMRollback.json", rollbackStepVariableCreator, AzureARMRollbackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2AzureARMInline() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineAzureCreateARMResourceInline.json", creator, AzureCreateARMResourceStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.aaa.spec.execution.steps.aaa.name",
            "pipeline.stages.aaa.spec.execution.steps.aaa.timeout",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.provisionerIdentifier",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.delegateSelectors",
            "pipeline.stages.aaa.spec.execution.steps.aaa.description",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.files",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.parameters.store.spec.secretFiles",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.files",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.template.store.spec.secretFiles",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.connectorRef",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.subscription",
            "pipeline.stages.aaa.spec.execution.steps.aaa.spec.configuration.scope.spec.resourceGroup");
  }
}
