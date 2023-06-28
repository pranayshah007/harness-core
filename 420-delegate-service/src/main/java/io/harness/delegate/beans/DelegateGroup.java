/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.beans.DelegateEntityOwner.DelegateEntityOwnerKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldNameConstants(innerTypeName = "DelegateGroupKeys")
@StoreIn(DbAliases.HARNESS)
@SecondaryStoreIn(DbAliases.DMS)
@Entity(value = "delegateGroups", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateGroup implements PersistentEntity, UuidAware {
  public static final Duration TTL = ofDays(7);
  // Custom limit for delegate-name as 63 characters because kubernetes component name can be at most 63 characters.
  public static final int MAX_LENGTH_SUPPORTED_BY_DELEGATE = 63;

  @Id @NotNull private String uuid;

  @NotEmpty private String name;

  private String delegateType;

  private List<String> runnerTypes;

  private String description;

  @NotEmpty private String accountId;

  // Will be used for NG to hold information about delegate group if it is owned at Org / Project
  private DelegateEntityOwner owner;

  // Will be used for segregation of CG vs. NG delegate groups.
  private boolean ng;

  private String delegateConfigurationId;

  private DelegateSizeDetails sizeDetails;

  private K8sConfigDetails k8sConfigDetails;

  private Set<String> tags;

  @Builder.Default private DelegateGroupStatus status = DelegateGroupStatus.ENABLED;

  @FdTtlIndex private Date validUntil;

  @EntityIdentifier(maxLength = MAX_LENGTH_SUPPORTED_BY_DELEGATE) private String identifier;

  private long upgraderLastUpdated;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("byAccount")
                 .unique(true)
                 .field(DelegateGroupKeys.accountId)
                 .field(DelegateGroupKeys.name)
                 .field(DelegateGroupKeys.ng)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .field(DelegateGroupKeys.accountId)
                 .field(DelegateGroupKeys.ng)
                 .field(DelegateGroupKeys.owner)
                 .name("byAcctNgOwner")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_identification")
                 .unique(true)
                 .field(DelegateGroupKeys.accountId)
                 .field(DelegateGroupKeys.owner)
                 .field(DelegateGroupKeys.identifier)
                 .build())
        .build();
  }

  @UtilityClass
  public static final class DelegateGroupKeys {
    public static final String owner_identifier = owner + "." + DelegateEntityOwnerKeys.identifier;
  }
}
