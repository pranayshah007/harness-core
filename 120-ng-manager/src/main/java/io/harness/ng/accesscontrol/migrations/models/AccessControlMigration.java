/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations.models;

import static io.harness.ng.DbAliases.NG_MANAGER;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "AccessControlMigrationKeys")
@StoreIn(NG_MANAGER)
@Document("aclMigrations")
@Entity(value = "aclMigrations", noClassnameStored = true)
@TypeAlias("aclMigrations")
@OwnedBy(HarnessTeam.PL)
public class AccessControlMigration {
  @Id @org.springframework.data.annotation.Id String id;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  long durationInSeconds;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifierOrgIdentifierProjectIdentifierIdx")
                 .field(AccessControlMigrationKeys.accountIdentifier)
                 .field(AccessControlMigrationKeys.orgIdentifier)
                 .field(AccessControlMigrationKeys.projectIdentifier)
                 .unique(true)
                 .build())
        .build();
  }
}
