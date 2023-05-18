/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.templatelibrary;

import static io.harness.rule.OwnerRule.ABHINAV;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.TemplateConstants.SSH;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.COMMAND_TEMPLATE_VALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.EXEC_COMMAND_UNIT;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.REF_COMMAND_TEMPLATE_VALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.REF_COMMAND_UNIT;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.commandRefUuid;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.commandTemplateRefUri;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.commandTemplateUri;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.expectedCommandTemplate;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.expectedReturnCommandTemplate;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.variable;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.BaseYaml;

import software.wings.beans.command.Command;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateReference;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.command.ExecCommandUnitYamlHandler;
import software.wings.service.intfc.template.TemplateService;
import software.wings.yaml.command.CommandRefYaml;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CommandTemplateRefYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHandlerFactory yamlHandlerFactory;
  @Mock private TemplateService templateService;
  @InjectMocks @Inject private CommandTemplateRefYamlHandler commandTemplateRefYamlHandler;
  @Mock private ExecCommandUnitYamlHandler execCommandUnitYamlHandler;
  @Mock private TemplateLibraryYamlHandler yamlHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToBean() throws IOException {
    when(yamlHandlerFactory.getYamlHandler(YamlType.GLOBAL_TEMPLATE_LIBRARY, SSH)).thenReturn(yamlHandler);
    when(yamlHandler.upsertFromYaml(any(), any())).thenReturn(null);
    when(templateService.get(any())).thenReturn(Template.builder().version(1).build());

    ChangeContext<CommandRefYaml> changeContext =
        getChangeContext(REF_COMMAND_UNIT, REF_COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, commandTemplateRefYamlHandler);
    CommandRefYaml yamlObject = (CommandRefYaml) getYaml(REF_COMMAND_UNIT, CommandRefYaml.class);
    changeContext.setYaml(yamlObject);
    changeContext.getChange().setAccountId(GLOBAL_ACCOUNT_ID);

    ChangeContext<ExecCommandUnit.Yaml> changeContextForSecond =
        getChangeContext(EXEC_COMMAND_UNIT, COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, execCommandUnitYamlHandler);
    ExecCommandUnit.Yaml yamlObjectForSecond =
        (ExecCommandUnit.Yaml) getYaml(EXEC_COMMAND_UNIT, ExecCommandUnit.Yaml.class);
    changeContextForSecond.setYaml(yamlObjectForSecond);
    changeContextForSecond.getChange().setAccountId(GLOBAL_ACCOUNT_ID);

    List<ChangeContext> changeSetContext = Arrays.asList(changeContext, changeContextForSecond);

    when(templateService.fetchTemplateFromUri(
             commandTemplateUri, changeContext.getChange().getAccountId(), GLOBAL_APP_ID))
        .thenReturn(null, expectedCommandTemplate);
    when(templateService.fetchTemplateFromUri(
             commandTemplateRefUri, changeContextForSecond.getChange().getAccountId(), GLOBAL_APP_ID))
        .thenReturn(expectedReturnCommandTemplate);
    when(templateService.fetchTemplateVersionFromUri(any(), any())).thenReturn("1");
    assertThat(commandTemplateRefYamlHandler.upsertFromYaml(changeContext, changeSetContext)).isNotNull();
  }

  protected <Y extends BaseYaml, H extends BaseYamlHandler> ChangeContext<Y> getChangeContext(
      String yamlContent, String yamlFilePath, H yamlHandler) {
    // Invalid yaml path
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(yamlContent)
                                      .build();

    ChangeContext<Y> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.GLOBAL_TEMPLATE_LIBRARY);
    changeContext.setYamlSyncHandler(yamlHandler);
    return changeContext;
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToYaml() {
    when(templateService.fetchTemplateUri(commandRefUuid)).thenReturn(commandTemplateRefUri);
    when(templateService.get(commandRefUuid)).thenReturn(expectedCommandTemplate);
    when(templateService.makeNamespacedTemplareUri(commandRefUuid, "2")).thenReturn(commandTemplateRefUri);
    Command refCommand =
        Command.Builder.aCommand()
            .withName("Child")
            .withTemplateReference(
                TemplateReference.builder().templateVersion((long) 2).templateUuid(commandRefUuid).build())
            .withTemplateVariables(Arrays.asList(variable))
            .build();
    refCommand.setReferenceUuid(commandRefUuid);
    CommandRefYaml yaml = commandTemplateRefYamlHandler.toYaml(refCommand, GLOBAL_APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getTemplateUri()).contains(commandTemplateRefUri);
    assertThat(yaml.getVariables()).isNotNull();
  }
}
