/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migration.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.entities.NGSchema;
import io.harness.ng.DbAliases;

import java.util.Map;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@StoreIn(DbAliases.TEST)
@Entity(value = "ngschema")
@Document("ngschema")
@HarnessEntity(exportable = true)
@Persistent
@OwnedBy(DX)
public class NGSchemaTestClass extends NGSchema {
  public NGSchemaTestClass(
      String id, Long createdAt, Long lastUpdatedAt, String name, Map<MigrationType, Integer> migrationDetails) {
    super(id, createdAt, lastUpdatedAt, name, migrationDetails);
  }
}
