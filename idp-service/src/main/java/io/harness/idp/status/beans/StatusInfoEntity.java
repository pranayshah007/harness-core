/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.beans;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.status.enums.StatusType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.IDP)
@Entity(value = "statusInfo", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "StatusInfoEntityKeys")
@Document("statusInfo")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class StatusInfoEntity implements PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  private StatusType type;
  private StatusInfo.CurrentStatusEnum status;
  private String reason;
  @LastModifiedDate Long lastModifiedAt;
  private boolean isDeleted;
  private long deletedAt;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_type")
                 .unique(true)
                 .field(StatusInfoEntityKeys.accountIdentifier)
                 .field(StatusInfoEntityKeys.type)
                 .build())
        .build();
  }
}
