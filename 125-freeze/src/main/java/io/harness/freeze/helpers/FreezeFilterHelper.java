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
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class FreezeFilterHelper {
  public static Update getUpdateOperations(FreezeConfigEntity freezeConfigEntity) {
    Update update = new Update();
    update.set(FreezeConfigEntityKeys.accountId, freezeConfigEntity.getAccountId());
    update.set(FreezeConfigEntityKeys.orgIdentifier, freezeConfigEntity.getOrgIdentifier());
    update.set(FreezeConfigEntityKeys.projectIdentifier, freezeConfigEntity.getProjectIdentifier());
    update.set(FreezeConfigEntityKeys.identifier, freezeConfigEntity.getIdentifier());
    update.set(FreezeConfigEntityKeys.freezeScope, freezeConfigEntity.getFreezeScope());
    update.set(FreezeConfigEntityKeys.createdAt, freezeConfigEntity.getCreatedAt());
    update.set(FreezeConfigEntityKeys.createdBy, freezeConfigEntity.getCreatedBy());
    update.set(FreezeConfigEntityKeys.description, freezeConfigEntity.getDescription());
    update.set(FreezeConfigEntityKeys.lastUpdatedAt, freezeConfigEntity.getLastUpdatedAt());
    update.set(FreezeConfigEntityKeys.lastUpdatedBy, freezeConfigEntity.getLastUpdatedBy());
    update.set(FreezeConfigEntityKeys.name, freezeConfigEntity.getName());
    update.set(FreezeConfigEntityKeys.status, freezeConfigEntity.getStatus());
    update.set(FreezeConfigEntityKeys.tags, freezeConfigEntity.getTags());
    update.set(FreezeConfigEntityKeys.type, freezeConfigEntity.getType());
    update.set(FreezeConfigEntityKeys.yaml, freezeConfigEntity.getYaml());
    return update;
  }

  public static Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      String searchTerm, FreezeType type, FreezeStatus freezeStatus) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    final List<Criteria> andCriterias = new ArrayList<>();
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria =
          new Criteria().orOperator(where(FreezeConfigEntityKeys.name)
                                        .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
              where(FreezeConfigEntityKeys.status).is(freezeStatus),
              where(FreezeConfigEntityKeys.identifier)
                  .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      andCriterias.add(searchCriteria);
    }

    if (type != null) {
      final Criteria typeCriteria = new Criteria().orOperator(
          where(FreezeConfigEntityKeys.type).is(type.name()), where(FreezeConfigEntityKeys.type).is(null));
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
