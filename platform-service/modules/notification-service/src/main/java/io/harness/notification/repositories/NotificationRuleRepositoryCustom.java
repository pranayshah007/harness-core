/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.repositories;

import io.harness.notification.entities.NotificationRule;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface NotificationRuleRepositoryCustom {
  NotificationRule findOne(Criteria criteria);

  Page<NotificationRule> findAll(Criteria criteria, Pageable pageable);

  List<NotificationRule> findAll(Criteria criteria);
}
