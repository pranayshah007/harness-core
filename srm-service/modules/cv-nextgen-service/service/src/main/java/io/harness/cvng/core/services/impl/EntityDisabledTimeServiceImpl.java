/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class EntityDisabledTimeServiceImpl implements EntityDisabledTimeService {
  @Inject private HPersistence hPersistence;

  @Override
  public void save(EntityDisableTime entityDisableTime) {
    hPersistence.save(entityDisableTime);
  }

  @Override
  public List<EntityDisableTime> get(String entityId, String accountId) {
    return hPersistence.createQuery(EntityDisableTime.class, excludeAuthorityCount)
        .filter(EntityDisableTime.EntityDisabledTimeKeys.entityUUID, entityId)
        .filter(EntityDisableTime.EntityDisabledTimeKeys.accountId, accountId)
        .order(Sort.ascending(EntityDisableTime.EntityDisabledTimeKeys.startTime))
        .asList();
  }
}
