/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(PL)
@Value
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "elasticsearchPendingBulkMigrations", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ElasticsearchBulkMigrationJobKeys")
@Slf4j
public class ElasticsearchBulkMigrationJob implements PersistentEntity {
  @Id private String entityClass;
  private String newIndexName;
  private String oldIndexName;
  private String fromVersion;
  private String toVersion;
}
