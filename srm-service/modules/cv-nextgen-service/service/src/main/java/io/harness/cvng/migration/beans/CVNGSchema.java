/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "CVNGSchemaKeys")
@StoreIn(DbAliases.CVNG)
@Entity(value = "cvngSchema", noClassnameStored = true)
@HarnessEntity(exportable = true)
public final class CVNGSchema
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, PersistentRegularIterable {
  public static final String SCHEMA_ID = "schema";
  public static final String VERSION = "version";
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @SchemaIgnore @FdIndex private long createdAt;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  private int version;

  private CVNGMigrationStatus cvngMigrationStatus;
  private Long cvngNextIteration;

  private Object object;

  @Override
  public String getUuid() {
    return SCHEMA_ID;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (CVNGSchemaKeys.cvngNextIteration.equals(fieldName)) {
      this.cvngNextIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CVNGSchemaKeys.cvngNextIteration.equals(fieldName)) {
      return this.cvngNextIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public enum CVNGMigrationStatus { RUNNING, SUCCESS, PENDING, ERROR }
}
