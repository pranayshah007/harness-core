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
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jodah.failsafe.RetryPolicy;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    NGServiceOverridesEntity updatedEntity = serviceOverridesServiceV2.update(ngServiceOverridesEntity);
    assertThat(updatedEntity).isNotNull();
    assertThat(updatedEntity.getEnvironmentRef()).isEqualTo(ENVIRONMENT_REF);
    assertThat(updatedEntity.getServiceRef()).isEqualTo(SERVICE_REF);
    assertThat(updatedEntity.getType()).isEqualTo(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(updatedEntity.getSpec()).isNotNull();
    assertThat(updatedEntity.getSpec().getVariables()).isNull();
    assertThat(updatedEntity.getSpec().getManifests()).isNotEmpty();
    assertThat(updatedEntity.getSpec().getManifests()).hasSize(1);
    assertThat(updatedEntity.getSpec().getManifests().get(0).getManifest().getIdentifier()).isEqualTo("manifestId");
    assertThat(updatedEntity.getSpec().getManifests().get(0).getManifest().getType())
        .isEqualTo(ManifestConfigType.KUSTOMIZE);
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
  public void testUpdateNoIdentifierGiven() {
    basicOverrideEntity.setIdentifier(null);
    assertThatThrownBy(() -> serviceOverridesServiceV2.update(basicOverrideEntity))
        .hasMessageContaining("One of the required fields is null.");
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
