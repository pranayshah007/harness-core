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
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.INVALID_COMMAND_TEMPLATE_VALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.INVALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.VALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.commandTemplateForSetup;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.commandTemplateName;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.expectedReturnCommandTemplate;
import static software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibaryYamlConstants.rootTemplateFolder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.template.Template;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.intfc.template.TemplateService;
import software.wings.yaml.templatelibrary.CommandTemplateYaml;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CommandTemplateYamlHandlerTest extends TemplateLibraryYamlHandlerTestBase {
  @InjectMocks @Inject private CommandTemplateYamlHandler yamlHandler;
  @Mock private TemplateService templateService;
  @Mock private YamlHandlerFactory yamlHandlerFactory;

  @Before
  public void runBeforeTest() {
    MockitoAnnotations.initMocks(this);
    setup(COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, commandTemplateName);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCRUDAndGetForExistingTemplate() throws Exception {
    when(yamlHelper.ensureTemplateFolder(
             GLOBAL_ACCOUNT_ID, COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, GLOBAL_APP_ID, TEMPLATE_GALLERY_UUID))
        .thenReturn(rootTemplateFolder);
    when(templateService.findByFolder(rootTemplateFolder, commandTemplateName, GLOBAL_APP_ID))
        .thenReturn(commandTemplateForSetup);
    when(templateService.update(any())).thenReturn(expectedReturnCommandTemplate);
    ChangeContext<CommandTemplateYaml> changeContext =
        getChangeContext(VALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE, COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, yamlHandler);
    CommandTemplateYaml yamlObject =
        (CommandTemplateYaml) getYaml(VALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE, CommandTemplateYaml.class);
    changeContext.setYaml(yamlObject);
    changeContext.getChange().setAccountId(GLOBAL_ACCOUNT_ID);
    Template template = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(template).isNotNull();
    assertThat(template.getTemplateObject()).isNotNull();
    assertThat(template.getName()).isEqualTo(commandTemplateName);

    CommandTemplateYaml yaml = yamlHandler.toYaml(template, GLOBAL_APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo(SSH);

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();

    Template savedTemplate = yamlHandler.get(GLOBAL_ACCOUNT_ID, COMMAND_TEMPLATE_VALID_YAML_FILE_PATH);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getName()).isEqualTo(commandTemplateName);

    yamlHandler.delete(changeContext);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testFailures() throws IOException {
    testFailures(VALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE, COMMAND_TEMPLATE_VALID_YAML_FILE_PATH,
        INVALID_COMMAND_TEMPLATE_WITHOUT_VARIABLE, INVALID_COMMAND_TEMPLATE_VALID_YAML_FILE_PATH, yamlHandler,
        CommandTemplateYaml.class);
  }
}
