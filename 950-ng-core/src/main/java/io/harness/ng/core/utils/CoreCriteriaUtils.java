/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

@UtilityClass
@OwnedBy(HarnessTeam.DX)
public class CoreCriteriaUtils {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_ID = "orgIdentifier";
  private final String PROJECT_ID = "projectIdentifier";
  private final String DELETED = "deleted";

  public Criteria createCriteriaForGetList(
      String accountId, String orgIdentifier, String projectIdentifier, boolean deleted) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(ACCOUNT_ID).is(accountId);
      criteria.and(ORG_ID).is(orgIdentifier);
      criteria.and(PROJECT_ID).is(projectIdentifier);
    } else {
      throw new InvalidRequestException("Account identifier cannot be null");
    }
    criteria.and(DELETED).is(deleted);
    return criteria;
  }

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(ACCOUNT_ID).is(accountId);
      criteria.and(ORG_ID).is(orgIdentifier);
      criteria.and(PROJECT_ID).is(projectIdentifier);
    } else {
      throw new InvalidRequestException("Account identifier cannot be null");
    }
    return criteria;
  }
}
