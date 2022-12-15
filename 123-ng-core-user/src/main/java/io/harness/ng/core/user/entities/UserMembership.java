/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeKeys;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "UserMembershipKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "userMembershipsV2", noClassnameStored = true)
@Document("userMembershipsV2")
@TypeAlias("userMembershipsV2")
@OwnedBy(PL)
public class UserMembership implements PersistentRegularIterable, PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueUserMembershipV2UserAccountOrgProject")
                 .field(UserMembershipKeys.userId)
                 .field(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                 .field(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("userMembershipV2AccountOrgProjectList")
                 .field(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                 .field(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotEmpty String userId;
  Scope scope;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;

  @FdIndex private Long nextIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @JsonIgnore
  @Override
  public String getUuid() {
    return this.uuid;
  }

  @UtilityClass
  public static final class UserMembershipKeys {
    public static final String ACCOUNT_IDENTIFIER_KEY = UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier;
    public static final String ORG_IDENTIFIER_KEY = UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier;
    public static final String PROJECT_IDENTIFIER_KEY = UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier;
  }
}
