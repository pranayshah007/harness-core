/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.beans.command.ServiceCommand.TEMPLATE_UUID_KEY;
import static software.wings.beans.template.TemplateType.SSH;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_APP_INSTALL_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_STOP_PATH;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_DESC_CHANGED;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_COMMAND_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;

import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateReference;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import com.mongodb.DBCursor;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public class SshCommandTemplateProcessorTest extends TemplateBaseTestHelper {
  private static final String MY_STOP = "MyStop";
  private static final String STANDALONE_EXEC = "Standalone Exec";
  private static final String ANOTHER_COMMAND = "AnotherCommand";
  private static final String MY_START = "MyStart";
  private static final String MY_INSTALL = "MyInstall";
  private static final String ANOTHER_APP_ID = "ANOTHER_APP_ID";
  @Mock private MorphiaIterator<ServiceCommand, ServiceCommand> serviceCommandIterator;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private Query<ServiceCommand> query;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private Query<Workflow> workflowQuery;
  @Mock private MorphiaIterator<Workflow, Workflow> workflowIterator;
  @Mock private FieldEnd end;
  @Mock private DBCursor dbCursor;

  @Inject private SshCommandTemplateProcessor sshCommandTemplateProcessor;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldLoadTomcatStandardCommands() {
    Template template = templateService.loadYaml(SSH, TOMCAT_WAR_STOP_PATH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    assertThat(template).isNotNull();
    assertThat(template.getName()).isEqualTo("Stop");
    assertThat(template.getVersion()).isEqualTo(1);
    assertThat(template.getVariables()).extracting("name").contains("RuntimePath");
    SshCommandTemplate savedSshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    assertThat(savedSshCommandTemplate).isNotNull();
    assertThat(savedSshCommandTemplate.getCommandUnits()).isNotEmpty();
    assertThat(savedSshCommandTemplate.getCommandUnits()).extracting(CommandUnit::getName).contains("Process Stopped");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadDefaultCommandTemplates() {
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    Template template = templateService.fetchTemplateByKeywordForAccountGallery(GLOBAL_ACCOUNT_ID, "install");
    assertThat(template).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldLoadIISCommands() {
    Template template =
        templateService.loadYaml(SSH, POWER_SHELL_IIS_APP_INSTALL_PATH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    assertThat(template).isNotNull();
    assertThat(template.getName()).isEqualTo("Install IIS Application");
    assertThat(template.getVersion()).isEqualTo(1);
    assertThat(template.getVariables()).extracting("name").contains("AppPoolName");
    SshCommandTemplate savedSshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    assertThat(savedSshCommandTemplate).isNotNull();
    assertThat(savedSshCommandTemplate.getCommandUnits()).isNotEmpty();
    assertThat(savedSshCommandTemplate.getCommandUnits())
        .extracting(CommandUnit::getName)
        .contains("Download Artifact");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddCommandTemplate() {
    Template template = getSshCommandTemplate();
    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getKeywords()).isNotEmpty();
    assertThat(savedTemplate.getKeywords()).contains(template.getName().toLowerCase());
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    assertThat(savedTemplate.getVariables()).isNotEmpty();
    assertThat(savedTemplate.getVariables()).extracting("name").contains("MyVar");
    Variable savedVariable = savedTemplate.getVariables()
                                 .stream()
                                 .filter(variable -> variable.getValue().equals("MyValue"))
                                 .findFirst()
                                 .orElse(null);
    assertThat(savedVariable.isMandatory()).isFalse();

    SshCommandTemplate savedSshCommandTemplate = (SshCommandTemplate) savedTemplate.getTemplateObject();
    assertThat(savedSshCommandTemplate).isNotNull();
    assertThat(savedSshCommandTemplate.getCommandUnits()).isNotEmpty();
    assertThat(savedSshCommandTemplate.getCommandUnits()).extracting(CommandUnit::getName).contains("EXEC");
  }

  private Template getSshCommandTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(anExecCommandUnit()
                                     .withName("EXEC")
                                     .withCommandPath("/home/xxx/tomcat")
                                     .withCommandString("${artifact.description}/${MyVar}")
                                     .build()))
            .build();

    return Template.builder()
        .name(TEMPLATE_NAME)
        .folderId(parentFolder.getUuid())
        .appId(GLOBAL_APP_ID)
        .accountId(GLOBAL_ACCOUNT_ID)
        .templateObject(commandTemplate)
        .appId(GLOBAL_APP_ID)
        .variables(asList(aVariable().name("MyVar").value("MyValue").build()))
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandTemplate() {
    Template template = getSshCommandTemplate();
    Template savedTemplate = templateService.save(template);

    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getVersion()).isEqualTo(1);

    savedTemplate.setDescription(TEMPLATE_DESC_CHANGED);

    savedTemplate.setVariables(asList(aVariable().name("MyVar").value("MyValue").build(),
        aVariable().name("MySecondVar").value("MySecondValue").build()));
    savedTemplate.setName("Another Template");
    Template updatedTemplate = templateService.update(savedTemplate);
    assertThat(updatedTemplate).isNotNull();
    assertThat(updatedTemplate.getVersion()).isEqualTo(2L);
    assertThat(updatedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(updatedTemplate.getDescription()).isEqualTo(TEMPLATE_DESC_CHANGED);
    assertThat(updatedTemplate.getVariables()).isNotEmpty();
    assertThat(updatedTemplate.getKeywords()).isNotEmpty();
    assertThat(updatedTemplate.getKeywords()).doesNotContain(template.getName().toLowerCase());
    assertThat(updatedTemplate.getKeywords()).contains("Another Template".toLowerCase());

    Variable firstVariable = updatedTemplate.getVariables()
                                 .stream()
                                 .filter(variable -> variable.getName().equals("MyVar"))
                                 .findFirst()
                                 .orElse(null);
    assertThat(firstVariable).isNotNull();
    assertThat(firstVariable.getValue()).isEqualTo("MyValue");

    Variable secondVariable = updatedTemplate.getVariables()
                                  .stream()
                                  .filter(variable -> variable.getName().equals("MySecondVar"))
                                  .findFirst()
                                  .orElse(null);
    assertThat(secondVariable).isNotNull();
    assertThat(secondVariable.getValue()).isEqualTo("MySecondValue");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandsLinked() {
    Template template = getSshCommandTemplate();

    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);

    savedTemplate.setDescription(TEMPLATE_DESC_CHANGED);

    savedTemplate.setVariables(asList(aVariable().name("MyVar").value("MyValue").build(),
        aVariable().name("MySecondVar").value("MySecondValue").build()));

    on(sshCommandTemplateProcessor).set("wingsPersistence", wingsPersistence);
    on(sshCommandTemplateProcessor).set("serviceResourceService", serviceResourceService);

    when(wingsPersistence.createQuery(ServiceCommand.class, excludeAuthority)).thenReturn(query);
    when(wingsPersistence.createQuery(Workflow.class, excludeAuthority)).thenReturn(workflowQuery);

    Command command =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat").withCommandString("bin/startup.sh").build())
            .build();

    ServiceCommand serviceCommand = aServiceCommand()
                                        .withServiceId(SERVICE_ID)
                                        .withUuid(SERVICE_COMMAND_ID)
                                        .withDefaultVersion(1)
                                        .withAppId(APP_ID)
                                        .withName("START")
                                        .withCommand(command)
                                        .withTemplateUuid(savedTemplate.getUuid())
                                        .withTemplateVersion(LATEST_TAG)
                                        .build();

    when(query.filter(TEMPLATE_UUID_KEY, savedTemplate.getUuid())).thenReturn(query);
    when(query.fetch()).thenReturn(serviceCommandIterator);

    when(serviceCommandIterator.getCursor()).thenReturn(dbCursor);
    when(serviceCommandIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(serviceCommandIterator.next()).thenReturn(serviceCommand);

    when(workflowQuery.filter(WorkflowKeys.linkedTemplateUuids, savedTemplate.getUuid())).thenReturn(workflowQuery);
    when(workflowQuery.fetch()).thenReturn(workflowIterator);
    when(workflowIterator.getCursor()).thenReturn(dbCursor);
    when(workflowIterator.hasNext()).thenReturn(false);

    templateService.updateLinkedEntities(savedTemplate);

    verify(serviceResourceService)
        .updateCommand(serviceCommand.getAppId(), serviceCommand.getServiceId(), serviceCommand, true);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotUpdateServiceCommandName_onUpdateCommandsLinked() {
    Template template = getSshCommandTemplate();
    template.setName("Changed Template Name");

    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);

    savedTemplate.setDescription(TEMPLATE_DESC_CHANGED);

    savedTemplate.setVariables(asList(aVariable().name("MyVar").value("MyValue").build(),
        aVariable().name("MySecondVar").value("MySecondValue").build()));

    on(sshCommandTemplateProcessor).set("wingsPersistence", wingsPersistence);
    on(sshCommandTemplateProcessor).set("serviceResourceService", serviceResourceService);

    when(wingsPersistence.createQuery(ServiceCommand.class, excludeAuthority)).thenReturn(query);
    when(wingsPersistence.createQuery(Workflow.class, excludeAuthority)).thenReturn(workflowQuery);

    Command command =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat").withCommandString("bin/startup.sh").build())
            .build();

    ServiceCommand serviceCommand = aServiceCommand()
                                        .withServiceId(SERVICE_ID)
                                        .withUuid(SERVICE_COMMAND_ID)
                                        .withDefaultVersion(1)
                                        .withAppId(APP_ID)
                                        .withName("START")
                                        .withCommand(command)
                                        .withTemplateUuid(savedTemplate.getUuid())
                                        .withTemplateVersion(LATEST_TAG)
                                        .build();

    when(query.filter(TEMPLATE_UUID_KEY, savedTemplate.getUuid())).thenReturn(query);
    when(query.fetch()).thenReturn(serviceCommandIterator);

    when(serviceCommandIterator.getCursor()).thenReturn(dbCursor);
    when(serviceCommandIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(serviceCommandIterator.next()).thenReturn(serviceCommand);

    when(workflowQuery.filter(WorkflowKeys.linkedTemplateUuids, savedTemplate.getUuid())).thenReturn(workflowQuery);
    when(workflowQuery.fetch()).thenReturn(workflowIterator);
    when(workflowIterator.getCursor()).thenReturn(dbCursor);
    when(workflowIterator.hasNext()).thenReturn(false);

    templateService.updateLinkedEntities(savedTemplate);

    verify(serviceResourceService)
        .updateCommand(serviceCommand.getAppId(), serviceCommand.getServiceId(), serviceCommand, true);
    assertThat(serviceCommand.getName()).isEqualTo("START");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCRUDCommandTemplate() {
    // Create individual commands like MyStart, MyStop, MyAnotherCommand
    Template myStop = createMyStopCommand();
    Template myStart = createMyStartCommand();
    Template myAnotherCommand = createMyAnotherCommand();
    // Create command MyInstall composed of other commands from template library
    Template installCommand = createInstallCommand(myStop, myStart, myAnotherCommand);
    assertThat(installCommand).isNotNull();
    assertThat(installCommand.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(installCommand.getVersion()).isEqualTo(1);
    SshCommandTemplate savedSshCommandTemplate = (SshCommandTemplate) installCommand.getTemplateObject();
    assertThat(savedSshCommandTemplate.getCommandUnits().size()).isEqualTo(4);
    assertThat(savedSshCommandTemplate.getReferencedTemplateList().size()).isEqualTo(4);
    assertThat(savedSshCommandTemplate.getReferencedTemplateList().get(0).getTemplateReference().getTemplateUuid())
        .isEqualTo(myStop.getUuid());
    assertThat(savedSshCommandTemplate.getReferencedTemplateList().get(0).getTemplateReference().getTemplateVersion())
        .isEqualTo(myStop.getVersion());
    assertThat(savedSshCommandTemplate.getReferencedTemplateList().get(0).getVariableMapping()).containsKey("V3");
    assertThat(savedSshCommandTemplate.getReferencedTemplateList().get(0).getVariableMapping().get("V3").getValue())
        .isEqualTo("hello3");

    // update top-level variable and check if its reflected in templateVariablesList
    installCommand.setVariables(
        asList(aVariable().name("V3").value("hello3-updated").build(), aVariable().name("V4").value("world4").build(),
            aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build()));
    Template updatedTemplate = templateService.update(installCommand);
    SshCommandTemplate updatedSshCommandTemplate = (SshCommandTemplate) updatedTemplate.getTemplateObject();
    assertThat(updatedSshCommandTemplate.getCommandUnits().size()).isEqualTo(4);
    assertThat(updatedSshCommandTemplate.getReferencedTemplateList().size()).isEqualTo(4);
    assertThat(updatedSshCommandTemplate.getReferencedTemplateList().get(0).getTemplateReference().getTemplateUuid())
        .isEqualTo(myStop.getUuid());
    assertThat(updatedSshCommandTemplate.getReferencedTemplateList().get(0).getTemplateReference().getTemplateVersion())
        .isEqualTo(myStop.getVersion());
    assertThat(updatedSshCommandTemplate.getReferencedTemplateList().get(0).getVariableMapping()).containsKey("V3");
    assertThat(updatedSshCommandTemplate.getReferencedTemplateList().get(0).getVariableMapping().get("V3").getValue())
        .isEqualTo("hello3-updated");
    assertThat(updatedTemplate.getVariables().size()).isEqualTo(4);
    assertThat(updatedTemplate.getVariables()).extracting("name", "value").contains(tuple("V3", "hello3-updated"));

    // try deleting my stop - should fail since its referenced in myinstall
    templateService.delete(GLOBAL_ACCOUNT_ID, myStop.getUuid());
    // delete myinstall
    deleteTemplate(installCommand);
    deleteTemplates(myStop, myStart, myAnotherCommand);
  }

  private void deleteTemplate(Template installCommand) {
    assertThat(templateService.delete(GLOBAL_ACCOUNT_ID, installCommand.getUuid())).isTrue();
  }

  private void deleteTemplates(Template myStop, Template myStart, Template myAnotherCommand) {
    deleteTemplate(myStop);
    deleteTemplate(myStart);
    deleteTemplate(myAnotherCommand);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCreateTemplateDuplicateVariablesDifferentFixedValues() {
    // Create individual commands like MyStart, MyStop, MyAnotherCommand
    Template myStop = createMyStopCommand();
    Template myStart = createMyStartCommand();
    Template myAnotherCommand = createMyAnotherCommand();
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    // Create command MyInstall composed of other commands from template library
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    List<Variable> myStopVariables =
        asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build());
    List<Variable> myStartVariables =
        asList(aVariable().name("V1").value("${V3}").build(), aVariable().name("V2").value("there2").build());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop, myStopVariables, MY_STOP), createStandaloneExec(),
                createCommand(myStart, myStartVariables, MY_START),
                createCommand(
                    myAnotherCommand, asList(aVariable().name("V3").value("hello4").build()), ANOTHER_COMMAND)))
            .commandType(CommandType.OTHER)
            .build();
    List<Variable> myInstallVariables =
        asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
            aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build());
    Template template = Template.builder()
                            .name(MY_INSTALL)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .type("SSH")
                            .templateObject(commandTemplate)
                            .variables(myInstallVariables)
                            .build();
    templateService.save(template);

    deleteTemplates(myStop, myStart, myAnotherCommand);
  }

  private ExecCommandUnit createStandaloneExec() {
    return anExecCommandUnit()
        .withName(STANDALONE_EXEC)
        .withScriptType(ScriptType.BASH)
        .withCommandString("echo ${V5}")
        .withCommandPath("/tmp")
        .build();
  }

  private TemplateReference createTemplateReference(Template myStop) {
    return TemplateReference.builder().templateUuid(myStop.getUuid()).templateVersion(myStop.getVersion()).build();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCreateTemplateVariableNotPassedInParent() {
    // Create individual commands like MyStart, MyStop, MyAnotherCommand
    Template myStop = createMyStopCommand();
    Template myStart = createMyStartCommand();
    Template myAnotherCommand = createMyAnotherCommand();
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    // Create command MyInstall composed of other commands from template library
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    List<Variable> myStopVariables =
        asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build());
    List<Variable> myStartVariables =
        asList(aVariable().name("V1").value("hi").build(), aVariable().name("V2").value("there2").build());
    List<Variable> anotherCommandVariables = asList(aVariable().name("V3").value("${V1}").build());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop, myStopVariables, MY_STOP), createStandaloneExec(),
                createCommand(myStart, myStartVariables, MY_START),
                createCommand(myAnotherCommand, anotherCommandVariables, ANOTHER_COMMAND)))
            .commandType(CommandType.OTHER)
            .build();
    List<Variable> installCommandVariables =
        asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
            aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build());
    Template template = Template.builder()
                            .name(MY_INSTALL)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .type("SSH")
                            .templateObject(commandTemplate)
                            .variables(installCommandVariables)
                            .build();
    templateService.save(template);

    deleteTemplates(myStop, myStart, myAnotherCommand);
  }

  private Command createCommand(Template template, List<Variable> myStopVariables, String name) {
    return aCommand()
        .withName(name)
        .withTemplateReference(createTemplateReference(template))
        .withTemplateVariables(myStopVariables)
        .build();
  }

  private Template createMyStopCommand() {
    return createMyStopCommand(GLOBAL_APP_ID);
  }

  private Template createMyStopCommand(String appId) {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate = SshCommandTemplate.builder()
                                             .commandUnits(asList(anExecCommandUnit()
                                                                      .withName("EXEC-1")
                                                                      .withCommandPath("/home/xxx/tomcat")
                                                                      .withCommandString("echo ${V3}")
                                                                      .withScriptType(ScriptType.BASH)
                                                                      .build(),
                                                 anExecCommandUnit()
                                                     .withName("EXEC-2")
                                                     .withCommandPath("/home/xxx/tomcat")
                                                     .withCommandString("echo ${V4}")
                                                     .withScriptType(ScriptType.BASH)
                                                     .build()))
                                             .build();

    Template template = Template.builder()
                            .name(MY_STOP)
                            .folderId(parentFolder.getUuid())
                            .appId(appId)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .templateObject(commandTemplate)
                            .variables(asList(aVariable().name("V3").value("hello").build(),
                                aVariable().name("V4").value("world").build()))
                            .build();
    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(appId);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    return savedTemplate;
  }

  private Template createMyStartCommand() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate = SshCommandTemplate.builder()
                                             .commandUnits(asList(anExecCommandUnit()
                                                                      .withName("EXEC-1")
                                                                      .withCommandPath("/home/xxx/tomcat")
                                                                      .withCommandString("echo ${V1}")
                                                                      .withScriptType(ScriptType.BASH)
                                                                      .build(),
                                                 anExecCommandUnit()
                                                     .withName("EXEC-2")
                                                     .withCommandPath("/home/xxx/tomcat")
                                                     .withCommandString("echo ${V2}")
                                                     .withScriptType(ScriptType.BASH)
                                                     .build()))
                                             .build();

    Template template = Template.builder()
                            .name(MY_START)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .templateObject(commandTemplate)
                            .variables(asList(aVariable().name("V1").value("hi").build(),
                                aVariable().name("V2").value("world").build()))
                            .build();
    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    return savedTemplate;
  }

  private Template createMyAnotherCommand() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate = SshCommandTemplate.builder()
                                             .commandUnits(asList(anExecCommandUnit()
                                                                      .withName("EXEC-1")
                                                                      .withCommandPath("/home/xxx/tomcat")
                                                                      .withCommandString("echo ${V3}")
                                                                      .withScriptType(ScriptType.BASH)
                                                                      .build()))
                                             .build();

    Template template = Template.builder()
                            .name(ANOTHER_COMMAND)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .templateObject(commandTemplate)
                            .variables(asList(aVariable().name("V3").value("hi").build()))
                            .build();
    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    return savedTemplate;
  }

  private Template createInstallCommand(Template myStop, Template myStart, Template myAnotherCommand) {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop,
                                     asList(aVariable().name("V3").value("hello3").build(),
                                         aVariable().name("V4").value("world4").build()),
                                     MY_STOP),
                createStandaloneExec(),
                createCommand(myStart,
                    asList(
                        aVariable().name("V1").value("${V3}").build(), aVariable().name("V2").value("there2").build()),
                    MY_START),
                createCommand(
                    myAnotherCommand, asList(aVariable().name("V3").value("hello3").build()), ANOTHER_COMMAND)))
            .commandType(CommandType.OTHER)
            .build();
    Template template =
        Template.builder()
            .name(MY_INSTALL)
            .folderId(parentFolder.getUuid())
            .appId(GLOBAL_APP_ID)
            .accountId(GLOBAL_ACCOUNT_ID)
            .type("SSH")
            .templateObject(commandTemplate)
            .variables(
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
                    aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build()))
            .build();
    return templateService.save(template);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotLinkAppLevelTemplateToAccountLevelTemplate() {
    // Create individual commands like MyStart, MyStop
    Template myStop = createMyStopCommand(APP_ID);
    Template myStart = createMyStartCommand();
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop,
                                     asList(aVariable().name("V3").value("hello3").build(),
                                         aVariable().name("V4").value("world4").build()),
                                     MY_STOP),
                createCommand(myStart,
                    asList(
                        aVariable().name("V1").value("${V3}").build(), aVariable().name("V2").value("there2").build()),
                    MY_START)))
            .commandType(CommandType.OTHER)
            .build();
    Template template =
        Template.builder()
            .name(MY_INSTALL)
            .folderId(parentFolder.getUuid())
            .appId(GLOBAL_APP_ID)
            .accountId(GLOBAL_ACCOUNT_ID)
            .type("SSH")
            .templateObject(commandTemplate)
            .variables(
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
                    aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build()))
            .build();
    templateService.save(template);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotLinkTemplatesAcrossApps() {
    // Create individual commands like MyStop with ANOTHER_APP_ID
    Template myStop = createMyStopCommand(ANOTHER_APP_ID);

    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop,
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build()),
                MY_STOP)))
            .commandType(CommandType.OTHER)
            .build();
    Template template =
        Template.builder()
            .name(MY_INSTALL)
            .folderId(parentFolder.getUuid())
            .appId(APP_ID)
            .accountId(GLOBAL_ACCOUNT_ID)
            .type("SSH")
            .templateObject(commandTemplate)
            .variables(
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
                    aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build()))
            .build();
    templateService.save(template);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCanLinkAccountLevelTemplateToAppLevelTemplate() {
    // Create individual commands like MyStop with GLOBAL_APP_ID
    linkAccountLevelToAppLevelAndValidate();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testInvalidUpdateWhenLinkingTemplatesAcrossApps() {
    Template savedTemplate = linkAccountLevelToAppLevelAndValidate();
    SshCommandTemplate savedCommandTemplate = (SshCommandTemplate) savedTemplate.getTemplateObject();
    Template myStop = createMyStopCommand(ANOTHER_APP_ID);
    savedCommandTemplate.getCommandUnits().add(createCommand(myStop,
        asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build()),
        MY_STOP));
    savedTemplate.setTemplateObject(savedCommandTemplate);
    templateService.update(savedTemplate);
  }

  private Template linkAccountLevelToAppLevelAndValidate() {
    Template myStop = createMyStopCommand();

    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    SshCommandTemplate commandTemplate =
        SshCommandTemplate.builder()
            .commandUnits(asList(createCommand(myStop,
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build()),
                MY_STOP)))
            .commandType(CommandType.OTHER)
            .build();
    Template template =
        Template.builder()
            .name(MY_INSTALL)
            .folderId(parentFolder.getUuid())
            .appId(APP_ID)
            .accountId(GLOBAL_ACCOUNT_ID)
            .type("SSH")
            .templateObject(commandTemplate)
            .variables(
                asList(aVariable().name("V3").value("hello3").build(), aVariable().name("V4").value("world4").build(),
                    aVariable().name("V5").value("bye").build(), aVariable().name("V2").value("there2").build()))
            .build();
    Template savedTemplate = templateService.save(template);
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(APP_ID);
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    SshCommandTemplate savedSshCommandTemplate = (SshCommandTemplate) savedTemplate.getTemplateObject();
    assertThat(savedSshCommandTemplate.getCommandUnits().size()).isEqualTo(1);
    return savedTemplate;
  }
}
