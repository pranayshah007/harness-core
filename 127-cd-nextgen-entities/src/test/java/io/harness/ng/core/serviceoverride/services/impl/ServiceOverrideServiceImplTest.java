/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.services.impl;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class ServiceOverrideServiceImplTest extends NGCoreTestBase {
  @Inject ServiceOverrideServiceImpl serviceOverrideService;
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "orgIdentifier";
  private final String PROJECT_IDENTIFIER = "projectIdentifier";
  private final String ENV_REF = "envIdentifier";
  private final String SERVICE_REF = "serviceIdentifier";
  private final String ORG_ENV_REF = "org.envIdentifier";
  private final String ORG_SERVICE_REF = "org.serviceIdentifier";
  private final String ACCOUNT_ENV_REF = "account.envIdentifier";
  private final String ACCOUNT_SERVICE_REF = "account.serviceIdentifier";

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> serviceOverrideService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidateEmptyServiceOverrides() {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\\\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: \"\"\n      value: var1\n      type: String\n    - name: op1\n      value: var1\n      type: String")
            .build();
    assertThatThrownBy(() -> serviceOverrideService.validateOverrideValues(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("Empty variable name for 1 variable override in service ref: [%s]", SERVICE_REF));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidateDuplicateServiceOverrides() {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\\\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: op1\n      value: var1\n      type: String\n    - name: op1\n      value: var1\n      type: String")
            .build();
    assertThatThrownBy(() -> serviceOverrideService.validateOverrideValues(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("Duplicate Service overrides provided: [op1] for service: [%s]", SERVICE_REF));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    // upsert
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\\\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variableOverrides: \n    - name: memory\n      value: var1\n      type: String\n    - name: cpu\n      value: var1\n      type: String")
            .build();
    NGServiceOverridesEntity upsertedServiceOverridesEntity = serviceOverrideService.upsert(serviceOverridesEntity);
    assertThat(upsertedServiceOverridesEntity).isNotNull();
    assertThat(upsertedServiceOverridesEntity.getAccountId()).isEqualTo(serviceOverridesEntity.getAccountId());
    assertThat(upsertedServiceOverridesEntity.getOrgIdentifier()).isEqualTo(serviceOverridesEntity.getOrgIdentifier());
    assertThat(upsertedServiceOverridesEntity.getProjectIdentifier())
        .isEqualTo(serviceOverridesEntity.getProjectIdentifier());
    assertThat(upsertedServiceOverridesEntity.getServiceRef()).isEqualTo(serviceOverridesEntity.getServiceRef());
    assertThat(upsertedServiceOverridesEntity.getEnvironmentRef())
        .isEqualTo(serviceOverridesEntity.getEnvironmentRef());
    assertThat(upsertedServiceOverridesEntity.getYaml()).isNotNull();

    // list
    Criteria criteriaFromFilter =
        CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    Pageable pageRequest = PageUtils.getPageRequest(0, 100, null);
    Page<NGServiceOverridesEntity> list = serviceOverrideService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceOverridesInputs() throws IOException {
    String filename = "serviceOverrides-with-runtime-inputs.yaml";
    String yaml = readFile(filename);

    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .environmentRef(ENV_REF)
                                                          .serviceRef(SERVICE_REF)
                                                          .yaml(yaml)
                                                          .build();
    serviceOverrideService.upsert(serviceOverridesEntity);

    String serviceOverrideInputs = serviceOverrideService.createServiceOverrideInputsYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_REF, SERVICE_REF);

    String resFile = "serviceOverrides-with-runtime-inputs-res.yaml";
    String resInputs = readFile(resFile);
    assertThat(serviceOverrideInputs).isEqualTo(resInputs);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceOverridesInputsWithoutRuntimeInputs() throws IOException {
    String filename = "serviceOverrides-without-runtime-inputs.yaml";
    String yaml = readFile(filename);

    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .environmentRef(ENV_REF)
                                                          .serviceRef(SERVICE_REF)
                                                          .yaml(yaml)
                                                          .build();
    serviceOverrideService.upsert(serviceOverridesEntity);

    String serviceOverrideInputs = serviceOverrideService.createServiceOverrideInputsYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_REF, SERVICE_REF);

    assertThat(serviceOverrideInputs).isNull();
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteAllInEnv() {
    final String proj = UUIDGenerator.generateUuid();
    final String env1 = UUIDGenerator.generateUuid();
    final String env2 = UUIDGenerator.generateUuid();
    NGServiceOverridesEntity e1 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    NGServiceOverridesEntity e2 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    NGServiceOverridesEntity e3 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env2)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    serviceOverrideService.upsert(e1);
    serviceOverrideService.upsert(e2);
    serviceOverrideService.upsert(e3);

    assertThat(serviceOverrideService.deleteAllInEnv(ACCOUNT_ID, ORG_IDENTIFIER, proj, env1)).isTrue();

    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e1.getEnvironmentRef(), e1.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e2.getEnvironmentRef(), e2.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e3.getEnvironmentRef(), e3.getServiceRef()))
        .isPresent();
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteAllInProject() {
    final String org1 = UUIDGenerator.generateUuid();
    final String org2 = UUIDGenerator.generateUuid();
    final String proj1 = UUIDGenerator.generateUuid();
    final String proj2 = UUIDGenerator.generateUuid();
    final String env1 = UUIDGenerator.generateUuid();
    final String env2 = UUIDGenerator.generateUuid();
    NGServiceOverridesEntity e1 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org1)
                                      .projectIdentifier(proj1)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    NGServiceOverridesEntity e2 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org1)
                                      .projectIdentifier(proj1)
                                      .environmentRef(env2)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    NGServiceOverridesEntity e3 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .projectIdentifier(proj2)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    NGServiceOverridesEntity e4 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .projectIdentifier(proj1)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    NGServiceOverridesEntity e5 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .projectIdentifier(proj2)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    serviceOverrideService.upsert(e1);
    serviceOverrideService.upsert(e2);
    serviceOverrideService.upsert(e3);
    serviceOverrideService.upsert(e4);
    serviceOverrideService.upsert(e5);

    // should delete e3, e5
    assertThat(serviceOverrideService.deleteAllInProject(ACCOUNT_ID, org2, proj2)).isTrue();

    assertThat(serviceOverrideService.get(
                   ACCOUNT_ID, e1.getOrgIdentifier(), proj1, e1.getEnvironmentRef(), e1.getServiceRef()))
        .isPresent();
    assertThat(serviceOverrideService.get(
                   ACCOUNT_ID, e2.getOrgIdentifier(), proj1, e2.getEnvironmentRef(), e2.getServiceRef()))
        .isPresent();
    assertThat(serviceOverrideService.get(
                   ACCOUNT_ID, e3.getOrgIdentifier(), proj1, e3.getEnvironmentRef(), e3.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(
                   ACCOUNT_ID, e4.getOrgIdentifier(), proj1, e4.getEnvironmentRef(), e4.getServiceRef()))
        .isPresent();
    assertThat(serviceOverrideService.get(
                   ACCOUNT_ID, e5.getOrgIdentifier(), proj1, e5.getEnvironmentRef(), e5.getServiceRef()))
        .isNotPresent();
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteAllInProjectForAService() {
    final String proj = UUIDGenerator.generateUuid();
    final String env1 = UUIDGenerator.generateUuid();
    final String env2 = UUIDGenerator.generateUuid();
    final String svc1 = UUIDGenerator.generateUuid();
    final String svc2 = UUIDGenerator.generateUuid();
    NGServiceOverridesEntity e1 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env1)
                                      .serviceRef(svc1)
                                      .build();
    NGServiceOverridesEntity e2 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env1)
                                      .serviceRef(svc1)
                                      .build();

    NGServiceOverridesEntity e3 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env2)
                                      .serviceRef(svc1)
                                      .build();

    NGServiceOverridesEntity e4 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(proj)
                                      .environmentRef(env1)
                                      .serviceRef(svc2)
                                      .build();

    serviceOverrideService.upsert(e1);
    serviceOverrideService.upsert(e2);
    serviceOverrideService.upsert(e3);
    serviceOverrideService.upsert(e4);

    assertThat(serviceOverrideService.deleteAllInProjectForAService(ACCOUNT_ID, ORG_IDENTIFIER, proj, svc1)).isTrue();

    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e1.getEnvironmentRef(), e1.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e2.getEnvironmentRef(), e2.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e3.getEnvironmentRef(), e3.getServiceRef()))
        .isNotPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, proj, e4.getEnvironmentRef(), e4.getServiceRef()))
        .isPresent();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateBlankServiceOverrides() {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\\\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: \"    \"\n      value: var1\n      type: String\n    - name: op1\n      value: var1\n      type: String")
            .build();
    assertThatThrownBy(() -> serviceOverrideService.validateOverrideValues(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("Empty variable name for 1 variable override in service ref: [%s]", SERVICE_REF));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testOrgAccountLevelCRUD() {
    // upsert
    // org level env overriding account level service
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(ACCOUNT_SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  environmentRef: envIdentifier\n  serviceRef: account.serviceIdentifier\n  variableOverrides: \n    - name: memory\n      value: var1\n      type: String\n    - name: cpu\n      value: var1\n      type: String")
            .build();
    NGServiceOverridesEntity upsertedServiceOverridesEntity = serviceOverrideService.upsert(serviceOverridesEntity);
    assertThat(upsertedServiceOverridesEntity).isNotNull();
    assertThat(upsertedServiceOverridesEntity.getOrgIdentifier()).isEqualTo(serviceOverridesEntity.getOrgIdentifier());
    assertThat(upsertedServiceOverridesEntity.getProjectIdentifier())
        .isEqualTo(serviceOverridesEntity.getProjectIdentifier());
    assertThat(upsertedServiceOverridesEntity.getServiceRef()).isEqualTo(serviceOverridesEntity.getServiceRef());
    assertThat(upsertedServiceOverridesEntity.getEnvironmentRef())
        .isEqualTo("org." + serviceOverridesEntity.getEnvironmentRef());
    assertThat(upsertedServiceOverridesEntity.getYaml()).isNotNull();

    NGServiceOverridesEntity serviceOverridesEntity2 =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(ORG_SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  environmentRef: envIdentifier\n  serviceRef: org.serviceIdentifier\n  variableOverrides: \n    - name: memory\n      value: var1\n      type: String\n    - name: cpu\n      value: var1\n      type: String")
            .build();
    serviceOverrideService.upsert(serviceOverridesEntity2);
    // list
    Criteria criteriaFromFilter = CoreCriteriaUtils.createCriteriaForGetList(ACCOUNT_ID, ORG_IDENTIFIER, null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 100, null);
    Page<NGServiceOverridesEntity> list = serviceOverrideService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);

    // get
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, null, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isPresent();

    // delete
    assertThat(serviceOverrideService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCRUDWithScopedEnvironmentRef() {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .environmentRef("org." + ENV_REF)
            .serviceRef(ACCOUNT_SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  environmentRef: account.envIdentifier\n  serviceRef: account.serviceIdentifier\n  variableOverrides: \n    - name: memory\n      value: var1\n      type: String\n    - name: cpu\n      value: var1\n      type: String")
            .build();
    NGServiceOverridesEntity upsertedServiceOverridesEntity = serviceOverrideService.upsert(serviceOverridesEntity);
    assertThat(upsertedServiceOverridesEntity).isNotNull();
    assertThat(upsertedServiceOverridesEntity.getEnvironmentRef())
        .isEqualTo(serviceOverridesEntity.getEnvironmentRef());
    assertThat(upsertedServiceOverridesEntity.getYaml()).isNotNull();

    // get with scoped environment ref and environment identifier
    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, null, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isPresent();
    assertThat(serviceOverrideService.get(ACCOUNT_ID, ORG_IDENTIFIER, null, ENV_REF, ACCOUNT_SERVICE_REF)).isPresent();

    // delete with scoped environment ref
    assertThat(serviceOverrideService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isTrue();

    serviceOverrideService.upsert(serviceOverridesEntity);
    assertThat(
        serviceOverrideService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ORG_ENV_REF, ACCOUNT_SERVICE_REF))
        .isTrue();

    serviceOverrideService.upsert(serviceOverridesEntity);
    assertThat(serviceOverrideService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, ENV_REF, ACCOUNT_SERVICE_REF)).isTrue();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testDeleteAllOrgLevelOverrides() {
    final String org1 = UUIDGenerator.generateUuid();
    final String org2 = UUIDGenerator.generateUuid();

    final String env1 = UUIDGenerator.generateUuid();
    final String env2 = UUIDGenerator.generateUuid();

    NGServiceOverridesEntity e1 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org1)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    NGServiceOverridesEntity e2 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org1)
                                      .environmentRef(env2)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    NGServiceOverridesEntity e3 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();
    NGServiceOverridesEntity e4 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    NGServiceOverridesEntity e5 = NGServiceOverridesEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(org2)
                                      .environmentRef(env1)
                                      .serviceRef(UUIDGenerator.generateUuid())
                                      .build();

    serviceOverrideService.upsert(e1);
    serviceOverrideService.upsert(e2);
    serviceOverrideService.upsert(e3);
    serviceOverrideService.upsert(e4);
    serviceOverrideService.upsert(e5);

    // should delete e3, e5
    assertThat(serviceOverrideService.deleteAllInOrg(ACCOUNT_ID, org2)).isTrue();

    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, e1.getOrgIdentifier(), null, e1.getEnvironmentRef(), e1.getServiceRef()))
        .isPresent();
    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, e2.getOrgIdentifier(), null, e2.getEnvironmentRef(), e2.getServiceRef()))
        .isPresent();
    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, e3.getOrgIdentifier(), null, e3.getEnvironmentRef(), e3.getServiceRef()))
        .isNotPresent();
    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, e4.getOrgIdentifier(), null, e4.getEnvironmentRef(), e4.getServiceRef()))
        .isNotPresent();
    assertThat(
        serviceOverrideService.get(ACCOUNT_ID, e5.getOrgIdentifier(), null, e5.getEnvironmentRef(), e5.getServiceRef()))
        .isNotPresent();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServiceOverridesWithEmptyValuedStringVariables() throws IOException {
    String filename = "serviceOverridesWithEmptyValuedVariables.yaml";
    String yaml = readFile(filename);

    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .environmentRef(ENV_REF)
                                                          .serviceRef(SERVICE_REF)
                                                          .yaml(yaml)
                                                          .build();

    assertThatThrownBy(() -> serviceOverrideService.validateOverrideValues(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format(
            "values not provided for 3 variable overrides var1 var2 var3 in service ref: [%s]", SERVICE_REF));
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
