/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckEntity.CheckKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CheckRepositoryCustomImpl implements CheckRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public CheckEntity update(CheckEntity checkEntity) {
    Criteria criteria = Criteria.where(CheckKeys.accountIdentifier)
                            .is(checkEntity.getAccountIdentifier())
                            .and(CheckKeys.identifier)
                            .is(checkEntity.getIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CheckKeys.name, checkEntity.getName());
    update.set(CheckKeys.description, checkEntity.getDescription());
    update.set(CheckKeys.defaultBehaviour, checkEntity.getDefaultBehaviour());
    update.set(CheckKeys.expression, checkEntity.getExpression());
    update.set(CheckKeys.rules, checkEntity.getRules());
    update.set(CheckKeys.failMessage, checkEntity.getFailMessage());
    update.set(CheckKeys.ruleStrategy, checkEntity.getRuleStrategy());
    update.set(CheckKeys.tags, checkEntity.getTags());
    update.set(CheckKeys.labels, checkEntity.getLabels());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, CheckEntity.class);
  }
}
