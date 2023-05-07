/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.checkerframework.checker.units.qual.C;
import org.opensaml.xmlsec.signature.P;
import org.springframework.data.mongodb.core.query.Criteria;

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
    if (isNotEmpty(orgIdentifier)) {
      criteria.and(ORG_ID).is(orgIdentifier);
    }
    if (isNotEmpty(PROJECT_ID)) {
      criteria.and(PROJECT_ID).is(projectIdentifier);
    }
    if (type != null) {
      criteria.and(TYPE).is(type);
    }
    return criteria;
  }
}
