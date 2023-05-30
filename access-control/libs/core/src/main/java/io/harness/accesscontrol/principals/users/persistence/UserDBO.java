/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users.persistence;

import static io.harness.accesscontrol.scopes.core.ScopeHelper.getAccountFromScopeIdentifier;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import static java.util.Optional.ofNullable;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "UserDBOKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "users", noClassnameStored = true)
@Document("users")
@TypeAlias("users")
public class UserDBO implements PersistentRegularIterable, AccessControlEntity {
  @Setter @Id @dev.morphia.annotations.Id String id;
  @NotEmpty final String scopeIdentifier;
  @NotEmpty final String identifier;
  @Setter String email;
  @Setter String name;

  @Setter @CreatedDate Long createdAt;
  @Setter @LastModifiedDate Long lastModifiedAt;
  @Setter @CreatedBy EmbeddedUser createdBy;
  @Setter @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Setter @Version Long version;

  @FdIndex @Setter Long nextReconciliationIterationAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueUserPrimaryKey")
                 .field(UserDBOKeys.scopeIdentifier)
                 .field(UserDBOKeys.identifier)
                 .unique(true)
                 .build())
        .build();
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (UserDBOKeys.nextReconciliationIterationAt.equals(fieldName)) {
      nextReconciliationIterationAt = nextIteration;
    }
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextReconciliationIterationAt;
  }

  @Override
  public String getUuid() {
    return id;
  }

  @Override
  public Optional<String> getAccountId() {
    return ofNullable(getAccountFromScopeIdentifier(scopeIdentifier));
  }
}
