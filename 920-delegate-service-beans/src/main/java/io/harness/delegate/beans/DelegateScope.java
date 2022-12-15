/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldNameConstants(innerTypeName = "DelegateScopeKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateScopes", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateScope implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                      UpdatedByAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueName")
                 .unique(true)
                 .field(DelegateScopeKeys.accountId)
                 .field(DelegateScopeKeys.name)
                 .build())
        .build();
  }
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  @NotEmpty private String accountId;
  private String name;
  private List<TaskGroup> taskTypes;
  private List<EnvironmentType> environmentTypes;
  private List<String> applications;
  private List<String> environments;
  private List<String> serviceInfrastructures;
  private List<String> services;
  private List<String> infrastructureDefinitions;

  public boolean isValid() {
    return (isNotEmpty(taskTypes)) || (isNotEmpty(environmentTypes)) || (isNotEmpty(applications))
        || (isNotEmpty(environments)) || (isNotEmpty(serviceInfrastructures)) || (isNotEmpty(infrastructureDefinitions))
        || (isNotEmpty(services));
  }
}
