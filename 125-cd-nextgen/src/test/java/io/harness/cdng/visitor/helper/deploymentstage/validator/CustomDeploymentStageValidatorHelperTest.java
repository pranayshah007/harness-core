/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helper.deploymentstage.validator;

import static io.harness.executions.steps.StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;
import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_STAGE;
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
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.visitor.helpers.deploymentstage.validator.CustomDeploymentStageValidatorHelper;
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

public class CustomDeploymentStageValidatorHelperTest extends CategoryTest {
  @Mock TemplateResourceClient templateResourceClient;
  @InjectMocks
  CustomDeploymentStageValidatorHelper customDeploymentStageValidatorHelper =
      new CustomDeploymentStageValidatorHelper();

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
  public void testValidate() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    steps.add(ExecutionWrapperConfig.builder()
                  .step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT + "_1"))
                  .build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleFetchInstancesInRollback() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    steps.add(ExecutionWrapperConfig.builder()
                  .step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT + "_1"))
                  .build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 2");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateValidWorkflow() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepGroupAndParallel() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)));
    steps.add(ExecutionWrapperConfig.builder().stepGroup(getStepGroup("stepGroup", arrayNode)).build());
    steps.add(ExecutionWrapperConfig.builder().parallel(arrayNode).build());

    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 3");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepGroupTemplate() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());

    String accountStepGrp = readFile("cdng/account-stepgroup-template-fetch-instance.yaml");
    String orgStepGrp = readFile("cdng/org-stepgroup-template-fetch-instance.yaml");

    Call<ResponseDTO<TemplateResponseDTO>> callRequestAccount = mock(Call.class);
    doReturn(callRequestAccount)
        .when(templateResourceClient)
        .get(eq("accountStepGroupTemplate"), any(), any(), any(), any(), anyBoolean());
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
        .get(eq("orgStepGroupTemplate"), any(), any(), any(), any(), anyBoolean());
    when(callRequestOrg.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType(DEPLOYMENT_STAGE)
                                                         .yaml(orgStepGrp)
                                                         .build())));

    steps.add(ExecutionWrapperConfig.builder()
                  .stepGroup(getStepGroupTemplate("stepGroup", "accountStepGroupTemplate", "v1"))
                  .build());

    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 3");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  public static JsonNode getFetchInstances(String identifier) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", identifier);
    stepElementConfig.put("type", CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT);
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
