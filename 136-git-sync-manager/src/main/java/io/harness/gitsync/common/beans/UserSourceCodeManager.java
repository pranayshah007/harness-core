/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.userprofile.commons.SCMType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@SuperBuilder
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "UserSourceCodeManagerKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Document("userSourceCodeManagers")
@TypeAlias("io.harness.gitsync.common.beans.UserSourceCodeManager")
@Entity(value = "userSourceCodeManagers", noClassnameStored = true)
@Persistent
@OwnedBy(PIPELINE)
public abstract class UserSourceCodeManager {
  @JsonIgnore @Id @dev.morphia.annotations.Id String id;
  @NotEmpty String userIdentifier;
  @NotEmpty @FdIndex String accountIdentifier;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @NotEmpty SCMType type;
  String userName;
  String userEmail;
  public abstract SCMType getType();

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_userIdentifier_type_idx")
                 .unique(true)
                 .field(UserSourceCodeManagerKeys.accountIdentifier)
                 .field(UserSourceCodeManagerKeys.userIdentifier)
                 .field(UserSourceCodeManagerKeys.type)
                 .build())
        .build();
  }
}
