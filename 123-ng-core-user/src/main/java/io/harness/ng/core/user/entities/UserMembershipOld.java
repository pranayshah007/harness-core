/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static lombok.AccessLevel.NONE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "UserMembershipKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "userMemberships", noClassnameStored = true)
@Document("userMemberships")
@TypeAlias("userMemberships")
@OwnedBy(PL)
public class UserMembershipOld implements PersistentRegularIterable, PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("userMembershipAccountOrgProject")
                 .field(UserMembershipKeys.scopes + "."
                     + "accountIdentifier")
                 .field(UserMembershipKeys.scopes + "."
                     + "orgIdentifier")
                 .field(UserMembershipKeys.scopes + "."
                     + "projectIdentifier")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("uniqueUserMembershipUserId")
                 .field(UserMembershipKeys.userId)
                 .unique(true)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotEmpty String userId;
  @NotEmpty String emailId;
  String name;
  @Getter(NONE) @Valid @Builder.Default List<Scope> scopes = new ArrayList<>();
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
    return this.userId;
  }

  public List<Scope> getScopes() {
    return isEmpty(scopes) ? new ArrayList<>() : scopes;
  }
}
