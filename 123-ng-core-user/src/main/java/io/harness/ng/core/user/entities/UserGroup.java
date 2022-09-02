/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.lang.Boolean.FALSE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.sso.SSOType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "UserGroupKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "user-groups", noClassnameStored = true)
@Document("user-groups")
@TypeAlias("user-groups")
@OwnedBy(PL)
public class UserGroup implements PersistentEntity, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_user_groups_index")
                 .unique(true)
                 .field(UserGroupKeys.accountIdentifier)
                 .field(UserGroupKeys.orgIdentifier)
                 .field(UserGroupKeys.projectIdentifier)
                 .field(UserGroupKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("user_groups_ssoid_index")
                 .field(UserGroupKeys.linkedSsoId)
                 .field(UserGroupKeys.isSsoLinked)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotNull String accountIdentifier;
  @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @EntityIdentifier(allowBlank = true) String projectIdentifier;
  @EntityIdentifier String identifier;

  @Builder.Default Boolean isSsoLinked = FALSE;
  @Builder.Default Boolean externallyManaged = FALSE; // Usergroup is imported from SCIM or not
  private SSOType linkedSsoType;
  private String linkedSsoId;
  private String linkedSsoDisplayName;
  private String ssoGroupId;
  private String ssoGroupName;

  @NGEntityName String name;
  @NotNull List<String> users;
  @NotNull List<NotificationSettingConfig> notificationConfigs;
  boolean harnessManaged;

  @NotNull @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @CreatedDate long createdAt;
  @LastModifiedDate long lastModifiedAt;
  @Version long version;
  boolean deleted;

  public boolean isExternallyManaged() {
    return Boolean.TRUE.equals(externallyManaged);
  }
}
