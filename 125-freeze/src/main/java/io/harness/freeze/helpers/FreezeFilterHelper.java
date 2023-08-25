/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
public class FreezeFilterHelper {
  public static Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      String searchTerm, FreezeType type, FreezeStatus freezeStatus, Long startTime, Long endTime) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    final List<Criteria> andCriterias = new ArrayList<>();
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria =
          new Criteria().orOperator(where(FreezeConfigEntityKeys.name)
                                        .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
              where(FreezeConfigEntityKeys.identifier)
                  .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      andCriterias.add(searchCriteria);
    }

    if (freezeStatus != null) {
      andCriterias.add(new Criteria().where(FreezeConfigEntityKeys.status).is(freezeStatus));
    }
    if (startTime != null && endTime != null) {
      Criteria timeFilterCriteria = new Criteria().andOperator(where(FreezeConfigEntityKeys.lastUpdatedAt).lte(endTime),
          where(FreezeConfigEntityKeys.lastUpdatedAt).gte(startTime));
      andCriterias.add(timeFilterCriteria);
    }

    if (type != null) {
      final Criteria typeCriteria = new Criteria().where(FreezeConfigEntityKeys.type).is(type.name());
      andCriterias.add(typeCriteria);
    }

    if (isNotEmpty(andCriterias)) {
      criteria.andOperator(andCriterias.toArray(Criteria[] ::new));
    }
    return criteria;
  }

  public static Criteria getFreezeEqualityCriteria(FreezeConfigEntity freezeConfig) {
    return Criteria.where(FreezeConfigEntityKeys.accountId)
        .is(freezeConfig.getAccountId())
        .and(FreezeConfigEntityKeys.orgIdentifier)
        .is(freezeConfig.getOrgIdentifier())
        .and(FreezeConfigEntityKeys.projectIdentifier)
        .is(freezeConfig.getProjectIdentifier())
        .and(FreezeConfigEntityKeys.identifier)
        .is(freezeConfig.getIdentifier());
  }
}
