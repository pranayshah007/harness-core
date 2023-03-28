/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "UserMetadataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "userMetadata", noClassnameStored = true)
@Document("userMetadata")
@TypeAlias("userMetadata")
@OwnedBy(PL)
@ChangeDataCapture(table = "ng_users", dataStore = "ng-harness",
    fields = {UserMetadataKeys.email, UserMetadataKeys.createdAt, UserMetadataKeys.name, UserMetadataKeys.userId,
        UserMetadataKeys.lastModifiedAt},
    handler = "NgUsers")
public class UserMetadata implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueUserMetadataEmailId")
                 .field(UserMetadataKeys.email)
                 .unique(true)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String userId;
  @NotEmpty String email;
  String name;
  @Getter(value = AccessLevel.PRIVATE) @NotEmpty Boolean locked;
  @Getter(value = AccessLevel.PRIVATE) @NotEmpty Boolean disabled;
  @Getter(value = AccessLevel.PRIVATE) @NotEmpty Boolean externallyManaged;
  @Getter(value = AccessLevel.PRIVATE) Boolean twoFactorAuthenticationEnabled;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;

  public Boolean isLocked() {
    return this.locked;
  }

  public boolean isDisabled() {
    return Boolean.TRUE.equals(disabled);
  }

  public boolean isExternallyManaged() {
    return Boolean.TRUE.equals(externallyManaged);
  }

  public boolean isTwoFactorAuthenticationEnabled() {
    return Boolean.TRUE.equals(twoFactorAuthenticationEnabled);
  }
}
