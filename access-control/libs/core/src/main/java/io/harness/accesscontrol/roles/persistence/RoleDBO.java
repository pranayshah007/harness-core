/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.persistence;

import static io.harness.accesscontrol.scopes.core.ScopeHelper.getAccountFromScopeIdentifier;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import static java.util.Optional.ofNullable;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "RoleDBOKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "roles", noClassnameStored = true)
@Document("roles")
@TypeAlias("roles")
public class RoleDBO implements PersistentEntity, AccessControlEntity {
  @JsonProperty("_id") @Setter @Id @dev.morphia.annotations.Id String id;
  @EntityIdentifier final String identifier;
  final String scopeIdentifier;
  @NGEntityName final String name;
  @NotEmpty final Set<String> allowedScopeLevels;
  @NotNull final Set<String> permissions;
  final boolean managed;
  @Builder.Default final Boolean internal = Boolean.FALSE;
  final String description;
  final Map<String, String> tags;

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
                 .field(RoleDBOKeys.identifier)
                 .field(RoleDBOKeys.scopeIdentifier)
                 .build())
        .build();
  }

  @Override
  public Optional<String> getAccountId() {
    return ofNullable(getAccountFromScopeIdentifier(scopeIdentifier));
  }
}
