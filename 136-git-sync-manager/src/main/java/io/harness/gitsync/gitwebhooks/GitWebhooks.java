package io.harness.gitsync.gitwebhooks;

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitWebhooks", noClassnameStored = true)
@Document("gitWebhooks")
@TypeAlias("gitWebhooks")
@FieldNameConstants(innerTypeName = "GitWebhooksKeys")
@OwnedBy(PIPELINE)
public class GitWebhooks implements PersistentEntity, UuidAccess {
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  String name;
  String accountIdentifier;
  String identifier;
  String connectorRef;
  String repoName;
  List<String> folderPaths;
  Boolean isEnabled;
  @Setter @NonFinal @SchemaIgnore @CreatedDate @Builder.Default Long createdAt = 0L;
  @Wither @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_name_connectorRef_repoName_isEnabled_unique_idx")
                 .unique(true)
                 .field(GitWebhooksKeys.accountIdentifier)
                 .field(GitWebhooksKeys.name)
                 .field(GitWebhooksKeys.connectorRef)
                 .field(GitWebhooksKeys.repoName)
                 .field(GitWebhooksKeys.isEnabled)
                 .build())
        .build();
  }
}
