/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.validations.impl;

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
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.HttpHelmPollingItemGenerator;
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

public class ManifestTriggerValidatorTest extends CategoryTest {
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private PipelineServiceClient pipelineServiceClient;
  private HttpHelmPollingItemGenerator httpHelmPollingItemGenerator;
  @Mock private GeneratorFactory generatorFactory;
  private BuildTriggerHelper buildTriggerHelper;
  private ManifestTriggerValidator manifestTriggerValidator;

  private String ngTriggerYaml_manifest;
  private String pipelineYaml;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    ngTriggerYaml_manifest =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-http-v2.yaml")),
            StandardCharsets.UTF_8);
    pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline.yaml")), StandardCharsets.UTF_8);
    buildTriggerHelper = spy(new BuildTriggerHelper(pipelineServiceClient));
    manifestTriggerValidator = new ManifestTriggerValidator(buildTriggerHelper, generatorFactory);
    httpHelmPollingItemGenerator = new HttpHelmPollingItemGenerator(buildTriggerHelper);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateBasedOnManifestType() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", ngTriggerYaml_manifest, true);
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(httpHelmPollingItemGenerator);
    manifestTriggerValidator.validateBasedOnManifestType(buildTriggerOpsData);

    // invalid trigger
    buildTriggerOpsData.getTriggerSpecMap().clear();
    assertThatThrownBy(() -> manifestTriggerValidator.validateBasedOnManifestType(buildTriggerOpsData))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression type might contain some unresolved expressions which could not be evaluated.");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ManifestTriggerValidator spyManifestTriggerValidator =
        spy(new ManifestTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", ngTriggerYaml_manifest, true);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplatesResolvedPipelineResponseDTO.builder().resolvedTemplatesPipelineYaml(pipelineYaml).build())));
    doNothing().when(spyManifestTriggerValidator).validateBasedOnManifestType(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(httpHelmPollingItemGenerator);
    ValidationResult validate = spyManifestTriggerValidator.validate(triggerDetails);
    assertThat(validate.isSuccess()).isTrue();
  }
}
