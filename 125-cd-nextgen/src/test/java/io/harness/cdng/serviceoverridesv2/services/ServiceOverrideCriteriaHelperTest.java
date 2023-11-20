/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideCriteriaHelper;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;

public class ServiceOverrideCriteriaHelperTest extends CDNGTestBase {
  private static final String IDENTIFIER = "identifierA";
  private static final String ENVIRONMENT_REF = "envA";
  private static final String INFRA_IDENTIFIER = "infraA";

  private static final String SERVICE_REF = "serviceA";

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectId";

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListForEnvRefs() {
    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, ServiceOverridesType.ENV_SERVICE_OVERRIDE, null,
        OverrideFilterPropertiesDTO.builder().environmentRefs(Arrays.asList("Env1", "Env2")).build());
    assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListForSvcRefs() {
    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, ServiceOverridesType.ENV_SERVICE_OVERRIDE, null,
        OverrideFilterPropertiesDTO.builder().serviceRefs(List.of("Svc1")).build());
    assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListForSearchTerm() {
    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, ServiceOverridesType.ENV_SERVICE_OVERRIDE, "search",
        OverrideFilterPropertiesDTO.builder().serviceRefs(List.of("Svc1")).build());
    assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    assertThat(criteria.getCriteriaObject().get("identifier").toString()).contains("search");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateCriteriaWithAllIdentifiers() {
    OverrideFilterPropertiesDTO overrideFilterPropertiesDTO = OverrideFilterPropertiesDTO.builder()
                                                                  .serviceRefs(List.of("Svc1"))
                                                                  .infraIdentifiers(List.of("Infra1"))
                                                                  .environmentRefs(List.of("Env1"))
                                                                  .build();

    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, ServiceOverridesType.ENV_SERVICE_OVERRIDE, null, overrideFilterPropertiesDTO);
    assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }
}
