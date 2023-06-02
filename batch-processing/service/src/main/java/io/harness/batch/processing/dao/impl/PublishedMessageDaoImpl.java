/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.persistence.HQuery.excludeCount;

import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.events.PublishedMessage.PublishedMessageKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@Singleton
public class PublishedMessageDaoImpl implements PublishedMessageDao {
  @Autowired @Inject private HPersistence hPersistence;

  @Override
  public List<PublishedMessage> fetchPublishedMessage(
      String accountId, String messageType, Long startTime, Long endTime, int batchSize) {
    Query<PublishedMessage> query = hPersistence.createQuery(PublishedMessage.class, excludeCount)
                                        .filter(PublishedMessageKeys.accountId, accountId)
                                        .filter(PublishedMessageKeys.type, messageType)
                                        .order(PublishedMessageKeys.createdAt);

    query.and(query.criteria(PublishedMessageKeys.createdAt).greaterThanOrEq(startTime),
        query.criteria(PublishedMessageKeys.createdAt).lessThan(endTime));
    query.useReadPreference(ReadPreference.secondaryPreferred());
    return query.asList(new FindOptions().limit(batchSize));
  }
}
