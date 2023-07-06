/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services.impl;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureInputsMergedResponseDto;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class InfrastructureEntityServiceImplTest extends CDNGEntitiesTestBase {
  @Mock InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;
  @Mock NGSettingsClient settingsClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;

  @InjectMocks @Inject InfrastructureEntityServiceImpl infrastructureEntityService;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String ENV_ID = "ENV_ID";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> infrastructureEntityService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputs() {
    String filename = "infrastructure-with-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .build();

    infrastructureEntityService.create(createInfraRequest);
    verify(infrastructureEntitySetupUsageHelper, times(1)).createSetupUsages(eq(createInfraRequest), any());

    String infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(ACCOUNT_ID,
        ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER"), false, NoInputMergeInputAction.RETURN_EMPTY);
    String resFile = "infrastructure-with-runtime-inputs-res.yaml";
    String resInputs = readFile(resFile);
    assertThat(infrastructureInputsFromYaml).isEqualTo(resInputs);

    infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID,
        PROJECT_ID, "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);
    assertThat(infrastructureInputsFromYaml).isEqualTo(resInputs);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputsWithoutRuntimeInputs() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER1")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .build();

    infrastructureEntityService.create(createInfraRequest);

    String infrastructureInputsFromYaml =
        infrastructureEntityService.createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER",
            Arrays.asList("IDENTIFIER1"), false, NoInputMergeInputAction.RETURN_EMPTY);

    assertThat(infrastructureInputsFromYaml).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputsV2WithoutRuntimeInputs() throws IOException {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .build();

    infrastructureEntityService.create(createInfraRequest);

    String infrastructureInputsFromYaml =
        infrastructureEntityService.createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER",
            Arrays.asList("IDENTIFIER"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    assertThat(infrastructureInputsFromYaml).isNotNull().isNotEmpty();
    String resInputs = readFile("infra-inputset-yaml-with-no-runtime-inputs.yaml");
    assertThat(infrastructureInputsFromYaml).isEqualTo(resInputs);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER1")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                                                  .build();

    InfrastructureEntity createdInfra = infrastructureEntityService.create(createInfraRequest);
    assertThat(createdInfra).isNotNull();
    assertThat(createdInfra.getAccountId()).isEqualTo(createInfraRequest.getAccountId());
    assertThat(createdInfra.getOrgIdentifier()).isEqualTo(createInfraRequest.getOrgIdentifier());
    assertThat(createdInfra.getProjectIdentifier()).isEqualTo(createInfraRequest.getProjectIdentifier());
    assertThat(createdInfra.getIdentifier()).isEqualTo(createInfraRequest.getIdentifier());
    assertThat(createdInfra.getName()).isEqualTo(createInfraRequest.getName());
    assertThat(createdInfra.getDeploymentType()).isEqualTo(ServiceDefinitionType.NATIVE_HELM);

    // Get operations
    Optional<InfrastructureEntity> getInfra =
        infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1");

    assertThat(getInfra).isPresent();
    assertThat(getInfra.get()).isEqualTo(createdInfra);

    // Update operations
    InfrastructureEntity updateInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER1")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .name("UPDATED_INFRA")
                                                  .description("NEW_DESCRIPTION")
                                                  .yaml(yaml)
                                                  .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                                                  .build();

    InfrastructureEntity updatedInfraResponse = infrastructureEntityService.update(updateInfraRequest);
    verify(infrastructureEntitySetupUsageHelper, times(1)).getAllReferredEntities(eq(updateInfraRequest));
    assertThat(updatedInfraResponse.getAccountId()).isEqualTo(updateInfraRequest.getAccountId());
    assertThat(updatedInfraResponse.getOrgIdentifier()).isEqualTo(updateInfraRequest.getOrgIdentifier());
    assertThat(updatedInfraResponse.getProjectIdentifier()).isEqualTo(updateInfraRequest.getProjectIdentifier());
    assertThat(updatedInfraResponse.getIdentifier()).isEqualTo(updateInfraRequest.getIdentifier());
    assertThat(updatedInfraResponse.getName()).isEqualTo(updateInfraRequest.getName());
    assertThat(updatedInfraResponse.getDescription()).isEqualTo(updateInfraRequest.getDescription());
    assertThat(updatedInfraResponse.getDeploymentType()).isEqualTo(ServiceDefinitionType.NATIVE_HELM);

    updateInfraRequest.setAccountId("NEW_ACCOUNT");
    assertThatThrownBy(() -> infrastructureEntityService.update(updateInfraRequest))
        .isInstanceOf(InvalidRequestException.class);
    updateInfraRequest.setAccountId(ACCOUNT_ID);

    // adding test for 'Deployment Type is not allowed to change'
    updateInfraRequest.setDeploymentType(ServiceDefinitionType.KUBERNETES);
    assertThatThrownBy(() -> infrastructureEntityService.update(updateInfraRequest))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(updatedInfraResponse.getDeploymentType()).isNotEqualTo(ServiceDefinitionType.KUBERNETES);

    // adding test for immutable infra type
    updateInfraRequest.setType(InfrastructureType.ASG);
    assertThatThrownBy(() -> infrastructureEntityService.update(updateInfraRequest))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(updatedInfraResponse.getType()).isNotEqualTo(InfrastructureType.ASG);

    // Upsert operations
    InfrastructureEntity upsertInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("NEW_IDENTIFIER")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier("NEW_PROJECT")
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .name("UPSERTED_INFRA")
                                                  .description("NEW_DESCRIPTION")
                                                  .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                                                  .build();
    InfrastructureEntity upsertedInfra = infrastructureEntityService.upsert(upsertInfraRequest, UpsertOptions.DEFAULT);
    verify(infrastructureEntitySetupUsageHelper, times(1)).getAllReferredEntities(eq(createInfraRequest));
    assertThat(upsertedInfra.getAccountId()).isEqualTo(upsertInfraRequest.getAccountId());
    assertThat(upsertedInfra.getOrgIdentifier()).isEqualTo(upsertInfraRequest.getOrgIdentifier());
    assertThat(upsertedInfra.getProjectIdentifier()).isEqualTo(upsertInfraRequest.getProjectIdentifier());
    assertThat(upsertedInfra.getIdentifier()).isEqualTo(upsertInfraRequest.getIdentifier());
    assertThat(upsertedInfra.getEnvIdentifier()).isEqualTo(upsertInfraRequest.getEnvIdentifier());
    assertThat(upsertedInfra.getName()).isEqualTo(upsertInfraRequest.getName());
    assertThat(upsertedInfra.getDescription()).isEqualTo(upsertInfraRequest.getDescription());
    assertThat(upsertedInfra.getDeploymentType()).isEqualTo(upsertInfraRequest.getDeploymentType());

    // Upsert operations // update via Upsert
    upsertInfraRequest = InfrastructureEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .identifier("NEW_IDENTIFIER")
                             .orgIdentifier(ORG_ID)
                             .projectIdentifier("NEW_PROJECT")
                             .envIdentifier("ENV_IDENTIFIER")
                             .name("UPSERTED_INFRA")
                             .description("NEW_DESCRIPTION")
                             .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                             .build();

    upsertedInfra = infrastructureEntityService.upsert(upsertInfraRequest, UpsertOptions.DEFAULT);
    verify(infrastructureEntitySetupUsageHelper, times(1)).getAllReferredEntities(createInfraRequest);
    assertThat(upsertedInfra.getAccountId()).isEqualTo(upsertInfraRequest.getAccountId());
    assertThat(upsertedInfra.getOrgIdentifier()).isEqualTo(upsertInfraRequest.getOrgIdentifier());
    assertThat(upsertedInfra.getProjectIdentifier()).isEqualTo(upsertInfraRequest.getProjectIdentifier());
    assertThat(upsertedInfra.getIdentifier()).isEqualTo(upsertInfraRequest.getIdentifier());
    assertThat(upsertedInfra.getEnvIdentifier()).isEqualTo(upsertInfraRequest.getEnvIdentifier());
    assertThat(upsertedInfra.getName()).isEqualTo(upsertInfraRequest.getName());
    assertThat(upsertedInfra.getDescription()).isEqualTo(upsertInfraRequest.getDescription());
    assertThat(upsertedInfra.getDeploymentType()).isEqualTo(upsertInfraRequest.getDeploymentType());

    // adding test for 'Deployment Type is not allowed to change'
    updateInfraRequest.setDeploymentType(ServiceDefinitionType.KUBERNETES);
    assertThatThrownBy(() -> infrastructureEntityService.update(updateInfraRequest))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(updatedInfraResponse.getDeploymentType()).isNotEqualTo(ServiceDefinitionType.KUBERNETES);

    // List infra operations.
    Criteria criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "", Collections.emptyList(), null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);

    // delete operations
    boolean delete =
        infrastructureEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1", false);
    assertThat(delete).isTrue();

    Optional<InfrastructureEntity> deletedInfra =
        infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1");
    assertThat(deletedInfra.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testForceDeletion() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequestDiffEnv = InfrastructureEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .envIdentifier("ENV_IDENTIFIER")
                                                         .identifier("IDENTIFIER1")
                                                         .yaml(yaml)
                                                         .build();

    infrastructureEntityService.create(createInfraRequestDiffEnv);
    doThrow(new ReferencedEntityException("unsafe to delete"))
        .when(infrastructureEntitySetupUsageHelper)
        .checkThatInfraIsNotReferredByOthers(any(InfrastructureEntity.class));

    // force delete = false
    assertThatExceptionOfType(ReferencedEntityException.class)
        .isThrownBy(()
                        -> infrastructureEntityService.delete(
                            ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1", false));

    // force delete = true
    infrastructureEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1", true);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCascadeDeletion() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }
    InfrastructureEntity createInfraRequestDiffEnv = InfrastructureEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .identifier("IDENTIFIER3")
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .envIdentifier("ENV_IDENTIFIER1")
                                                         .yaml(yaml)
                                                         .build();

    infrastructureEntityService.create(createInfraRequestDiffEnv);

    // List infra operations.
    Criteria criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "", Collections.emptyList(), null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);

    // delete operations
    boolean delete = infrastructureEntityService.forceDeleteAllInEnv(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER");
    assertThat(delete).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());

    // 1 infra remains
    Criteria criteriaAllInProject = CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    Page<InfrastructureEntity> listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);

    boolean deleteProject = infrastructureEntityService.forceDeleteAllInProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(deleteProject).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(3)).deleteSetupUsages(any());

    listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isZero();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testInfraList() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .deploymentType(ServiceDefinitionType.KUBERNETES)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }

    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i + 2)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }

    Criteria criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "", Collections.emptyList(), null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(4);

    criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        "ENV_IDENTIFIER", "", Collections.emptyList(), ServiceDefinitionType.KUBERNETES);
    list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);
    assertThat(list.getContent()
                   .stream()
                   .filter(i -> i.getDeploymentType() == ServiceDefinitionType.KUBERNETES)
                   .collect(Collectors.toList()))
        .hasSize(2);

    criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        "ENV_IDENTIFIER", "", Collections.emptyList(), ServiceDefinitionType.NATIVE_HELM);
    list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);
    assertThat(list.getContent()
                   .stream()
                   .filter(i -> i.getDeploymentType() == ServiceDefinitionType.NATIVE_HELM)
                   .collect(Collectors.toList()))
        .hasSize(2);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Parameters({"infrastructure/infrastructure-inputs-in-pipeline.yaml, infrastructure/infrastructure-with-few-inputs.yaml, infrastructure/infrastructureInput-merged.yaml, false",
      "infrastructure/infrastructure-inputs-in-pipeline.yaml, infrastructure/infrastructure-with-no-input.yaml , infrastructure/empty-file.yaml, true",
      "infrastructure/empty-file.yaml, infrastructure/infrastructure-with-few-inputs.yaml, infrastructure/infrastructureInput-merged.yaml, false"})
  public void
  testMergeInfrastructureInputs(String pipelineInputYamlPath, String actualEntityYamlPath, String mergedInputYamlPath,
      boolean isMergedYamlEmpty) {
    String yaml = readFile(actualEntityYamlPath);
    InfrastructureEntity createRequest = InfrastructureEntity.builder()
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_ID)
                                             .projectIdentifier(PROJECT_ID)
                                             .envIdentifier(ENV_ID)
                                             .name("Infra1")
                                             .identifier("Infra1")
                                             .yaml(yaml)
                                             .build();

    infrastructureEntityService.create(createRequest);

    String oldTemplateInputYaml = readFile(pipelineInputYamlPath);
    String mergedTemplateInputsYaml = readFile(mergedInputYamlPath);
    InfrastructureInputsMergedResponseDto responseDto = infrastructureEntityService.mergeInfraStructureInputs(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, "Infra1", oldTemplateInputYaml);
    String mergedYaml = responseDto.getMergedInfrastructureInputsYaml();
    if (isMergedYamlEmpty) {
      assertThat(mergedYaml).isEmpty();
    } else {
      assertThat(mergedYaml).isNotNull().isNotEmpty();
      assertThat(mergedYaml).isEqualTo(mergedTemplateInputsYaml);
    }
    assertThat(responseDto.getInfrastructureYaml()).isNotNull().isNotEmpty().isEqualTo(yaml);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testOrgLevelInfraCRUD() {
    String filename = "infrastructure-at-org-level.yaml";
    String yaml = readFile(filename);
    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i)
                                                    .orgIdentifier(ORG_ID)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }
    InfrastructureEntity createInfraRequestDiffEnv = InfrastructureEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .identifier("IDENTIFIER3")
                                                         .orgIdentifier(ORG_ID)
                                                         .envIdentifier("ENV_IDENTIFIER1")
                                                         .yaml(yaml)
                                                         .build();

    infrastructureEntityService.create(createInfraRequestDiffEnv);

    // List infra operations.
    Criteria criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(
        ACCOUNT_ID, ORG_ID, null, "ENV_IDENTIFIER", "", Collections.emptyList(), null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);

    // delete operations
    boolean delete = infrastructureEntityService.forceDeleteAllInEnv(ACCOUNT_ID, ORG_ID, null, "org.ENV_IDENTIFIER");
    assertThat(delete).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());

    // 1 infra remains
    Criteria criteriaAllInProject = CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, ORG_ID, null);
    Page<InfrastructureEntity> listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);

    boolean deleteProject = infrastructureEntityService.forceDeleteAllInProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(deleteProject).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());

    listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAccountLevelInfraCRUD() {
    String filename = "infrastructure-at-account-level.yaml";
    String yaml = readFile(filename);
    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }
    InfrastructureEntity createInfraRequestDiffEnv = InfrastructureEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .identifier("IDENTIFIER3")
                                                         .envIdentifier("ENV_IDENTIFIER1")
                                                         .yaml(yaml)
                                                         .build();

    infrastructureEntityService.create(createInfraRequestDiffEnv);

    // List infra operations.
    Criteria criteriaForInfraFilter = InfrastructureFilterHelper.createListCriteria(
        ACCOUNT_ID, null, null, "ENV_IDENTIFIER", "", Collections.emptyList(), null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);

    // delete operations
    boolean delete =
        infrastructureEntityService.forceDeleteAllInEnv(ACCOUNT_ID, ORG_ID, PROJECT_ID, "account.ENV_IDENTIFIER");
    assertThat(delete).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());

    // 1 infra remains
    Criteria criteriaAllInProject = CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, null, null);
    Page<InfrastructureEntity> listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);

    boolean deleteProject = infrastructureEntityService.forceDeleteAllInProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(deleteProject).isTrue();
    // no deletions
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());

    listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testModifyScopeToMatchEnvironment() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    // infra request has org and project id but env is account level
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("IDENTIFIER1")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .envIdentifier("account.ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .deploymentType(ServiceDefinitionType.NATIVE_HELM)
                                                  .build();

    InfrastructureEntity createdInfra = infrastructureEntityService.create(createInfraRequest);
    assertThat(createdInfra).isNotNull();
    assertThat(createdInfra.getAccountId()).isEqualTo(createInfraRequest.getAccountId());
    // account scoped infra
    assertThat(createdInfra.getOrgIdentifier()).isNull();
    assertThat(createdInfra.getProjectIdentifier()).isNull();
    assertThat(createdInfra.getIdentifier()).isEqualTo(createInfraRequest.getIdentifier());
    assertThat(createdInfra.getEnvIdentifier()).isEqualTo("ENV_IDENTIFIER");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCascadeDeletionForOrgLevelInfrastructures() {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    for (int i = 1; i < 3; i++) {
      InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .identifier("IDENTIFIER" + i)
                                                    .orgIdentifier(ORG_ID)
                                                    .envIdentifier("ENV_IDENTIFIER")
                                                    .yaml(yaml)
                                                    .build();

      infrastructureEntityService.create(createInfraRequest);
    }

    // delete operations
    boolean delete = infrastructureEntityService.forceDeleteAllInOrg(ACCOUNT_ID, ORG_ID);
    assertThat(delete).isTrue();
    verify(infrastructureEntitySetupUsageHelper, times(2)).deleteSetupUsages(any());
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
