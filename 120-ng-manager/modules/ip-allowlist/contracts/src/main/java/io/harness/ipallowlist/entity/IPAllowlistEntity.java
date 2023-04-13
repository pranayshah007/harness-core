/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "IPAllowlistConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ipAllowlist", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("ipAllowlist")
@Persistent
@TypeAlias("IPAllowlistEntity")
@OwnedBy(HarnessTeam.PL)
public class IPAllowlistEntity implements PersistentEntity, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_identifier_unique_index")
                 .field(IPAllowlistConfigKeys.accountIdentifier)
                 .field(IPAllowlistConfigKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_allowedSourceType_index")
                 .field(IPAllowlistConfigKeys.accountIdentifier)
                 .field(IPAllowlistConfigKeys.allowedSourceType)
                 .build())
        .build();
  }
  @Id @dev.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed @NotEmpty String identifier;
  @Trimmed @NotEmpty String name;
  String fullyQualifiedIdentifier;
  String description;
  @Singular @Size(max = 128) List<NGTag> tags;
  @Builder.Default Boolean enabled = Boolean.FALSE;
  @Builder.Default
  List<io.harness.spec.server.ng.v1.model.AllowedSourceType> allowedSourceType =
      List.of(io.harness.spec.server.ng.v1.model.AllowedSourceType.API,
          io.harness.spec.server.ng.v1.model.AllowedSourceType.UI);

  @NotEmpty String ipAddress;
  @CreatedDate Long created;
  @LastModifiedDate Long updated;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
}
