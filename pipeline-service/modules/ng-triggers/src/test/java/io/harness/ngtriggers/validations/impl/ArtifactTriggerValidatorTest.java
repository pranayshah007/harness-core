/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.validations.impl;

import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class ArtifactTriggerValidatorTest extends CategoryTest {
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private PipelineServiceClient pipelineServiceClient;
  private DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;

  private GcrPollingItemGenerator gcrPollingItemGenerator;
  @Mock private GeneratorFactory generatorFactory;
  private BuildTriggerHelper buildTriggerHelper;
  private ArtifactTriggerValidator artifactTriggerValidator;

  private String ngTriggerYaml_artifact_dockerregistry;
  private String pipelineYaml;

  private String gcr_artifact_trigger;
  private String gcr_artifact_pipeline;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    ngTriggerYaml_artifact_dockerregistry = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-dockerregistry-v2.yaml")),
        StandardCharsets.UTF_8);
    pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline.yaml")), StandardCharsets.UTF_8);

    gcr_artifact_trigger = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gcr_artifact_trigger.yaml")), StandardCharsets.UTF_8);

    gcr_artifact_pipeline = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gcr_artifact_pipeline.yaml")), StandardCharsets.UTF_8);

    buildTriggerHelper = spy(new BuildTriggerHelper(pipelineServiceClient));
    artifactTriggerValidator = new ArtifactTriggerValidator(buildTriggerHelper, generatorFactory);
    dockerRegistryPollingItemGenerator = new DockerRegistryPollingItemGenerator(buildTriggerHelper);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateBasedOnArtifactType() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", ngTriggerYaml_artifact_dockerregistry, true);
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);
    artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData);

    // invalid trigger
    buildTriggerOpsData.getTriggerSpecMap().clear();
    assertThatThrownBy(() -> artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression type might contain some unresolved expressions which could not be evaluated.");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateBasedOnArtifactTypeThrowsException() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", ngTriggerYaml_artifact_dockerregistry, true);
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(null);
    assertThatThrownBy(() -> artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to find Polling Generator For Trigger. Please Check Manifest Config In Trigger");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ArtifactTriggerValidator spyArtifactTriggerValidator =
        spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", ngTriggerYaml_artifact_dockerregistry, true);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplatesResolvedPipelineResponseDTO.builder().resolvedTemplatesPipelineYaml(pipelineYaml).build())));
    doNothing().when(spyArtifactTriggerValidator).validateBasedOnArtifactType(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);
    ValidationResult validate = spyArtifactTriggerValidator.validate(triggerDetails);
    assertThat(validate.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateThrowsExceptionForServiceV1() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ArtifactTriggerValidator spyArtifactTriggerValidator =
        spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", gcr_artifact_trigger, false);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(TemplatesResolvedPipelineResponseDTO.builder()
                                                                 .resolvedTemplatesPipelineYaml(gcr_artifact_pipeline)
                                                                 .build())));
    doNothing().when(spyArtifactTriggerValidator).validateBasedOnArtifactType(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(gcrPollingItemGenerator);
    ValidationResult validate = spyArtifactTriggerValidator.validate(triggerDetails);
    assertThat(validate.isSuccess()).isFalse();
  }
}
