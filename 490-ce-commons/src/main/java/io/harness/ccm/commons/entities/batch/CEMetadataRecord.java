/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.batch;

import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "ceMetadataRecord", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "CEMetadataRecordKeys")
public final class CEMetadataRecord implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("accountId").field(CEMetadataRecordKeys.accountId).unique(true).build())
        .build();
  }
  @Id private String uuid;
  private String accountId;
  private Boolean clusterConnectorConfigured;
  private Boolean clusterDataConfigured;
  private Boolean awsConnectorConfigured;
  private Boolean azureConnectorConfigured;
  private Boolean awsDataPresent;
  private Boolean gcpConnectorConfigured;
  private Boolean gcpDataPresent;
  private Boolean azureDataPresent;
  private Boolean applicationDataPresent;
  private Boolean segmentDataReadyEventSent;
  private Boolean segmentModuleInterfaceLoadedEventSent;
  private long lastUpdatedAt;
}
