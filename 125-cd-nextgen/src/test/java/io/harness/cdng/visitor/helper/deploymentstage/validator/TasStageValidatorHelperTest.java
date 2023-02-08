/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helper.deploymentstage.validator;

import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_STAGE;
import static io.harness.executions.steps.StepSpecTypeConstants.SWAP_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TANZU_COMMAND;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_APP_RESIZE;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BASIC_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_CANARY_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLING_DEPLOY;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLING_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_SWAP_ROUTES;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.TasStageValidatorHelper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class TasStageValidatorHelperTest extends CategoryTest {
  @Mock TemplateResourceClient templateResourceClient;
  @InjectMocks TasStageValidatorHelper tasStageValidatorHelper = new TasStageValidatorHelper();
  private final String accountId = "ACCOUNT_ID";
  private final String projectId = "PROJECT_ID";
  private final String orgId = "ORG_ID";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleBasicAppSetup() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BASIC_APP_SETUP)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BASIC_APP_SETUP)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one Basic App Setup step is valid, found: 2");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateMultipleRollingDeploy() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getRollingDeploy(TAS_ROLLING_DEPLOY)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getRollingDeploy(TAS_ROLLING_DEPLOY)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one Rolling Deploy step is valid, found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleCanaryAppSetup() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_CANARY_APP_SETUP)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_CANARY_APP_SETUP)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one Canary App Setup step is valid, found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleBGAppSetup() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one BG App Setup step is valid, found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleAppSetup() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_CANARY_APP_SETUP)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BASIC_APP_SETUP)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(new ArrayList<>()).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one App Setup or Rolling Deploy is supported");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateBasicWorkflow() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BASIC_APP_SETUP)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppResize(TAS_APP_RESIZE)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppRollback(TAS_ROLLBACK)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateBGWorkflow() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppResize(TAS_APP_RESIZE)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getSwapRoute(TAS_SWAP_ROUTES)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateCanaryWorkflow() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_CANARY_APP_SETUP)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppResize(TAS_APP_RESIZE)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getAppResize(TAS_APP_RESIZE + "_1")).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppRollback(TAS_ROLLBACK)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleAppRollback() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppRollback(TAS_ROLLBACK)).build());
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getAppRollback(TAS_ROLLBACK + "_1")).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("At max one App Rollback step is valid, found: 2");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateMultipleRollingRollback() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getRollingDeploy(TAS_ROLLING_DEPLOY)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getRollingDeploy(TANZU_COMMAND)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getRollingRollback(TAS_ROLLING_ROLLBACK)).build());
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getRollingRollback(TAS_ROLLING_ROLLBACK + "_1")).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("At max one Rolling Rollback step is valid, found: 2");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateMultipleTanzuCommand() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getTanzuCommand(TANZU_COMMAND)).build());
    steps.add(ExecutionWrapperConfig.builder().step(getTanzuCommand(TANZU_COMMAND + "_1")).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    DeploymentStageConfig.builder()
        .deploymentType(ServiceDefinitionType.TAS)
        .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleSwapRollback() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK)).build());
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK + "_1")).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("At max one Swap Rollback step is valid, found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepGroupAndParallel() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getAppSetup(TAS_BG_APP_SETUP)));
    steps.add(ExecutionWrapperConfig.builder().stepGroup(getStepGroup("stepGroup", arrayNode)).build());
    steps.add(ExecutionWrapperConfig.builder().parallel(arrayNode).build());

    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK)).build());
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK + "_1")).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one BG App Setup step is valid, found: 3");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepGroupTemplate() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(ExecutionWrapperConfig.builder().step(getAppSetup(TAS_BG_APP_SETUP)).build());

    String accountStepGrp = readFile("cdng/account-stepgroup-template-app-setup.yaml");
    String orgStepGrp = readFile("cdng/org-stepgroup-template-app-setup.yaml");

    Call<ResponseDTO<TemplateResponseDTO>> callRequestAccount = mock(Call.class);
    doReturn(callRequestAccount)
        .when(templateResourceClient)
        .get(eq("accountStepGrpTemplate"), any(), any(), any(), any(), anyBoolean());
    when(callRequestAccount.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType(DEPLOYMENT_STAGE)
                                                         .yaml(accountStepGrp)
                                                         .build())));

    Call<ResponseDTO<TemplateResponseDTO>> callRequestOrg = mock(Call.class);
    doReturn(callRequestOrg)
        .when(templateResourceClient)
        .get(eq("orgStepGrpTemplate"), any(), any(), any(), any(), anyBoolean());
    when(callRequestOrg.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType(DEPLOYMENT_STAGE)
                                                         .yaml(orgStepGrp)
                                                         .build())));

    steps.add(ExecutionWrapperConfig.builder()
                  .stepGroup(getStepGroupTemplate("stepGroup", "accountStepGrpTemplate", "v1"))
                  .build());

    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(ExecutionWrapperConfig.builder().step(getSwapRollback(SWAP_ROLLBACK)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> tasStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Only one BG App Setup step is valid, found: 3");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  public static JsonNode getAppSetup(String type) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", type);
    stepElementConfig.put("type", type);
    stepElementConfig.put("name", type);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("tasInstanceCountType", "FromManifest");
    stepSpecType.put("existingVersionToKeep", "3");
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getRollingDeploy(String type) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", type);
    stepElementConfig.put("type", type);
    stepElementConfig.put("name", type);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getRollingRollback(String type) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", type);
    stepElementConfig.put("type", TAS_ROLLING_ROLLBACK);
    stepElementConfig.put("name", type);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getTanzuCommand(String type) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", type);
    stepElementConfig.put("type", TANZU_COMMAND);
    stepElementConfig.put("name", type);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getAppResize(String identifier) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", identifier);
    stepElementConfig.put("type", TAS_APP_RESIZE);
    stepElementConfig.put("name", TAS_APP_RESIZE);

    ObjectNode value = mapper.createObjectNode();
    value.put("value", "100");

    ObjectNode newAppInstances = mapper.createObjectNode();
    newAppInstances.put("type", "Percentage");
    newAppInstances.set("value", value);

    ObjectNode stepSpecType = mapper.createObjectNode();
    newAppInstances.set("newAppInstances", newAppInstances);
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getSwapRoute(String identifier) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", identifier);
    stepElementConfig.put("type", TAS_SWAP_ROUTES);
    stepElementConfig.put("name", identifier);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("downSizeOldApplication", true);
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getSwapRollback(String identifier) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", identifier);
    stepElementConfig.put("type", SWAP_ROLLBACK);
    stepElementConfig.put("name", identifier);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("upsizeInActiveApp", true);
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getAppRollback(String identifier) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", identifier);
    stepElementConfig.put("type", TAS_ROLLBACK);
    stepElementConfig.put("name", identifier);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getStepGroup(String identifier, ArrayNode steps) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepGroupElementConfig = mapper.createObjectNode();
    stepGroupElementConfig.put("identifier", identifier);
    stepGroupElementConfig.put("name", identifier);
    stepGroupElementConfig.set("steps", steps);

    return stepGroupElementConfig;
  }

  public static JsonNode getStepGroupTemplate(String identifier, String templateRef, String versionLabel) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepGroupElementConfig = mapper.createObjectNode();
    stepGroupElementConfig.put("identifier", identifier);
    stepGroupElementConfig.put("name", identifier);

    ObjectNode template = mapper.createObjectNode();
    template.put("templateRef", templateRef);
    template.put("versionLabel", versionLabel);
    stepGroupElementConfig.set("template", template);

    return stepGroupElementConfig;
  }
}
