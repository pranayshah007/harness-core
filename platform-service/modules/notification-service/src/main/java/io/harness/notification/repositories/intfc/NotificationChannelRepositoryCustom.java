/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.repositories.intfc;

import io.harness.notification.entities.NotificationChannel;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface NotificationChannelRepositoryCustom {
  NotificationChannel findOne(Criteria criteria);

  Page<NotificationChannel> findAll(Criteria criteria, Pageable pageable);

  List<NotificationChannel> findAllWithCriteria(Criteria criteria);
}
