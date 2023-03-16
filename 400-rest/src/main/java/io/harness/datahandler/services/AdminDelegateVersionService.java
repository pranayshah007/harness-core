/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_JAR;
import static io.harness.delegate.beans.VersionOverrideType.UPGRADER_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.WATCHER_JAR;

import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.beans.VersionOverride.VersionOverrideKeys;
import io.harness.delegate.beans.VersionOverrideType;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AdminDelegateVersionService {
  private final HPersistence persistence;

  public String setDelegateImageTag(
      final String delegateTag, final String accountId, final boolean validTillNextRelease, final int validFor) {
    return setVersionOverride(accountId, DELEGATE_IMAGE_TAG, delegateTag, validTillNextRelease, validFor);
  }

  public void setUpgraderImageTag(
      final String upgraderTag, final String accountId, final boolean validTillNextRelease, final int validFor) {
    setVersionOverride(accountId, UPGRADER_IMAGE_TAG, upgraderTag, validTillNextRelease, validFor);
  }

  public void setDelegateVersion(
      final String delegateVersion, final String accountId, final boolean validTillNextRelease, final int validFor) {
    setVersionOverride(accountId, DELEGATE_JAR, delegateVersion, validTillNextRelease, validFor);
  }

  public void setWatcherVersion(
      final String watcherVersion, final String accountId, final boolean validTillNextRelease, final int validFor) {
    setVersionOverride(accountId, WATCHER_JAR, watcherVersion, validTillNextRelease, validFor);
  }

  private String setVersionOverride(final String accountId, final VersionOverrideType overrideType,
      final String overrideValue, final boolean validTillNextRelease, final int validFor) {
    final Query<VersionOverride> filter = persistence.createQuery(VersionOverride.class)
                                              .filter(VersionOverrideKeys.accountId, accountId)
                                              .filter(VersionOverrideKeys.overrideType, overrideType);
    final UpdateOperations<VersionOverride> updateOperation =
        persistence.createUpdateOperations(VersionOverride.class).set(VersionOverrideKeys.version, overrideValue);

    if (validTillNextRelease) {
      updateOperation.set(VersionOverrideKeys.validTillNextRelease, true);
      log.info("Setting {} with {} for accountID, will be valid till next release", overrideType, overrideValue);
    } else {
      // Set validUntil only when we don't want to have this version till next release.
      final DateTime validity = DateTime.now().plusDays(validFor);
      updateOperation.set(VersionOverrideKeys.validUntil, validity.toDate());
      log.info("Setting {} with {} for accountID, will be valid till {} days ", overrideType, overrideValue, validFor);
    }
    return persistence.upsert(filter, updateOperation, HPersistence.upsertReturnNewOptions).getVersion();
  }
}
