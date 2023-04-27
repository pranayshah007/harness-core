/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.privileged.persistence;

import static io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDBO.COLLECTION_NAME;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import static java.util.Optional.empty;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

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

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "PrivilegedRoleAssignmentDBOKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = COLLECTION_NAME, noClassnameStored = true)
@Document(COLLECTION_NAME)
@TypeAlias(COLLECTION_NAME)
public class PrivilegedRoleAssignmentDBO implements PersistentEntity, AccessControlEntity {
  public static final String COLLECTION_NAME = "privilegedRoleAssignments";

  @Setter @Id @dev.morphia.annotations.Id String id;
  @NotNull final PrincipalType principalType;
  @NotEmpty final String principalIdentifier;
  @NotEmpty final String roleIdentifier;
  final String linkedRoleAssignment;

  @Setter @CreatedDate Long createdAt;
  @Setter @LastModifiedDate Long lastModifiedAt;
  @Setter @CreatedBy EmbeddedUser createdBy;
  @Setter @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Setter @Version Long version;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueIndexV2")
                 .unique(true)
                 .field(PrivilegedRoleAssignmentDBOKeys.principalType)
                 .field(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                 .field(PrivilegedRoleAssignmentDBOKeys.roleIdentifier)
                 .field("managed") // backward compatibility
                 .field("scopeIdentifier") // backward compatibility
                 .field(PrivilegedRoleAssignmentDBOKeys.linkedRoleAssignment)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("queryIndex")
                 .unique(false)
                 .field(PrivilegedRoleAssignmentDBOKeys.roleIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("queryIndex1")
                 .unique(false)
                 .field(PrivilegedRoleAssignmentDBOKeys.principalType)
                 .field(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                 .build())
        .build();
  }

  @Override
  public Optional<String> getAccountId() {
    return empty();
  }
}
