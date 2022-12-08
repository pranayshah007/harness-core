/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.template;

import static io.harness.generator.AccountGenerator.adminUserEmail;
import static io.harness.generator.AccountGenerator.readOnlyEmail;
import static io.harness.generator.TemplateFolderGenerator.TemplateFolders.APP_FOLDER_SHELL_SCRIPTS;
import static io.harness.generator.TemplateFolderGenerator.TemplateFolders.TEMPLATE_FOLDER_SHELL_SCRIPTS;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.TemplateFolderGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.framework.utils.TestUtils;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.template.TemplateFolderService;

import com.google.inject.Inject;
import io.restassured.http.ContentType;
import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.GenericType;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class TemplateFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private TemplateFolderGenerator templateFolderGenerator;
  @Inject private TemplateFolderService templateFolderService;
  Workflow buildWorkflow;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  final String SCRIPT_TEMPLATE_NAME = "Another Sample Shell Script";
  final String SCRIPT_NAME1 = "Another Sample Shell Script - 1";
  final String SCRIPT_NAME2 = "Another Sample Shell Script - 2";
  final String SCRIPT_NAME3 = "Another Sample Shell Script - 3";
  OwnerManager.Owners owners;
  Application application;
  Account account;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));
    account = owners.obtainAccount();
    if (account == null) {
      account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    }
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldExecuteShellScriptTemplateWorkflow() {
    GenericType<RestResponse<WorkflowExecution>> workflowExecutionType =
        new GenericType<RestResponse<WorkflowExecution>>() {

        };

    buildWorkflow = workflowGenerator.ensurePredefined(seed, owners, WorkflowGenerator.Workflows.BUILD_SHELL_SCRIPT);

    assertThat(buildWorkflow).isNotNull();

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), null, buildWorkflow.getUuid(),
        Collections.<Artifact>emptyList()); // workflowExecutionRestResponse.getResource();
    assertThat(workflowExecution).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void createUpdateDeleteShellScriptTemplate() {
    // Create template
    TemplateFolder parentFolder =
        templateFolderGenerator.ensurePredefined(seed, owners, TEMPLATE_FOLDER_SHELL_SCRIPTS, GLOBAL_APP_ID);
    ShellScriptTemplate shellScriptTemplate = ShellScriptTemplate.builder()
                                                  .scriptType(ScriptType.BASH.name())
                                                  .scriptString("echo \"Hello\" ${name}\n"
                                                      + "export A=\"aaa\"\n"
                                                      + "export B=\"bbb\"")
                                                  .outputVars("A,B")
                                                  .build();
    Template template = Template.builder()
                            .type(TemplateType.SHELL_SCRIPT.name())
                            .accountId(account.getUuid())
                            .name(SCRIPT_TEMPLATE_NAME)
                            .templateObject(shellScriptTemplate)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .variables(asList(aVariable().type(TEXT).name("name").mandatory(true).build()))
                            .build();
    GenericType<RestResponse<Template>> templateType = new GenericType<RestResponse<Template>>() {

    };

    RestResponse<Template> savedTemplateResponse = saveTemplate(template, templateType, bearerToken);

    Template savedTemplate = savedTemplateResponse.getResource();

    assertTemplate(savedTemplate, SCRIPT_TEMPLATE_NAME, 1L);

    // Get template and validate template object and variables
    savedTemplateResponse = saveShellScriptTemplate(templateType, savedTemplate, bearerToken);

    savedTemplate = savedTemplateResponse.getResource();
    assertTemplate(savedTemplate, SCRIPT_TEMPLATE_NAME, 1L);
    assertThat(savedTemplate.getTemplateObject()).isNotNull();
    shellScriptTemplate = (ShellScriptTemplate) savedTemplate.getTemplateObject();

    assertThat(shellScriptTemplate.getOutputVars()).isEqualTo("A,B");
    assertThat(shellScriptTemplate.getTimeoutMillis()).isEqualTo(600000);
    assertThat(shellScriptTemplate.getScriptString())
        .isEqualTo("echo \"Hello\" ${name}\n"
            + "export A=\"aaa\"\n"
            + "export B=\"bbb\"");

    // update template and validate
    shellScriptTemplate = ShellScriptTemplate.builder()
                              .scriptType(ScriptType.BASH.name())
                              .scriptString("echo \"Hello\" ${name}\n"
                                  + "export A=\"aaa\"\n"
                                  + "export B=\"bbb\"\n"
                                  + "export C=\"ccc\"")
                              .outputVars("A,B,C")
                              .build();
    template = Template.builder()
                   .type(TemplateType.SHELL_SCRIPT.name())
                   .accountId(account.getUuid())
                   .name(SCRIPT_TEMPLATE_NAME)
                   .templateObject(shellScriptTemplate)
                   .folderId(parentFolder.getUuid())
                   .appId(GLOBAL_APP_ID)
                   .variables(asList(aVariable().type(TEXT).name("name").mandatory(true).build()))
                   .version(savedTemplate.getVersion())
                   .build();
    savedTemplateResponse = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .contentType(ContentType.JSON)
                                .body(template)
                                .queryParam("accountId", account.getUuid())
                                .pathParam("templateId", savedTemplate.getUuid())
                                .put("/templates/{templateId}")
                                .as(templateType.getType());
    savedTemplate = savedTemplateResponse.getResource();
    assertTemplate(savedTemplate, SCRIPT_TEMPLATE_NAME, 2L);

    assertThat(savedTemplate.getTemplateObject()).isNotNull();
    shellScriptTemplate = (ShellScriptTemplate) savedTemplate.getTemplateObject();
    assertThat(shellScriptTemplate.getOutputVars()).isEqualTo("A,B,C");
    assertThat(shellScriptTemplate.getTimeoutMillis()).isEqualTo(600000);
    assertThat(shellScriptTemplate.getScriptString())
        .isEqualTo("echo \"Hello\" ${name}\n"
            + "export A=\"aaa\"\n"
            + "export B=\"bbb\"\n"
            + "export C=\"ccc\"");

    // Delete template
    deleteTemplate(bearerToken, account.getUuid(), savedTemplate.getUuid(), 200);

    // Make sure that it is deleted
    savedTemplateResponse = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .queryParam("accountId", account.getUuid())
                                .queryParam("version", "1")
                                .pathParam("templateId", savedTemplate.getUuid())
                                .get("/templates/{templateId}")
                                .as(templateType.getType());
    assertThat(savedTemplateResponse.getResource()).isNull();
  }

  private RestResponse<Template> saveShellScriptTemplate(
      GenericType<RestResponse<Template>> templateType, Template savedTemplate, String bearerToken) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .contentType(ContentType.JSON)
        .queryParam("accountId", account.getUuid())
        .pathParam("templateId", savedTemplate.getUuid())
        .get("/templates/{templateId}")
        .as(templateType.getType());
  }

  private void assertTemplate(Template savedTemplate, String script_template_name, long l) {
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getUuid()).isNotEmpty();
    assertThat(savedTemplate.getName()).isEqualTo(script_template_name);
    assertThat(savedTemplate.getType()).isEqualTo(TemplateType.SHELL_SCRIPT.name());
    assertThat(savedTemplate.getVersion()).isEqualTo(l);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testCRUDTemplateRBAC() {
    String readOnlyPassword = "readonlyuser";
    String bearerToken = Setup.getAuthToken(readOnlyEmail, readOnlyPassword);

    // Create template
    TemplateFolder parentFolder =
        templateFolderGenerator.ensurePredefined(seed, owners, TEMPLATE_FOLDER_SHELL_SCRIPTS, GLOBAL_APP_ID);
    ShellScriptTemplate shellScriptTemplate = ShellScriptTemplate.builder()
                                                  .scriptType(ScriptType.BASH.name())
                                                  .scriptString("echo \"Hello\" ${name}\n"
                                                      + "export A=\"aaa\"\n"
                                                      + "export B=\"bbb\"")
                                                  .outputVars("A,B")
                                                  .build();
    Template template = Template.builder()
                            .type(TemplateType.SHELL_SCRIPT.name())
                            .accountId(account.getUuid())
                            .name(SCRIPT_NAME3)
                            .templateObject(shellScriptTemplate)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .variables(asList(aVariable().type(TEXT).name("name").mandatory(true).build()))
                            .build();
    GenericType<RestResponse<Template>> templateType = new GenericType<RestResponse<Template>>() {

    };

    RestResponse<Template> savedTemplateResponse = saveTemplate(template, templateType, bearerToken);
    assertThat(savedTemplateResponse.getResponseMessages()).isNotEmpty();
    assertThat(savedTemplateResponse.getResponseMessages().get(0).getCode().getStatus().getCode()).isEqualTo(400);

    bearerToken = Setup.getAuthToken(adminUserEmail, "admin");
    savedTemplateResponse = saveTemplate(template, templateType, bearerToken);

    Template savedTemplate = savedTemplateResponse.getResource();

    assertThat(savedTemplate).isNotNull();

    // Get template and validate template object and variables
    savedTemplateResponse = saveShellScriptTemplate(templateType, savedTemplate, bearerToken);

    savedTemplate = savedTemplateResponse.getResource();
    assertThat(savedTemplate).isNotNull();

    // update template and validate
    shellScriptTemplate = ShellScriptTemplate.builder()
                              .scriptType(ScriptType.BASH.name())
                              .scriptString("echo \"Hello\" ${name}\n"
                                  + "export A=\"aaa\"\n"
                                  + "export B=\"bbb\"\n"
                                  + "export C=\"ccc\"")
                              .outputVars("A,B,C")
                              .build();
    template = Template.builder()
                   .type(TemplateType.SHELL_SCRIPT.name())
                   .accountId(account.getUuid())
                   .name(SCRIPT_NAME3)
                   .templateObject(shellScriptTemplate)
                   .folderId(parentFolder.getUuid())
                   .appId(GLOBAL_APP_ID)
                   .variables(asList(aVariable().type(TEXT).name("name").mandatory(true).build()))
                   .version(savedTemplate.getVersion())
                   .build();

    bearerToken = Setup.getAuthToken(readOnlyEmail, readOnlyPassword);

    savedTemplateResponse = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .contentType(ContentType.JSON)
                                .body(template)
                                .queryParam("accountId", account.getUuid())
                                .pathParam("templateId", savedTemplate.getUuid())
                                .put("/templates/{templateId}")
                                .as(templateType.getType());

    assertThat(savedTemplateResponse.getResponseMessages()).isNotEmpty();
    assertThat(savedTemplateResponse.getResponseMessages().get(0).getCode().getStatus().getCode()).isEqualTo(400);

    // Delete template shouldn't be allowed
    deleteTemplate(bearerToken, account.getUuid(), savedTemplate.getUuid(), 400);

    bearerToken = Setup.getAuthToken(adminUserEmail, "admin");

    // Delete template
    deleteTemplate(bearerToken, account.getUuid(), savedTemplate.getUuid(), 200);
  }

  private void deleteTemplate(String bearerToken, String accountId, String templateId, int expectedHttpResponse) {
    // Delete template
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("accountId", accountId)
        .pathParam("templateId", templateId)
        .delete("/templates/{templateId}")
        .then()
        .statusCode(expectedHttpResponse);
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldNotUpdateTemplateWithDuplicateNameInSameFolder() {
    GenericType<RestResponse<Template>> templateType = new GenericType<RestResponse<Template>>() {};
    Template template1 = createTemplateAndValidate(SCRIPT_NAME1, GLOBAL_APP_ID);
    Template template2 = createTemplateAndValidate(SCRIPT_NAME2, GLOBAL_APP_ID);
    template2.setName(SCRIPT_NAME1);
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .contentType(ContentType.JSON)
        .body(template2)
        .queryParam("accountId", account.getUuid())
        .pathParam("templateId", template2.getUuid())
        .put("/templates/{templateId}")
        .as(templateType.getType());
    RestResponse<Template> savedTemplateResponse = saveShellScriptTemplate(templateType, template2, bearerToken);
    assertThat(savedTemplateResponse.getResource().getName()).isEqualTo(SCRIPT_NAME2);
    deleteTemplate(template1.getUuid());
    deleteTemplate(template2.getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  public void testAddFetchRemoveTemplateFavorites() {
    GenericType<RestResponse<Set<String>>> responseType = new GenericType<RestResponse<Set<String>>>() {};
    // create templates in global template library
    Template template1 = createTemplateAndValidate(SCRIPT_NAME1, GLOBAL_APP_ID);
    Template template2 = createTemplateAndValidate(SCRIPT_NAME2, GLOBAL_APP_ID);
    Template template3 = createTemplateAndValidate(SCRIPT_NAME3, GLOBAL_APP_ID);
    // create templates in app
    Template template4 = createTemplateAndValidate("App template-1", application.getUuid());
    resetCache(application.getAccountId());

    // Mark template as favorites and validate favorites for user
    markTemplateAsFavorite(template1.getUuid());
    markTemplateAsFavorite(template2.getUuid());
    markTemplateAsFavorite(template4.getUuid());

    RestResponse<Set<String>> response = fetchFavoriteTemplates(responseType);
    Set<String> favorites = response.getResource();
    assertThat(favorites).isNotEmpty();
    assertThat(favorites).contains(template1.getUuid(), template2.getUuid(), template4.getUuid());
    assertThat(favorites).doesNotContain(template3.getUuid());

    // un-mark template as favorite and validate favorites for user does not contain
    unmarkTemplateAsFavorite(template1.getUuid());
    unmarkTemplateAsFavorite(template2.getUuid());
    unmarkTemplateAsFavorite(template4.getUuid());
    response = fetchFavoriteTemplates(responseType);
    favorites = response.getResource();
    assertThat(favorites).doesNotContain(template1.getUuid(), template2.getUuid(), template4.getUuid());

    // cleanup - delete all created templates
    deleteTemplate(template1.getUuid());
    deleteTemplate(template2.getUuid());
    deleteTemplate(template3.getUuid());
    deleteTemplate(template4.getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  public void testMarkInvalidTemplateAsFavorite() {
    assertThat(Setup.portal()
                   .auth()
                   .oauth2(bearerToken)
                   .contentType(ContentType.JSON)
                   .queryParam("accountId", account.getUuid())
                   .pathParam("templateId", TestUtils.generateRandomUUID())
                   .put("/personalization/templates/{templateId}/favorite")
                   .getStatusCode()
        == HttpStatus.SC_BAD_REQUEST)
        .isTrue();
  }

  private void markTemplateAsFavorite(String templateId) {
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .contentType(ContentType.JSON)
        .queryParam("accountId", account.getUuid())
        .pathParam("templateId", templateId)
        .put("/personalization/templates/{templateId}/favorite");
  }

  private void unmarkTemplateAsFavorite(String templateId) {
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .contentType(ContentType.JSON)
        .queryParam("accountId", account.getUuid())
        .pathParam("templateId", templateId)
        .delete("/personalization/templates/{templateId}/favorite");
  }

  private RestResponse<Set<String>> fetchFavoriteTemplates(GenericType<RestResponse<Set<String>>> responseType) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .contentType(ContentType.JSON)
        .queryParam("accountId", account.getUuid())
        .get("/personalization/templates/favorite")
        .as(responseType.getType());
  }

  private Template createTemplateAndValidate(String name, String appId) {
    // Create template
    TemplateFolder parentFolder;
    if (appId.equals(GLOBAL_APP_ID)) {
      parentFolder =
          templateFolderGenerator.ensurePredefined(seed, owners, TEMPLATE_FOLDER_SHELL_SCRIPTS, GLOBAL_APP_ID);
    } else {
      parentFolder = templateFolderGenerator.ensurePredefined(seed, owners, APP_FOLDER_SHELL_SCRIPTS, appId);
    }
    ShellScriptTemplate shellScriptTemplate = ShellScriptTemplate.builder()
                                                  .scriptType(ScriptType.BASH.name())
                                                  .scriptString("echo \"Hello\" ${name}\n"
                                                      + "export A=\"aaa\"\n"
                                                      + "export B=\"bbb\"")
                                                  .outputVars("A,B")
                                                  .build();
    Template template = Template.builder()
                            .type(TemplateType.SHELL_SCRIPT.name())
                            .accountId(account.getUuid())
                            .name(name)
                            .templateObject(shellScriptTemplate)
                            .folderId(parentFolder.getUuid())
                            .appId(appId)
                            .variables(asList(aVariable().type(TEXT).name("name").mandatory(true).build()))
                            .build();
    GenericType<RestResponse<Template>> templateType = new GenericType<RestResponse<Template>>() {

    };

    RestResponse<Template> savedTemplateResponse = saveTemplate(template, templateType, bearerToken, appId);

    Template savedTemplate = savedTemplateResponse.getResource();
    assertTemplate(savedTemplate, name, 1L);

    // Get template and validate template object and variables
    savedTemplateResponse = saveShellScriptTemplate(templateType, savedTemplate, bearerToken);

    savedTemplate = savedTemplateResponse.getResource();
    assertTemplate(savedTemplate, name, 1L);
    assertThat(savedTemplate.getTemplateObject()).isNotNull();
    shellScriptTemplate = (ShellScriptTemplate) savedTemplate.getTemplateObject();
    assertThat(shellScriptTemplate.getOutputVars()).isEqualTo("A,B");
    assertThat(shellScriptTemplate.getTimeoutMillis()).isEqualTo(600000);
    assertThat(shellScriptTemplate.getScriptString())
        .isEqualTo("echo \"Hello\" ${name}\n"
            + "export A=\"aaa\"\n"
            + "export B=\"bbb\"");
    return savedTemplate;
  }

  private RestResponse<Template> saveTemplate(
      Template template, GenericType<RestResponse<Template>> templateType, String bearerToken) {
    return saveTemplate(template, templateType, bearerToken, GLOBAL_APP_ID);
  }

  private RestResponse<Template> saveTemplate(
      Template template, GenericType<RestResponse<Template>> templateType, String bearerToken, String appId) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("accountId", account.getUuid())
        .queryParam("appId", appId)
        .body(template)
        .contentType(ContentType.JSON)
        .post("/templates")
        .as(templateType.getType());
  }

  private void deleteTemplate(String templateId) {
    // Delete template
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("accountId", account.getUuid())
        .pathParam("templateId", templateId)
        .delete("/templates/{templateId}")
        .then()
        .statusCode(200);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(FunctionalTests.class)
  @Ignore("Temporary disabled, seems dependent on index creation")
  public void testDuplicateFolderSave() {
    TemplateFolder templateFolder =
        templateFolderGenerator.ensurePredefined(seed, owners, TEMPLATE_FOLDER_SHELL_SCRIPTS, GLOBAL_APP_ID);

    checkSave(templateFolder);
    checkSaveSafelyAndGet(templateFolder);
  }

  private void checkSave(TemplateFolder templateFolder) {
    TemplateFolder newTemplateFolder = templateFolder.cloneInternal();
    newTemplateFolder.setAccountId(templateFolder.getAccountId());
    newTemplateFolder.setParentId(templateFolder.getParentId());
    newTemplateFolder.setGalleryId(templateFolder.getGalleryId());

    assertThatThrownBy(() -> templateFolderService.save(newTemplateFolder, templateFolder.getGalleryId()))
        .hasMessageContaining("Duplicate");
  }

  private void checkSaveSafelyAndGet(TemplateFolder templateFolder) {
    TemplateFolder savedTemplateFolder =
        templateFolderService.saveSafelyAndGet(templateFolder, templateFolder.getGalleryId());

    assertThat(savedTemplateFolder).isEqualTo(templateFolder);
  }
}
