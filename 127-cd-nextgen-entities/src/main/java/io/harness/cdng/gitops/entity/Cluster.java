/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.entity;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(GITOPS)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ClusterKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitopsClusters", noClassnameStored = true)
@Document("gitopsClusters")
@TypeAlias("io.harness.cdng.gitops.entity.Cluster")
public class Cluster implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("unique_accountId_orgId_projId_envRef_id")
                 .field(ClusterKeys.accountId)
                 .field(ClusterKeys.orgIdentifier)
                 .field(ClusterKeys.projectIdentifier)
                 .field(ClusterKeys.agentIdentifier)
                 .field(ClusterKeys.envRef)
                 .field(ClusterKeys.clusterRef)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projId_envRef_createdAt")
                 .field(ClusterKeys.accountId)
                 .field(ClusterKeys.orgIdentifier)
                 .field(ClusterKeys.projectIdentifier)
                 .field(ClusterKeys.agentIdentifier)
                 .field(ClusterKeys.envRef)
                 .sortField(ClusterKeys.createdAt)
                 .build())
        .build();
  }
  @Wither @Id @dev.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;
  @NotEmpty @EntityIdentifier String clusterRef;

  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @Trimmed String agentIdentifier;

  @NotEmpty String envRef;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;

  public String fetchEnvRef() {
    return FullyQualifiedIdentifierHelper.getRefFromIdentifierOrRef(
        accountId, orgIdentifier, projectIdentifier, envRef);
  }
}
