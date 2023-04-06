/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.variable.VariableType;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@FieldNameConstants(innerTypeName = "VariableKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "variables", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("variables")
@Persistent
public abstract class Variable implements PersistentEntity, NGAccountAccess {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty @NGEntityName String name;
  String description;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @NotNull VariableType type;
  @NotNull VariableValueType valueType;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_identifier_unique_idx")
                 .field(VariableKeys.accountIdentifier)
                 .field(VariableKeys.orgIdentifier)
                 .field(VariableKeys.projectIdentifier)
                 .field(VariableKeys.identifier)
                 .unique(true)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_createdAt_decreasing_sort_Index")
                 .fields(Arrays.asList(
                     VariableKeys.accountIdentifier, VariableKeys.orgIdentifier, VariableKeys.projectIdentifier))
                 .descSortField(VariableKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_lastModifiedAt_decreasing_sort_Index")
                 .fields(Arrays.asList(
                     VariableKeys.accountIdentifier, VariableKeys.orgIdentifier, VariableKeys.projectIdentifier))
                 .descSortField(VariableKeys.lastModifiedAt)
                 .build())
        .build();
  }

  public String getScope() {
    if (orgIdentifier != null) {
      if (projectIdentifier != null) {
        return "project";
      }
      return "org";
    }
    return "account";
  }

  public String getExpression() {
    return "variable" + (getScope().equals("project") ? "" : "." + getScope()) + "." + getIdentifier();
  }
}
