/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.repositories;

import io.harness.notification.entities.Notification;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Notification> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria);
    query.with(pageable);
    List<Notification> notificationList = mongoTemplate.find(query, Notification.class);
    return PageableExecutionUtils.getPage(
        notificationList, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Notification.class));
  }
}
