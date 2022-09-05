/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.billing;

import io.harness.annotations.StoreIn;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.ChangeDataCaptureSink;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.AwsCrossAccountAttributes;

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
@Entity(value = "ceCloudAccount", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "CECloudAccountKeys")
@ChangeDataCapture(table = "awsTruthTable", dataStore = DbAliases.CENG,
    fields = {"accountId", "infraAccountId", "accountName"}, handler = "TimeScaleDBChangeHandler")
@ChangeDataCapture(table = "awsTruthTable", dataStore = DbAliases.CENG, sink = ChangeDataCaptureSink.BQ,
    fields = {"accountId", "infraAccountId", "accountName"}, handler = "CECloudAccountBigQueryChangeHandler")
@OwnedBy(HarnessTeam.CE)
public final class CECloudAccount
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("no_dup_account")
                 .unique(true)
                 .field(CECloudAccountKeys.accountId)
                 .field(CECloudAccountKeys.infraAccountId)
                 .field(CECloudAccountKeys.infraMasterAccountId)
                 .field(CECloudAccountKeys.masterAccountSettingId)
                 .build())
        .build();
  }
  @Id String uuid;
  String accountId;
  String accountArn;
  String accountName;
  String infraAccountId;
  String infraMasterAccountId; // master account id
  AccountStatus accountStatus;
  String masterAccountSettingId; // setting id of ce connectors
  AwsCrossAccountAttributes awsCrossAccountAttributes;

  long createdAt;
  long lastUpdatedAt;

  public enum AccountStatus { NOT_VERIFIED, CONNECTED, NOT_CONNECTED }
}
