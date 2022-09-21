/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateInputsRefreshHelper;
import io.harness.template.helpers.TemplateMergeServiceHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import io.harness.template.helpers.TemplateYamlSchemaMergeHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
@PrepareForTest({TemplateYamlSchemaMergeHelper.class})
public class TemplateInputsRefreshHelperTest extends TemplateServiceTestBase {
  @Mock private NGTemplateServiceHelper templateServiceHelper;

  @InjectMocks TemplateInputsRefreshHelper templateInputsRefreshHelper;
  @InjectMocks TemplateMergeServiceHelper templateMergeServiceHelper;

  @Mock private AccountClient accountClient;

  private static final String ACCOUNT_ID = "accountId";

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setup() throws IllegalAccessException {
    on(templateMergeServiceHelper).set("templateServiceHelper", templateServiceHelper);
    on(templateInputsRefreshHelper).set("templateMergeServiceHelper", templateMergeServiceHelper);
    Mockito.mockStatic(TemplateYamlSchemaMergeHelper.class);
    PowerMockito
            .when(
                    TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(FeatureName.NG_TEMPLATE_VARIABLES, null, accountClient))
            .thenReturn(false);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForIncreasedRuntimeInputs() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "stage-template-with-two-runtime-inputs.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projId)
                                        .yaml(stageTemplate)
                                        .identifier("t4")
                                        .deleted(false)
                                        .versionLabel("v1")
                                        .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-one-runtime-input.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-two-runtime-inputs.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForDecreasedRuntimeInputs() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "stage-template-with-one-runtime-input-2.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projId)
                                        .yaml(stageTemplate)
                                        .identifier("t4")
                                        .deleted(false)
                                        .versionLabel("v1")
                                        .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-two-runtime-inputs.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-one-runtime-input.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForNoRuntimeInputs() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "stage-template-with-zero-runtime-inputs.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projId)
                                        .yaml(stageTemplate)
                                        .identifier("t4")
                                        .deleted(false)
                                        .versionLabel("v1")
                                        .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-two-runtime-inputs.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-zero-runtime-inputs.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForAddedRuntimeInputs() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "stage-template-with-two-runtime-inputs.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projId)
                                        .yaml(stageTemplate)
                                        .identifier("t4")
                                        .deleted(false)
                                        .versionLabel("v1")
                                        .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-zero-runtime-inputs.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-two-runtime-inputs.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForNoRuntimeInputsInTemplatesAndPipelineBoth() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "stage-template-with-zero-runtime-inputs.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projId)
                                        .yaml(stageTemplate)
                                        .identifier("t4")
                                        .deleted(false)
                                        .versionLabel("v1")
                                        .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-zero-runtime-inputs.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-zero-runtime-inputs.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForIncreasedInputTemplateVariables() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "step-template-with-variables.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .yaml(stageTemplate)
            .identifier("t4")
            .deleted(false)
            .versionLabel("v1")
            .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
            .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-two-runtime-inputs.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-two-variable-and-input.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    PowerMockito
            .when(
                    TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(FeatureName.NG_TEMPLATE_VARIABLES, accountId, accountClient))
            .thenReturn(true);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRefreshTemplatesForDecreasedInputTemplateVariables() {
    String accountId = ACCOUNT_ID;
    String orgId = "default";
    String projId = "VT";

    String filename = "step-template-with-one-runtime-variable.yaml";
    String stageTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .yaml(stageTemplate)
            .identifier("t4")
            .deleted(false)
            .versionLabel("v1")
            .build();

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(accountId, orgId, projId, "t4", "v1", false))
            .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-two-variable-and-input.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    String expectedPipelineYamlFile = "pipeline-with-variable-two-input.yaml";
    String expectedPipelineYaml = readFile(expectedPipelineYamlFile);

    PowerMockito
            .when(
                    TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(FeatureName.NG_TEMPLATE_VARIABLES, accountId, accountClient))
            .thenReturn(true);

    String refreshedYaml = templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projId, pipelineYaml);

    YamlNode yamlNode = null, expectedYamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(refreshedYaml).getNode();
      expectedYamlNode = YamlUtils.readTree(expectedPipelineYaml).getNode();
    } catch (IOException e) {
    }

    assertThat(yamlNode).isEqualTo(expectedYamlNode);
  }
}
