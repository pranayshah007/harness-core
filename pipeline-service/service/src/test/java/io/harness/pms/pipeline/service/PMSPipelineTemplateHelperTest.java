/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSPipelineTemplateHelperTest extends CategoryTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock private PipelineEnforcementService pipelineEnforcementService;
  @InjectMocks private PMSPipelineTemplateHelper pipelineTemplateHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PROJECT_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String GIVEN_YAML = "yaml";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineTemplateHelper =
        new PMSPipelineTemplateHelper(pmsFeatureFlagHelper, templateResourceClient, pipelineEnforcementService);
    doReturn(true).when(pipelineEnforcementService).isFeatureRestricted(any(), anyString());
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidTemplateInPipelineHasTemplateRef() throws IOException {
    String fileName = "pipeline-with-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(TemplateApplyRequestDTO.class), any());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(givenYaml).build())));
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper
            .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml, BOOLEAN_FALSE_VALUE)
            .getMergedPipelineYaml();
    assertThat(resolveTemplateRefsInPipeline).isEqualTo(givenYaml);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testInValidTemplateInPipelineWhenDoesNotContainTemplateRef() throws IOException {
    String fileName = "pipeline-with-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null,
            "false", TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).build(), false);
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().build();
    when(callRequest.execute())
        .thenThrow(new InvalidRequestException("Exception in resolving template refs in given yaml."));
    assertThatThrownBy(()
                           -> pipelineTemplateHelper.resolveTemplateRefsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml, BOOLEAN_FALSE_VALUE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exception in resolving template refs in given yaml.");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetTemplateReferencesForGivenYamlWhenFFIsOnAndGitSyncNotEnabled() throws IOException {
    Call<ResponseDTO<List<EntityDetailProtoDTO>>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .getTemplateReferenceForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null,
            TemplateReferenceRequestDTO.builder().yaml(GIVEN_YAML).build());
    List<EntityDetailProtoDTO> expected =
        Collections.singletonList(EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.TEMPLATE).build());
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(expected)));

    List<EntityDetailProtoDTO> finalList =
        pipelineTemplateHelper.getTemplateReferencesForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML);
    assertThat(finalList).isEqualTo(expected);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetRefreshedYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    RefreshResponseDTO refreshResponseDTO = RefreshResponseDTO.builder().refreshedYaml("refreshed yaml").build();
    Call<ResponseDTO<RefreshResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .getRefreshedYaml(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null, "false", refreshRequest);
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(refreshResponseDTO)));

    RefreshResponseDTO refreshedResponse =
        pipelineTemplateHelper.getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, null, "false");
    assertThat(refreshedResponse).isEqualTo(refreshResponseDTO);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForGivenYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder()
            .validYaml(false)
            .errorNodeSummary(ErrorNodeSummary.builder().build())
            .build();
    Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .validateTemplateInputsForGivenYaml(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null, "false", refreshRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(validateTemplateInputsResponseDTO)));

    ValidateTemplateInputsResponseDTO responseDTO = pipelineTemplateHelper.validateTemplateInputsForGivenYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, null, "false");
    assertThat(responseDTO).isEqualTo(validateTemplateInputsResponseDTO);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshAllTemplatesForYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    YamlFullRefreshResponseDTO refreshResponseDTO =
        YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml("refreshed yaml").build();
    Call<ResponseDTO<YamlFullRefreshResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .refreshAllTemplatesForYaml(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null, "false", refreshRequest);
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(refreshResponseDTO)));

    YamlFullRefreshResponseDTO refreshedResponse =
        pipelineTemplateHelper.refreshAllTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, null, "false");
    assertThat(refreshedResponse).isEqualTo(refreshResponseDTO);
  }
}
