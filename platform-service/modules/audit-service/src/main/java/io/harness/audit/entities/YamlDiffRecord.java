/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "YamlDiffRecordKeys")
@Entity(value = "yamlDiff", noClassnameStored = true)
@Document("yamlDiff")
@TypeAlias("yamlDiff")
@JsonInclude(NON_NULL)
@StoreIn(DbAliases.AUDITS)
public class YamlDiffRecord {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotNull String auditId;
  @NotNull @NotBlank String accountIdentifier;
  @NotNull Instant timestamp;

  String oldYaml;
  String newYaml;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("auditIdx").unique(true).field(YamlDiffRecordKeys.auditId).build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifierAuditIdIdx")
                 .field(YamlDiffRecordKeys.accountIdentifier)
                 .field(YamlDiffRecordKeys.auditId)
                 .build())
        .build();
  }
}
