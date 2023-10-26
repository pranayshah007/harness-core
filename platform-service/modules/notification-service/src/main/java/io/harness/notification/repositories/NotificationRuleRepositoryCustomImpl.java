/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.NotificationRule;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
@OwnedBy(PL)
@Slf4j
public class NotificationRuleRepositoryCustomImpl implements NotificationRuleRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public NotificationRule findOne(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, NotificationRule.class);
  }

  @Override
  public Page<NotificationRule> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria);
    query.with(pageable);
    List<NotificationRule> notificationList = mongoTemplate.find(query, NotificationRule.class);
    return PageableExecutionUtils.getPage(notificationList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NotificationRule.class));
  }

  @Override
  public List<NotificationRule> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, NotificationRule.class);
  }
}
