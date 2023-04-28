/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.TemplateInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.rule.Owner;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class TemplateInputsValidatorTest extends TemplateServiceTestBase {
  private static final String RESOURCE_PATH_PREFIX = "refresh/validate/";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projId";
  @InjectMocks InputsValidator inputsValidator;
  @InjectMocks TemplateInputsValidator templateInputsValidator;
  @InjectMocks TemplateMergeServiceHelper templateMergeServiceHelper;
  @Mock NGTemplateServiceHelper templateServiceHelper;
  @Mock NgManagerReconcileClient ngManagerReconcileClient;
  @Before
  public void setup() throws IOException {
    on(templateMergeServiceHelper).set("templateServiceHelper", templateServiceHelper);
    on(inputsValidator).set("templateMergeServiceHelper", templateMergeServiceHelper);
    on(inputsValidator).set("ngManagerReconcileClient", ngManagerReconcileClient);
    on(templateInputsValidator).set("inputsValidator", inputsValidator);

    // default behaviour of validation
    Call<ResponseDTO<InputsValidationResponse>> ngManagerReconcileCall = mock(Call.class);
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), anyString(), any(NgManagerRefreshRequestDTO.class));
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), eq(null), any(NgManagerRefreshRequestDTO.class));
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), eq(null), eq(null), any(NgManagerRefreshRequestDTO.class));

    doReturn(Response.success(ResponseDTO.newResponse(InputsValidationResponse.builder().isValid(true).build())))
        .when(ngManagerReconcileCall)
        .execute();
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private TemplateEntity convertYamlToTemplateEntity(String yaml) {
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(yaml, NGTemplateConfig.class);
      NGTemplateInfoConfig templateInfoConfig = templateConfig.getTemplateInfoConfig();
      return TemplateEntity.builder()
          .accountId(ACCOUNT_ID)
          .orgIdentifier(templateInfoConfig.getOrgIdentifier())
          .projectIdentifier(templateInfoConfig.getProjectIdentifier())
          .identifier(templateInfoConfig.getIdentifier())
          .name(templateInfoConfig.getName())
          .yaml(yaml)
          .templateEntityType(templateInfoConfig.getType())
          .versionLabel(templateInfoConfig.getVersionLabel())
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateNestedTemplateInputsForTemplatesWithInfiniteRecursion() {
    String yaml = "template:\n"
        + "  projectIdentifier: projId\n"
        + "  orgIdentifier: orgId\n"
        + "  identifier: stageTemplate\n"
        + "  versionLabel: 1\n"
        + "  name: template1\n"
        + "  type: Stage\n"
        + "  spec:\n"
        + "    template:\n"
        + "        templateRef: stageTemplate";

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier("stageTemplate")
                                        .name("stageTemplate")
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .yaml(yaml)
                                        .build();
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             anyString(), anyString(), anyString(), any(), any(), eq(false), eq(false)))
        .thenReturn(Optional.of(templateEntity));
    assertThatThrownBy(
        ()
            -> templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
                new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exponentially growing template nesting. Aborting");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateNestedTemplateInputsWithCorrectYaml() {
    String stageTemplateWithCorrectInputs = readFile("stage-template-with-correct-inputs.yaml");
    String stepTemplateYaml = readFile("step-template.yaml");

    TemplateEntity stepTemplate = convertYamlToTemplateEntity(stepTemplateYaml);
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false, false))
        .thenReturn(Optional.of(stepTemplate));

    ValidateTemplateInputsResponseDTO response =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(
                convertYamlToTemplateEntity(stageTemplateWithCorrectInputs), EntityGitDetails.builder().build()));
    assertThat(response).isNotNull();
    assertThat(response.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateNestedTemplateInputsWithInValidYaml() {
    String stageTemplateWithInCorrectInputs = readFile("stage-template-with-incorrect-inputs.yaml");
    String stepTemplateYaml = readFile("step-template.yaml");

    TemplateEntity stepTemplate = convertYamlToTemplateEntity(stepTemplateYaml);
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false, false))
        .thenReturn(Optional.of(stepTemplate));

    TemplateEntity stageTemplate = convertYamlToTemplateEntity(stageTemplateWithInCorrectInputs);
    ValidateTemplateInputsResponseDTO response =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(stageTemplate, EntityGitDetails.builder().build()));
    assertThat(response).isNotNull();
    assertThat(response.isValidYaml()).isFalse();
    assertThat(response.getErrorNodeSummary()).isNotNull();
    assertThat(response.getErrorNodeSummary().getChildrenErrorNodes()).isEmpty();
    ErrorNodeSummary errorNode = response.getErrorNodeSummary();
    assertThat(errorNode.getNodeInfo())
        .isEqualToComparingFieldByField(NodeInfo.builder()
                                            .name(stageTemplate.getName())
                                            .identifier(stageTemplate.getIdentifier())
                                            .localFqn(null)
                                            .build());
    assertThat(errorNode.getTemplateInfo())
        .isEqualToComparingFieldByField(TemplateInfo.builder()
                                            .templateIdentifier(stageTemplate.getIdentifier())
                                            .versionLabel(stageTemplate.getVersionLabel())
                                            .templateEntityType(stageTemplate.getTemplateEntityType())
                                            .build());
    assertThat(errorNode.getChildrenErrorNodes()).isEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateNestedTemplateInputsWithInValidYamlNested() {
    String pipelineTemplateWithIncorrectInputsYaml = readFile("pipeline-template-with-incorrect-input.yaml");
    String stageTemplateWithInCorrectInputsYaml = readFile("stage-template-with-incorrect-inputs.yaml");
    String stageTemplateWithCorrectInputsYaml = readFile("stage-template-with-correct-inputs.yaml");
    String stepTemplateYaml = readFile("step-template.yaml");

    TemplateEntity pipelineTemplateWithIncorrectInputs =
        convertYamlToTemplateEntity(pipelineTemplateWithIncorrectInputsYaml);
    TemplateEntity stageTemplateWithInCorrectInputs = convertYamlToTemplateEntity(stageTemplateWithInCorrectInputsYaml);
    TemplateEntity stageTemplateWithCorrectInputs = convertYamlToTemplateEntity(stageTemplateWithCorrectInputsYaml);
    TemplateEntity stepTemplate = convertYamlToTemplateEntity(stepTemplateYaml);
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false, false))
        .thenReturn(Optional.of(stepTemplate));
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "1", false, false))
        .thenReturn(Optional.of(stageTemplateWithInCorrectInputs));
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "2", false, false))
        .thenReturn(Optional.of(stageTemplateWithCorrectInputs));
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "pipelineTemplate", "1", false, false))
        .thenReturn(Optional.of(pipelineTemplateWithIncorrectInputs));

    ValidateTemplateInputsResponseDTO response =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(pipelineTemplateWithIncorrectInputs, EntityGitDetails.builder().build()));
    assertThat(response).isNotNull();
    assertThat(response.isValidYaml()).isFalse();
    assertThat(response.getErrorNodeSummary()).isNotNull();
    ErrorNodeSummary errorNodeSummary = response.getErrorNodeSummary();
    assertThat(errorNodeSummary.getNodeInfo())
        .isEqualToComparingFieldByField(NodeInfo.builder()
                                            .name(pipelineTemplateWithIncorrectInputs.getName())
                                            .identifier(pipelineTemplateWithIncorrectInputs.getIdentifier())
                                            .localFqn(null)
                                            .build());
    assertThat(errorNodeSummary.getTemplateInfo())
        .isEqualToComparingFieldByField(TemplateInfo.builder()
                                            .templateIdentifier(pipelineTemplateWithIncorrectInputs.getIdentifier())
                                            .versionLabel(pipelineTemplateWithIncorrectInputs.getVersionLabel())
                                            .templateEntityType(TemplateEntityType.PIPELINE_TEMPLATE)
                                            .build());
    assertThat(errorNodeSummary.getChildrenErrorNodes()).isNotNull().isNotEmpty().hasSize(1);

    ErrorNodeSummary childErrorNode = errorNodeSummary.getChildrenErrorNodes().get(0);
    assertThat(childErrorNode.getNodeInfo())
        .isEqualToComparingFieldByField(NodeInfo.builder()
                                            .name("Stage 1")
                                            .identifier("Stage_1")
                                            .localFqn("template.spec.stages.Stage_1.template")
                                            .build());
    assertThat(childErrorNode.getTemplateInfo())
        .isEqualToComparingFieldByField(TemplateInfo.builder()
                                            .templateIdentifier(stageTemplateWithInCorrectInputs.getIdentifier())
                                            .versionLabel(stageTemplateWithInCorrectInputs.getVersionLabel())
                                            .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                            .build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateNestedTemplateInputsWithInValidYamlForPipelineYaml() {
    String pipelineWithIncorrectInputsYaml = readFile("pipeline-with-incorrect-templates.yaml");
    String stageTemplateWithInCorrectInputsYaml = readFile("stage-template-with-incorrect-inputs.yaml");
    String stageTemplateWithCorrectInputsYaml = readFile("stage-template-with-correct-inputs.yaml");
    String stepTemplateYaml = readFile("step-template.yaml");

    TemplateEntity stageTemplateWithInCorrectInputs = convertYamlToTemplateEntity(stageTemplateWithInCorrectInputsYaml);
    TemplateEntity stageTemplateWithCorrectInputs = convertYamlToTemplateEntity(stageTemplateWithCorrectInputsYaml);
    TemplateEntity stepTemplate = convertYamlToTemplateEntity(stepTemplateYaml);
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false, false))
        .thenReturn(Optional.of(stepTemplate));
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "1", false, false))
        .thenReturn(Optional.of(stageTemplateWithInCorrectInputs));
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "2", false, false))
        .thenReturn(Optional.of(stageTemplateWithCorrectInputs));

    ValidateTemplateInputsResponseDTO response = templateInputsValidator.validateNestedTemplateInputsForGivenYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineWithIncorrectInputsYaml, false);
    assertThat(response).isNotNull();
    assertThat(response.isValidYaml()).isFalse();
    assertThat(response.getErrorNodeSummary()).isNotNull();
    ErrorNodeSummary errorNodeSummary = response.getErrorNodeSummary();
    assertThat(errorNodeSummary.getNodeInfo()).isNotNull();
    assertThat(errorNodeSummary.getNodeInfo().getIdentifier()).isEqualTo("pipelineTest");
    assertThat(errorNodeSummary.getNodeInfo().getName()).isEqualTo("Test Pipline");
    assertThat(errorNodeSummary.getNodeInfo().getLocalFqn()).isEqualTo("pipeline");
    assertThat(errorNodeSummary.getChildrenErrorNodes()).isNotNull().isNotEmpty().hasSize(2);

    ErrorNodeSummary firstChild = errorNodeSummary.getChildrenErrorNodes().get(0);
    assertThat(firstChild.getNodeInfo())
        .isEqualToComparingFieldByField(NodeInfo.builder()
                                            .name("Stage 1")
                                            .identifier("Stage_1")
                                            .localFqn("pipeline.stages.Stage_1.template")
                                            .build());
    assertThat(firstChild.getTemplateInfo())
        .isEqualToComparingFieldByField(TemplateInfo.builder()
                                            .templateIdentifier(stageTemplateWithInCorrectInputs.getIdentifier())
                                            .versionLabel(stageTemplateWithInCorrectInputs.getVersionLabel())
                                            .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                            .build());

    ErrorNodeSummary secondChild = errorNodeSummary.getChildrenErrorNodes().get(1);
    assertThat(secondChild.getNodeInfo())
        .isEqualToComparingFieldByField(NodeInfo.builder()
                                            .name("Stage 3")
                                            .identifier("Stage_3")
                                            .localFqn("pipeline.stages.Stage_3.template")
                                            .build());
    assertThat(secondChild.getTemplateInfo())
        .isEqualToComparingFieldByField(TemplateInfo.builder()
                                            .templateIdentifier(stageTemplateWithInCorrectInputs.getIdentifier())
                                            .versionLabel(stageTemplateWithInCorrectInputs.getVersionLabel())
                                            .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                            .build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateWhenNGManagerMarksInvalid() throws IOException {
    Call<ResponseDTO<InputsValidationResponse>> ngManagerReconcileCall = mock(Call.class);
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), anyString(), any(NgManagerRefreshRequestDTO.class));

    doReturn(Response.success(ResponseDTO.newResponse(InputsValidationResponse.builder().isValid(false).build())))
        .when(ngManagerReconcileCall)
        .execute();

    String stageTemplateWithCorrectInputs = readFile("stage-template-with-correct-inputs.yaml");
    String stepTemplateYaml = readFile("step-template.yaml");

    TemplateEntity stepTemplate = convertYamlToTemplateEntity(stepTemplateYaml);
    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false, false))
        .thenReturn(Optional.of(stepTemplate));

    ValidateTemplateInputsResponseDTO response =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(
                convertYamlToTemplateEntity(stageTemplateWithCorrectInputs), EntityGitDetails.builder().build()));
    assertThat(response).isNotNull();
    assertThat(response.isValidYaml()).isFalse();
  }
}
