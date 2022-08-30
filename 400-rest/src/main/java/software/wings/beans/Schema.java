/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "schema", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class Schema implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static final String SCHEMA_ID = "schema";
  public static final String VERSION = "version";
  public static final String BACKGROUND_VERSION = "backgroundVersion";
  public static final String SEED_DATA_VERSION = "seedDataVersion";
  public static final String TIMESCALEDB_VERSION = "timescaleDbVersion";
  public static final String TIMESCALEDB_DATA_VERSION = "timescaleDBDataVersion";
  public static final String ON_PRIMARY_MANAGER_VERSION = "onPrimaryManagerVersion";
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @SchemaIgnore @FdIndex private long createdAt;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  private int version;
  private int backgroundVersion;
  private int seedDataVersion;
  private int timescaleDbVersion;
  private int timescaleDBDataVersion;
  private int onPrimaryManagerVersion;

  @Override
  public String getUuid() {
    return SCHEMA_ID;
  }
}
