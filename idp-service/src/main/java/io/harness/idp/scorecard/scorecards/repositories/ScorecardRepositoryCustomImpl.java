/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.repositories;

import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity.ScorecardKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ScorecardRepositoryCustomImpl implements ScorecardRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public ScorecardEntity update(ScorecardEntity scorecardEntity) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(scorecardEntity.getAccountIdentifier())
                            .and(ScorecardKeys.identifier)
                            .is(scorecardEntity.getIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScorecardKeys.filters, scorecardEntity.getFilters());
    update.set(ScorecardKeys.description, scorecardEntity.getDescription());
    update.set(ScorecardKeys.checks, scorecardEntity.getChecks());
    update.set(ScorecardKeys.name, scorecardEntity.getName());
    update.set(ScorecardKeys.published, scorecardEntity.isPublished());
    update.set(ScorecardKeys.weightageStrategy, scorecardEntity.getWeightageStrategy());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, ScorecardEntity.class);
  }

  @Override
  public DeleteResult delete(String accountIdentifier, String identifier) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScorecardKeys.identifier)
                            .is(identifier);
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, ScorecardEntity.class);
  }
}
