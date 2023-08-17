/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CI)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CIExecutionMetadataKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "ciExecutionMetadata", noClassnameStored = true)
@Document("ciExecutionMetadata")
@TypeAlias("ciExecutionMetadata")
@HarnessEntity(exportable = true)
public class CIExecutionMetadata {
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  OSType buildType;
  String stageExecutionId;
  String queueId;
  String status;
  Infrastructure.Type infraType;
  @Builder.Default
  @FdTtlIndex
  private Date expireAfter = Date.from(OffsetDateTime.now().plusSeconds(86400).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdAndBuildType")
                 .field(CIExecutionMetadataKeys.accountId)
                 .field(CIExecutionMetadataKeys.buildType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("stageExecutionIdAndAccountId")
                 .field(CIExecutionMetadataKeys.stageExecutionId)
                 .field(CIExecutionMetadataKeys.accountId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdAndStatus")
                 .field(CIExecutionMetadataKeys.accountId)
                 .field(CIExecutionMetadataKeys.status)
                 .build())
        .build();
  }
}
