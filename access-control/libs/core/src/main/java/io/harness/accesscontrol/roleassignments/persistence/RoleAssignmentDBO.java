/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.persistence;

import static io.harness.accesscontrol.scopes.core.ScopeHelper.getAccountFromScopeIdentifier;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import static java.util.Optional.ofNullable;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
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
@FieldNameConstants(innerTypeName = "RoleAssignmentDBOKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "roleassignments", noClassnameStored = true)
@Document("roleassignments")
@TypeAlias("roleassignments")
public class RoleAssignmentDBO implements PersistentEntity, AccessControlEntity {
  @JsonProperty("_id") @Setter @Id @dev.morphia.annotations.Id String id;
  @EntityIdentifier final String identifier;
  @NotEmpty final String scopeIdentifier;
  final String scopeLevel;
  @NotEmpty final String resourceGroupIdentifier;
  @NotEmpty final String roleIdentifier;
  final String principalScopeLevel;
  @NotEmpty final String principalIdentifier;
  @NotNull final PrincipalType principalType;
  @Getter(value = AccessLevel.NONE) final Boolean managed;
  @Getter(value = AccessLevel.NONE) final Boolean internal;
  @Getter(value = AccessLevel.NONE) final Boolean disabled;

  public boolean isManaged() {
    return managed != null && managed;
  }

  public Boolean getManaged() {
    return managed;
  }

  public boolean isDisabled() {
    return disabled != null && disabled;
  }

  public Boolean getDisabled() {
    return disabled;
  }

  public boolean isInternal() {
    return internal != null && internal;
  }

  @Setter @CreatedDate Long createdAt;
  @Setter @LastModifiedDate Long lastModifiedAt;
  @Setter @CreatedBy EmbeddedUser createdBy;
  @Setter @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Setter @Version Long version;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueIndex")
                 .unique(true)
                 .field(RoleAssignmentDBOKeys.identifier)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("roleIndex")
                 .field(RoleAssignmentDBOKeys.roleIdentifier)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("roleScopeLevelIndex")
                 .field(RoleAssignmentDBOKeys.roleIdentifier)
                 .field(RoleAssignmentDBOKeys.scopeLevel)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("resourceGroupIndex")
                 .field(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("principalIndex")
                 .field(RoleAssignmentDBOKeys.principalType)
                 .field(RoleAssignmentDBOKeys.principalScopeLevel)
                 .field(RoleAssignmentDBOKeys.principalIdentifier)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("principalTypeIndex")
                 .field(RoleAssignmentDBOKeys.principalType)
                 .field(RoleAssignmentDBOKeys.principalScopeLevel)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("uniqueRoleAssignmentIndex")
                 .unique(true)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .field(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                 .field(RoleAssignmentDBOKeys.roleIdentifier)
                 .field(RoleAssignmentDBOKeys.principalIdentifier)
                 .field(RoleAssignmentDBOKeys.principalScopeLevel)
                 .field(RoleAssignmentDBOKeys.principalType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("userGroupPrincipalIndex")
                 .field(RoleAssignmentDBOKeys.principalIdentifier)
                 .field(RoleAssignmentDBOKeys.principalType)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .build();
  }

  @Override
  public Optional<String> getAccountId() {
    return ofNullable(getAccountFromScopeIdentifier(scopeIdentifier));
  }
}
