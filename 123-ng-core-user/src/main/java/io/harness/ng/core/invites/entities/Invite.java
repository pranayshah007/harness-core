/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.invites.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.mongodb.lang.NonNull;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "InviteKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "invites", noClassnameStored = true)
@Document("invites")
@TypeAlias("invites")
@OwnedBy(PL)
public class Invite implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ng_invite_account_org_project_identifiers_email_role_deleted")
                 .field(InviteKeys.deleted)
                 .field(InviteKeys.email)
                 .field(InviteKeys.accountIdentifier)
                 .field(InviteKeys.orgIdentifier)
                 .field(InviteKeys.projectIdentifier)
                 .build())
        .build();
  }

  @NotEmpty String accountIdentifier;
  @Id @dev.morphia.annotations.Id @EntityIdentifier String id;
  String orgIdentifier;
  String projectIdentifier;
  @NotEmpty String email;
  @Size(min = 1, max = 100) List<RoleBinding> roleBindings;
  @Size(max = 100) List<String> userGroups;
  String name;
  String givenName;
  String familyName;
  @NonNull InviteType inviteType;
  @CreatedDate Long createdAt;
  @Version Long version;
  String inviteToken;
  String externalId;
  @NonNull @Builder.Default Boolean approved = Boolean.FALSE;
  @Builder.Default Boolean deleted = Boolean.FALSE;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  @Builder.Default
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(30).toInstant());
}
