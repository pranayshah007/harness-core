/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datahandler.services;

import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminRingService {
  private final HPersistence persistence;

  public boolean updateDelegateImageTag(final String imageTag, final String ringName) {
    return updateRingKey(imageTag, ringName, DelegateRingKeys.delegateImageTag);
  }

  public boolean updateUpgraderImageTag(final String imageTag, final String ringName) {
    return updateRingKey(imageTag, ringName, DelegateRingKeys.upgraderImageTag);
  }

  public boolean updateDelegateVersion(final List<String> versions, final String ringName) {
    return updateRingKey(versions, ringName, DelegateRingKeys.delegateVersions);
  }

  public boolean updateWatcherVersion(final List<String> versions, final String ringName) {
    return updateRingKey(versions, ringName, DelegateRingKeys.watcherVersions);
  }

  private boolean updateRingKey(final Object ringKeyValue, final String ringName, final String ringKey) {
    final Query<DelegateRing> filter =
        persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName);
    final UpdateOperations<DelegateRing> updateOperation =
        persistence.createUpdateOperations(DelegateRing.class).set(ringKey, ringKeyValue);
    final UpdateResults updateResults = persistence.update(filter, updateOperation);
    return updateResults.getUpdatedExisting();
  }
}
