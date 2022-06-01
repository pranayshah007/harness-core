/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "InfrastructureEntityKeys")
@Entity(value = "infrastructures", noClassnameStored = true)
@Document("infrastructures")
@TypeAlias("io.harness.ng.core.infrastructure.entity.InfrastructureEntity")
@StoreIn(DbAliases.NG_MANAGER)
public class InfrastructureEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_envIdentifier_infraIdentifier")
                 .unique(true)
                 .field(InfrastructureEntityKeys.accountId)
                 .field(InfrastructureEntityKeys.orgIdentifier)
                 .field(InfrastructureEntityKeys.projectIdentifier)
                 .field(InfrastructureEntityKeys.envIdentifier)
                 .field(InfrastructureEntityKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_envIdentifier_createdAt")
                 .field(InfrastructureEntityKeys.accountId)
                 .field(InfrastructureEntityKeys.orgIdentifier)
                 .field(InfrastructureEntityKeys.projectIdentifier)
                 .field(InfrastructureEntityKeys.envIdentifier)
                 .field(InfrastructureEntityKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;
  @NotEmpty @EntityIdentifier String identifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;

  @Trimmed String envIdentifier;

  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @NotEmpty @EntityName String name;
  @Size(max = 1024) String description;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @NotNull InfrastructureType type;
  String yaml;
}
