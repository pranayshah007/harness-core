/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.services.impl;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentServiceImplTest extends NGCoreTestBase {
  @Inject EnvironmentServiceImpl environmentService;

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> environmentService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testForceDeleteAll() {
    Environment e1 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID")
                         .build();
    Environment e2 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID")
                         .build();

    // env from different project
    Environment e3 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID_1")
                         .build();

    environmentService.create(e1);
    environmentService.create(e2);
    environmentService.create(e3);

    boolean deleted = environmentService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    assertThat(deleted).isTrue();

    Optional<Environment> environment1 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", e1.getIdentifier(), false);
    Optional<Environment> environment2 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", e2.getIdentifier(), false);
    assertThat(environment1).isNotPresent();
    assertThat(environment2).isNotPresent();

    Optional<Environment> environment3 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID_1", e3.getIdentifier(), false);
    assertThat(environment3).isPresent();
    assertThat(environment3.get().getIdentifier()).isEqualTo(e3.getIdentifier());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEnvironmentServiceLayer() {
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .build();

    // Create operations
    Environment createdEnvironment = environmentService.create(createEnvironmentRequest);
    assertThat(createdEnvironment).isNotNull();
    assertThat(createdEnvironment.getAccountId()).isEqualTo(createEnvironmentRequest.getAccountId());
    assertThat(createdEnvironment.getOrgIdentifier()).isEqualTo(createEnvironmentRequest.getOrgIdentifier());
    assertThat(createdEnvironment.getProjectIdentifier()).isEqualTo(createEnvironmentRequest.getProjectIdentifier());
    assertThat(createdEnvironment.getIdentifier()).isEqualTo(createEnvironmentRequest.getIdentifier());
    assertThat(createdEnvironment.getName()).isEqualTo(createEnvironmentRequest.getIdentifier());
    assertThat(createdEnvironment.getVersion()).isEqualTo(0L);

    // Get Operations
    Optional<Environment> getEnvironment =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", false);
    assertThat(getEnvironment).isPresent();
    assertThat(getEnvironment.get()).isEqualTo(createdEnvironment);

    // Update Operations
    Environment updateEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .name("UPDATED_ENV")
                                               .description("NEW_DESCRIPTION")
                                               .build();

    Environment updatedEnvironment = environmentService.update(updateEnvironmentRequest);
    assertThat(updatedEnvironment).isNotNull();
    assertThat(updatedEnvironment.getAccountId()).isEqualTo(updateEnvironmentRequest.getAccountId());
    assertThat(updatedEnvironment.getOrgIdentifier()).isEqualTo(updateEnvironmentRequest.getOrgIdentifier());
    assertThat(updatedEnvironment.getProjectIdentifier()).isEqualTo(updateEnvironmentRequest.getProjectIdentifier());
    assertThat(updatedEnvironment.getIdentifier()).isEqualTo(updateEnvironmentRequest.getIdentifier());
    assertThat(updatedEnvironment.getName()).isEqualTo(updateEnvironmentRequest.getName());
    assertThat(updatedEnvironment.getDescription()).isEqualTo(updateEnvironmentRequest.getDescription());
    assertThat(updatedEnvironment.getVersion()).isEqualTo(1L);

    updateEnvironmentRequest.setIdentifier("NEW_ENV");
    assertThatThrownBy(() -> environmentService.update(updateEnvironmentRequest))
        .isInstanceOf(InvalidRequestException.class);
    updatedEnvironment.setIdentifier("IDENTIFIER");

    // Upsert operations
    Environment upsertEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("NEW_ENV")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("NEW_PROJECT")
                                               .name("UPSERTED_ENV")
                                               .description("NEW_DESCRIPTION")
                                               .build();
    Environment upsertEnv = environmentService.upsert(upsertEnvironmentRequest);
    assertThat(upsertEnv).isNotNull();
    assertThat(upsertEnv.getAccountId()).isEqualTo(upsertEnvironmentRequest.getAccountId());
    assertThat(upsertEnv.getOrgIdentifier()).isEqualTo(upsertEnvironmentRequest.getOrgIdentifier());
    assertThat(upsertEnv.getProjectIdentifier()).isEqualTo(upsertEnvironmentRequest.getProjectIdentifier());
    assertThat(upsertEnv.getIdentifier()).isEqualTo(upsertEnvironmentRequest.getIdentifier());
    assertThat(upsertEnv.getName()).isEqualTo(upsertEnvironmentRequest.getName());
    assertThat(upsertEnv.getDescription()).isEqualTo(upsertEnvironmentRequest.getDescription());

    // List services operations.
    Criteria criteriaFromFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 100, null);
    Page<Environment> list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    assertThat(EnvironmentMapper.writeDTO(list.getContent().get(0)))
        .isEqualTo(EnvironmentMapper.writeDTO(updatedEnvironment));

    criteriaFromFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);
    pageRequest = PageUtils.getPageRequest(0, 100, null);

    list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(0);

    // Upsert operations in org level
    Environment upsertEnvironmentRequestOrgLevel = Environment.builder()
                                                       .accountId("ACCOUNT_ID")
                                                       .identifier("NEW_ENV")
                                                       .orgIdentifier("ORG_ID")
                                                       .name("UPSERTED_ENV")
                                                       .description("NEW_DESCRIPTION")
                                                       .build();
    upsertEnv = environmentService.upsert(upsertEnvironmentRequestOrgLevel);
    assertThat(upsertEnv).isNotNull();
    assertThat(upsertEnv.getAccountId()).isEqualTo(upsertEnvironmentRequest.getAccountId());
    assertThat(upsertEnv.getOrgIdentifier()).isEqualTo(upsertEnvironmentRequest.getOrgIdentifier());
    assertThat(upsertEnv.getProjectIdentifier()).isNull();
    assertThat(upsertEnv.getIdentifier()).isEqualTo(upsertEnvironmentRequest.getIdentifier());
    assertThat(upsertEnv.getName()).isEqualTo(upsertEnvironmentRequest.getName());
    assertThat(upsertEnv.getDescription()).isEqualTo(upsertEnvironmentRequest.getDescription());

    criteriaFromFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);
    pageRequest = PageUtils.getPageRequest(0, 100, null);

    list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    List<EnvironmentResponseDTO> dtoList =
        list.getContent().stream().map(EnvironmentMapper::writeDTO).collect(Collectors.toList());
    assertThat(dtoList).containsOnly(EnvironmentMapper.writeDTO(upsertEnv));

    // Delete operations
    boolean delete = environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", 1L);
    assertThat(delete).isTrue();

    Optional<Environment> deletedEnvironment =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "UPDATED_ENV", false);
    assertThat(deletedEnvironment.isPresent()).isFalse();
  }
}
