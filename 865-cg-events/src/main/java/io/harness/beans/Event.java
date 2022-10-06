/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "EventsKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "events", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class Event implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, ApplicationAccess,
                              AccountAccess, PersistentRegularIterable {
  public enum EventCreatorSource { CD, CDNG, CIE }

  @FdIndex @NotEmpty @NotNull private String accountId;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore @FdIndex private long createdAt;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @NotNull @FdIndex private EventStatus status;
  @NotNull private String method;
  private int failedRetryCount;
  private long maxRetryAllowed;
  private EventDetail details;
  private EventCreatorSource source;
  private EventPayload payload;
  private long deliveredAt;
  private long lastFailureTime;
  @NotEmpty @FdIndex private String eventConfigId;
  @FdIndex private long nextIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

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
  public void setLastUpdatedAt(long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  @Override
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
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
