/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitXWebhook", noClassnameStored = true)
@Document("gitXWebhook")
@TypeAlias("io.harness.gitsync.gitxwebhooks.entity.gitXWebhook")
@FieldNameConstants(innerTypeName = "GitXWebhookKeys")
@OwnedBy(PIPELINE)
public class GitXWebhook implements PersistentEntity, UuidAccess {
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  String accountIdentifier;
  String name;
  String identifier;
  String connectorRef;
  String repoName;
  List<String> folderPaths;
  Boolean isEnabled;
  Long lastEventTriggerTime;
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate @Builder.Default Long createdAt = 0L;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_identifier_unique_idx")
                 .unique(true)
                 .field(GitXWebhookKeys.accountIdentifier)
                 .field(GitXWebhookKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_repoName_unique_idx")
                 .unique(true)
                 .field(GitXWebhookKeys.accountIdentifier)
                 .field(GitXWebhookKeys.repoName)
                 .build())
        .build();
  }
}
