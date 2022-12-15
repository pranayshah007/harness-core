/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dashboard;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

@Data
@Builder
@FieldNameConstants(innerTypeName = "keys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "dashboardSettings", noClassnameStored = true)
@JsonIgnoreProperties(value = {"lastUpdatedBy"}, allowSetters = true)
@HarnessEntity(exportable = true)
public class DashboardSettings implements NameAccess, PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                          UpdatedAtAware, UpdatedByAware, AccountAccess {
  private EmbeddedUser createdBy;
  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  private long createdAt;
  private long lastUpdatedAt;
  @FdIndex private String accountId;
  private String data;
  private String description;
  private String name;
  @Transient private boolean isOwner;
  @Transient private boolean isShared;
  @Transient private boolean canUpdate;
  @Transient private boolean canDelete;
  @Transient private boolean canManage;
  private List<DashboardAccessPermissions> permissions;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
}
