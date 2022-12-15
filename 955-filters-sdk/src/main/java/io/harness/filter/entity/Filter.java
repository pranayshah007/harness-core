/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter.entity;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterVisibility;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "FilterKeys")
@StoreIn(DbAliases.ALL)
@Entity(value = "filters", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("filters")
@TypeAlias("io.harness.entity.Filter")
@OwnedBy(DX)
public class Filter implements PersistentEntity {
  @JsonIgnore @Id @org.mongodb.morphia.annotations.Id String id;
  @JsonIgnore String accountIdentifier;
  @NotNull String name;
  @NotNull String identifier;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull FilterType filterType;
  @NotEmpty @JsonIgnore String fullyQualifiedIdentifier;
  FilterProperties filterProperties;
  @JsonIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @JsonIgnore @Version Long version;
  FilterVisibility filterVisibility;

  @UtilityClass
  public static final class FilterKeys {
    public static final String userId = FilterKeys.createdBy + "."
        + "uuid";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_type")
                 .unique(true)
                 .field(FilterKeys.fullyQualifiedIdentifier)
                 .field(FilterKeys.filterType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_orgId_projectId_name_type_Index")
                 .unique(true)
                 .fields(Arrays.asList(FilterKeys.accountIdentifier, FilterKeys.orgIdentifier,
                     FilterKeys.projectIdentifier, FilterKeys.name, FilterKeys.filterType))
                 .build())
        .build();
  }
}
