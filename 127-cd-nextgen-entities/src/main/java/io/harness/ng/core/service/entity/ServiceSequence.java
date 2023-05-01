/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.CustomSequenceDTO;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "serviceSequence", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ServiceSequenceKeys")
@Document("serviceSequence")
@TypeAlias("io.harness.cdng.service.beans.ServiceSequenceKeys")
public class ServiceSequence implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationIdentifier_projectIdentifier_serviceIdentifier")
                 .unique(true)
                 .field(ServiceSequenceKeys.accountId)
                 .field(ServiceSequenceKeys.orgIdentifier)
                 .field(ServiceSequenceKeys.projectIdentifier)
                 .field(ServiceSequenceKeys.serviceIdentifier)
                 .build())
        .build();
  }

  @Wither @Id @dev.morphia.annotations.Id private String uuid;

  @Trimmed @NotEmpty private String accountId;
  @Trimmed private String orgIdentifier;
  @Trimmed private String projectIdentifier;
  @Trimmed private String serviceIdentifier;

  private CustomSequenceDTO defaultSequence;
  private CustomSequenceDTO customSequence;

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
}
