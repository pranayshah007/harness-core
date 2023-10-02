/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;

import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@UtilityClass
public class ServiceOverrideCriteriaHelper {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_ID = "orgIdentifier";
  private final String PROJECT_ID = "projectIdentifier";

  private final String TYPE = "type";

  public Criteria createCriteriaForGetList(
      @NotNull String accountId, String orgIdentifier, String projectIdentifier, ServiceOverridesType type) {
    Criteria criteria = new Criteria();
    criteria.and(ACCOUNT_ID).is(accountId);
    criteria.and(ORG_ID).is(orgIdentifier);
    criteria.and(PROJECT_ID).is(projectIdentifier);
    criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.spec).exists(true).ne(null);

    if (type != null) {
      criteria.and(TYPE).is(type);
    }
    return criteria;
  }
}
