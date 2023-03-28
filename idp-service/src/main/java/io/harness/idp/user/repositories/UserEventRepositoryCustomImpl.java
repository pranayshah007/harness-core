/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.repositories;

import io.harness.idp.user.beans.entity.UserEventEntity;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class UserEventRepositoryCustomImpl implements UserEventRepositoryCustom {
  private MongoTemplate mongoTemplate;
  public UserEventEntity saveOrUpdate(UserEventEntity userEventEntity) {
    Criteria criteria =
        Criteria.where(UserEventEntity.UserEventValue.accountIdentifier).is(userEventEntity.getAccountIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(UserEventEntity.UserEventValue.hasEvent, userEventEntity.isHasEvent());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
    return mongoTemplate.findAndModify(query, update, options, UserEventEntity.class);
  }
}
