/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.validations;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.validations.impl.ArtifactTriggerValidator;
import io.harness.ngtriggers.validations.impl.ManifestTriggerValidator;
import io.harness.ngtriggers.validations.impl.PipelineRefValidator;
import io.harness.ngtriggers.validations.impl.TriggerIdentifierRefValidator;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
        .fetchPipelineForTrigger(ngTriggerEntity);

    PipelineRefValidator pipelineRefValidator = new PipelineRefValidator(buildTriggerHelper);
    ValidationResult validate =
        pipelineRefValidator.validate(TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build());
    assertThat(validate.isSuccess()).isFalse();
    assertThat(validate.getMessage()).isEqualTo("Pipeline with Ref -> acc:org:prj:pipeline does not exists");
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
}
