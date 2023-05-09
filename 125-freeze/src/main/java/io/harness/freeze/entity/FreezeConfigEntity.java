/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Singular;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "FreezeConfigEntityKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "freezeConfigNG", noClassnameStored = true)
@Document("freezeConfigNG")
@TypeAlias("freezeConfigNG")
@HarnessEntity(exportable = true)
@Persistent
public class FreezeConfigEntity implements PersistentEntity, AccountAccess, UuidAware, CreatedAtAware, UpdatedAtAware,
                                           CreatedByAware, UpdatedByAware, PersistentIrregularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("status_nextIteration_shouldSendNotification_idx")
                 .field(FreezeConfigEntityKeys.status)
                 .field(FreezeConfigEntityKeys.nextIteration)
                 .field(FreezeConfigEntityKeys.shouldSendNotification)
                 .build())
        .build();
  }
  @Id @dev.morphia.annotations.Id String uuid;

  @NotEmpty String accountId;

  @NotNull FreezeType type;

  @With @EntityName String name;

  @Setter FreezeStatus status;

  @Size(max = 1024) String description;
  @Singular @Size(max = 128) List<NGTag> tags;

  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;

  @NotEmpty @EntityIdentifier String identifier;
  @With @NotEmpty String yaml;

  @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;

  Scope freezeScope;

  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  Long nextIteration;

  @Deprecated List<Long> nextIterations;
  boolean shouldSendNotification;

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    return Collections.emptyList();
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return null;
  }
}
