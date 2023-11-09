/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_CUSTOM_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.beans.VersionOverride.VersionOverrideKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class UpdateOverrideTypeToCustomDelegateImage implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    Query<VersionOverride> query = persistence.createQuery(VersionOverride.class, excludeAuthority)
                                       .filter(VersionOverrideKeys.overrideType, DELEGATE_IMAGE_TAG);

    try (HIterator<VersionOverride> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        VersionOverride versionOverride = iterator.next();
        if (isNotEmpty(versionOverride.getVersion()) && !versionOverride.getVersion().startsWith("harness/delegate")) {
          log.info("Updating OverrideType to DELEGATE_CUSTOM_IMAGE_TAG for version {}", versionOverride.getVersion());
          UpdateOperations<VersionOverride> updateOperation = persistence.createUpdateOperations(VersionOverride.class);
          setUnset(updateOperation, VersionOverrideKeys.overrideType, DELEGATE_CUSTOM_IMAGE_TAG);
          persistence.update(versionOverride, updateOperation);
        }
      }
    }
  }
}
