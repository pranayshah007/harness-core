/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.services.impl;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
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
  public void testValidateDuplicateServiceOverrides() {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .environmentRef(ENV_REF)
            .serviceRef(SERVICE_REF)
            .yaml(
                "serviceOverrides:\n  orgIdentifier: orgIdentifier\\\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variableOverrides: \n    - name: op1\n      value: var1\n      type: String\n    - name: op1\n      value: var1\n      type: String")
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
}
