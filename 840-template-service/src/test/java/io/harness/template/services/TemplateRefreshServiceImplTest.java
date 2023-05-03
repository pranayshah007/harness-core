/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO.TemplateResponseDTOBuilder;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.TemplateInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.helpers.TemplateInputsRefreshHelper;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.resources.beans.PermissionTypes;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TemplateRefreshServiceImplTest extends TemplateServiceTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projId";
  private static final String TEMPLATE_IDENTIFIER = "TEMPLATE_ID";
  @InjectMocks TemplateRefreshServiceImpl templateRefreshService;
  @Mock NGTemplateService templateService;
  @Mock TemplateInputsRefreshHelper templateInputsRefreshHelper;
  @Mock TemplateInputsValidator templateInputsValidator;
  @Mock AccessControlClient accessControlClient;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshAndUpdateTemplateWhenTemplateDoesnotExist() {
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(()
                           -> templateRefreshService.refreshAndUpdateTemplate(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, TEMPLATE_IDENTIFIER, "1", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Template with the Identifier %s and versionLabel %s does not exist or has been deleted",
                TEMPLATE_IDENTIFIER, "1"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldRefreshAndUpdateTemplate() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String updatedYaml = readFile("stage-template.yaml");
    String stageTemplateIdentifier = "stageTemplate";
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(TemplateEntity.builder().yaml(yaml).build()));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml, false))
        .thenReturn(updatedYaml);

    templateRefreshService.refreshAndUpdateTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false);

    ArgumentCaptor<TemplateEntity> templateEntityArgumentCaptor = ArgumentCaptor.forClass(TemplateEntity.class);
    verify(templateService)
        .updateTemplateEntity(
            templateEntityArgumentCaptor.capture(), eq(ChangeType.MODIFY), eq(false), eq("Refreshed template inputs"));
    TemplateEntity templateEntity = templateEntityArgumentCaptor.getValue();
    assertThat(templateEntity).isNotNull();
    assertThat(templateEntity.getYaml()).isEqualTo(updatedYaml);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void shouldRefreshAndUpdateTemplateWithCaching() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String updatedYaml = readFile("stage-template.yaml");
    String stageTemplateIdentifier = "stageTemplate";
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(true)))
        .thenReturn(Optional.of(TemplateEntity.builder().yaml(yaml).build()));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml, true))
        .thenReturn(updatedYaml);

    templateRefreshService.refreshAndUpdateTemplate(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", true);

    ArgumentCaptor<TemplateEntity> templateEntityArgumentCaptor = ArgumentCaptor.forClass(TemplateEntity.class);
    verify(templateService)
        .updateTemplateEntity(
            templateEntityArgumentCaptor.capture(), eq(ChangeType.MODIFY), eq(false), eq("Refreshed template inputs"));
    TemplateEntity templateEntity = templateEntityArgumentCaptor.getValue();
    assertThat(templateEntity).isNotNull();
    assertThat(templateEntity.getYaml()).isEqualTo(updatedYaml);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateTemplateInputsForCorrectYaml() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(true)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    ValidateTemplateInputsResponseDTO responseDTO = templateRefreshService.validateTemplateInputsInTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
    assertThat(responseDTO.getErrorNodeSummary()).isNull();
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void shouldValidateTemplateInputsForCorrectYamlWithCaching() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(true)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(true)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    ValidateTemplateInputsResponseDTO responseDTO = templateRefreshService.validateTemplateInputsInTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", true);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
    assertThat(responseDTO.getErrorNodeSummary()).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateTemplateInputsForInCorrectYaml() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    ErrorNodeSummary errorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).build())
            .childrenErrorNodes(new ArrayList<>())
            .build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .thenReturn(
            ValidateTemplateInputsResponseDTO.builder().validYaml(false).errorNodeSummary(errorNodeSummary).build());

    ValidateTemplateInputsResponseDTO responseDTO = templateRefreshService.validateTemplateInputsInTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isFalse();
    assertThat(responseDTO.getErrorNodeSummary()).isEqualTo(errorNodeSummary);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlDiffOnRefreshingTemplate() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String updatedYaml = "updatedYaml";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml, false))
        .thenReturn(updatedYaml);

    YamlDiffResponseDTO responseDTO = templateRefreshService.getYamlDiffOnRefreshingTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getOriginalYaml()).isEqualTo(yaml);
    assertThat(responseDTO.getRefreshedYaml()).isEqualTo(updatedYaml);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetYamlDiffOnRefreshingTemplateWithCaching() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String updatedYaml = "updatedYaml";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(true)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml, true))
        .thenReturn(updatedYaml);

    YamlDiffResponseDTO responseDTO = templateRefreshService.getYamlDiffOnRefreshingTemplate(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", true);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getOriginalYaml()).isEqualTo(yaml);
    assertThat(responseDTO.getRefreshedYaml()).isEqualTo(updatedYaml);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForValidYaml() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    ErrorNodeSummary errorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).build())
            .childrenErrorNodes(new ArrayList<>())
            .build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .thenReturn(
            ValidateTemplateInputsResponseDTO.builder().validYaml(true).errorNodeSummary(errorNodeSummary).build());

    templateRefreshService.recursivelyRefreshTemplates(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false);
    verify(templateService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false, false);
    verify(templateInputsValidator)
        .validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build()));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForValidYamlWithCaching() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity templateEntity = TemplateEntity.builder().yaml(yaml).build();
    ErrorNodeSummary errorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).build())
            .childrenErrorNodes(new ArrayList<>())
            .build();
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), eq(true)))
        .thenReturn(Optional.of(templateEntity));
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build())))
        .thenReturn(
            ValidateTemplateInputsResponseDTO.builder().validYaml(true).errorNodeSummary(errorNodeSummary).build());

    templateRefreshService.recursivelyRefreshTemplates(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", true);
    verify(templateService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1", false, true);
    verify(templateInputsValidator)
        .validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(templateEntity, EntityGitDetails.builder().build()));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForInValidYaml() {
    TemplateResponseDTOBuilder templateResponseDTOBuilder =
        TemplateResponseDTO.builder().accountId(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID);
    String pipelineYaml = "pipeline yaml";
    String updatedPipelineTemplateYaml = readFile("refresh/validate/pipeline-template-with-incorrect-input.yaml");
    String stageYaml = "stage yaml";
    String updatedStageYaml = readFile("stage-template.yaml");
    String pipelineTemplateIdentifier = "pipelineTemplate";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity pipelineTemplateEntity = TemplateEntity.builder().yaml(pipelineYaml).build();
    TemplateEntity stageTemplateEntity = TemplateEntity.builder().yaml(stageYaml).build();
    ErrorNodeSummary stageTemplateErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).versionLabel("1").build())
            .templateResponse(templateResponseDTOBuilder.identifier(stageTemplateIdentifier)
                                  .name(stageTemplateIdentifier)
                                  .versionLabel("1")
                                  .yaml(stageYaml)
                                  .build())
            .build();
    ErrorNodeSummary pipelineTemplateErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(pipelineTemplateIdentifier).build())
            .templateInfo(
                TemplateInfo.builder().templateIdentifier(pipelineTemplateIdentifier).versionLabel("1").build())
            .templateResponse(templateResponseDTOBuilder.identifier(pipelineTemplateIdentifier)
                                  .name(pipelineTemplateIdentifier)
                                  .versionLabel("1")
                                  .yaml(pipelineYaml)
                                  .build())
            .childrenErrorNodes(Arrays.asList(
                stageTemplateErrorNodeSummary, stageTemplateErrorNodeSummary, stageTemplateErrorNodeSummary))
            .build();
    when(templateService.get(
             anyString(), anyString(), anyString(), eq(pipelineTemplateIdentifier), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(pipelineTemplateEntity));
    when(templateService.get(
             anyString(), anyString(), anyString(), eq(stageTemplateIdentifier), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(stageTemplateEntity));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .thenReturn(updatedPipelineTemplateYaml);
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageYaml, false))
        .thenReturn(updatedStageYaml);
    when(templateInputsValidator.validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             new TemplateEntityGetResponse(pipelineTemplateEntity, EntityGitDetails.builder().build())))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(false)
                        .errorNodeSummary(pipelineTemplateErrorNodeSummary)
                        .build());

    InOrder inOrder = inOrder(templateInputsRefreshHelper);
    templateRefreshService.recursivelyRefreshTemplates(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineTemplateIdentifier, "1", false);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, pipelineTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, stageTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(templateInputsValidator)
        .validateNestedTemplateInputsForTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            new TemplateEntityGetResponse(pipelineTemplateEntity, EntityGitDetails.builder().build()));
    inOrder.verify(templateInputsRefreshHelper, times(1))
        .refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageYaml, false);
    inOrder.verify(templateInputsRefreshHelper, times(1))
        .refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForYaml_InValidYamlAndDifferentScope() {
    TemplateResponseDTOBuilder templateResponseDTOBuilder =
        TemplateResponseDTO.builder().accountId(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID);
    String pipelineYaml = "pipeline yaml";
    String refreshedPipelineYaml = "Refreshed yaml";
    String pipelineTemplateYaml = "pipeline template yaml";
    String updatedPipelineTemplateYaml = readFile("refresh/validate/pipeline-template-with-incorrect-input.yaml");
    String stageYaml = "stage yaml";
    String updatedStageYaml = readFile("stage-template.yaml");
    String updatedAccountLevelStageYaml = readFile("refresh/validate/stage-template-account-level.yaml");
    String pipelineTemplateIdentifier = "pipelineTemplate";
    String stageTemplateIdentifier = "stageTemplate";
    TemplateEntity pipelineTemplateEntity = TemplateEntity.builder().yaml(pipelineTemplateYaml).build();
    TemplateEntity stageTemplateEntity = TemplateEntity.builder().yaml(stageYaml).build();
    ErrorNodeSummary stageTemplateErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).versionLabel("1").build())
            .templateResponse(templateResponseDTOBuilder.identifier(stageTemplateIdentifier)
                                  .name(stageTemplateIdentifier)
                                  .versionLabel("1")
                                  .yaml(stageYaml)
                                  .build())
            .build();
    ErrorNodeSummary accountLevelStageTemplateErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(stageTemplateIdentifier).build())
            .templateInfo(TemplateInfo.builder().templateIdentifier(stageTemplateIdentifier).versionLabel("1").build())
            .templateResponse(TemplateResponseDTO.builder()
                                  .accountId(ACCOUNT_ID)
                                  .identifier(stageTemplateIdentifier)
                                  .name(stageTemplateIdentifier)
                                  .versionLabel("1")
                                  .yaml(stageYaml)
                                  .build())
            .build();
    ErrorNodeSummary pipelineTemplateErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder().identifier(pipelineTemplateIdentifier).build())
            .templateInfo(
                TemplateInfo.builder().templateIdentifier(pipelineTemplateIdentifier).versionLabel("1").build())
            .templateResponse(templateResponseDTOBuilder.identifier(pipelineTemplateIdentifier)
                                  .versionLabel("1")
                                  .templateEntityType(TemplateEntityType.PIPELINE_TEMPLATE)
                                  .name(pipelineTemplateIdentifier)
                                  .yaml(pipelineTemplateYaml)
                                  .build())
            .childrenErrorNodes(Arrays.asList(stageTemplateErrorNodeSummary, accountLevelStageTemplateErrorNodeSummary,
                stageTemplateErrorNodeSummary))
            .build();
    when(templateService.get(
             anyString(), anyString(), anyString(), eq(pipelineTemplateIdentifier), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(pipelineTemplateEntity));
    when(templateService.get(
             anyString(), anyString(), anyString(), eq(stageTemplateIdentifier), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(stageTemplateEntity));
    when(templateService.get(
             anyString(), eq(null), eq(null), eq(stageTemplateIdentifier), anyString(), eq(false), eq(false)))
        .thenReturn(Optional.of(stageTemplateEntity));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineTemplateYaml, false))
        .thenReturn(updatedPipelineTemplateYaml);
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageYaml, false))
        .thenReturn(updatedStageYaml);
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, null, null, stageYaml, false))
        .thenReturn(updatedAccountLevelStageYaml);
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .thenReturn(refreshedPipelineYaml);
    when(templateInputsValidator.validateNestedTemplateInputsForGivenYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(false)
                        .errorNodeSummary(ErrorNodeSummary.builder()
                                              .childrenErrorNodes(Arrays.asList(pipelineTemplateErrorNodeSummary))
                                              .build())
                        .build());

    InOrder inOrder = inOrder(templateInputsRefreshHelper);
    YamlFullRefreshResponseDTO refreshResponse =
        templateRefreshService.recursivelyRefreshTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, pipelineTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, stageTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, null, null), Resource.of(TEMPLATE, stageTemplateIdentifier),
            PermissionTypes.TEMPLATE_EDIT_PERMISSION);

    verify(templateInputsValidator)
        .validateNestedTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    inOrder.verify(templateInputsRefreshHelper, times(1))
        .refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageYaml, false);
    inOrder.verify(templateInputsRefreshHelper, times(1)).refreshTemplates(ACCOUNT_ID, null, null, stageYaml, false);
    inOrder.verify(templateInputsRefreshHelper, times(1))
        .refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineTemplateYaml, false);
    inOrder.verify(templateInputsRefreshHelper, times(1))
        .refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, stageTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of(TEMPLATE, pipelineTemplateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, null, null), Resource.of(TEMPLATE, stageTemplateIdentifier),
            PermissionTypes.TEMPLATE_EDIT_PERMISSION);

    assertThat(refreshResponse.isShouldRefreshYaml()).isTrue();
    assertThat(refreshResponse.getRefreshedYaml()).isEqualTo(refreshedPipelineYaml);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForYaml_ValidYaml() {
    String pipelineYaml = "pipeline yaml";
    String refreshedPipelineYaml = "Refreshed yaml";

    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .thenReturn(refreshedPipelineYaml);
    when(templateInputsValidator.validateNestedTemplateInputsForGivenYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(true)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    YamlFullRefreshResponseDTO refreshResponse =
        templateRefreshService.recursivelyRefreshTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);
    verify(templateInputsValidator)
        .validateNestedTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);

    assertThat(refreshResponse.isShouldRefreshYaml()).isFalse();
    assertThat(refreshResponse.getRefreshedYaml()).isNull();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshTemplatesForYaml_ValidYamlWithCaching() {
    String pipelineYaml = "pipeline yaml";
    String refreshedPipelineYaml = "Refreshed yaml";

    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, true))
        .thenReturn(refreshedPipelineYaml);
    when(templateInputsValidator.validateNestedTemplateInputsForGivenYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, true))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(true)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    YamlFullRefreshResponseDTO refreshResponse =
        templateRefreshService.recursivelyRefreshTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, true);
    verify(templateInputsValidator)
        .validateNestedTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, true);

    assertThat(refreshResponse.isShouldRefreshYaml()).isFalse();
    assertThat(refreshResponse.getRefreshedYaml()).isNull();
  }
}
