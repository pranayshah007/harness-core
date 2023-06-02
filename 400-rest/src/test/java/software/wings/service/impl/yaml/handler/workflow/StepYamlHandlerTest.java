/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.GraphNode;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.command.CommandType;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;
import software.wings.sm.StepType;
import software.wings.utils.RepositoryFormat;
import software.wings.yaml.handler.YamlHandlerTestBase;
import software.wings.yaml.workflow.StepYaml;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._955_CG_YAML)
public class StepYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private StepYamlHandler stepYamlHandler;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private YamlHelper yamlHelper;
  private NexusArtifactStream nexusArtifactStream;
  @Mock private TemplateService templateService;

  @Before
  public void setUp() {
    nexusArtifactStream = getNexusArtifactStream();
    Application application = Application.Builder.anApplication().name("a1").uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, "a1")).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getAppId(ACCOUNT_ID, "Setup/Applications/a1/Workflows/build.yaml")).thenReturn(APP_ID);
    when(serviceResourceService.getServiceByName(APP_ID, "s1")).thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(artifactStreamService.getArtifactStreamByName(APP_ID, SERVICE_ID, "test")).thenReturn(nexusArtifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToBeanWithoutBuildNo() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("regex", false);
    properties.put("artifactStreamName", "test");
    properties.put("serviceName", "s1");
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "npm-internal");
    runtimeValues.put("package", "npm-app1");
    properties.put("runtimeValues", runtimeValues);
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder()
                          .name("Artifact Collection")
                          .properties(properties)
                          .type(StateType.ARTIFACT_COLLECTION.name())
                          .build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    try {
      stepYamlHandler.upsertFromYaml(changeContext, null);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
      assertThat(e.getMessage()).contains("buildNo not provided");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToBeanWithRegex() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("regex", true);
    properties.put("artifactStreamName", "test");
    properties.put("serviceName", "s1");
    properties.put("buildNo", "1.0.0");
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "npm-internal");
    runtimeValues.put("package", "npm-app1");
    properties.put("runtimeValues", runtimeValues);
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder()
                          .name("Artifact Collection")
                          .properties(properties)
                          .type(StateType.ARTIFACT_COLLECTION.name())
                          .build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    try {
      stepYamlHandler.upsertFromYaml(changeContext, null);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
      assertThat(e.getMessage()).contains("Regex cannot be set for parameterized artifact source");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToBeanWithoutRuntimeValues() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("buildNo", "1.0.0");
    properties.put("artifactStreamName", "test");
    properties.put("serviceName", "s1");
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "npm-internal");
    properties.put("runtimeValues", runtimeValues);
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder()
                          .name("Artifact Collection")
                          .properties(properties)
                          .type(StateType.ARTIFACT_COLLECTION.name())
                          .build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    when(artifactStreamService.getArtifactStreamParameters(ARTIFACT_STREAM_ID)).thenReturn(asList("repo", "package"));
    try {
      stepYamlHandler.upsertFromYaml(changeContext, null);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
      assertThat(e.getMessage()).contains("runtime values not provided");
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testToBeanWithoutArtifactStreamId() {
    Map<String, Object> properties = new HashMap<>();
    Map<String, Object> templateExpressions = new HashMap<>();
    templateExpressions.put("expression", "${ArtS");
    templateExpressions.put("fieldName", "artifactStreamId");
    properties.put("templateExpressions", Collections.singletonList(templateExpressions));
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder()
                          .name("Artifact Collection")
                          .properties(properties)
                          .templateExpressions(asList(TemplateExpression.Yaml.Builder.aYaml()
                                                          .withExpression("${ArtS")
                                                          .withFieldName("artifactStreamId")
                                                          .build()))
                          .type(StateType.ARTIFACT_COLLECTION.name())
                          .build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    when(artifactStreamService.getArtifactStreamParameters(ARTIFACT_STREAM_ID)).thenReturn(asList("repo", "package"));
    assertThatThrownBy(() -> stepYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template variable:[${ArtS] is not valid, should start with ${ and end with }, can have a-z,A-Z,0-9,-_");
  }

  private NexusArtifactStream getNexusArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .packageName("${package}")
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .repositoryFormat(RepositoryFormat.npm.name())
                                                  .name("test")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    return nexusArtifactStream;
  }

  /*
  This test is used to check that if the template of Service Command is used in Workflow Step,
  Then commandType = OTHER is ensured in properties in Step Yaml.This is due to check on UI for displaying command
  Template. (CDC-6787)
   */
  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testToYamlAddCommandTypeWhenUsingServiceCommandTemplate() {
    GraphNode aStep = GraphNode.builder()
                          .type(StepType.COMMAND.name())
                          .templateUuid(TEMPLATE_ID)
                          .name("serviceCommandTemplateStep")
                          .build();

    SshCommandTemplate sshCommandTemplate = SshCommandTemplate.builder().commandType(CommandType.OTHER).build();

    // Template of type SSH (Service Command) will have property commandType = OTHER in yaml. This test is due to
    // condition on UI.
    Template template = Template.builder()
                            .name("temp")
                            .appId(APP_ID)
                            .type(TemplateType.SSH.name())
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .templateObject(sshCommandTemplate)
                            .build();

    when(templateService.makeNamespacedTemplareUri(any(), any())).thenReturn("TEMPLATE_URI");
    when(templateService.get(TEMPLATE_ID)).thenReturn(template);

    StepYaml yaml = stepYamlHandler.toYaml(aStep, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getProperties().get("commandType")).isEqualTo(CommandType.OTHER.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForToYamlWithEmptyName() {
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder().type("STEP_TYPE").name("").build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    assertThatThrownBy(() -> stepYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name is empty for STEP_TYPE step");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForYamlWithDotInShellScriptName() {
    ChangeContext<StepYaml> changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.WORKFLOW)
            .withYaml(StepYaml.builder().name("a.b").type(StepType.SHELL_SCRIPT.name()).build())
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath("Setup/Applications/a1/Workflows/build.yaml")
                            .withAccountId(ACCOUNT_ID)
                            .build())
            .build();

    assertThatThrownBy(() -> stepYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Shell script step [a.b] has '.' in its name");
  }
}
