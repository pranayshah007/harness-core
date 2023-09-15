/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.ng.core.activityhistory.entity.NGActivity.ActivityHistoryEntityKeys;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.inject.Singleton;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
public class NGActivityQueryCriteriaHelper {
  public void addTimeFilterInTheCriteria(Criteria criteria, long startTime, long endTime) {
    criteria.and(ActivityHistoryEntityKeys.activityTime).gte(startTime).lt(endTime);
  }

  public void populateEntityFQNFilterInCriteria(Criteria criteria, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier) {
    criteria.and(ActivityHistoryEntityKeys.referredEntityFQN)
        .is(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, referredEntityIdentifier));
  }

  public void addReferredEntityTypeCriteria(Criteria criteria, EntityType referredEntityType) {
    if (referredEntityType != null) {
      criteria.and(ActivityHistoryEntityKeys.referredEntityType).is(String.valueOf(referredEntityType));
    }
  }

  public void addReferredByEntityTypeCriteria(Criteria criteria, Set<EntityType> referredByEntityTypes) {
    if (isNotEmpty(referredByEntityTypes)) {
      criteria.and(ActivityHistoryEntityKeys.referredByEntityType)
          .in(referredByEntityTypes.stream().map(String::valueOf).collect(Collectors.toList()));
    }
  }

  public void addActivityTypeCriteria(Criteria criteria, Set<NGActivityType> ngActivityTypes) {
    if (!isEmpty(ngActivityTypes)) {
      criteria.and(ActivityHistoryEntityKeys.type).in(ngActivityTypes);
    }
  }

  public void addSearchTermCriteria(Criteria criteria, String searchTerm) {
    if (isNotBlank(searchTerm)) {
      criteria.orOperator(Criteria.where(ActivityHistoryEntityKeys.referredByEntityName).regex(searchTerm),
          Criteria.where(ActivityHistoryEntityKeys.referredByEntityIdentifier).regex(searchTerm),
          Criteria.where(ActivityHistoryEntityKeys.referredByEntityIdentifier).regex(searchTerm),
          Criteria.where(ActivityHistoryEntityKeys.usageType).regex(searchTerm));
    }
  }
}
