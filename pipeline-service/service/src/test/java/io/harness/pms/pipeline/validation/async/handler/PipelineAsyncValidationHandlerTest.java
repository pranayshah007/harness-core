/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class PipelineAsyncValidationHandlerTest extends CategoryTest {
  PipelineAsyncValidationHandler pipelineAsyncValidationHandler;
  @Mock PipelineAsyncValidationService validationService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock PipelineRefreshService pipelineRefreshService;

  PipelineEntity pipelineEntity;
  PipelineValidationEvent pipelineValidationEvent;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId("acc")
                         .orgIdentifier("org")
                         .projectIdentifier("proj")
                         .identifier("pipeline")
                         .build();
    pipelineValidationEvent = PipelineValidationEvent.builder()
                                  .uuid("abc123")
                                  .params(ValidationParams.builder().pipelineEntity(pipelineEntity).build())
                                  .build();
    pipelineAsyncValidationHandler = new PipelineAsyncValidationHandler(pipelineValidationEvent, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSuccessfulRun() {
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    doReturn(validateTemplateInputsResponseDTO)
        .when(pipelineRefreshService)
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false");
    doReturn(templateMergeResponse)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, true, false);
    doReturn(new HashSet<>(Arrays.asList("CD", "CI")))
        .when(pipelineTemplateHelper)
        .getTemplatesModuleInfo(templateMergeResponse);

    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
    pipelineAsyncValidationHandler.run();
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    verify(pipelineRefreshService, times(1))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false");
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS,
            ValidationResult.builder()
                .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
                .build());
    assertThat(pipelineEntity.getTemplateModules()).containsExactly("CD", "CI");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForFailure() {
    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
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
    verify(pipelineRefreshService, times(0))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForSuccess() {
    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
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

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testReconcileForFailure() {
    doReturn(ValidateTemplateInputsResponseDTO.builder().validYaml(false).build())
        .when(pipelineRefreshService)
        .validateTemplateInputsInPipeline("acc", "org", "proj", "pipeline", "false");
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().build();
    pipelineAsyncValidationHandler.run();
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    verify(pipelineRefreshService, times(1))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false");
  }
}
