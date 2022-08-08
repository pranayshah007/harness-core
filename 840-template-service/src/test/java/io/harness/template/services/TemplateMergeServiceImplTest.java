/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.exception.NGTemplateResolveException;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateMergeServiceHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TemplateMergeServiceImplTest extends TemplateServiceTestBase {
  @InjectMocks private TemplateMergeServiceImpl templateMergeService;

  @Mock private NGTemplateServiceHelper templateServiceHelper;
  @InjectMocks TemplateMergeServiceHelper templateMergeServiceHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setup() throws IllegalAccessException {
    on(templateMergeServiceHelper).set("templateServiceHelper", templateServiceHelper);
    on(templateMergeService).set("templateMergeServiceHelper", templateMergeServiceHelper);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateTemplateInputsFromStepTemplateWithRuntimeInputs() {
    String filename = "template-step.yaml";
    String yaml = readFile(filename);
    String templateYaml = templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml);
    assertThat(templateYaml).isNotNull();

    String resFile = "template-step-templateInputs.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateTemplateInputsFromStepTemplateWithoutRuntimeInputs() {
    String filename = "step-template-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml);
    assertThat(templateYaml).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateTemplateInputsFromPipelineTemplateWithRuntimeInputs() {
    String filename = "template-pipeline.yaml";
    String yaml = readFile(filename);
    String templateYaml = templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml);
    assertThat(templateYaml).isNotNull();

    String resFile = "template-pipeline-templateInputs.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateTemplateInputsFromPipelineTemplateWithoutRuntimeInputs() {
    String filename = "pipeline-template-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml);
    assertThat(templateYaml).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeTemplateSpecToPipelineYaml_StepTemplateAtDiffScope() {
    String filename = "template-step.yaml";
    String shellScriptTemplateStepYaml = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .yaml(shellScriptTemplateStepYaml)
                                        .deleted(false)
                                        .versionLabel("1")
                                        .identifier("template1")
                                        .templateScope(Scope.ORG)
                                        .build();

    TemplateEntity templateEntity2 = TemplateEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .yaml(shellScriptTemplateStepYaml)
                                         .deleted(false)
                                         .versionLabel("1")
                                         .identifier("template1")
                                         .templateScope(Scope.PROJECT)
                                         .build();

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, null, "template1", "1", false))
        .thenReturn(Optional.of(templateEntity));
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template1", "", false))
        .thenReturn(Optional.of(templateEntity2));

    String approvalTemplateStepYaml = readFile("approval-step-template.yaml");
    TemplateEntity approvalTemplateEntity = TemplateEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .yaml(approvalTemplateStepYaml)
                                                .deleted(false)
                                                .identifier("template2")
                                                .templateScope(Scope.ACCOUNT)
                                                .build();
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, null, null, "template2", "1", false))
        .thenReturn(Optional.of(approvalTemplateEntity));

    String pipelineYamlFile = "pipeline-with-template-step-diff-scope.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    TemplateMergeResponseDTO pipelineMergeResponse =
        templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    String finalPipelineYaml = pipelineMergeResponse.getMergedPipelineYaml();
    assertThat(finalPipelineYaml).isNotNull();
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).isNotNull();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.ORG)
                                         .moduleInfo(new HashSet<>())
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep11")
                                         .stableTemplate(false)
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .moduleInfo(new HashSet<>())
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep12")
                                         .stableTemplate(true)
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template2")
                                         .versionLabel("1")
                                         .scope(Scope.ACCOUNT)
                                         .moduleInfo(new HashSet<>())
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.approval")
                                         .stableTemplate(false)
                                         .build());
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).hasSize(3);
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).containsAll(templateReferenceSummaryList);

    String resFile = "pipeline-with-template-step-replaced.yaml";
    String resPipeline = readFile(resFile);
    assertThat(finalPipelineYaml).isEqualTo(resPipeline);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeTemplateSpecToPipelineYaml_StepTemplate() {
    String filename = "template-step.yaml";
    String shellScriptTemplateStepYaml = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .yaml(shellScriptTemplateStepYaml)
                                        .identifier("template1")
                                        .deleted(false)
                                        .templateScope(Scope.PROJECT)
                                        .identifier("template1")
                                        .versionLabel("1")
                                        .build();

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template1", "1", false))
        .thenReturn(Optional.of(templateEntity));
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template1", "", false))
        .thenReturn(Optional.of(templateEntity));

    String approvalTemplateStepYaml = readFile("approval-step-template.yaml");
    TemplateEntity approvalTemplateEntity = TemplateEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .yaml(approvalTemplateStepYaml)
                                                .identifier("template2")
                                                .versionLabel("1")
                                                .templateScope(Scope.PROJECT)
                                                .deleted(false)
                                                .build();
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template2", "1", false))
        .thenReturn(Optional.of(approvalTemplateEntity));

    String pipelineYamlFile = "pipeline-with-template-step.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    TemplateMergeResponseDTO pipelineMergeResponse =
        templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    String finalPipelineYaml = pipelineMergeResponse.getMergedPipelineYaml();
    assertThat(finalPipelineYaml).isNotNull();
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).isNotNull();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep11")
                                         .stableTemplate(false)
                                         .moduleInfo(new HashSet<>())
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep12")
                                         .stableTemplate(true)
                                         .moduleInfo(new HashSet<>())
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template2")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.approval")
                                         .stableTemplate(false)
                                         .moduleInfo(new HashSet<>())
                                         .build());
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).hasSize(3);
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).containsAll(templateReferenceSummaryList);

    String resFile = "pipeline-with-template-step-replaced.yaml";
    String resPipeline = readFile(resFile);
    assertThat(finalPipelineYaml).isEqualTo(resPipeline);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeTemplateSpecToPipelineYaml_StageTemplate() {
    String stageTemplateFileName = "stage-template.yaml";
    String stageTemplateYaml = readFile(stageTemplateFileName);
    TemplateEntity stageTemplateEntity = TemplateEntity.builder()
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_ID)
                                             .projectIdentifier(PROJECT_ID)
                                             .yaml(stageTemplateYaml)
                                             .templateScope(Scope.PROJECT)
                                             .identifier("stageTemplate")
                                             .deleted(false)
                                             .build();

    String httpTemplateFileName = "http-step-template.yaml";
    String httpTemplateYaml = readFile(httpTemplateFileName);
    TemplateEntity httpTemplateEntity = TemplateEntity.builder()
                                            .accountId(ACCOUNT_ID)
                                            .orgIdentifier(ORG_ID)
                                            .projectIdentifier(PROJECT_ID)
                                            .yaml(httpTemplateYaml)
                                            .identifier("httpTemplate")
                                            .templateScope(Scope.PROJECT)
                                            .deleted(false)
                                            .build();

    String approvalTemplateFileName = "approval-step-template.yaml";
    String approvalTemplateYaml = readFile(approvalTemplateFileName);
    TemplateEntity approvalTemplateEntity = TemplateEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .yaml(approvalTemplateYaml)
                                                .identifier("approvalTemplate")
                                                .templateScope(Scope.PROJECT)
                                                .deleted(false)
                                                .build();

    when(
        templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "1", false))
        .thenReturn(Optional.of(stageTemplateEntity));
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false))
        .thenReturn(Optional.of(httpTemplateEntity));
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", "1", false))
        .thenReturn(Optional.of(approvalTemplateEntity));

    String pipelineYamlFile = "pipeline-with-stage-template.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    TemplateMergeResponseDTO pipelineMergeResponse =
        templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    String finalPipelineYaml = pipelineMergeResponse.getMergedPipelineYaml();
    assertThat(finalPipelineYaml).isNotNull();
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).isNotNull().hasSize(1);
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries())
        .contains(TemplateReferenceSummary.builder()
                      .templateIdentifier("stageTemplate")
                      .versionLabel("1")
                      .scope(Scope.PROJECT)
                      .fqn("pipeline.stages.qaStage")
                      .moduleInfo(new HashSet<>())
                      .build());

    String resFile = "pipeline-with-stage-template-replaced.yaml";
    String resPipeline = readFile(resFile);
    assertThat(finalPipelineYaml).isEqualTo(resPipeline);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testmergeTemplateInputsInObjectWithInfiniteRecursion() {
    String filename = "stage-temp-inf-rec.yaml";
    String yaml = readFile(filename);

    TemplateEntity templateEntity1 = TemplateEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .yaml(yaml)
                                         .build();
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(
             anyString(), anyString(), anyString(), any(), any(), eq(false)))
        .thenReturn(Optional.of(templateEntity1));

    String pipelineYamlFile = "pipeline-temp-inf-rec.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);

    assertThatThrownBy(
        () -> templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exponentially growing template nesting. Aborting");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeTemplateSpecToPipelineYaml_InvalidStepTemplate() {
    String filename = "template-step.yaml";
    String shellScriptTemplateStepYaml = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .yaml(shellScriptTemplateStepYaml)
                                        .deleted(false)
                                        .build();

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template1", "1", false))
        .thenReturn(Optional.of(templateEntity));

    String approvalTemplateStepYaml = readFile("approval-step-template-without-runtime-inputs.yaml");
    TemplateEntity approvalTemplateEntity = TemplateEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .yaml(approvalTemplateStepYaml)
                                                .deleted(false)
                                                .build();
    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "template2", "2", false))
        .thenReturn(Optional.of(approvalTemplateEntity));

    String pipelineYamlFile = "pipeline-with-invalid-template-steps.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    try {
      templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    } catch (NGTemplateResolveException ngTemplateResolveException) {
      assertThat(ngTemplateResolveException.getErrorResponseDTO()).isNotNull();
      assertThat(ngTemplateResolveException.getErrorResponseDTO().getErrorMap()).hasSize(3);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeTemplateSpecToPipelineYamlHavingTemplateFields() {
    String filename = "http-step-template.yaml";
    String httpStepTemplate = readFile(filename);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .yaml(httpStepTemplate)
                                        .identifier("httpTemplate")
                                        .templateScope(Scope.PROJECT)
                                        .deleted(false)
                                        .versionLabel("1")
                                        .build();

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", false))
        .thenReturn(Optional.of(templateEntity));

    String pipelineYamlFile = "pipeline-with-template-field.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    TemplateMergeResponseDTO pipelineMergeResponse =
        templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    String finalPipelineYaml = pipelineMergeResponse.getMergedPipelineYaml();
    assertThat(finalPipelineYaml).isNotNull();
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).isNotNull();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("httpTemplate")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .fqn("pipeline.stages.ci.spec.execution.steps.http")
                                         .moduleInfo(new HashSet<>())
                                         .build());
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).hasSize(1);
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).containsAll(templateReferenceSummaryList);

    String resFile = "pipeline-with-template-field-replaced.yaml";
    String resPipeline = readFile(resFile);
    assertThat(finalPipelineYaml).isEqualTo(resPipeline);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testApplyTemplatesYamlForOpaPolicy() {
    String pipelineTemplateFileName = "pipeline-template-for-opa-policy.yaml";
    String pipelineTemplateYaml = readFile(pipelineTemplateFileName);
    TemplateEntity pipelineTemplateEntity = TemplateEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .yaml(pipelineTemplateYaml)
                                                .identifier("PipelineTemplevel1")
                                                .templateScope(Scope.PROJECT)
                                                .deleted(false)
                                                .versionLabel("1")
                                                .build();

    String stageTemplateFileName = "stage-template-for-opa-policy.yaml";
    String stageTemplateYaml = readFile(stageTemplateFileName);
    TemplateEntity stageTemplateEntity = TemplateEntity.builder()
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_ID)
                                             .projectIdentifier(PROJECT_ID)
                                             .yaml(stageTemplateYaml)
                                             .templateScope(Scope.PROJECT)
                                             .identifier("stg_temp2")
                                             .deleted(false)
                                             .versionLabel("1")
                                             .build();

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "PipelineTemplevel1", "1", false))
        .thenReturn(Optional.of(pipelineTemplateEntity));

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_ID, PROJECT_ID, "stg_temp2", "1", false))
        .thenReturn(Optional.of(stageTemplateEntity));

    String pipelineYamlFile = "pipeline-with-template-for-opa-policy.yaml";
    String pipelineYaml = readFile(pipelineYamlFile);
    TemplateMergeResponseDTO pipelineMergeResponse =
        templateMergeService.applyTemplatesToYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, true);
    String finalPipelineYaml = pipelineMergeResponse.getMergedPipelineYamlWithTemplateRef();
    assertThat(finalPipelineYaml).isNotNull();
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).isNotNull();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("PipelineTemplevel1")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .moduleInfo(new HashSet<>())
                                         .fqn("pipeline")
                                         .build());
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).hasSize(1);
    assertThat(pipelineMergeResponse.getTemplateReferenceSummaries()).containsAll(templateReferenceSummaryList);

    String resFile = "pipeline-with-template-with-opa-policy-response.yaml";
    String resPipeline = readFile(resFile);
    assertThat(finalPipelineYaml).isEqualTo(resPipeline);
  }
}
