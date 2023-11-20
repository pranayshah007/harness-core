/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filter.FilterType.OVERRIDE;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.scope.ScopeHelper;

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

  public Criteria createCriteriaForGetList(@NotNull String accountId, String orgIdentifier, String projectIdentifier,
      ServiceOverridesType type, String searchTerm, OverrideFilterPropertiesDTO filterProperties) {
    Criteria criteria = new Criteria();
    criteria.and(ACCOUNT_ID).is(accountId);
    criteria.and(ORG_ID).is(orgIdentifier);
    criteria.and(PROJECT_ID).is(projectIdentifier);
    criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.spec).exists(true).ne(null);

    if (isNotEmpty(searchTerm)) {
      criteria.and(NGServiceOverridesEntityKeys.identifier)
          .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS);
    }

    if (type != null) {
      criteria.and(TYPE).is(type);
    }

    if (filterProperties != null) {
      updateCriteriaWithFilterProperties(criteria, filterProperties);
    }
    return criteria;
  }

  private void updateCriteriaWithFilterProperties(Criteria criteria, OverrideFilterPropertiesDTO filterProperties) {
    populateInFilter(criteria, NGServiceOverridesEntityKeys.environmentRef, filterProperties.getEnvironmentRefs());
    populateInFilter(criteria, NGServiceOverridesEntityKeys.serviceRef, filterProperties.getServiceRefs());
    populateInFilter(criteria, NGServiceOverridesEntityKeys.infraIdentifier, filterProperties.getInfraIdentifiers());
  }
}
