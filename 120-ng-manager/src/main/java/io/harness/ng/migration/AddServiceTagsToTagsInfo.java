/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddServiceTagsToTagsInfo implements NGMigration {
  public static final int BATCH_LIMIT = 1000;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject NGCoreDataMigrationHelper ngCoreDataMigrationHelper;
  @Inject MongoPersistence mongoPersistence;

  private static final String QUERY =
      "INSERT INTO tags_info VALUES (?,'SERVICE',?) ON CONFLICT (id) DO UPDATE SET tags = excluded.tags;";
  static final String DEBUG_LINE = "Service Tags Timescale Migration: ";

  @Override
  public void migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
    }
    try {
      log.info(DEBUG_LINE + "Migration of Service tags to tags_info table started");

      final DBCollection collection = mongoPersistence.getCollection(ServiceEntity.class);
      BasicDBObject projection =
          new BasicDBObject(ServiceEntityKeys.identifier, Boolean.TRUE).append(ServiceEntityKeys.tags, Boolean.TRUE);

      ngCoreDataMigrationHelper.migrateServiceTag(collection, projection, null, BATCH_LIMIT, QUERY);

      log.info(DEBUG_LINE + "Migration of Service tags to tags_info table successful");

    } catch (Exception e) {
      log.error(DEBUG_LINE + "Exception occurred migrating service tags to tags_info class", e);
    }
  }
}
