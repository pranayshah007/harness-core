/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class ServiceOverrideV2SettingsUpdateServiceImplTest extends CDNGTestBase {
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

  @Inject ServiceOverrideV2SettingsUpdateService v2SettingsUpdateService;

  @Before
  public void setup() {
    Reflect.on(v2SettingsUpdateService).set("mongoTemplate", mongoTemplate);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testProjectScopeSettingsUpdate() {
    createOverrideTestData();
    ServiceOverrideSettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, projectIds.get(0), false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelUpdateInfo(), 1);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testOrgScopeSettingsUpdate() {
    createOverrideTestData();
    ServiceOverrideSettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList =
        responseDTO.getProjectLevelUpdateInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelUpdateInfo());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAccountScopeSettingsUpdate() {
    createOverrideTestData();
    ServiceOverrideSettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, null, null, false, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelUpdateInfo()).isEmpty();
    assertThat(responseDTO.getOrgLevelUpdateInfo()).isEmpty();

    AccountLevelOverrideV2SettingsUpdateResponseDTO accountResponseDto = responseDTO.getAccountLevelUpdateInfo();
    assertAccountResponseDTO(accountResponseDto);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAccountLevelWithChildScopes() {
    createOverrideTestData();
    ServiceOverrideSettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, null, null, true, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelUpdateInfo()).isNotEmpty();
    assertThat(responseDTO.getOrgLevelUpdateInfo()).isNotEmpty();

    assertAccountResponseDTO(responseDTO.getAccountLevelUpdateInfo());
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelUpdateInfo());
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelUpdateInfo(), 2);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testOrgScopeSettingsUpdateRevert() {
    createOverrideTestDataForSettingsUpdateRevert();
    ServiceOverrideSettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, true);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList =
        responseDTO.getProjectLevelUpdateInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelSettingsUpdateRevertResponseDTO(responseDTO.getOrgLevelUpdateInfo());
  }

  private void assertAccountResponseDTO(AccountLevelOverrideV2SettingsUpdateResponseDTO accountResponseDto) {
    assertThat(accountResponseDto).isNotNull();

    assertThat(accountResponseDto.isSettingsUpdateSuccessFul()).isTrue();
    assertThat(accountResponseDto.getOverrideResult().getTotalServiceOverrideCount()).isEqualTo(2L);
    assertThat(accountResponseDto.getOverrideResult().getYamlUpdatedServiceOverridesCount()).isEqualTo(2L);

    // assert for the updated entities
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(ACCOUNT_IDENTIFIER);
    criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).isNull()),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).isNull()),
        Criteria.where("yaml_deprecated").exists(true));

    List<NGServiceOverridesEntity> overridesEntities =
        mongoTemplate.find(new Query(criteria), NGServiceOverridesEntity.class);
    assertThat(overridesEntities).hasSize(2);
  }

  private static void assertOrgLevelResponseDTO(List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelInfoList) {
    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);
    OrgLevelOverrideV2SettingsUpdateResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isSettingsUpdateSuccessFul()).isTrue();

    assertThat(orgResponseDTO.getOverrideResult().getTotalServiceOverrideCount()).isEqualTo(2L);
    assertThat(orgResponseDTO.getOverrideResult().getYamlUpdatedServiceOverridesCount()).isEqualTo(2L);
  }

  private void assertProjectLevelResponseDTO(
      List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList, int projectsNumber) {
    assertThat(projectLevelInfoList).isNotEmpty();
    assertThat(projectLevelInfoList).hasSize(projectsNumber);

    ProjectLevelOverrideV2SettingsUpdateResponseDTO projectResponseDTO = projectLevelInfoList.get(0);
    assertThat(projectResponseDTO.isSettingsUpdateSuccessFul()).isTrue();
    assertThat(projectResponseDTO.getOverrideResult().getTotalServiceOverrideCount()).isEqualTo(2L);
    assertThat(projectResponseDTO.getOverrideResult().getYamlUpdatedServiceOverridesCount()).isEqualTo(2L);

    // assert migrated overrides
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectIds.get(0))
                            .and("yaml_deprecated")
                            .exists(true);

    List<NGServiceOverridesEntity> overridesEntities =
        mongoTemplate.find(new Query(criteria), NGServiceOverridesEntity.class);
    assertThat(overridesEntities).hasSize(2);
  }

  private static void assertOrgLevelSettingsUpdateRevertResponseDTO(
      List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelInfoList) {
    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);
    OrgLevelOverrideV2SettingsUpdateResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isSettingsUpdateSuccessFul()).isTrue();

    assertThat(orgResponseDTO.getOverrideResult().getTotalServiceOverrideCount()).isEqualTo(2L);
    assertThat(orgResponseDTO.getOverrideResult().getYamlUpdatedServiceOverridesCount()).isEqualTo(2L);
  }

  private void createOverrideTestData() {
    createTestOrgAndProject();
    createTestOverrideInProject();
    createTestOverrideInOrg();
    createTestOverrideInAccount();
  }

  private void createOverrideTestDataForSettingsUpdateRevert() {
    createTestOrgAndProject();
    createTestOverrideInProject();
    createTestOverrideInOrgForSettingsUpdateRevert();
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
                .isV2(true)
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
              .isV2(true)
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
              .isV2(true)
              .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
              .accountId(ACCOUNT_IDENTIFIER)
              .yaml(String.format(
                  "serviceOverrides:\n  environmentRef: %s\n  serviceRef: %s\n  manifests:\n    - manifest:\n        identifier: manifestIdentifier\n        type: HelmRepoOverride\n        spec:\n          type: Http\n          connectorRef: account.puthrayahelm\n",
                  accountEnvRefs.get(i), accountSvcRefs.get(i)))
              .build());
    }
  }

  private void createTestOverrideInOrgForSettingsUpdateRevert() {
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
      Criteria criteria = new Criteria()
                              .and(NGServiceOverridesEntityKeys.identifier)
                              .is(generateEnvSvcBasedIdentifier(orgEnvRefs.get(i), orgSvcRefs.get(i)));
      Query query = new Query(criteria);
      Update update = new Update();
      update.rename(NGServiceOverridesEntityKeys.yaml, "yaml_deprecated");
      mongoTemplate.updateFirst(query, update, NGServiceOverridesEntity.class);
    }
  }

  private static String generateEnvSvcBasedIdentifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }
}
