/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILOS;

import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.HarnessException;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Event.Type;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.common.InfrastructureConstants;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.impl.yaml.handler.workflow.WorkflowYamlHandler.WorkflowInfo;
import software.wings.service.intfc.template.TemplateService;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.workflow.BasicWorkflowYaml;
import software.wings.yaml.workflow.WorkflowYaml;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.inject.Inject;
import java.io.File;
import java.net.URISyntaxException;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class WorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock protected TemplateService templateService;
  @Mock protected WorkflowServiceTemplateHelper workflowServiceTemplateHelper;

  @InjectMocks @Inject private CanaryWorkflowYamlHandler canaryWorkflowYamlHandler;
  @InjectMocks @Inject private MultiServiceWorkflowYamlHandler multiServiceWorkflowYamlHandler;
  @InjectMocks @Inject private RollingWorkflowYamlHandler rollingWorkflowYamlHandler;
  @InjectMocks @Inject private BlueGreenWorkflowYamlHandler blueGreenWorkflowYamlHandler;
  @InjectMocks @Inject private BasicWorkflowYamlHandler basicWorkflowYamlHandler;
  @InjectMocks @Inject private BuildWorkflowYamlHandler buildWorkflowYamlHandler;

  private String workflowName = "workflowName";
  private final String resourcePath = "400-rest/src/test/resources/workflows";
  private final String yamlFilePath = "Setup/Applications/APP_NAME/Workflows/workflow.yaml";
  private final String templateId = "templateId";

  @UtilityClass
  private static class workflowYamlFiles {
    // Canary Workflow YAML file
    private static final String CANARY_WORKFLOW = "canaryWorkflow.yaml";
    // Canary Workflow Invalid YAML file (invalid workflow type)
    private static final String CANARY_WORKFLOW_INVALID = "canaryWorkflowInvalid.yaml";
    // Canary Workflow Incomplete YAML file (missing service name)
    private static final String CANARY_WORKFLOW_INCOMPLETE = "canaryWorkflowIncomplete.yaml";

    // Multi Service Workflow YAML file
    private static final String MULTI_SERVICE_WORKFLOW = "multiServiceWorkflow.yaml";
    // Multi Service Workflow Invalid YAML file (invalid execution scope)
    private static final String MULTI_SERVICE_WORKFLOW_INVALID = "multiServiceWorkflowInvalid.yaml";
    // Multi Service Workflow Incomplete YAML file (missing phase service name)
    private static final String MULTI_SERVICE_WORKFLOW_INCOMPLETE = "multiServiceWorkflowIncomplete.yaml";

    // Rolling Workflow Invalid YAML file (invalid phase step type)
    private static final String ROLLING_WORKFLOW_INVALID_TYPE = "rollingWorkflowInvalid.yaml";
    // Rolling Workflow Incomplete YAML file (missing deployment type)
    private static final String ROLLING_WORKFLOW_INCOMPLETE = "rollingWorkflowIncomplete.yaml";

    // BlueGreen Workflow YAML file
    private static final String BLUE_GREEN_WORKFLOW = "blueGreenWorkflow.yaml";
    // BlueGreen Workflow Invalid YAML file (invalid concurrency strategy)
    private static final String BLUE_GREEN_WORKFLOW_INVALID = "blueGreenWorkflowInvalid.yaml";
    // BlueGreen Workflow Incomplete YAML file (missing service name and provision nodes)
    private static final String BLUE_GREEN_WORKFLOW_INCOMPLETE = "blueGreenWorkflowIncomplete.yaml";

    // Basic Workflow YAML file
    private static final String BASIC_WORKFLOW = "basicWorkflow.yaml";
    // Basic Workflow Invalid YAML file (invalid failure type error)
    private static final String BASIC_WORKFLOW_INVALID = "basicWorkflowInvalid.yaml";
    // Basic Workflow Incomplete YAML file (missing infra definition id)
    private static final String BASIC_WORKFLOW_INCOMPLETE = "basicWorkflowIncomplete.yaml";

    // Build Workflow YAML file
    private static final String BUILD_WORKFLOW = "buildWorkflow.yaml";
    // Build Workflow YAML file (with description)
    private static final String BUILD_WORKFLOW_DESCRIPTION = "buildWorkflowWithDescription.yaml";
    // Build Workflow Invalid YAML file (invalid deployment type)
    private static final String BUILD_WORKFLOW_INVALID = "buildWorkflowInvalid.yaml";
    // Build Workflow Incomplete YAML file (missing deployment type)
    private static final String BUILD_WORKFLOW_INCOMPLETE = "buildWorkflowIncomplete.yaml";

    // Workflow YAML file with Shell script
    private static final String WORKFLOW_WITH_SHELL_SCRIPT = "workflowShellScript.yaml";
    // Workflow YAML file with Shell script (invalid shell script name)
    private static final String WORKFLOW_WITH_SHELL_SCRIPT_INVALID_NAME = "workflowShellScriptInvalidName.yaml";
    // Workflow YAML file with Shell script (invalid shell script type)
    private static final String WORKFLOW_WITH_SHELL_SCRIPT_INVALID_TYPE = "workflowShellScriptInvalidType.yaml";
    // Workflow YAML file with incomplete Shell script (missing shell script type)
    private static final String WORKFLOW_WITH_SHELL_SCRIPT_INCOMPLETE = "workflowShellScriptIncomplete.yaml";

    // Workflow YAML with Shell script template library
    private static final String WORKFLOW_SHELL_SCRIPT_TEMP_LIB = "workflowShellScriptTemplateLib.yaml";
    // Workflow YAML with Shell script template library
    private static final String WORKFLOW_HTTP_TEMP_LIB = "workflowHttpTemplateLib.yaml";
    // Workflow YAML with Shell script template library
    private static final String WORKFLOW_SERIVCE_COMMAND_TEMP_LIB = "workflowServiceCommandTemplateLib.yaml";
    // Workflow YAML with Shell script template library
    private static final String WORKFLOW_SHELL_SCRIPT_TEMP_LIB_INCOMPLETE =
        "workflowShellScriptTemplateLibIncomplete.yaml";
  }

  @Before
  public void setup() {
    setup(yamlFilePath, workflowName);
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new WingsTestConstants.MockChecker(true, ActionType.CREATE_WORKFLOW));
    on(workflowServiceTemplateHelper).set("templateService", templateService);
    when(templateService.fetchTemplateIdFromUri(any(), any(), any())).thenReturn(templateId);
    when(templateService.fetchTemplateIdFromUri(any(), any())).thenReturn(templateId);
    when(templateService.makeNamespacedTemplareUri(any(), any())).thenReturn("harness-dev-3/tempLib:latest");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudCanaryWorkflow() throws Exception {
    testCRUD(workflowYamlFiles.CANARY_WORKFLOW, canaryWorkflowYamlHandler, OrchestrationWorkflowType.CANARY);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudCanaryWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.CANARY_WORKFLOW_INVALID, canaryWorkflowYamlHandler,
                               OrchestrationWorkflowType.CANARY))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageContaining("Could not resolve type id 'INVALID_TYPE'");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudCanaryWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.CANARY_WORKFLOW_INCOMPLETE, canaryWorkflowYamlHandler,
                               OrchestrationWorkflowType.CANARY))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Service Cannot be Empty for name :null");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudMultiServiceWorkflow() throws Exception {
    testCRUD(workflowYamlFiles.MULTI_SERVICE_WORKFLOW, multiServiceWorkflowYamlHandler,
        OrchestrationWorkflowType.MULTI_SERVICE);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudMultiServiceWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.MULTI_SERVICE_WORKFLOW_INVALID,
                               multiServiceWorkflowYamlHandler, OrchestrationWorkflowType.MULTI_SERVICE))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Cannot find the value: WORKFLOW_2");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudMultiServiceWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.MULTI_SERVICE_WORKFLOW_INCOMPLETE,
                               multiServiceWorkflowYamlHandler, OrchestrationWorkflowType.MULTI_SERVICE))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Service Cannot be Empty for name :null");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudRollingWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.ROLLING_WORKFLOW_INVALID_TYPE, rollingWorkflowYamlHandler,
                               OrchestrationWorkflowType.ROLLING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No enum constant software.wings.sm.StepType.INVALID_TYPE");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudRollingWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.ROLLING_WORKFLOW_INCOMPLETE, rollingWorkflowYamlHandler,
                               OrchestrationWorkflowType.ROLLING))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageContaining("missing type id property 'type'");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBlueGreenWorkflow() throws Exception {
    testCRUD(workflowYamlFiles.BLUE_GREEN_WORKFLOW, blueGreenWorkflowYamlHandler, OrchestrationWorkflowType.BLUE_GREEN);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBlueGreenWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BLUE_GREEN_WORKFLOW_INVALID, blueGreenWorkflowYamlHandler,
                               OrchestrationWorkflowType.BLUE_GREEN))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Not a valid Concurrency Strategy");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBlueGreenWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BLUE_GREEN_WORKFLOW_INCOMPLETE, blueGreenWorkflowYamlHandler,
                               OrchestrationWorkflowType.BLUE_GREEN))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Service Cannot be Empty for name :null");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBasicWorkflow() throws Exception {
    testCRUD(workflowYamlFiles.BASIC_WORKFLOW, basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBasicWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BASIC_WORKFLOW_INVALID, basicWorkflowYamlHandler,
                               OrchestrationWorkflowType.BASIC))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Cannot find the value: INVALID_ERROR");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBasicWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BASIC_WORKFLOW_INCOMPLETE, basicWorkflowYamlHandler,
                               OrchestrationWorkflowType.BASIC))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Invalid InfraDefinition Id");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBuildWorkflow() throws Exception {
    testCRUD(workflowYamlFiles.BUILD_WORKFLOW, buildWorkflowYamlHandler, OrchestrationWorkflowType.BUILD);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBuildWorkflowWithDescription() throws Exception {
    testCRUD(workflowYamlFiles.BUILD_WORKFLOW_DESCRIPTION, buildWorkflowYamlHandler, OrchestrationWorkflowType.BUILD);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBuildWorkflowInvalidYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BUILD_WORKFLOW_INVALID, buildWorkflowYamlHandler,
                               OrchestrationWorkflowType.BUILD))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageContaining("Could not resolve type id 'INVALID_TYPE'");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudBuildWorkflowIncompleteYaml() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.BUILD_WORKFLOW_INCOMPLETE, buildWorkflowYamlHandler,
                               OrchestrationWorkflowType.BUILD))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageContaining("missing type id property 'type'");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScript() throws Exception {
    testCRUD(workflowYamlFiles.WORKFLOW_WITH_SHELL_SCRIPT, basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScriptWithInvalidName() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.WORKFLOW_WITH_SHELL_SCRIPT_INVALID_NAME,
                               basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Step name is empty for SHELL_SCRIPT step");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScriptWithInvalidType() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.WORKFLOW_WITH_SHELL_SCRIPT_INVALID_TYPE,
                               basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No enum constant software.wings.sm.StepType.SHELL_SCRIPT_INVALID");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScriptIncomplete() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.WORKFLOW_WITH_SHELL_SCRIPT_INCOMPLETE,
                               basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Step type could not be empty");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScriptTempLib() throws Exception {
    Template template = getTemplate(TemplateType.SHELL_SCRIPT.toString());
    when(templateService.get(any(), any())).thenReturn(template);
    when(templateService.get(any())).thenReturn(template);

    testCRUD(
        workflowYamlFiles.WORKFLOW_SHELL_SCRIPT_TEMP_LIB, basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowHttpTempLib() throws Exception {
    Template template = getTemplate(TemplateType.HTTP.toString());
    when(templateService.get(any(), any())).thenReturn(template);
    when(templateService.get(any())).thenReturn(template);

    testCRUD(workflowYamlFiles.WORKFLOW_HTTP_TEMP_LIB, basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowServiceCommandTempLib() throws Exception {
    Template template = getTemplate("COMMAND");
    when(templateService.get(any(), any())).thenReturn(template);
    when(templateService.get(any())).thenReturn(template);

    testCRUD(
        workflowYamlFiles.WORKFLOW_SERIVCE_COMMAND_TEMP_LIB, basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCrudWorkflowShellScriptTempLibIncomplete() {
    assertThatThrownBy(()
                           -> testCRUD(workflowYamlFiles.WORKFLOW_SHELL_SCRIPT_TEMP_LIB_INCOMPLETE,
                               basicWorkflowYamlHandler, OrchestrationWorkflowType.BASIC))
        .isInstanceOf(HarnessException.class)
        .hasMessageContaining("Step name is empty for SHELL_SCRIPT step");
  }

  // Main method
  private void testCRUD(String yamlFileName, WorkflowYamlHandler workflowYamlHandler, OrchestrationWorkflowType type)
      throws Exception {
    File yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);
    assertThat(yamlFile).isNotNull();

    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<WorkflowYaml> changeContext = getChangeContext(yamlString, yamlFilePath, workflowYamlHandler);

    WorkflowYaml yamlObject = (WorkflowYaml) getYaml(yamlString, WorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = workflowYamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();

    if (type != OrchestrationWorkflowType.BUILD) {
      assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy()).isNotNull();
      assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getUnitType())
          .isEqualTo(ConcurrencyStrategy.UnitType.INFRA);
      assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getResourceUnit())
          .isEqualTo(InfrastructureConstants.INFRA_ID_EXPRESSION);
    }

    WorkflowYaml yaml = (WorkflowYaml) workflowYamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo(type.toString());

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    workflowYamlHandler.delete(changeContext);
    Workflow deletedWorkflow = workflowYamlHandler.get(ACCOUNT_ID, yamlFilePath);
    assertThat(deletedWorkflow).isNull();
  }

  @Nullable
  private File getYamlFile(String yamlFileName) {
    File yamlFile = null;
    try {
      yamlFile =
          new File(getClass().getClassLoader().getResource(resourcePath + PATH_DELIMITER + yamlFileName).toURI());
    } catch (URISyntaxException e) {
      fail("Unable to find yaml file " + yamlFileName);
    }
    return yamlFile;
  }

  private Template getTemplate(String templateType) {
    return Template.builder().type(templateType).build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnCRUDFromYaml() throws Exception {
    File yamlFile = new File(resourcePath + PATH_DELIMITER + workflowYamlFiles.BASIC_WORKFLOW);
    assertThat(yamlFile).isNotNull();

    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<BasicWorkflowYaml> changeContext =
        getChangeContext(yamlString, yamlFilePath, basicWorkflowYamlHandler);
    changeContext.getChange().setSyncFromGit(true);

    BasicWorkflowYaml yamlObject = (BasicWorkflowYaml) getYaml(yamlString, BasicWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    Workflow workflow = basicWorkflowYamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, null, workflow, Type.CREATE, true, false);

    when(yamlHelper.getWorkflowByAppIdYamlPath(APP_ID, yamlFilePath)).thenReturn(workflow);
    basicWorkflowYamlHandler.delete(changeContext);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, workflow, null, Type.DELETE, true, false);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersCanary() {
    testSetRollbackProvisionersFor(new CanaryWorkflowYamlHandler());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersBuild() {
    testSetRollbackProvisionersFor(new BuildWorkflowYamlHandler());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersMultiService() {
    testSetRollbackProvisionersFor(new MultiServiceWorkflowYamlHandler());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersBasic() {
    testSetRollbackProvisionersFor(new BasicWorkflowYamlHandler());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersRolling() {
    testSetRollbackProvisionersFor(new RollingWorkflowYamlHandler());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetRollbackProvisionersBG() {
    testSetRollbackProvisionersFor(new BlueGreenWorkflowYamlHandler());
  }

  private void testSetRollbackProvisionersFor(WorkflowYamlHandler yamlHandler) {
    PhaseStep rollbackProvisioners = new PhaseStep(PhaseStepType.ROLLBACK_PROVISIONERS);
    rollbackProvisioners.setName("Rollback Provisioners");
    WorkflowInfo workflowInfo = WorkflowInfo.builder().rollbackProvisioners(rollbackProvisioners).build();
    WorkflowBuilder workflow = WorkflowBuilder.aWorkflow();
    yamlHandler.setOrchestrationWorkflow(workflowInfo, workflow);
    assertThat(((CanaryOrchestrationWorkflow) workflow.build().getOrchestrationWorkflow()).getRollbackProvisioners())
        .isEqualTo(rollbackProvisioners);
  }
}
