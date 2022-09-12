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
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.invites.entities.Role;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "UserProjectMapKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "userProjectMaps", noClassnameStored = true)
@Document("userProjectMaps")
@TypeAlias("userProjectMaps")
@OwnedBy(PL)
public class UserProjectMap implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("uniqueUserProjectMap")
                 .field(UserProjectMapKeys.userId)
                 .field(UserProjectMapKeys.accountIdentifier)
                 .field(UserProjectMapKeys.orgIdentifier)
                 .field(UserProjectMapKeys.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .unique(false)
                 .name("accountOrgProjectMapIdx")
                 .field(UserProjectMapKeys.accountIdentifier)
                 .field(UserProjectMapKeys.orgIdentifier)
                 .field(UserProjectMapKeys.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("userProjectMapMigrationIdx")
                 .field(UserProjectMapKeys.moved)
                 .field(UserProjectMapKeys.tries)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @Trimmed @NotEmpty String userId;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @Trimmed @NotEmpty List<Role> roles;
  Boolean migrated;
  Boolean moved;
  Integer tries;
  @Wither @Version Long version;
}
