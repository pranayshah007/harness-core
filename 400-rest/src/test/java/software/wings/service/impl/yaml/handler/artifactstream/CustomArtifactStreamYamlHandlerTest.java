/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.template.artifactsource.CustomRepositoryMapping.AttributeMapping.builder;
import static software.wings.common.TemplateConstants.APP_PREFIX;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_VERSION;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.NameValuePair;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.template.Template;
import software.wings.beans.template.artifactsource.ArtifactSourceTemplate;
import software.wings.beans.template.artifactsource.CustomArtifactSourceTemplate;
import software.wings.beans.template.artifactsource.CustomRepositoryMapping;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class CustomArtifactStreamYamlHandlerTest extends WingsBaseTest {
  private static final String SCRIPT_STRING = "echo Hello World!! and echo ${secrets.getValue(My Secret)}";
  private static final String TEMPLATE_URI = "Harness/My Template";
  private static final String PARENT_FOLDER_UUID = "PARENT_FOLDER_UUID";

  @InjectMocks @Inject private CustomArtifactStreamYamlHandler yamlHandler;
  @Mock private YamlHelper yamlHelper;
  @Mock private TemplateService templateService;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToYamlForCustomArtifactStreamLinkedFromAppTemplate() {
    CustomRepositoryMapping mapping = getCustomRepositoryMapping();
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) createCustomArtifactStreamFromTemplate(mapping);
    CustomArtifactSourceTemplate customArtifactSourceTemplate =
        CustomArtifactSourceTemplate.builder().script(SCRIPT_STRING).customRepositoryMapping(mapping).build();
    Template template = createTemplate(customArtifactSourceTemplate, APP_ID);
    when(templateService.makeNamespacedTemplareUri(TEMPLATE_ID, "LATEST"))
        .thenReturn(APP_PREFIX + TEMPLATE_URI + ":LATEST");
    when(templateService.get(TEMPLATE_ID)).thenReturn(template);
    CustomArtifactStream.Yaml yaml = yamlHandler.toYaml(customArtifactStream, APP_ID);
    assertThat(yaml.getType()).isEqualTo("CUSTOM");
    assertThat(yaml.getTemplateUri()).isEqualTo("App/Harness/My Template:LATEST");
    assertThat(yaml.getTemplateVariables().size()).isEqualTo(1);
    assertThat(yaml.getHarnessApiVersion()).isEqualTo("1.0");
    assertThat(yaml.getScripts()).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToYamlForCustomArtifactStreamLinkedFromGlobalTemplate() {
    CustomRepositoryMapping mapping = getCustomRepositoryMapping();
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) createCustomArtifactStreamFromTemplate(mapping);
    CustomArtifactSourceTemplate customArtifactSourceTemplate =
        CustomArtifactSourceTemplate.builder().script(SCRIPT_STRING).customRepositoryMapping(mapping).build();
    Template template = createTemplate(customArtifactSourceTemplate, GLOBAL_APP_ID);
    when(templateService.makeNamespacedTemplareUri(TEMPLATE_ID, "LATEST")).thenReturn(TEMPLATE_URI + ":LATEST");
    when(templateService.get(TEMPLATE_ID)).thenReturn(template);
    CustomArtifactStream.Yaml yaml = yamlHandler.toYaml(customArtifactStream, APP_ID);
    assertThat(yaml.getType()).isEqualTo("CUSTOM");
    assertThat(yaml.getTemplateUri()).isEqualTo("Harness/My Template:LATEST");
    assertThat(yaml.getTemplateVariables().size()).isEqualTo(1);
    assertThat(yaml.getHarnessApiVersion()).isEqualTo("1.0");
    assertThat(yaml.getScripts()).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testToYamlForCustomArtifactStreamWhenTemplateIdIsMissing() {
    CustomRepositoryMapping mapping = getCustomRepositoryMapping();
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) createCustomArtifactStreamFromTemplate(mapping);
    customArtifactStream.setTemplateUuid(null);
    customArtifactStream.setTemplateVariables(null);
    customArtifactStream.setTemplateVersion(null);
    when(templateService.makeNamespacedTemplareUri(null, "LATEST")).thenReturn(null);
    CustomArtifactStream.Yaml yaml = yamlHandler.toYaml(customArtifactStream, APP_ID);
    assertThat(yaml.getType()).isEqualTo("CUSTOM");
    assertThat(yaml.getTemplateUri()).isNull();
    assertThat(yaml.getTemplateVariables()).isEmpty();
    assertThat(yaml.getHarnessApiVersion()).isEqualTo("1.0");
    assertThat(yaml.getScripts()).isNotNull();
    assertThat(yaml.getScripts()).hasSize(1);
    assertThat(yaml.getScripts().get(0).getTimeout()).isNull();
    assertThat(yaml.getScripts().get(0).getAction()).isEqualTo(CustomArtifactStream.Action.FETCH_VERSIONS);
    assertThat(yaml.getScripts().get(0).getScriptString()).isEqualTo(SCRIPT_STRING);
    assertThat(yaml.getScripts().get(0).getCustomRepositoryMapping()).isEqualTo(mapping);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToBeanForCustomArtifactStreamLinkedFromAppTemplate() {
    CustomRepositoryMapping mapping = getCustomRepositoryMapping();
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) createCustomArtifactStreamFromTemplate(mapping);
    CustomArtifactSourceTemplate customArtifactSourceTemplate =
        CustomArtifactSourceTemplate.builder().script(SCRIPT_STRING).customRepositoryMapping(mapping).build();
    Template template = createTemplate(customArtifactSourceTemplate, APP_ID);
    when(templateService.fetchTemplateUri(TEMPLATE_ID)).thenReturn(TEMPLATE_URI);
    when(templateService.get(TEMPLATE_ID)).thenReturn(template);
    CustomArtifactStream.Yaml baseYaml = CustomArtifactStream.Yaml.builder().harnessApiVersion("1.0").build();
    baseYaml.setTemplateUri("App/" + TEMPLATE_URI);
    baseYaml.setTemplateVariables(asList(NameValuePair.builder().name("path").value("path1").build()));
    baseYaml.setType("CUSTOM");
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: CUSTOM\n"
                                                          + "templateUri: App/Harness/custom test:latest\n"
                                                          + "templateVariables:\n"
                                                          + "- name: path\n"
                                                          + "  value: path1\n")
                                                      .withAccountId(ACCOUNT_ID)
                                                      .withChangeType(MODIFY)
                                                      .build())
                                      .build();
    when(yamlHelper.getNameFromYamlFilePath(eq("Setup/Applications/a1/Services/s1/as1/test.yaml")))
        .thenReturn("test.yaml");
    when(templateService.fetchTemplateIdFromUri(eq(ACCOUNT_ID), eq(APP_ID), eq(TEMPLATE_URI))).thenReturn(TEMPLATE_ID);
    yamlHandler.toBean(customArtifactStream, changeContext, APP_ID);
    assertThat(customArtifactStream.getTemplateVariables().size()).isEqualTo(1);
    assertThat(customArtifactStream.getTemplateVariables().get(0).getValue()).isEqualTo("path1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToBeanForCustomArtifactStreamLinkedFromGlobalTemplate() {
    CustomRepositoryMapping mapping = getCustomRepositoryMapping();
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) createCustomArtifactStreamFromTemplate(mapping);
    CustomArtifactSourceTemplate customArtifactSourceTemplate =
        CustomArtifactSourceTemplate.builder().script(SCRIPT_STRING).customRepositoryMapping(mapping).build();
    Template template = createTemplate(customArtifactSourceTemplate, GLOBAL_APP_ID);
    when(templateService.fetchTemplateUri(TEMPLATE_ID)).thenReturn(TEMPLATE_URI);
    when(templateService.get(TEMPLATE_ID)).thenReturn(template);
    CustomArtifactStream.Yaml baseYaml = CustomArtifactStream.Yaml.builder().harnessApiVersion("1.0").build();
    baseYaml.setTemplateUri(TEMPLATE_URI);
    baseYaml.setTemplateVariables(asList(NameValuePair.builder().name("path").value("path1").build()));
    baseYaml.setType("CUSTOM");
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: CUSTOM\n"
                                                          + "templateUri: Harness/custom test:latest\n"
                                                          + "templateVariables:\n"
                                                          + "- name: path\n"
                                                          + "  value: path1\n")
                                                      .withAccountId(ACCOUNT_ID)
                                                      .withChangeType(MODIFY)
                                                      .build())
                                      .build();
    when(yamlHelper.getNameFromYamlFilePath(eq("Setup/Applications/a1/Services/s1/as1/test.yaml")))
        .thenReturn("test.yaml");
    when(templateService.fetchTemplateIdFromUri(eq(ACCOUNT_ID), eq(TEMPLATE_URI))).thenReturn(TEMPLATE_ID);
    yamlHandler.toBean(customArtifactStream, changeContext, APP_ID);
    assertThat(customArtifactStream.getTemplateVariables().size()).isEqualTo(1);
    assertThat(customArtifactStream.getTemplateVariables().get(0).getValue()).isEqualTo("path1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(CustomArtifactStream.Yaml.class);
  }

  private Template createTemplate(CustomArtifactSourceTemplate customArtifactSourceTemplate, String appId) {
    return Template.builder()
        .type("ARTIFACT_SOURCE")
        .templateObject(ArtifactSourceTemplate.builder().artifactSource(customArtifactSourceTemplate).build())
        .folderId(PARENT_FOLDER_UUID)
        .appId(appId)
        .accountId(GLOBAL_ACCOUNT_ID)
        .name("Custom Artifact Template 1")
        .variables(asList(aVariable().type(TEXT).name("path").mandatory(true).build()))
        .build();
  }

  private CustomRepositoryMapping getCustomRepositoryMapping() {
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("path").mappedAttribute("${path}").build());
    return CustomRepositoryMapping.builder()
        .artifactRoot("$.results")
        .buildNoPath("name")
        .artifactAttributes(attributeMapping)
        .build();
  }

  @NotNull
  private ArtifactStream createCustomArtifactStreamFromTemplate(CustomRepositoryMapping mapping) {
    ArtifactStream customArtifactStream =
        CustomArtifactStream.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .serviceId(SERVICE_ID)
            .name("Custom Artifact Stream" + System.currentTimeMillis())
            .scripts(Arrays.asList(CustomArtifactStream.Script.builder()
                                       .action(CustomArtifactStream.Action.FETCH_VERSIONS)
                                       .scriptString(SCRIPT_STRING)
                                       .customRepositoryMapping(mapping)
                                       .build()))
            .build();
    customArtifactStream.setTemplateUuid(TEMPLATE_ID);
    customArtifactStream.setTemplateVersion(TEMPLATE_VERSION);
    customArtifactStream.setTemplateVariables(asList(aVariable().name("path").value("path").type(TEXT).build()));
    return customArtifactStream;
  }
}
