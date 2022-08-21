/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.models;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.secretmanagerclient.SecretType;
import io.harness.security.dto.Principal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "SecretKeys")
@Entity(value = "secrets", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("secrets")
@StoreIn(DbAliases.NG_MANAGER)
public class Secret {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_identification")
                 .unique(true)
                 .field(SecretKeys.accountIdentifier)
                 .field(SecretKeys.orgIdentifier)
                 .field(SecretKeys.projectIdentifier)
                 .field(SecretKeys.identifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;
  String description;
  List<NGTag> tags;
  SecretType type;
  Boolean draft;
  Principal owner;

  public boolean isDraft() {
    return draft != null && draft;
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
  SecretSpec secretSpec;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  public SecretDTOV2 toDTO() {
    SecretDTOV2 dto = SecretDTOV2.builder()
                          .orgIdentifier(getOrgIdentifier())
                          .projectIdentifier(getProjectIdentifier())
                          .identifier(getIdentifier())
                          .name(getName())
                          .description(getDescription())
                          .tags(convertToMap(getTags()))
                          .type(getType())
                          .spec(Optional.ofNullable(getSecretSpec()).map(SecretSpec::toDTO).orElse(null))
                          .build();
    dto.setOwner(getOwner());
    return dto;
  }

  @FdIndex Boolean migratedFromManager;
}
