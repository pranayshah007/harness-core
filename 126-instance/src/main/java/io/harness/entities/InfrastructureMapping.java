/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "InfrastructureMappingNGKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "infrastructureMappingNG", noClassnameStored = true)
@Document("infrastructureMappingNG")
@Persistent
@OwnedBy(HarnessTeam.DX)
public class InfrastructureMapping {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_infrastructureKey_unique_idx")
                 .unique(true)
                 .field(InfrastructureMappingNGKeys.accountIdentifier)
                 .field(InfrastructureMappingNGKeys.orgIdentifier)
                 .field(InfrastructureMappingNGKeys.projectIdentifier)
                 .field(InfrastructureMappingNGKeys.infrastructureKey)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String infrastructureKind;
  private String connectorRef;
  private String envId;
  private String serviceId;
  private String infrastructureKey;
}
