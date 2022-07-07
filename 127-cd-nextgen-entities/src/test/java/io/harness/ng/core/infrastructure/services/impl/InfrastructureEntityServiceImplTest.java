/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services.impl;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructureEntityServiceImplTest extends CDNGEntitiesTestBase {
  @Inject InfrastructureEntityServiceImpl infrastructureEntityService;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> infrastructureEntityService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputs() throws IOException {
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

    String infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(
        ACCOUNT_ID, PROJECT_ID, ORG_ID, "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER"), false);
    String resFile = "infrastructure-with-runtime-inputs-res.yaml";
    String resInputs = readFile(resFile);
    assertThat(infrastructureInputsFromYaml).isEqualTo(resInputs);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputsWithoutRuntimeInputs() throws IOException {
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

    String infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(
        ACCOUNT_ID, PROJECT_ID, ORG_ID, "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER1"), false);

    assertThat(infrastructureInputsFromYaml).isNull();
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
                                                  .build();

    InfrastructureEntity createdInfra = infrastructureEntityService.create(createInfraRequest);
    assertThat(createdInfra).isNotNull();
    assertThat(createdInfra.getAccountId()).isEqualTo(createInfraRequest.getAccountId());
    assertThat(createdInfra.getOrgIdentifier()).isEqualTo(createInfraRequest.getOrgIdentifier());
    assertThat(createdInfra.getProjectIdentifier()).isEqualTo(createInfraRequest.getProjectIdentifier());
    assertThat(createdInfra.getIdentifier()).isEqualTo(createInfraRequest.getIdentifier());
    assertThat(createdInfra.getName()).isEqualTo(createInfraRequest.getName());

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
                                                  .build();

    InfrastructureEntity updatedInfraResponse = infrastructureEntityService.update(updateInfraRequest);
    assertThat(updatedInfraResponse.getAccountId()).isEqualTo(updateInfraRequest.getAccountId());
    assertThat(updatedInfraResponse.getOrgIdentifier()).isEqualTo(updateInfraRequest.getOrgIdentifier());
    assertThat(updatedInfraResponse.getProjectIdentifier()).isEqualTo(updateInfraRequest.getProjectIdentifier());
    assertThat(updatedInfraResponse.getIdentifier()).isEqualTo(updateInfraRequest.getIdentifier());
    assertThat(updatedInfraResponse.getName()).isEqualTo(updateInfraRequest.getName());
    assertThat(updatedInfraResponse.getDescription()).isEqualTo(updateInfraRequest.getDescription());

    updateInfraRequest.setAccountId("NEW_ACCOUNT");
    assertThatThrownBy(() -> infrastructureEntityService.update(updateInfraRequest))
        .isInstanceOf(InvalidRequestException.class);
    updateInfraRequest.setAccountId(ACCOUNT_ID);

    // Upsert operations
    InfrastructureEntity upsertInfraRequest = InfrastructureEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .identifier("NEW_IDENTIFIER")
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier("NEW_PROJECT")
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .name("UPSERTED_INFRA")
                                                  .description("NEW_DESCRIPTION")
                                                  .build();
    InfrastructureEntity upsertedInfra = infrastructureEntityService.upsert(upsertInfraRequest);
    assertThat(upsertedInfra.getAccountId()).isEqualTo(upsertInfraRequest.getAccountId());
    assertThat(upsertedInfra.getOrgIdentifier()).isEqualTo(upsertInfraRequest.getOrgIdentifier());
    assertThat(upsertedInfra.getProjectIdentifier()).isEqualTo(upsertInfraRequest.getProjectIdentifier());
    assertThat(upsertedInfra.getIdentifier()).isEqualTo(upsertInfraRequest.getIdentifier());
    assertThat(upsertedInfra.getEnvIdentifier()).isEqualTo(upsertInfraRequest.getEnvIdentifier());
    assertThat(upsertedInfra.getName()).isEqualTo(upsertInfraRequest.getName());
    assertThat(upsertedInfra.getDescription()).isEqualTo(upsertInfraRequest.getDescription());

    // List infra operations.
    Criteria criteriaForInfraFilter =
        InfrastructureFilterHelper.createCriteriaForGetList(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);

    // delete operations
    boolean delete =
        infrastructureEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1");
    assertThat(delete).isTrue();

    Optional<InfrastructureEntity> deletedInfra =
        infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "IDENTIFIER1");
    assertThat(deletedInfra.isPresent()).isFalse();
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
    Criteria criteriaForInfraFilter =
        InfrastructureFilterHelper.createCriteriaForGetList(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER", "");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<InfrastructureEntity> list = infrastructureEntityService.list(criteriaForInfraFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);

    // delete operations
    boolean delete = infrastructureEntityService.forceDeleteAllInEnv(ACCOUNT_ID, ORG_ID, PROJECT_ID, "ENV_IDENTIFIER");
    assertThat(delete).isTrue();

    // 1 infra remains
    Criteria criteriaAllInProject = CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    Page<InfrastructureEntity> listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(1);

    boolean deleteProject = infrastructureEntityService.forceDeleteAllInProject(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(deleteProject).isTrue();

    listPostDeletion = infrastructureEntityService.list(criteriaAllInProject, pageRequest);
    assertThat(listPostDeletion.getContent()).isNotNull();
    assertThat(listPostDeletion.getContent().size()).isEqualTo(0);
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
