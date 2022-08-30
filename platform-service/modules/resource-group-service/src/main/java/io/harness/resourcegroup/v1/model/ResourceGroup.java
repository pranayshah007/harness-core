/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.model;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.PersistentEntity;
import io.harness.resourcegroup.model.ResourceSelector;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
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

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ResourceGroupKeys")
@StoreIn(DbAliases.RESOURCEGROUP)
@Document("resourceGroup")
@Entity("resourceGroup")
@TypeAlias("resourceGroup")
public class ResourceGroup implements PersistentRegularIterable, PersistentEntity {
  public static final String DEFAULT_COLOR = "#0063F7";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("resourceGroupPrimaryKey_resourceSelectors")
                 .field(ResourceGroupKeys.accountIdentifier)
                 .field(ResourceGroupKeys.orgIdentifier)
                 .field(ResourceGroupKeys.projectIdentifier)
                 .field(ResourceGroupKeys.identifier)
                 .field(ResourceGroupKeys.resourceSelectors)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("uniqueResourceGroup")
                 .field(ResourceGroupKeys.accountIdentifier)
                 .field(ResourceGroupKeys.orgIdentifier)
                 .field(ResourceGroupKeys.projectIdentifier)
                 .field(ResourceGroupKeys.identifier)
                 .unique(true)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  @NotEmpty @Size(max = 128) String identifier;
  @NotEmpty @Size(max = 128) String name;
  @Size(max = 1024) String description;
  @NotEmpty @Size(min = 7, max = 7) String color;
  @Size(max = 128) @Singular List<NGTag> tags;
  @FdIndex @NotNull @Builder.Default Boolean harnessManaged = Boolean.FALSE;
  @NotNull @Size(max = 256) @Singular List<ResourceSelector> resourceSelectors;
  @Builder.Default Boolean fullScopeSelected = Boolean.FALSE;
  Set<String> allowedScopeLevels;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @CreatedBy EmbeddedUser createdBy;
  @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Version Long version;

  @FdIndex private Long nextIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @JsonIgnore
  @Override
  public String getUuid() {
    return this.id;
  }
}
