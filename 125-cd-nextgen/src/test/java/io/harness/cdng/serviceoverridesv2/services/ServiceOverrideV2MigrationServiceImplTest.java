/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideMigrationResponse;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ServiceOverrideV2MigrationServiceImplTest extends CDNGTestBase {
  @Inject private ServiceOverridesRepositoryV2 serviceOverridesRepositoryV2;
  @Inject ServiceOverrideRepository serviceOverrideRepository;
  @Inject MongoTemplate mongoTemplate;

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final List<String> projectIds = List.of("project0", "project1");
  private static final List<String> svcRefs = List.of("service0", "service1");
  private static final List<String> orgSvcRefs = List.of("org.service0", "org.service1");
  private static final List<String> accountSvcRefs = List.of("account.service0", "account.service1");

  private static final List<String> envRefs = List.of("env0", "env1");
  private static final List<String> orgEnvRefs = List.of("org.env0", "org.env1");
  private static final List<String> accountEnvRefs = List.of("account.env0", "account.env1");

  @Inject ServiceOverrideV2MigrationService v2MigrationService;

  @Before
  public void setup() {
    Reflect.on(v2MigrationService).set("mongoTemplate", mongoTemplate);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testProjectScopeMigration() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, projectIds.get(0), false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList = responseDTO.getProjectLevelMigrationInfo();
    assertThat(projectLevelInfoList).isNotEmpty();
    assertThat(projectLevelInfoList).hasSize(1);

    ProjectLevelOverrideMigrationResponseDTO projectResponseDTO = projectLevelInfoList.get(0);
    assertThat(projectResponseDTO.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(projectResponseDTO.isEnvMigrationSuccessful()).isTrue();
    assertThat(projectResponseDTO.getTotalServiceOverridesCount()).isEqualTo(2L);
    assertThat(projectResponseDTO.getMigratedServiceOverridesCount()).isEqualTo(2L);

    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getProjectId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(projectIds.get(0), projectIds.get(0));
    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getOrgId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getServiceRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(svcRefs.get(0), svcRefs.get(1));
    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getEnvRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(envRefs.get(0), envRefs.get(1));
    assertThat(projectResponseDTO.getServiceOverridesInfos()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    assertThat(projectResponseDTO.getMigratedEnvCount()).isEqualTo(0L);
    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testOrgScopeMigration() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList = responseDTO.getProjectLevelMigrationInfo();
    assertThat(projectLevelInfoList).isEmpty();

    List<OrgLevelOverrideMigrationResponseDTO> orgLevelInfoList = responseDTO.getOrgLevelMigrationInfo();

    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);
    OrgLevelOverrideMigrationResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(orgResponseDTO.isEnvsMigrationSuccessful()).isTrue();

    assertThat(orgResponseDTO.getTotalServiceOverridesCount()).isEqualTo(2L);
    assertThat(orgResponseDTO.getMigratedServiceOverridesCount()).isEqualTo(2L);

    assertThat(orgResponseDTO.getServiceOverridesInfo().get(0).getProjectId()).isBlank();

    assertThat(orgResponseDTO.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getOrgId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(orgResponseDTO.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(orgResponseDTO.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getServiceRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(orgSvcRefs.get(0), orgSvcRefs.get(1));
    assertThat(orgResponseDTO.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getEnvRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(orgEnvRefs.get(0), orgEnvRefs.get(1));
    assertThat(orgResponseDTO.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(orgResponseDTO.getMigratedEnvironmentCount()).isEqualTo(0L);
    assertThat(orgResponseDTO.getEnvironmentsInfo()).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAccountScopeMigration() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, null, null, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelMigrationInfo()).isEmpty();
    assertThat(responseDTO.getOrgLevelMigrationInfo()).isEmpty();

    AccountLevelOverrideMigrationResponseDTO accountResponseDto = responseDTO.getAccountLevelMigrationInfo();
    assertThat(accountResponseDto).isNotNull();

    assertThat(accountResponseDto.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(accountResponseDto.isEnvsMigrationSuccessful()).isTrue();

    assertThat(accountResponseDto.getTotalServiceOverridesCount()).isEqualTo(2L);
    assertThat(accountResponseDto.getMigratedServiceOverridesCount()).isEqualTo(2L);

    assertThat(accountResponseDto.getServiceOverridesInfo().get(0).getProjectId()).isBlank();
    assertThat(accountResponseDto.getServiceOverridesInfo().get(0).getOrgId()).isBlank();

    assertThat(accountResponseDto.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(accountResponseDto.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getServiceRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(accountSvcRefs.get(0), accountSvcRefs.get(1));
    assertThat(accountResponseDto.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::getEnvRef)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(accountEnvRefs.get(0), accountEnvRefs.get(1));
    assertThat(accountResponseDto.getServiceOverridesInfo()
                   .stream()
                   .map(SingleServiceOverrideMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(accountResponseDto.getMigratedEnvironmentsCount()).isEqualTo(0L);
    assertThat(accountResponseDto.getEnvironmentsInfo()).isEmpty();
  }

  private void createOverrideTestData() {
    mongoTemplate.save(Organization.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .identifier(ORG_IDENTIFIER)
                           .name(ORG_IDENTIFIER)
                           .build());
    mongoTemplate.save(Project.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .identifier(projectIds.get(0))
                           .name(projectIds.get(0))
                           .build());

    mongoTemplate.save(Project.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .identifier(projectIds.get(1))
                           .name(projectIds.get(1))
                           .build());

    createTestOverrideInProject();
    createTestOverrideInOrg();
    createTestOverrideInAccount();
  }

  private void createTestOverrideInProject() {
    for (String projectId : projectIds) {
      for (int i = 0; i < 2; i++) {
        mongoTemplate.save(
            NGServiceOverridesEntity.builder()
                .identifier(generateIdentifier(envRefs.get(i), svcRefs.get(i)))
                .environmentRef(envRefs.get(i))
                .serviceRef(svcRefs.get(i))
                .isV2(false)
                .projectIdentifier(projectId)
                .orgIdentifier(ORG_IDENTIFIER)
                .accountId(ACCOUNT_IDENTIFIER)
                .yaml(String.format(
                    "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  variables:\n    - name: var1\n      type: String\n      value: \"val1\"\n",
                    envRefs.get(i), svcRefs.get(i)))
                .build());
      }
    }
  }

  private void createTestOverrideInOrg() {
    for (int i = 0; i < 2; i++) {
      mongoTemplate.save(
          NGServiceOverridesEntity.builder()
              .identifier(generateIdentifier(orgEnvRefs.get(i), orgSvcRefs.get(i)))
              .environmentRef(orgEnvRefs.get(i))
              .serviceRef(orgSvcRefs.get(i))
              .isV2(false)
              .orgIdentifier(ORG_IDENTIFIER)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  variables:\n    - name: var1\n      type: String\n      value: \"val1\"\n",
                  orgEnvRefs.get(i), orgSvcRefs.get(i)))
              .build());
    }
  }

  private void createTestOverrideInAccount() {
    for (int i = 0; i < 2; i++) {
      mongoTemplate.save(
          NGServiceOverridesEntity.builder()
              .identifier(generateIdentifier(accountEnvRefs.get(i), accountSvcRefs.get(i)))
              .environmentRef(accountEnvRefs.get(i))
              .serviceRef(accountSvcRefs.get(i))
              .isV2(false)

              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  manifests:\n    - manifest:\n        identifier: asd\n        type: HelmRepoOverride\n        spec:\n          type: Http\n          connectorRef: account.puthrayahelm\n",
                  accountEnvRefs.get(i), accountSvcRefs.get(i)))
              .build());
    }
  }

  private static String generateIdentifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }
}
