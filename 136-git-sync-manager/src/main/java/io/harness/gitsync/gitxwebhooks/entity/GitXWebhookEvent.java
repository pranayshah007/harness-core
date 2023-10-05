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
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.enterprise.inject.Default;
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
@Entity(value = "gitXWebhookEvent", noClassnameStored = true)
@Document("gitXWebhookEvent")
@TypeAlias("io.harness.gitsync.gitxwebhooks.entity.gitXWebhookEvent")
@FieldNameConstants(innerTypeName = "GitXWebhookEventKeys")
@OwnedBy(PIPELINE)
public class GitXWebhookEvent implements PersistentEntity, UuidAccess {
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  String accountIdentifier;
  String eventIdentifier;
  String webhookIdentifier;
  String payload;
  String eventStatus;
  Long eventTriggeredTime;
  String beforeCommitId;
  String afterCommitId;
  String branch;
  Author author;
  List<String> processedFilePaths;
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate @Builder.Default Long createdAt = 0L;
  @FdTtlIndex @Default Date validUntil = Date.from(OffsetDateTime.now().plusDays(15).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_eventIdentifier_unique_idx")
                 .unique(true)
                 .field(GitXWebhookEventKeys.accountIdentifier)
                 .field(GitXWebhookEventKeys.eventIdentifier)
                 .build())
        .build();
  }
}
