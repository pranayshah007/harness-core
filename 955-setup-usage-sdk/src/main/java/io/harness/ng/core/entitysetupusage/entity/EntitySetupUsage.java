/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.entity;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef.IdentifierRefKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.EntityDetail.EntityDetailKeys;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetail;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotBlank;
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
@FieldNameConstants(innerTypeName = "EntitySetupUsageKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("entitySetupUsage")
@TypeAlias("io.harness.ng.core.entityReference.entity.EntitySetupUsage")
@OwnedBy(DX)
@Entity(value = "entitySetupUsage", noClassnameStored = true)
public class EntitySetupUsage implements PersistentEntity, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ReferredByEntityIndex")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityType)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("ReferredEntityIndex")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredEntityType)
                 .field(EntitySetupUsageKeys.referredEntityFQN)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("EntitySetupUsage_unique_index")
                 .field(EntitySetupUsageKeys.referredByEntityType)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .field(EntitySetupUsageKeys.referredByEntityRepoIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityBranch)
                 .field(EntitySetupUsageKeys.referredEntityType)
                 .field(EntitySetupUsageKeys.referredEntityFQN)
                 .field(EntitySetupUsageKeys.referredEntityRepoIdentifier)
                 .field(EntitySetupUsageKeys.referredEntityBranch)
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .unique(true)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("account_referredBy_createdAt_index")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .field(EntitySetupUsageKeys.referredByEntityType)
                 .field(EntitySetupUsageKeys.referredEntityType)
                 .field(EntitySetupUsageKeys.referredByEntityIsDefault)
                 .descSortField(EntitySetupUsageKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("account_referredByFQN_referredByIsDefault_createdAt_index")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .field(EntitySetupUsageKeys.referredByEntityIsDefault)
                 .descSortField(EntitySetupUsageKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_referredBy_referred_index")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .field(EntitySetupUsageKeys.referredByEntityType)
                 .field(EntitySetupUsageKeys.referredEntityFQN)
                 .field(EntitySetupUsageKeys.referredEntityType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_referredBy_referred_type_index")
                 .field(EntitySetupUsageKeys.accountIdentifier)
                 .field(EntitySetupUsageKeys.referredByEntityFQN)
                 .field(EntitySetupUsageKeys.referredByEntityType)
                 .field(EntitySetupUsageKeys.referredEntityType)
                 .field(EntitySetupUsageKeys.createdAt)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String id;
  @NotBlank @EqualsAndHashCode.Include String accountIdentifier;
  @NotNull EntityDetail referredEntity;
  @NotNull EntityDetail referredByEntity;
  SetupUsageDetail detail;

  @FdIndex @NotBlank @EqualsAndHashCode.Include String referredEntityFQN;
  @NotBlank @EqualsAndHashCode.Include String referredEntityType;
  @EqualsAndHashCode.Include String referredEntityRepoIdentifier;
  @EqualsAndHashCode.Include String referredEntityBranch;
  Boolean referredEntityIsDefault;

  @FdIndex @NotBlank @EqualsAndHashCode.Include String referredByEntityFQN;
  @NotBlank @EqualsAndHashCode.Include String referredByEntityType;
  @EqualsAndHashCode.Include String referredByEntityRepoIdentifier;
  @EqualsAndHashCode.Include String referredByEntityBranch;
  Boolean referredByEntityIsDefault;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  @UtilityClass
  public static final class EntitySetupUsageKeys {
    public static final String referredEntityName = EntitySetupUsageKeys.referredEntity + "." + EntityDetailKeys.name;
    public static final String referredByEntityName =
        EntitySetupUsageKeys.referredByEntity + "." + EntityDetailKeys.name;
    public static final String referredEntityRefScope =
        EntitySetupUsageKeys.referredEntity + "." + EntityDetailKeys.entityRef + "." + IdentifierRefKeys.scope;
    public static final String referredEntityRef =
        EntitySetupUsageKeys.referredEntity + "." + EntityDetailKeys.entityRef;
  }
}
