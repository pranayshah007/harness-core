/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
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
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CEGcpServiceAccountKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "gcpServiceAccount", noClassnameStored = true)
@OwnedBy(CE)
public class CEGcpServiceAccount implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_id_service_account_email")
                 .field(CEGcpServiceAccountKeys.accountId)
                 .field(CEGcpServiceAccountKeys.email)
                 .build())
        .build();
  }
  @Id String uuid;
  @NotEmpty String serviceAccountId;
  @NotEmpty String accountId;
  @NotEmpty String gcpProjectId;
  @NotEmpty String gcpUniqueId;
  @NotEmpty String email;
  long createdAt;
  long lastUpdatedAt;

  @Builder
  public CEGcpServiceAccount(
      String serviceAccountId, String gcpUniqueId, String accountId, String email, String gcpProjectId) {
    this.serviceAccountId = serviceAccountId;
    this.gcpProjectId = gcpProjectId;
    this.gcpUniqueId = gcpUniqueId;
    this.accountId = accountId;
    this.email = email;
  }
}
