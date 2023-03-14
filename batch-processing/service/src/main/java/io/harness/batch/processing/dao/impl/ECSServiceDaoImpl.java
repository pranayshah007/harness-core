/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.ccm.commons.entities.ecs.ECSService;
import io.harness.ccm.commons.entities.ecs.ECSService.ECSServiceKeys;
import io.harness.persistence.HPersistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class ECSServiceDaoImpl implements ECSServiceDao {
  @Autowired @Inject private HPersistence hPersistence;
  private final Cache<CacheKey, Boolean> saved = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).build();

  @Value
  private static class CacheKey {
    String clusterId;
    String serviceArn;
  }

  @Override
  public void create(ECSService ecsService) {
    if (ecsService == null) {
      return;
    }
    final CacheKey cacheKey = new CacheKey(ecsService.getClusterId(), ecsService.getServiceArn());
    saved.get(cacheKey,
        key
        -> (hPersistence.upsert(hPersistence.createQuery(ECSService.class)
                                    .field(ECSServiceKeys.accountId)
                                    .equal(ecsService.getAccountId())
                                    .field(ECSServiceKeys.clusterId)
                                    .equal(ecsService.getClusterId())
                                    .field(ECSServiceKeys.serviceArn)
                                    .equal(ecsService.getServiceArn()),
               hPersistence.createUpdateOperations(ECSService.class)
                   .set(ECSServiceKeys.accountId, ecsService.getAccountId())
                   .set(ECSServiceKeys.awsAccountId, ecsService.getAwsAccountId())
                   .set(ECSServiceKeys.clusterId, ecsService.getClusterId())
                   .set(ECSServiceKeys.serviceArn, ecsService.getServiceArn())
                   .set(ECSServiceKeys.serviceName, ecsService.getServiceName())
                   .set(ECSServiceKeys.launchType, ecsService.getLaunchType())
                   .set(ECSServiceKeys.resource, ecsService.getResource())
                   .set(ECSServiceKeys.labels, ecsService.getLabels()),
               HPersistence.upsertReturnNewOptions))
            != null);
  }

  @Override
  public Map<String, ECSService> fetchServices(String accountId, List<String> serviceArns) {
    Map<String, ECSService> resourceMap = new HashMap<>();
    for (ECSService ecsService : hPersistence.createQuery(ECSService.class, excludeValidate)
                                     .field(ECSServiceKeys.accountId)
                                     .equal(accountId)
                                     .field(ECSServiceKeys.serviceArn)
                                     .in(serviceArns)
                                     .fetch()) {
      resourceMap.put(ecsService.getServiceArn(), ecsService);
    }
    return resourceMap;
  }
}
