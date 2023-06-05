/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideCriteriaHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverridesServiceV2Impl;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.tuple.Pair;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

public class ServiceOverridesServiceV2ImplTest extends CDNGTestBase {
  @Inject private ServiceOverridesRepositoryV2 serviceOverridesRepositoryV2;
  @Mock private OutboxService outboxService;
  @Inject private ServiceOverrideValidatorService overrideValidatorService;
  @Inject private RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject @InjectMocks private ServiceOverridesServiceV2Impl serviceOverridesServiceV2;

  private static final String IDENTIFIER = "identifierA";
  private static final String ENVIRONMENT_REF = "envA";

  private static final String SERVICE_REF = "serviceA";

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectId";

  private static final NGServiceOverridesEntity basicOverrideEntity =
      NGServiceOverridesEntity.builder()
          .accountId(ACCOUNT_IDENTIFIER)
          .orgIdentifier(ORG_IDENTIFIER)
          .projectIdentifier(PROJECT_IDENTIFIER)
          .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
          .environmentRef(ENVIRONMENT_REF)
          .serviceRef(SERVICE_REF)
          .spec(ServiceOverridesSpec.builder()
                    .variables(List.of(
                        StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                        StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
                    .build())
          .build();

  @Before
  public void setup() {
    Reflect.on(serviceOverridesServiceV2).set("serviceOverrideRepositoryV2", serviceOverridesRepositoryV2);
    Reflect.on(serviceOverridesServiceV2).set("overrideValidatorService", overrideValidatorService);
    Reflect.on(serviceOverridesServiceV2).set("transactionTemplate", transactionTemplate);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGet() {
    serviceOverridesServiceV2.create(basicOverrideEntity);
    Optional<NGServiceOverridesEntity> createOverrideEntity =
        serviceOverridesServiceV2.get(basicOverrideEntity.getAccountId(), basicOverrideEntity.getOrgIdentifier(),
            basicOverrideEntity.getProjectIdentifier(), basicOverrideEntity.getIdentifier());
    assertThat(createOverrideEntity).isPresent();
    assertBasicOverrideEntityProperties(createOverrideEntity.get());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreate() {
    NGServiceOverridesEntity ngServiceOverridesEntity = serviceOverridesServiceV2.create(basicOverrideEntity);
    assertBasicOverrideEntityProperties(ngServiceOverridesEntity);
    assertThat(ngServiceOverridesEntity.getIdentifier()).isEqualTo(String.join("_", ENVIRONMENT_REF, SERVICE_REF));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdate() {
    NGServiceOverridesEntity ngServiceOverridesEntity = serviceOverridesServiceV2.create(basicOverrideEntity);
    ngServiceOverridesEntity.setSpec(
        ServiceOverridesSpec.builder()
            .manifests(Collections.singletonList(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder().identifier("manifestId").type(ManifestConfigType.KUSTOMIZE).build())
                    .build()))
            .build());

    NGServiceOverridesEntity updatedEntity1 = serviceOverridesServiceV2.update(ngServiceOverridesEntity);
    assertThat(updatedEntity1).isNotNull();
    assertThat(updatedEntity1.getEnvironmentRef()).isEqualTo(ENVIRONMENT_REF);
    assertThat(updatedEntity1.getServiceRef()).isEqualTo(SERVICE_REF);
    assertThat(updatedEntity1.getType()).isEqualTo(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(updatedEntity1.getSpec()).isNotNull();
    assertThat(updatedEntity1.getSpec().getVariables()).isNull();
    assertThat(updatedEntity1.getSpec().getManifests()).isNotEmpty();
    assertThat(updatedEntity1.getSpec().getManifests()).hasSize(1);
    assertThat(updatedEntity1.getSpec().getManifests().get(0).getManifest().getIdentifier()).isEqualTo("manifestId");
    assertThat(updatedEntity1.getSpec().getManifests().get(0).getManifest().getType())
        .isEqualTo(ManifestConfigType.KUSTOMIZE);

    // test multiple update
    ngServiceOverridesEntity.setSpec(
        ServiceOverridesSpec.builder()
            .manifests(Collections.singletonList(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder().identifier("manifestId").type(ManifestConfigType.K8_MANIFEST).build())
                    .build()))
            .build());

    NGServiceOverridesEntity updatedEntity2 = serviceOverridesServiceV2.update(ngServiceOverridesEntity);
    assertThat(updatedEntity2).isNotNull();
    assertThat(updatedEntity2.getSpec().getManifests()).isNotEmpty();
    assertThat(updatedEntity2.getSpec().getManifests().get(0).getManifest().getType())
        .isEqualTo(ManifestConfigType.K8_MANIFEST);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdateNonExistingEntity() {
    NGServiceOverridesEntity entityToBeUpdated =
        NGServiceOverridesEntity.builder()
            .identifier(IDENTIFIER)
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
            .environmentRef(ENVIRONMENT_REF)
            .serviceRef(SERVICE_REF)
            .spec(
                ServiceOverridesSpec.builder()
                    .variables(List.of(
                        StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                        StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
                    .build())
            .build();
    assertThatThrownBy(() -> serviceOverridesServiceV2.update(entityToBeUpdated))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "ServiceOverride [identifierA] under Project[projectId], Organization [orgIdentifier] doesn't exist.");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDelete() {
    serviceOverridesServiceV2.create(basicOverrideEntity);
    boolean isDeleted =
        serviceOverridesServiceV2.delete(basicOverrideEntity.getAccountId(), basicOverrideEntity.getOrgIdentifier(),
            basicOverrideEntity.getProjectIdentifier(), basicOverrideEntity.getIdentifier(), null);
    assertThat(isDeleted).isTrue();
    Optional<NGServiceOverridesEntity> entityInDBPostDelete =
        serviceOverridesServiceV2.get(basicOverrideEntity.getAccountId(), basicOverrideEntity.getOrgIdentifier(),
            basicOverrideEntity.getProjectIdentifier(), basicOverrideEntity.getIdentifier());
    assertThat(entityInDBPostDelete).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteWithoutCreate() {
    assertThatThrownBy(()
                           -> serviceOverridesServiceV2.delete(basicOverrideEntity.getAccountId(),
                               basicOverrideEntity.getOrgIdentifier(), basicOverrideEntity.getProjectIdentifier(),
                               "some_identifier", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Service Override with identifier: [some_identifier], projectId: [projectId], orgId: [orgIdentifier] does not exist");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteInternal() {
    assertThatThrownBy(()
                           -> serviceOverridesServiceV2.delete(basicOverrideEntity.getAccountId(),
                               basicOverrideEntity.getOrgIdentifier(), basicOverrideEntity.getProjectIdentifier(),
                               "some_identifier", basicOverrideEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Service Override [some_identifier], Project[projectId], Organization [orgIdentifier] couldn't be deleted.");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testList() {
    createDbTestDataForListCall();

    // project level with type
    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ServiceOverridesType.ENV_SERVICE_OVERRIDE);

    Pageable pageRequest =
        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));

    List<NGServiceOverridesEntity> overridesEntities =
        serviceOverridesServiceV2.list(criteria, pageRequest).get().collect(Collectors.toList());
    assertThat(overridesEntities).hasSize(1);
    assertThat(overridesEntities.get(0).getIdentifier()).isEqualTo("id0");

    // project level without type
    criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null);
    overridesEntities = serviceOverridesServiceV2.list(criteria, pageRequest).get().collect(Collectors.toList());
    assertThat(overridesEntities).hasSize(2);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("id0", "id1");

    // org level with type
    criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    overridesEntities = serviceOverridesServiceV2.list(criteria, pageRequest).get().collect(Collectors.toList());
    assertThat(overridesEntities).hasSize(1);
    assertThat(overridesEntities.get(0).getIdentifier()).isEqualTo("id2");

    // org level without type
    criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, null);
    overridesEntities = serviceOverridesServiceV2.list(criteria, pageRequest).get().collect(Collectors.toList());
    assertThat(overridesEntities).hasSize(2);
    assertThat(overridesEntities.stream().map(NGServiceOverridesEntity::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("id2", "id3");
  }

  private void createDbTestDataForListCall() {
    NGServiceOverridesEntity entity0 = NGServiceOverridesEntity.builder()
                                           .identifier("id0")
                                           .accountId(ACCOUNT_IDENTIFIER)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJECT_IDENTIFIER)
                                           .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
                                           .environmentRef(ENVIRONMENT_REF)
                                           .serviceRef(SERVICE_REF)
                                           .spec(ServiceOverridesSpec.builder().build())
                                           .build();

    NGServiceOverridesEntity entity1 =
        NGServiceOverridesEntity.builder()
            .identifier("id1")
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
            .environmentRef(ENVIRONMENT_REF)
            .spec(
                ServiceOverridesSpec.builder()
                    .variables(List.of(
                        StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                        StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
                    .build())
            .build();

    NGServiceOverridesEntity entity2 =
        NGServiceOverridesEntity.builder()
            .identifier("id2")
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
            .environmentRef(ENVIRONMENT_REF)
            .serviceRef(SERVICE_REF)
            .spec(
                ServiceOverridesSpec.builder()
                    .variables(List.of(
                        StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                        StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
                    .build())
            .build();

    NGServiceOverridesEntity entity3 =
        NGServiceOverridesEntity.builder()
            .identifier("id3")
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
            .environmentRef(ENVIRONMENT_REF)
            .spec(
                ServiceOverridesSpec.builder()
                    .variables(List.of(
                        StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                        StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
                    .build())
            .build();

    serviceOverridesServiceV2.create(entity0);
    serviceOverridesServiceV2.create(entity1);
    serviceOverridesServiceV2.create(entity2);
    serviceOverridesServiceV2.create(entity3);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertForCreate() {
    Pair<NGServiceOverridesEntity, Boolean> upsertResult = serviceOverridesServiceV2.upsert(basicOverrideEntity);
    assertThat(upsertResult).isNotNull();
    assertThat(upsertResult.getRight()).isTrue();
    assertBasicOverrideEntityProperties(upsertResult.getLeft());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertForUpdate() {
    NGServiceOverridesEntity overridesEntity = serviceOverridesServiceV2.create(basicOverrideEntity);

    overridesEntity.setSpec(
        ServiceOverridesSpec.builder()
            .manifests(List.of(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder().identifier("manifest1").type(ManifestConfigType.KUSTOMIZE).build())
                    .build()))
            .variables(
                List.of(StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                    StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
            .configFiles(List.of(
                ConfigFileWrapper.builder().configFile(ConfigFile.builder().identifier("configFile1").build()).build()))
            .build());

    overridesEntity = serviceOverridesServiceV2.update(overridesEntity);
    overridesEntity.setSpec(
        ServiceOverridesSpec.builder()
            .variables(List.of(
                StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valAUpsert")).build(),
                StringNGVariable.builder().name("varC").value(ParameterField.createValueField("valC")).build()))
            .manifests(List.of(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder().identifier("manifest2").type(ManifestConfigType.K8_MANIFEST).build())
                    .build()))
            .configFiles(List.of(
                ConfigFileWrapper.builder().configFile(ConfigFile.builder().identifier("configFile2").build()).build()))
            .build());

    Pair<NGServiceOverridesEntity, Boolean> upsertResult = serviceOverridesServiceV2.upsert(overridesEntity);
    assertThat(upsertResult).isNotNull();
    assertThat(upsertResult.getRight()).isFalse();
    NGServiceOverridesEntity upsertedOverride = upsertResult.getLeft();
    assertThat(upsertedOverride.getSpec().getVariables().stream().map(NGVariable::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("varA", "varB", "varC");
    assertThat(upsertedOverride.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ngVar -> (String) ngVar.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valAUpsert", "valB", "valC");

    assertThat(upsertedOverride.getSpec()
                   .getManifests()
                   .stream()
                   .map(ManifestConfigWrapper::getManifest)
                   .map(ManifestConfig::getIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("manifest1", "manifest2");
    assertThat(upsertedOverride.getSpec()
                   .getManifests()
                   .stream()
                   .map(ManifestConfigWrapper::getManifest)
                   .map(ManifestConfig::getType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ManifestConfigType.K8_MANIFEST, ManifestConfigType.KUSTOMIZE);

    assertThat(upsertedOverride.getSpec()
                   .getConfigFiles()
                   .stream()
                   .map(ConfigFileWrapper::getConfigFile)
                   .map(ConfigFile::getIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("configFile1", "configFile2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateServiceOverrideInputsYaml() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_SERVICE_OVERRIDE, true);
    serviceOverridesServiceV2.create(testOverrideEntity);
    String serviceOverrideInputsYaml = serviceOverridesServiceV2.createServiceOverrideInputsYaml(ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef(), testOverrideEntity.getServiceRef());
    String expectedInputString = "serviceOverrideInputs:\n"
        + "  variables:\n"
        + "    - name: varA\n"
        + "      type: String\n"
        + "      value: <+input>\n"
        + "    - name: varB\n"
        + "      type: String\n"
        + "      value: <+input>\n"
        + "  manifests:\n"
        + "    - manifest:\n"
        + "        identifier: manifest1\n"
        + "        type: Values\n"
        + "        spec:\n"
        + "          store:\n"
        + "            type: Github\n"
        + "            spec:\n"
        + "              connectorRef: <+input>\n"
        + "              branch: <+input>\n";
    assertThat(expectedInputString).isEqualTo(serviceOverrideInputsYaml);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateServiceOverrideInputsYamlNoInputs() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_SERVICE_OVERRIDE, false);
    serviceOverridesServiceV2.create(testOverrideEntity);
    String serviceOverrideInputsYaml = serviceOverridesServiceV2.createServiceOverrideInputsYaml(ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef(), testOverrideEntity.getServiceRef());

    assertThat(serviceOverrideInputsYaml).isNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateEnvOverrideInputsYaml() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, true);
    serviceOverridesServiceV2.create(testOverrideEntity);
    String envOverrideInputsYaml = serviceOverridesServiceV2.createEnvOverrideInputsYaml(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef());
    String expectedInputString = "environmentInputs:\n"
        + "  overrides:\n"
        + "    manifests:\n"
        + "      - manifest:\n"
        + "          identifier: manifest1\n"
        + "          type: Values\n"
        + "          spec:\n"
        + "            store:\n"
        + "              type: Github\n"
        + "              spec:\n"
        + "                connectorRef: <+input>\n"
        + "                branch: <+input>\n"
        + "  variables:\n"
        + "    - name: varA\n"
        + "      type: String\n"
        + "      value: <+input>\n"
        + "    - name: varB\n"
        + "      type: String\n"
        + "      value: <+input>\n";
    assertThat(expectedInputString).isEqualTo(envOverrideInputsYaml);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateEnvOverrideInputsYamlNoVars() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, true);
    testOverrideEntity.getSpec().setVariables(Collections.EMPTY_LIST);

    serviceOverridesServiceV2.create(testOverrideEntity);
    String envOverrideInputsYaml = serviceOverridesServiceV2.createEnvOverrideInputsYaml(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef());
    String expectedInputString = "environmentInputs:\n"
        + "  overrides:\n"
        + "    manifests:\n"
        + "      - manifest:\n"
        + "          identifier: manifest1\n"
        + "          type: Values\n"
        + "          spec:\n"
        + "            store:\n"
        + "              type: Github\n"
        + "              spec:\n"
        + "                connectorRef: <+input>\n"
        + "                branch: <+input>\n";

    assertThat(expectedInputString).isEqualTo(envOverrideInputsYaml);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateEnvOverrideInputsYamlNoManifests() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, true);
    testOverrideEntity.getSpec().setManifests(Collections.EMPTY_LIST);
    serviceOverridesServiceV2.create(testOverrideEntity);
    String envOverrideInputsYaml = serviceOverridesServiceV2.createEnvOverrideInputsYaml(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef());
    String expectedInputString = "environmentInputs:\n"
        + "  variables:\n"
        + "    - name: varA\n"
        + "      type: String\n"
        + "      value: <+input>\n"
        + "    - name: varB\n"
        + "      type: String\n"
        + "      value: <+input>\n";
    assertThat(expectedInputString).isEqualTo(envOverrideInputsYaml);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateEnvOverrideInputsYamlNoInputs() {
    NGServiceOverridesEntity testOverrideEntity =
        getTestOverrideEntityForRuntimeInput(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, false);
    serviceOverridesServiceV2.create(testOverrideEntity);
    String envOverrideInputsYaml = serviceOverridesServiceV2.createEnvOverrideInputsYaml(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, testOverrideEntity.getEnvironmentRef());
    assertThat(envOverrideInputsYaml).isNull();
  }

  private NGServiceOverridesEntity getTestOverrideEntityForRuntimeInput(
      ServiceOverridesType overridesType, boolean isInputsPresent) {
    return NGServiceOverridesEntity.builder()
        .accountId(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .type(overridesType)
        .environmentRef(ENVIRONMENT_REF)
        .serviceRef(SERVICE_REF)
        .spec(ServiceOverridesSpec.builder()
                  .variables(List.of(
                      StringNGVariable.builder()
                          .name("varA")
                          .value(isInputsPresent ? ParameterField.createExpressionField(true, "<+input>", null, true)
                                                 : ParameterField.createValueField("randomValue"))
                          .build(),
                      StringNGVariable.builder()
                          .name("varB")
                          .value(isInputsPresent ? ParameterField.createExpressionField(true, "<+input>", null, true)
                                                 : ParameterField.createValueField("randomValue"))
                          .build()))
                  .manifests(List.of(
                      ManifestConfigWrapper.builder()
                          .manifest(
                              ManifestConfig.builder()
                                  .type(ManifestConfigType.VALUES)
                                  .identifier("manifest1")
                                  .spec(ValuesManifest.builder()
                                            .identifier("manifest1")
                                            .store(ParameterField.createValueField(
                                                StoreConfigWrapper.builder()
                                                    .type(StoreConfigType.GITHUB)
                                                    .spec(GithubStore.builder()
                                                              .branch(isInputsPresent
                                                                      ? ParameterField.createExpressionField(true,
                                                                          "<+input>", null, true)
                                                                      : ParameterField.createValueField("randomValue"))
                                                              .connectorRef(isInputsPresent
                                                                      ? ParameterField.createExpressionField(
                                                                          true, "<+input>", null, true)
                                                                      : ParameterField.createValueField("randomValue"))
                                                              .paths(ParameterField.createValueField(List.of("file1")))
                                                              .build())
                                                    .build()))
                                            .build())
                                  .build())
                          .build()))
                  .build())
        .build();
  }

  private static void assertBasicOverrideEntityProperties(NGServiceOverridesEntity ngServiceOverridesEntity) {
    assertThat(ngServiceOverridesEntity).isNotNull();
    assertThat(ngServiceOverridesEntity.getEnvironmentRef()).isEqualTo(ENVIRONMENT_REF);
    assertThat(ngServiceOverridesEntity.getServiceRef()).isEqualTo(SERVICE_REF);
    assertThat(ngServiceOverridesEntity.getType()).isEqualTo(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(ngServiceOverridesEntity.getSpec()).isNotNull();
    assertThat(ngServiceOverridesEntity.getSpec().getVariables()).hasSize(2);
    assertThat(ngServiceOverridesEntity.getSpec()
                   .getVariables()
                   .stream()
                   .map(v -> (StringNGVariable) v)
                   .map(StringNGVariable::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("varA", "varB");
    assertThat(ngServiceOverridesEntity.getSpec()
                   .getVariables()
                   .stream()
                   .map(v -> (StringNGVariable) v)
                   .map(StringNGVariable::getValue)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ParameterField.createValueField("valA"), ParameterField.createValueField("valB"));
  }
}
