/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "MigratedEntityMappingKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "migratedEntityMapping")
@HarnessEntity(exportable = false)
public class MigratedEntityMapping implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                              UpdatedAtAware, UpdatedByAware, ApplicationAccess, AccountAccess {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @FdIndex @NotNull @SchemaIgnore private String accountId;
  @FdIndex @NotNull @SchemaIgnore private String cgEntityId;
  @FdIndex @NotNull @SchemaIgnore private String entityType;
  @FdIndex @SchemaIgnore private String appId;

  @FdIndex @NotNull @SchemaIgnore private String accountIdentifier;
  @FdIndex @SchemaIgnore private String orgIdentifier;
  @FdIndex @SchemaIgnore private String projectIdentifier;
  @FdIndex @SchemaIgnore private String identifier;
  @FdIndex @SchemaIgnore private Scope scope;
  @FdIndex @SchemaIgnore private String fullyQualifiedIdentifier;

  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  @Override
  public String getAccountId() {
    return accountId;
  }

  @Override
  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public long getCreatedAt() {
    return createdAt;
  }

  @Override
  public void setCreatedBy(EmbeddedUser createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public EmbeddedUser getCreatedBy() {
    return createdBy;
  }

  @Override
  public void setLastUpdatedAt(long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  @Override
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  @Override
  public void setLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  @Override
  public EmbeddedUser getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  @Override
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  @Override
  public String getAppId() {
    return appId;
  }
}
