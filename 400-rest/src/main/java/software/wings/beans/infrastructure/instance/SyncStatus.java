/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

/**
 * Keeps track of the last sync status and time of the infra mapping.
 *
 * @author rktummala on 05/19/18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "syncStatus", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "SyncStatusKeys")
public class SyncStatus extends Base {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("compositeIdx1")
                 .unique(true)
                 .field(Base.APP_ID_KEY2)
                 .field(SyncStatusKeys.serviceId)
                 .field(SyncStatusKeys.envId)
                 .field(SyncStatusKeys.infraMappingId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("compositeIdx2")
                 .field(Base.APP_ID_KEY2)
                 .field(SyncStatusKeys.infraMappingId)
                 .build())
        .build();
  }

  public static final String SERVICE_ID_KEY = "serviceId";
  public static final String ENV_ID_KEY = "envId";
  public static final String INFRA_MAPPING_ID_KEY = "infraMappingId";

  private String envId;
  private String serviceId;
  private String infraMappingId;
  private String infraMappingName;

  private long lastSyncedAt;
  private long lastSuccessfullySyncedAt;
  private String syncFailureReason;

  @Builder
  public SyncStatus(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, String envId, String serviceId, String infraMappingId,
      String infraMappingName, long lastSyncedAt, long lastSuccessfullySyncedAt, String syncFailureReason) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.envId = envId;
    this.serviceId = serviceId;
    this.infraMappingId = infraMappingId;
    this.infraMappingName = infraMappingName;
    this.lastSyncedAt = lastSyncedAt;
    this.lastSuccessfullySyncedAt = lastSuccessfullySyncedAt;
    this.syncFailureReason = syncFailureReason;
  }
}
