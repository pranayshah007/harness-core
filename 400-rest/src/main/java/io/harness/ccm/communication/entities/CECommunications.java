/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.communication.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
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
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CECommunicationsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "ceCommunications", noClassnameStored = true)
@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class CECommunications implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_email_type")
                 .unique(true)
                 .field(CECommunicationsKeys.accountId)
                 .field(CECommunicationsKeys.emailId)
                 .field(CECommunicationsKeys.type)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_enabled_type")
                 .field(CECommunicationsKeys.accountId)
                 .field(CECommunicationsKeys.enabled)
                 .field(CECommunicationsKeys.type)
                 .build())
        .build();
  }

  @Id String uuid;
  @NotBlank String accountId;
  @NotBlank String emailId;
  @NotBlank CommunicationType type;
  boolean enabled;
  boolean selfEnabled;
  long createdAt;
  long lastUpdatedAt;
}
