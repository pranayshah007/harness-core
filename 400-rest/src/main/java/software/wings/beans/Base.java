/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.security.ThreadLocalUserProvider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PrePersist;
import dev.morphia.annotations.Transient;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

/**
 * The Base class is used to extend all the bean classes that requires persistence. The base class
 * includes common fields such as uuid, createdBy, create timestamp, updatedBy and update timestamp.
 * These fields are common for the beans that are persisted as documents in the mongo DB.
 */

// Do not use base class for your collection class. Instead use subset of the interfaces from the persistence layer:
// PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware
// To implement these interfaces simply define the respective field
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"uuid", "appId"}, callSuper = false)
@FieldNameConstants(innerTypeName = "BaseKeys")
@OwnedBy(PL)
@TargetModule(_957_CG_BEANS)
@Deprecated
public class Base implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                             UpdatedByAware, ApplicationAccess {
  @Deprecated public static final String ID_KEY2 = "_id";
  @Deprecated public static final String APP_ID_KEY2 = "appId";
  @Deprecated public static final String ACCOUNT_ID_KEY2 = "accountId";
  @Deprecated public static final String LAST_UPDATED_AT_KEY2 = "lastUpdatedAt";

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  /**
   * TODO: Add isDeleted boolean field to enable soft delete. @swagat
   */

  @JsonIgnore
  @SchemaIgnore
  @Transient
  private transient String entityYamlPath; // TODO:: remove it with changeSet batching

  @JsonIgnore
  @SchemaIgnore
  public String getEntityYamlPath() {
    return entityYamlPath;
  }

  @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;

  @JsonIgnore
  @SchemaIgnore
  public boolean isSyncFromGit() {
    return syncFromGit;
  }

  public Base(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath) {
    this.uuid = uuid;
    this.appId = appId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.lastUpdatedBy = lastUpdatedBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.entityYamlPath = entityYamlPath;
  }

  /**
   * Invoked before inserting document in mongo by morphia.
   */
  @PrePersist
  public void onSave() {
    if (uuid == null) {
      uuid = generateUuid();
    }
    if (this instanceof Application) {
      this.appId = uuid;
    }
    EmbeddedUser embeddedUser = ThreadLocalUserProvider.threadLocalUser();
    if (createdBy == null && !(this instanceof Account)) {
      createdBy = embeddedUser;
    }
    final long currentTime = currentTimeMillis();
    if (createdAt == 0) {
      createdAt = currentTime;
    }
    lastUpdatedAt = currentTime;
    lastUpdatedBy = embeddedUser;
  }

  @JsonIgnore
  @SchemaIgnore
  public Map<String, Object> getShardKeys() {
    Map<String, Object> shardKeys = new HashMap<>();
    shardKeys.put("appId", appId);
    return shardKeys;
  }
}
