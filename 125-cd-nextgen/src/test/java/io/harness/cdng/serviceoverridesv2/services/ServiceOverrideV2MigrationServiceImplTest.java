/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideV2MigrationService;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.beans.SingleEnvMigrationResponse;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideMigrationResponse;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

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
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, projectIds.get(0), false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelMigrationInfo(), 1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testOrgScopeMigration() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList = responseDTO.getProjectLevelMigrationInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelMigrationInfo());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAccountScopeMigration() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, null, null, false, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelMigrationInfo()).isEmpty();
    assertThat(responseDTO.getOrgLevelMigrationInfo()).isEmpty();

    AccountLevelOverrideMigrationResponseDTO accountResponseDto = responseDTO.getAccountLevelMigrationInfo();
    assertAccountResponseDTO(accountResponseDto);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAccountLevelWithChildScopes() {
    createOverrideTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, null, null, true, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelMigrationInfo()).isNotEmpty();
    assertThat(responseDTO.getOrgLevelMigrationInfo()).isNotEmpty();

    assertAccountResponseDTO(responseDTO.getAccountLevelMigrationInfo());
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelMigrationInfo());
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelMigrationInfo(), 2);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testOrgScopeMigrationRevert() {
    createOverrideTestDataForMigrationRevert();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, true);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList = responseDTO.getProjectLevelMigrationInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelMigrationRevertResponseDTO(responseDTO.getOrgLevelMigrationInfo());
  }

  private void assertAccountResponseDTO(AccountLevelOverrideMigrationResponseDTO accountResponseDto) {
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

    // assert for the updated entities
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(ACCOUNT_IDENTIFIER);
    criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).isNull()),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).isNull()));

    List<NGServiceOverridesEntity> overridesEntities =
        mongoTemplate.find(new Query(criteria), NGServiceOverridesEntity.class);
    assertThat(overridesEntities).hasSize(2);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getIsV2).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getType).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            ServiceOverridesType.ENV_SERVICE_OVERRIDE, ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(overridesEntities.get(0)
                   .getSpec()
                   .getManifests()
                   .stream()
                   .map(ManifestConfigWrapper::getManifest)
                   .map(ManifestConfig::getIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("manifestIdentifier");
  }

  private static void assertOrgLevelResponseDTO(List<OrgLevelOverrideMigrationResponseDTO> orgLevelInfoList) {
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

  private void assertProjectLevelResponseDTO(
      List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList, int projectsNumber) {
    assertThat(projectLevelInfoList).isNotEmpty();
    assertThat(projectLevelInfoList).hasSize(projectsNumber);

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

    // assert migrated overrides
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectIds.get(0));

    List<NGServiceOverridesEntity> overridesEntities =
        mongoTemplate.find(new Query(criteria), NGServiceOverridesEntity.class);
    assertThat(overridesEntities).hasSize(2);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getIsV2).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getType).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            ServiceOverridesType.ENV_SERVICE_OVERRIDE, ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(overridesEntities.get(0)
                   .getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("var1");
  }

  private static void assertOrgLevelMigrationRevertResponseDTO(
      List<OrgLevelOverrideMigrationResponseDTO> orgLevelInfoList) {
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

  private void createOverrideTestData() {
    createTestOrgAndProject();
    createTestOverrideInProject();
    createTestOverrideInOrg();
    createTestOverrideInAccount();
  }

  private void createOverrideTestDataForMigrationRevert() {
    createTestOrgAndProject();
    createTestOverrideInProject();
    createTestOverrideInOrgForMigrationRevert();
    createTestOverrideInAccount();
  }

  private void createTestOrgAndProject() {
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
  }

  private void createTestOverrideInProject() {
    for (String projectId : projectIds) {
      for (int i = 0; i < 2; i++) {
        mongoTemplate.save(
            NGServiceOverridesEntity.builder()
                .identifier(generateEnvSvcBasedIdentifier(envRefs.get(i), svcRefs.get(i)))
                .environmentRef(envRefs.get(i))
                .serviceRef(svcRefs.get(i))
                .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
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
              .identifier(generateEnvSvcBasedIdentifier(orgEnvRefs.get(i), orgSvcRefs.get(i)))
              .environmentRef(orgEnvRefs.get(i))
              .serviceRef(orgSvcRefs.get(i))
              .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
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
              .identifier(generateEnvSvcBasedIdentifier(accountEnvRefs.get(i), accountSvcRefs.get(i)))
              .environmentRef(accountEnvRefs.get(i))
              .serviceRef(accountSvcRefs.get(i))
              .isV2(false)
              .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  manifests:\n    - manifest:\n        identifier: manifestIdentifier\n        type: HelmRepoOverride\n        spec:\n          type: Http\n          connectorRef: account.puthrayahelm\n",
                  accountEnvRefs.get(i), accountSvcRefs.get(i)))
              .build());
    }
  }

  private void createTestOverrideInOrgForMigrationRevert() {
    for (int i = 0; i < 2; i++) {
      mongoTemplate.save(
          NGServiceOverridesEntity.builder()
              .identifier(generateEnvSvcBasedIdentifier(orgEnvRefs.get(i), orgSvcRefs.get(i)))
              .environmentRef(orgEnvRefs.get(i))
              .serviceRef(orgSvcRefs.get(i))
              .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
              .isV2(true)
              .orgIdentifier(ORG_IDENTIFIER)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  variables:\n    - name: var1\n      type: String\n      value: \"val1\"\n",
                  orgEnvRefs.get(i), orgSvcRefs.get(i)))
              .build());
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAccountLevelWithChildScopesForEnv() {
    createEnvTestData();
    ServiceOverrideMigrationResponseDTO responseDTO =
        v2MigrationService.migrateToV2(ACCOUNT_IDENTIFIER, null, null, true, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelMigrationInfo()).isNotEmpty();
    assertThat(responseDTO.getOrgLevelMigrationInfo()).isNotEmpty();

    assertAccountResponseDTOForEnv(responseDTO.getAccountLevelMigrationInfo());
    assertOrgLevelResponseDTOForEnv(responseDTO.getOrgLevelMigrationInfo());
    assertProjectLevelResponseDTOForEnv(responseDTO.getProjectLevelMigrationInfo());
  }

  private void assertProjectLevelResponseDTOForEnv(
      List<ProjectLevelOverrideMigrationResponseDTO> projectLevelInfoList) {
    assertThat(projectLevelInfoList).isNotEmpty();
    assertThat(projectLevelInfoList).hasSize(2);

    ProjectLevelOverrideMigrationResponseDTO projectResponseDTO = projectLevelInfoList.get(0);
    assertThat(projectResponseDTO.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(projectResponseDTO.isEnvMigrationSuccessful()).isTrue();
    assertThat(projectResponseDTO.getTotalEnvironmentsCount()).isEqualTo(2L);
    assertThat(projectResponseDTO.getMigratedEnvCount()).isEqualTo(2L);

    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getProjectId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(projectIds.get(0), projectIds.get(0));
    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getOrgId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);

    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getEnvIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(envRefs.get(0), envRefs.get(1));

    assertThat(projectResponseDTO.getMigratedEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    assertThat(projectResponseDTO.getTotalServiceOverridesCount()).isEqualTo(0L);
    assertThat(projectResponseDTO.getServiceOverridesInfos()).isEmpty();

    // assert migrated environment
    Criteria criteria = new Criteria()
                            .and(EnvironmentKeys.accountId)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIds.get(0));

    List<Environment> envEntities = mongoTemplate.find(new Query(criteria), Environment.class);
    assertThat(envEntities).hasSize(2);
    assertThat(envEntities.stream().map(Environment::getIsMigratedToOverride).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    // assert create overrides from environment
    Criteria criteriaForCreatedOverride = new Criteria()
                                              .and(NGServiceOverridesEntityKeys.accountId)
                                              .is(ACCOUNT_IDENTIFIER)
                                              .and(NGServiceOverridesEntityKeys.orgIdentifier)
                                              .is(ORG_IDENTIFIER)
                                              .and(NGServiceOverridesEntityKeys.projectIdentifier)
                                              .is(projectIds.get(0));
    List<NGServiceOverridesEntity> createdOverridesEntities =
        mongoTemplate.find(new Query(criteriaForCreatedOverride), NGServiceOverridesEntity.class);
    assertThat(createdOverridesEntities).hasSize(2);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getAccountId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getOrgIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(createdOverridesEntities.stream()
                   .map(NGServiceOverridesEntity::getProjectIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(projectIds.get(0), projectIds.get(0));

    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            generateEnvBasedIdentifier(envRefs.get(0)), generateEnvBasedIdentifier(envRefs.get(1)));
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getType).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, ServiceOverridesType.ENV_GLOBAL_OVERRIDE);
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIsV2).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getServiceRef).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, null);

    assertThat(createdOverridesEntities.get(0).getSpec().getVariables().get(0).getName()).isEqualTo("var1");
    assertThat(createdOverridesEntities.get(0)
                   .getSpec()
                   .getManifests()
                   .stream()
                   .map(ManifestConfigWrapper::getManifest)
                   .map(ManifestConfig::getIdentifier))
        .containsExactlyInAnyOrder("manifest1", "manifest2");
    assertThat(createdOverridesEntities.get(0)
                   .getSpec()
                   .getConfigFiles()
                   .stream()
                   .map(ConfigFileWrapper::getConfigFile)
                   .map(ConfigFile::getIdentifier))
        .containsExactlyInAnyOrder("configFile1", "configFile2");
  }

  private void assertOrgLevelResponseDTOForEnv(List<OrgLevelOverrideMigrationResponseDTO> orgLevelInfoList) {
    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);

    OrgLevelOverrideMigrationResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(orgResponseDTO.isEnvsMigrationSuccessful()).isTrue();
    assertThat(orgResponseDTO.getTotalEnvironmentsCount()).isEqualTo(2L);
    assertThat(orgResponseDTO.getMigratedEnvironmentCount()).isEqualTo(2L);

    assertThat(orgResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getOrgId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(orgResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);

    assertThat(orgResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getEnvIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(orgEnvRefs.get(0), orgEnvRefs.get(1));

    assertThat(orgResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    assertThat(orgResponseDTO.getTotalServiceOverridesCount()).isEqualTo(0L);
    assertThat(orgResponseDTO.getServiceOverridesInfo()).isEmpty();

    // assert migrated environment
    Criteria criteria = new Criteria()
                            .and(EnvironmentKeys.accountId)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER);

    criteria.andOperator(Criteria.where(EnvironmentKeys.projectIdentifier).exists(false),
        Criteria.where(EnvironmentKeys.projectIdentifier).isNull());

    List<Environment> envEntities = mongoTemplate.find(new Query(criteria), Environment.class);
    assertThat(envEntities).hasSize(2);
    assertThat(envEntities.stream().map(Environment::getIsMigratedToOverride).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    // assert create overrides from environment
    Criteria criteriaForCreatedOverride = new Criteria()
                                              .and(NGServiceOverridesEntityKeys.accountId)
                                              .is(ACCOUNT_IDENTIFIER)
                                              .and(NGServiceOverridesEntityKeys.orgIdentifier)
                                              .is(ORG_IDENTIFIER);
    criteriaForCreatedOverride.andOperator(
        new Criteria().orOperator(Criteria.where(EnvironmentKeys.projectIdentifier).exists(false),
            Criteria.where(EnvironmentKeys.projectIdentifier).isNull()));

    List<NGServiceOverridesEntity> createdOverridesEntities =
        mongoTemplate.find(new Query(criteriaForCreatedOverride), NGServiceOverridesEntity.class);
    assertThat(createdOverridesEntities).hasSize(2);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getAccountId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getOrgIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ORG_IDENTIFIER, ORG_IDENTIFIER);
    assertThat(createdOverridesEntities.stream()
                   .map(NGServiceOverridesEntity::getProjectIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, null);

    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            generateEnvBasedIdentifier(orgEnvRefs.get(0)), generateEnvBasedIdentifier(orgEnvRefs.get(1)));
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getType).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, ServiceOverridesType.ENV_GLOBAL_OVERRIDE);
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIsV2).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(createdOverridesEntities.get(0).getSpec().getVariables().get(0).getName()).isEqualTo("var1");
  }

  private void assertAccountResponseDTOForEnv(AccountLevelOverrideMigrationResponseDTO accountResponseDTO) {
    assertThat(accountResponseDTO).isNotNull();

    assertThat(accountResponseDTO.isOverridesMigrationSuccessFul()).isTrue();
    assertThat(accountResponseDTO.isEnvsMigrationSuccessful()).isTrue();
    assertThat(accountResponseDTO.getTargetEnvironmentsCount()).isEqualTo(2L);
    assertThat(accountResponseDTO.getMigratedEnvironmentsCount()).isEqualTo(2L);

    assertThat(accountResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getAccountId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);

    assertThat(accountResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::getEnvIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(accountEnvRefs.get(0), accountEnvRefs.get(1));

    assertThat(accountResponseDTO.getEnvironmentsInfo()
                   .stream()
                   .map(SingleEnvMigrationResponse::isSuccessful)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    assertThat(accountResponseDTO.getTotalServiceOverridesCount()).isEqualTo(0L);
    assertThat(accountResponseDTO.getServiceOverridesInfo()).isEmpty();

    // assert migrated environment
    Criteria criteria = new Criteria().and(EnvironmentKeys.accountId).is(ACCOUNT_IDENTIFIER);

    criteria.andOperator(new Criteria().orOperator(Criteria.where(EnvironmentKeys.projectIdentifier).exists(false),
                             Criteria.where(EnvironmentKeys.projectIdentifier).isNull()),
        new Criteria().orOperator(Criteria.where(EnvironmentKeys.orgIdentifier).exists(false),
            Criteria.where(EnvironmentKeys.orgIdentifier).isNull()));

    List<Environment> envEntities = mongoTemplate.find(new Query(criteria), Environment.class);
    assertThat(envEntities).hasSize(2);
    assertThat(envEntities.stream().map(Environment::getIsMigratedToOverride).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);

    // assert create overrides from environment
    Criteria criteriaForCreatedOverride =
        new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(ACCOUNT_IDENTIFIER);

    criteriaForCreatedOverride.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).isNull()),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).isNull()));

    List<NGServiceOverridesEntity> createdOverridesEntities =
        mongoTemplate.find(new Query(criteriaForCreatedOverride), NGServiceOverridesEntity.class);
    assertThat(createdOverridesEntities).hasSize(2);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getAccountId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER);

    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            generateEnvBasedIdentifier(accountEnvRefs.get(0)), generateEnvBasedIdentifier(accountEnvRefs.get(1)));
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getType).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, ServiceOverridesType.ENV_GLOBAL_OVERRIDE);
    assertThat(createdOverridesEntities.stream().map(NGServiceOverridesEntity::getIsV2).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, true);
    assertThat(
        createdOverridesEntities.stream().map(NGServiceOverridesEntity::getServiceRef).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, null);

    assertThat(createdOverridesEntities.get(0).getSpec().getVariables().get(0).getName()).isEqualTo("var1");
  }

  private void createEnvTestData() {
    createTestOrgAndProject();
    createTestEnvInProject();
    createTestEnvInOrg();
    createTestEnvInAccount();
  }

  private void createTestEnvInAccount() {
    for (int i = 0; i < 2; i++) {
      mongoTemplate.save(
          Environment.builder()
              .identifier(envRefs.get(i))
              .type(EnvironmentType.PreProduction)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "environment:\n  name: %s\n  identifier: %s\n  tags: {}\n  type: Production\n  variables:\n    - name: var1\n      type: String\n      value: <+input>\n      description: \"\"\n",
                  envRefs.get(i), envRefs.get(i)))
              .build());
    }
  }

  private void createTestEnvInOrg() {
    for (int i = 0; i < 2; i++) {
      mongoTemplate.save(
          Environment.builder()
              .identifier(envRefs.get(i))
              .type(EnvironmentType.PreProduction)
              .orgIdentifier(ORG_IDENTIFIER)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "environment:\n  name: %s\n  identifier: %s\n  tags: {}\n  type: Production\n  orgIdentifier: %s\n  variables:\n    - name: var1\n      type: String\n      value: <+input>\n      description: \"\"\n",
                  envRefs.get(i), envRefs.get(i), ORG_IDENTIFIER))
              .build());
    }
  }

  private void createTestEnvInProject() {
    for (String projectId : projectIds) {
      for (int i = 0; i < 2; i++) {
        mongoTemplate.save(
            Environment.builder()
                .identifier(envRefs.get(i))
                .type(EnvironmentType.PreProduction)
                .projectIdentifier(projectId)
                .orgIdentifier(ORG_IDENTIFIER)
                .accountId(ACCOUNT_IDENTIFIER)
                .yaml(String.format(
                    "environment:\n  name: %s\n  identifier: %s\n  description: Environment to test service override CRUDs\n  tags: {\n    }\n  type: Production\n  orgIdentifier: %s\n  projectIdentifier: %s\n  variables:\n  - name: var1\n    type: String\n    value: value1\n  overrides:\n    manifests:\n    - manifest:\n        identifier: manifest1\n        type: Values\n        spec:\n          store:\n            type: Git\n            spec:\n              connectorRef: gitConnector\n              gitFetchType: Branch\n              paths:\n              - path1.yaml\n              branch: master\n    - manifest:\n        identifier: manifest2\n        type: Values\n        spec:\n          store:\n            type: Git\n            spec:\n              connectorRef: gitConnector\n              gitFetchType: Branch\n              paths:\n              - path2.yaml\n              branch: master\n    configFiles:\n    - configFile:\n        identifier: configFile1\n        spec:\n          store:\n            type: Harness\n            spec:\n              files:\n              - account:/Add-ons\n    - configFile:\n        identifier: configFile2\n        spec:\n          store:\n            type: Harness\n            spec:\n              files:\n              - account:/Add-ons/test\n              - account:/Add-ons\n",
                    envRefs.get(i), envRefs.get(i), ORG_IDENTIFIER, projectIds.get(i)))
                .build());
      }
    }
  }

  private static String generateEnvSvcBasedIdentifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }

  private static String generateEnvBasedIdentifier(String envRef) {
    return String.join("_", envRef).replace(".", "_");
  }
}
