/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.tasklet.dao.impl;

import io.harness.batch.processing.billing.tasklet.dao.intfc.DataGeneratedNotificationDao;
import io.harness.ccm.commons.entities.batch.DataGeneratedNotification;
import io.harness.ccm.commons.entities.batch.DataGeneratedNotification.DataGeneratedNotificationKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class DataGeneratedNotificationDaoImpl implements DataGeneratedNotificationDao {
  @Autowired @Inject private HPersistence hPersistence;

  @Override
  public boolean save(DataGeneratedNotification notification) {
    return hPersistence.save(notification) != null;
  }

  @Override
  public boolean isMailSent(String accountId) {
    Query<DataGeneratedNotification> query = hPersistence.createQuery(DataGeneratedNotification.class)
                                                 .filter(DataGeneratedNotificationKeys.accountId, accountId)
                                                 .filter(DataGeneratedNotificationKeys.mailSent, true);
    return query.get() != null;
  }
}
