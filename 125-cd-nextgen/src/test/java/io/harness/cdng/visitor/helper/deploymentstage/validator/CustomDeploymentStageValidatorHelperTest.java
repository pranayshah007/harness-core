/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helper.deploymentstage.validator;

import static io.harness.executions.steps.StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;
import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_STAGE;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_APP_RESIZE;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;
import static io.harness.rule.OwnerRule.RISHABH;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.CustomDeploymentStageValidatorHelper;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
  public void testValidateMultipleFetchInstances() {
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
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    rollbackSteps.add(ExecutionWrapperConfig.builder()
                          .step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT + "_1"))
                          .build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Rollback: Fetch instance script step should be present at max 1 time, found: 2");
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
  public void testValidateStepGroupTemplateDiffTemplateChildType() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());

    String accountStepGrp = readFile("cdng/account-stepgroup-template-fetch-instance.yaml");

    Call<ResponseDTO<TemplateResponseDTO>> callRequestAccount = mock(Call.class);
    doReturn(callRequestAccount)
        .when(templateResourceClient)
        .get(eq("accountStepGrpTemplate"), any(), any(), any(), any(), anyBoolean());
    Mockito.when(callRequestAccount.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType("CI")
                                                         .yaml(accountStepGrp)
                                                         .build())));

    steps.add(ExecutionWrapperConfig.builder()
                  .stepGroup(getStepGroupTemplate("stepGroup", "accountStepGrpTemplate", "v1"))
                  .build());

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
  public void testValidateStepGroupTemplateDiffTemplateChildTypeNullTemplate() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());

    Call<ResponseDTO<TemplateResponseDTO>> callRequestAccount = mock(Call.class);
    doReturn(callRequestAccount)
        .when(templateResourceClient)
        .get(eq("accountStepGrpTemplate"), any(), any(), any(), any(), anyBoolean());
    Mockito.when(callRequestAccount.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType(DEPLOYMENT_STAGE)
                                                         .yaml("notATemplate")
                                                         .build())));

    steps.add(ExecutionWrapperConfig.builder()
                  .stepGroup(getStepGroupTemplate("stepGroup", "accountStepGrpTemplate", "v1"))
                  .build());

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
  public void testValidateNullSteps() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", TAS_APP_RESIZE);
    stepElementConfig.put("type", TAS_APP_RESIZE);
    stepElementConfig.put("name", TAS_APP_RESIZE);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    steps.add(ExecutionWrapperConfig.builder().step(stepElementConfig).build());
    DeploymentStageConfig stageConfig = DeploymentStageConfig.builder()
                                            .deploymentType(ServiceDefinitionType.TAS)
                                            .execution(ExecutionElementConfig.builder().steps(steps).build())
                                            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 0");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepGroupTemplate() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());

    assertTemplateGet("orgStepGroupTemplate", "org-stepgroup-template-fetch-instance.yaml");
    assertTemplateGet("emptyStepGroupTemplate", null);
    assertTemplateGet("accountStepGroupTemplate", "account-stepgroup-template-fetch-instance.yaml");
    assertTemplateGet("projectStepGroupTemplate", "project-stepgroup-template-fetch-instance.yaml");

    steps.add(ExecutionWrapperConfig.builder()
                  .stepGroup(getStepGroupTemplate("stepGroup", "projectStepGroupTemplate", "v1"))
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

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateStepTemplate() throws IOException {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    steps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    steps.add(ExecutionWrapperConfig.builder()
                  .step(getStepGroupTemplate("stepTemplate", "account.accountStepTemplate", "v1"))
                  .build());
    steps.add(ExecutionWrapperConfig.builder()
                  .step(getStepGroupTemplate("stepTemplate", "org.orgStepTemplate", "v1"))
                  .build());
    steps.add(ExecutionWrapperConfig.builder()
                  .step(getStepGroupTemplate("stepTemplate", "projectStepTemplate", "v1"))
                  .build());
    steps.add(ExecutionWrapperConfig.builder().stepGroup(null).build());
    assertTemplateMetadata(accountId, null, null, "accountStepTemplate");
    assertTemplateMetadata(accountId, orgId, null, "orgStepTemplate");
    assertTemplateMetadata(accountId, orgId, projectId, "projectStepTemplate");

    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    rollbackSteps.add(
        ExecutionWrapperConfig.builder().step(getFetchInstances(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)).build());
    DeploymentStageConfig stageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();
    assertThatThrownBy(() -> customDeploymentStageValidatorHelper.validate(stageConfig, accountId, orgId, projectId))
        .hasMessage("Fetch instance script step should be present only 1 time found: 4");
  }

  private void assertTemplateMetadata(String accountId, String orgId, String projectId, String templateRef)
      throws IOException {
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.STEP_TEMPLATE))
            .templateIdentifiers(Arrays.asList(templateRef))
            .build();
    Call callRequest = mock(Call.class);
    Mockito
        .when(templateResourceClient.listTemplateMetadata(
            accountId, orgId, projectId, STABLE_TEMPLATE_TYPE, 0, 1, templateFilterPropertiesDTO))
        .thenReturn(callRequest);
    PageResponse<TemplateMetadataSummaryResponseDTO> pageResponse =
        PageResponse.<TemplateMetadataSummaryResponseDTO>builder()
            .content(Collections.singletonList(TemplateMetadataSummaryResponseDTO.builder()
                                                   .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                                   .childType(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)
                                                   .build()))
            .totalPages(1)
            .pageIndex(0)
            .pageSize(1)
            .build();
    Mockito.when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));
  }

  private void assertTemplateGet(String templateRef, String fileName) throws IOException {
    String stepGrpTemplate = isNull(fileName) ? null : readFile("cdng/" + fileName);
    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest).when(templateResourceClient).get(eq(templateRef), any(), any(), any(), any(), anyBoolean());
    Mockito.when(callRequest.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                                         .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                                         .childType(DEPLOYMENT_STAGE)
                                                         .yaml(stepGrpTemplate)
                                                         .build())));
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
