/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
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
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateProfiles", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "DelegateProfileKeys")
@OwnedBy(HarnessTeam.DEL)
public final class DelegateProfile implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                              UpdatedAtAware, UpdatedByAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .field(DelegateProfileKeys.accountId)
                 .field(DelegateProfileKeys.name)
                 .field(DelegateProfileKeys.owner)
                 .unique(true)
                 .name("uniqueName")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .field(DelegateProfileKeys.accountId)
                 .field(DelegateProfileKeys.ng)
                 .field(DelegateProfileKeys.owner)
                 .name("byAcctNgOwner")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_identification")
                 .unique(true)
                 .field(DelegateProfileKeys.accountId)
                 .field(DelegateProfileKeys.owner)
                 .field(DelegateProfileKeys.identifier)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @NotEmpty private String accountId;

  @NotEmpty private String name;
  private String description;

  private boolean primary;

  private boolean approvalRequired;

  private String startupScript;

  private List<DelegateProfileScopingRule> scopingRules;

  private List<String> selectors;

  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  @EntityIdentifier private String identifier;

  // Will be used for NG to hold information about who owns the record, Org or Project or account, if the field is
  // empty
  private DelegateEntityOwner owner;

  // Will be used for segregation of CG vs. NG records.
  private boolean ng;

  @UtilityClass
  public static final class DelegateProfileKeys {
    public static final String searchTermFilter = "searchTermFilter";
  }
}
