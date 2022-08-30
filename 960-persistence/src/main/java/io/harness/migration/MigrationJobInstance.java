/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migration;

import static io.harness.migration.MigrationJobInstance.COLLECTION_NAME;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.ALL)
@Entity(value = COLLECTION_NAME, noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "MigrationJobInstanceKeys")
public class MigrationJobInstance implements PersistentEntity, UpdatedAtAware {
  public static final String COLLECTION_NAME = "migrationJobInstances";

  @Id private String id;
  long lastUpdatedAt;

  private MigrationJob.Metadata metadata;

  public enum Status {
    BASELINE,
    PENDING,
    FAILED,
    SUCCEEDED;

    private static Set<Status> finalStatuses = EnumSet.<Status>of(BASELINE, FAILED, SUCCEEDED);

    public static boolean isFinalStatus(Status status) {
      return status != null && finalStatuses.contains(status);
    }
  }

  private Status status;
}
