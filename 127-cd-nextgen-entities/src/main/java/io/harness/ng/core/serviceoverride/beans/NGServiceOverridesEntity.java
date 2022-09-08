/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "serviceOverridesNG", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "NGServiceOverridesEntityKeys")
@Document("serviceOverridesNG")
@TypeAlias("io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity")
@RecasterAlias("io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity")
@OwnedBy(CDC)
public class NGServiceOverridesEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationIdentifier_projectIdentifier_environmentRef_serviceRef")
                 .unique(true)
                 .field(NGServiceOverridesEntityKeys.accountId)
                 .field(NGServiceOverridesEntityKeys.orgIdentifier)
                 .field(NGServiceOverridesEntityKeys.projectIdentifier)
                 .field(NGServiceOverridesEntityKeys.environmentRef)
                 .field(NGServiceOverridesEntityKeys.serviceRef)
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;

  @NotNull @Trimmed String orgIdentifier;
  @NotNull @Trimmed String projectIdentifier;
  @NotNull @Trimmed String serviceRef;
  @NotNull @Trimmed String environmentRef;

  String yaml;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
}
