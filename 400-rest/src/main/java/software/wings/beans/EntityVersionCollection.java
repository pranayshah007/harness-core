/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.yaml.BaseYaml;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@StoreIn(DbAliases.HARNESS)
@Entity(value = "entityVersions", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "EntityVersionCollectionKeys")
@HarnessEntity(exportable = true)
public class EntityVersionCollection extends EntityVersion {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .addAll(EntityVersion.mongoIndexes())
        .add(CompoundMongoIndex.builder()
                 .name("unique_locate")
                 .unique(true)
                 .field(EntityVersionKeys.entityType)
                 .field(EntityVersionKeys.entityUuid)
                 .field(EntityVersionKeys.version)
                 .build())
        .build();
  }

  public static final class Builder {
    private EntityType entityType;
    private String entityName;
    private ChangeType changeType;
    private String entityUuid;
    private String entityParentUuid;
    private String entityData;
    private Integer version;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String accountId;

    private Builder() {}

    public static Builder anEntityVersionCollection() {
      return new Builder();
    }

    public Builder withEntityType(EntityType entityType) {
      this.entityType = entityType;
      return this;
    }

    public Builder withEntityName(String entityName) {
      this.entityName = entityName;
      return this;
    }

    public Builder withChangeType(ChangeType changeType) {
      this.changeType = changeType;
      return this;
    }

    public Builder withEntityUuid(String entityUuid) {
      this.entityUuid = entityUuid;
      return this;
    }

    public Builder withEntityParentUuid(String entityParentUuid) {
      this.entityParentUuid = entityParentUuid;
      return this;
    }

    public Builder withEntityData(String entityData) {
      this.entityData = entityData;
      return this;
    }

    public Builder withVersion(Integer version) {
      this.version = version;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder but() {
      return anEntityVersionCollection()
          .withEntityType(entityType)
          .withEntityName(entityName)
          .withChangeType(changeType)
          .withEntityUuid(entityUuid)
          .withEntityParentUuid(entityParentUuid)
          .withEntityData(entityData)
          .withVersion(version)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withAccountId(accountId);
    }

    public EntityVersionCollection build() {
      EntityVersionCollection entityVersionCollection = new EntityVersionCollection();
      entityVersionCollection.setEntityType(entityType);
      entityVersionCollection.setEntityName(entityName);
      entityVersionCollection.setChangeType(changeType);
      entityVersionCollection.setEntityUuid(entityUuid);
      entityVersionCollection.setEntityParentUuid(entityParentUuid);
      entityVersionCollection.setEntityData(entityData);
      entityVersionCollection.setVersion(version);
      entityVersionCollection.setUuid(uuid);
      entityVersionCollection.setAppId(appId);
      entityVersionCollection.setCreatedBy(createdBy);
      entityVersionCollection.setCreatedAt(createdAt);
      entityVersionCollection.setLastUpdatedBy(lastUpdatedBy);
      entityVersionCollection.setLastUpdatedAt(lastUpdatedAt);
      entityVersionCollection.setAccountId(accountId);
      return entityVersionCollection;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYaml {}
}
