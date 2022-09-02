/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import software.wings.beans.security.UserGroup;
import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "apiKeys", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "ApiKeyEntryKeys")
@TargetModule(HarnessModule._957_CG_BEANS)
public class ApiKeyEntry implements PersistentEntity, UuidAccess, CreatedAtAccess, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueName")
                 .field(ApiKeyEntryKeys.accountId)
                 .field(ApiKeyEntryKeys.name)
                 .unique(true)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotEmpty private String accountId;
  @NotEmpty private String name;
  private List<String> userGroupIds;
  private List<UserGroup> userGroups;
  @FdIndex private long createdAt;
  @JsonView(JsonViews.Internal.class) @NotEmpty private char[] encryptedKey;
  @Transient private String decryptedKey;
  // todo(abhinav): remove this field when migration is completed.
  @JsonView(JsonViews.Internal.class) @NotEmpty @Deprecated private String hashOfKey;
  @JsonView(JsonViews.Internal.class) @NotEmpty private String sha256Hash;
}
