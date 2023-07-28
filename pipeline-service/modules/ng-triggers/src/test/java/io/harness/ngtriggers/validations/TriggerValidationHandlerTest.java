/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.validations;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.validations.impl.ArtifactTriggerValidator;
import io.harness.ngtriggers.validations.impl.ManifestTriggerValidator;
import io.harness.ngtriggers.validations.impl.PipelineRefValidator;
import io.harness.ngtriggers.validations.impl.TriggerIdentifierRefValidator;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class TriggerValidationHandlerTest extends CategoryTest {
  @Mock TriggerIdentifierRefValidator triggerIdentifierRefValidator;
  @Mock PipelineRefValidator pipelineRefValidator;
  @Mock ManifestTriggerValidator manifestTriggerValidator;
  @Mock ArtifactTriggerValidator artifactTriggerValidator;
  @Mock BuildTriggerHelper buildTriggerHelper;
  @InjectMocks TriggerValidationHandler triggerValidationHandler;
  NGTriggerEntity ngTriggerEntity;
  TriggerDetails triggerDetails;
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock GeneratorFactory generatorFactory;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId("acc")
                          .projectIdentifier("prj")
                          .orgIdentifier("org")
                          .targetIdentifier("pipeline")
                          .type(NGTriggerType.WEBHOOK)
                          .identifier("id")
                          .name("name")
                          .build();

    triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testApplyValidationsWebhook() {
    NGTriggerEntity ngTriggerEntityWebhook = NGTriggerEntity.builder()
                                                 .accountId("acc")
                                                 .projectIdentifier("prj")
                                                 .orgIdentifier("org")
                                                 .targetIdentifier("pipeline")
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .identifier("id")
                                                 .name("name")
                                                 .build();

    TriggerDetails triggerDetailsWebhook = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityWebhook).build();
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    when(pipelineRefValidator.validate(any())).thenReturn(validationResult);
    when(triggerIdentifierRefValidator.validate(any())).thenReturn(validationResult);
    when(manifestTriggerValidator.validate(any())).thenReturn(validationResult);
    when(artifactTriggerValidator.validate(any())).thenReturn(validationResult);

    ValidationResult finalValidationResult = triggerValidationHandler.applyValidations(triggerDetailsWebhook);
    assertThat(finalValidationResult).isEqualToComparingFieldByField(validationResult);
    verify(pipelineRefValidator, times(1)).validate(triggerDetailsWebhook);
    verify(triggerIdentifierRefValidator, times(1)).validate(triggerDetailsWebhook);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testApplyValidationsScheduled() {
    NGTriggerEntity ngTriggerEntityScheduled = NGTriggerEntity.builder()
                                                   .accountId("acc")
                                                   .projectIdentifier("prj")
                                                   .orgIdentifier("org")
                                                   .targetIdentifier("pipeline")
                                                   .type(NGTriggerType.SCHEDULED)
                                                   .identifier("id")
                                                   .name("name")
                                                   .build();
    TriggerDetails triggerDetailsScheduled = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityScheduled).build();
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    when(pipelineRefValidator.validate(any())).thenReturn(validationResult);
    when(triggerIdentifierRefValidator.validate(any())).thenReturn(validationResult);
    when(manifestTriggerValidator.validate(any())).thenReturn(validationResult);
    when(artifactTriggerValidator.validate(any())).thenReturn(validationResult);
    ValidationResult finalValidationResult = triggerValidationHandler.applyValidations(triggerDetailsScheduled);
    assertThat(finalValidationResult).isEqualToComparingFieldByField(validationResult);
    verify(pipelineRefValidator, times(1)).validate(triggerDetailsScheduled);
    verify(triggerIdentifierRefValidator, times(1)).validate(triggerDetailsScheduled);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testApplyValidationsArtifact() {
    NGTriggerEntity ngTriggerEntityArtifact = NGTriggerEntity.builder()
                                                  .accountId("acc")
                                                  .projectIdentifier("prj")
                                                  .orgIdentifier("org")
                                                  .targetIdentifier("pipeline")
                                                  .type(NGTriggerType.ARTIFACT)
                                                  .identifier("id")
                                                  .name("name")
                                                  .build();
    TriggerDetails triggerDetailsArtifact = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityArtifact).build();
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    when(pipelineRefValidator.validate(any())).thenReturn(validationResult);
    when(triggerIdentifierRefValidator.validate(any())).thenReturn(validationResult);
    when(manifestTriggerValidator.validate(any())).thenReturn(validationResult);
    when(artifactTriggerValidator.validate(any())).thenReturn(validationResult);
    ValidationResult finalValidationResult = triggerValidationHandler.applyValidations(triggerDetailsArtifact);
    assertThat(finalValidationResult).isEqualToComparingFieldByField(validationResult);
    verify(pipelineRefValidator, times(1)).validate(triggerDetailsArtifact);
    verify(triggerIdentifierRefValidator, times(1)).validate(triggerDetailsArtifact);
    verify(artifactTriggerValidator, times(1)).validate(triggerDetailsArtifact);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testApplyValidationsManifest() {
    NGTriggerEntity ngTriggerEntityManifest = NGTriggerEntity.builder()
                                                  .accountId("acc")
                                                  .projectIdentifier("prj")
                                                  .orgIdentifier("org")
                                                  .targetIdentifier("pipeline")
                                                  .type(NGTriggerType.MANIFEST)
                                                  .identifier("id")
                                                  .name("name")
                                                  .build();
    TriggerDetails triggerDetailsManifest = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityManifest).build();
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    when(pipelineRefValidator.validate(any())).thenReturn(validationResult);
    when(triggerIdentifierRefValidator.validate(any())).thenReturn(validationResult);
    when(manifestTriggerValidator.validate(any())).thenReturn(validationResult);
    when(artifactTriggerValidator.validate(any())).thenReturn(validationResult);
    ValidationResult finalValidationResult = triggerValidationHandler.applyValidations(triggerDetailsManifest);
    assertThat(finalValidationResult).isEqualToComparingFieldByField(validationResult);
    verify(pipelineRefValidator, times(1)).validate(triggerDetailsManifest);
    verify(triggerIdentifierRefValidator, times(1)).validate(triggerDetailsManifest);
    verify(manifestTriggerValidator, times(1)).validate(triggerDetailsManifest);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEvaluate() {
    List<TriggerValidator> applicableValidators = triggerValidationHandler.getApplicableValidators(triggerDetails);
    assertThat(applicableValidators).containsExactlyInAnyOrder(pipelineRefValidator, triggerIdentifierRefValidator);

    ngTriggerEntity.setType(NGTriggerType.SCHEDULED);
    applicableValidators = triggerValidationHandler.getApplicableValidators(triggerDetails);
    assertThat(applicableValidators).containsExactlyInAnyOrder(pipelineRefValidator, triggerIdentifierRefValidator);

    ngTriggerEntity.setType(NGTriggerType.MANIFEST);
    applicableValidators = triggerValidationHandler.getApplicableValidators(triggerDetails);
    assertThat(applicableValidators)
        .containsExactlyInAnyOrder(pipelineRefValidator, triggerIdentifierRefValidator, manifestTriggerValidator);

    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    applicableValidators = triggerValidationHandler.getApplicableValidators(triggerDetails);
    assertThat(applicableValidators)
        .containsExactlyInAnyOrder(pipelineRefValidator, triggerIdentifierRefValidator, artifactTriggerValidator);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPipelineRefValidator() {
    doReturn(Optional.empty())
        .doReturn(Optional.of("placeholder_for_actual_pipeline_yml"))
        .when(buildTriggerHelper)
        .fetchPipelineYamlForTrigger(triggerDetails);

    PipelineRefValidator pipelineRefValidator = new PipelineRefValidator(buildTriggerHelper);
    ValidationResult validate =
        pipelineRefValidator.validate(TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build());
    assertThat(validate.isSuccess()).isFalse();
    assertThat(validate.getMessage()).isEqualTo("Pipeline with Ref -> acc:org:prj:pipeline does not exists");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPipelineRefValidatorWhenPipelineBranchNameIsExpression() {
    doReturn(Optional.empty()).doReturn(Optional.empty()).when(buildTriggerHelper).fetchPipelineYamlForTrigger(any());

    PipelineRefValidator pipelineRefValidator = new PipelineRefValidator(buildTriggerHelper);
    ValidationResult validate = pipelineRefValidator.validate(
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder().pipelineBranchName("<+trigger.branch>").build())
            .build());
    assertThat(validate.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPipelineRefValidatorWhenPipelineBranchNameIsNull() {
    TriggerDetails triggerDetails1 = TriggerDetails.builder()
                                         .ngTriggerEntity(ngTriggerEntity)
                                         .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                                         .build();
    doReturn(Optional.of("placeholder_for_actual_pipeline_yml"))
        .when(buildTriggerHelper)
        .fetchPipelineYamlForTrigger(triggerDetails1);

    PipelineRefValidator pipelineRefValidator = new PipelineRefValidator(buildTriggerHelper);
    ValidationResult validate = pipelineRefValidator.validate(triggerDetails1);
    assertThat(validate.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testTriggerIdentifierRefValidator() {
    TriggerIdentifierRefValidator triggerIdentifierRefValidator = new TriggerIdentifierRefValidator();
    ValidationResult validate = triggerIdentifierRefValidator.validate(
        TriggerDetails.builder().ngTriggerEntity(NGTriggerEntity.builder().build()).build());

    assertThat(validate.isSuccess()).isFalse();
    assertThat(validate.getMessage())
        .isEqualTo("Identifier can not be null for trigger\n"
            + "Name can not be null for trigger\n"
            + "AccountId can not be null for trigger\n"
            + "OrgIdentifier can not be null for trigger\n"
            + "ProjectIdentifier can not be null for trigger\n"
            + "PipelineIdentifier can not be null for trigger\n");

    validate = triggerIdentifierRefValidator.validate(triggerDetails);
    assertThat(validate.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testArtifactTriggerValidatorWithRemotePipeline() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    artifactTriggerValidator = spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    ClassLoader classLoader = getClass().getClassLoader();
    String artifactTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-remote-pipeline.yaml")),
            StandardCharsets.UTF_8);
    TriggerDetails artifactTriggerDetails =
        ngTriggerElementMapper.toTriggerDetails("accountId", "orgId", "projectId", artifactTriggerYaml, true);
    String pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline.yaml")), StandardCharsets.UTF_8);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             artifactTriggerDetails.getNgTriggerEntity().getTargetIdentifier(), "accountId", "orgId", "projectId",
             artifactTriggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplatesResolvedPipelineResponseDTO.builder().resolvedTemplatesPipelineYaml(pipelineYaml).build())));
    doNothing().when(artifactTriggerValidator).validateBasedOnArtifactType(any());
    when(generatorFactory.retrievePollingItemGenerator(any()))
        .thenReturn(new AcrPollingItemGenerator(validationHelper));
    ValidationResult validate = artifactTriggerValidator.validate(artifactTriggerDetails);
    assertThat(validate.isSuccess()).isTrue();
  }
}
