/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.LookerEntityReconServiceHelper.performReconciliationHelper;

import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;

import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.looker.LookerEntityReconRecordRepository;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.MorphiaKeyIterator;

@Slf4j
public class CloudProviderReconServiceImpl implements LookerEntityReconService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject LookerEntityReconRecordRepository lookerEntityReconRecordRepository;
  @Inject PersistentLocker persistentLocker;
  @Inject HPersistence persistence;

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, TimeScaleEntity timeScaleEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleEntity, timeScaleDBService,
        lookerEntityReconRecordRepository, persistentLocker, persistence);
  }
  public Set<String> getEntityIdsFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    Set<String> cloudProviderIds = new HashSet<>();
    MorphiaKeyIterator<SettingAttribute> settingAttributes = persistence.createQuery(SettingAttribute.class)
                                                                 .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                                                                 .field(SettingAttributeKeys.accountId)
                                                                 .equal(accountId)
                                                                 .field(SettingAttributeKeys.createdAt)
                                                                 .exists()
                                                                 .field(SettingAttributeKeys.createdAt)
                                                                 .greaterThanOrEq(durationStartTs)
                                                                 .field(SettingAttributeKeys.createdAt)
                                                                 .lessThanOrEq(durationEndTs)
                                                                 .fetchKeys();
    settingAttributes.forEachRemaining(cloudProviderKey -> cloudProviderIds.add((String) cloudProviderKey.getId()));

    return cloudProviderIds;
  }
}
