/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddAccountIdToArtifactsMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  @SuppressWarnings("deprecation")
  public void migrate() {
    final DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "artifacts");
    log.info("Adding accountId to Artifacts");
    try (HIterator<Application> applicationHIterator =
             new HIterator<>(wingsPersistence.createQuery(Application.class).fetch())) {
      while (applicationHIterator.hasNext()) {
        Application application = applicationHIterator.next();
        log.info("Adding accountId to artifacts for application {}", application.getUuid());
        final WriteResult result = collection.updateMulti(
            new BasicDBObject(ArtifactKeys.appId, application.getUuid()).append(ArtifactKeys.accountId, null),
            new BasicDBObject("$set", new BasicDBObject(ArtifactKeys.accountId, application.getAccountId())));
        log.info("updated {} artifacts for application {} ", result.getN(), application.getUuid());
      }
    }
    log.info("Adding accountIds to Artifacts completed for all applications");
  }
}
