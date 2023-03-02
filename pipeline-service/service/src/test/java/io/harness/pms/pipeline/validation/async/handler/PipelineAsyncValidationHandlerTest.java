/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineAsyncValidationHandlerTest extends CategoryTest {
  PipelineAsyncValidationHandler pipelineAsyncValidationHandler;
  @Mock PipelineAsyncValidationService validationService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineGovernanceService pipelineGovernanceService;

  PipelineEntity pipelineEntity;
  PipelineValidationEvent pipelineValidationEvent;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    pipelineEntity = PipelineEntity.builder().accountId("acc").orgIdentifier("org").projectIdentifier("proj").build();
    pipelineValidationEvent = PipelineValidationEvent.builder()
                                  .uuid("abc123")
                                  .params(ValidationParams.builder().pipelineEntity(pipelineEntity).build())
                                  .build();
    pipelineAsyncValidationHandler = PipelineAsyncValidationHandler.builder()
                                         .validationEvent(pipelineValidationEvent)
                                         .validationService(validationService)
                                         .pipelineTemplateHelper(pipelineTemplateHelper)
                                         .pipelineGovernanceService(pipelineGovernanceService)
                                         .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunWithTemplateFailure() {
    doThrow(new InvalidRequestException("template failed"))
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, true, false);
    assertThatThrownBy(() -> pipelineAsyncValidationHandler.run())
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("template failed");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSuccessfulRun() {
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build();
    doReturn(templateMergeResponse)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, true, false);
    doReturn(new HashSet<>(Arrays.asList("CD", "CI")))
        .when(pipelineTemplateHelper)
        .getTemplatesModuleInfo(templateMergeResponse);
    doReturn(io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "yaml");
    pipelineAsyncValidationHandler.run();
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS,
            ValidationResult.builder()
                .templateInputsResponse(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build())
                .build());
    assertThat(pipelineEntity.getTemplateModules()).containsExactly("CD", "CI");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForFailure() {
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "yaml");
    pipelineAsyncValidationHandler.evaluatePoliciesAndUpdateResult(pipelineEntity,
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build(),
        ValidationResult.builder().build());
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.FAILURE,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                .build());
    verify(validationService, times(0))
        .updateEvent("abc123", ValidationStatus.SUCCESS,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                .build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForSuccess() {
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "yaml");
    pipelineAsyncValidationHandler.evaluatePoliciesAndUpdateResult(pipelineEntity,
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build(),
        ValidationResult.builder().build());
    verify(validationService, times(0))
        .updateEvent("abc123", ValidationStatus.FAILURE,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                .build());
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.SUCCESS,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                .build());
  }
}
