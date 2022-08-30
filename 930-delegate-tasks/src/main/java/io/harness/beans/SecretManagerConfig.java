/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.secretmanagerclient.NGMetadata.NGMetadataKeys;
import static io.harness.secretmanagerclient.NGSecretManagerMetadata.NGSecretManagerMetadataKeys;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccess;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.NGSecretManagerConfigDTOConverter;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;
import io.harness.validation.Update;

import software.wings.ngmigration.NGMigrationEntity;
import software.wings.security.ScopedEntity;
import software.wings.security.UsageRestrictions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

/**
 * This is a shared persistent entity to track Secret Managers of different type (KMS/AWS Secrets Manager/Vault etc.) in
 * a centralized location to simplify the logic to track which secret manager is the account level default.
 *
 *
 * @author marklu on 2019-05-31
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = {"createdBy", "createdAt", "lastUpdatedBy", "lastUpdatedAt"})
@StoreIn(DbAliases.HARNESS)
@Entity(value = "secretManagers")
@HarnessEntity(exportable = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "SecretManagerConfigKeys")
@OwnedBy(PL)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public abstract class SecretManagerConfig
    implements AccountAccess, EncryptionConfig, PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
               UpdatedAtAware, UpdatedByAware, PersistentRegularIterable, NGAccess, NGSecretManagerConfigDTOConverter,
               ExecutionCapabilityDemander, ScopedEntity, NGMigrationEntity, NameAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("uniqueIdx")
                 .field(SecretManagerConfigKeys.name)
                 .field(SecretManagerConfigKeys.accountId)
                 .field(SecretManagerConfigKeys.encryptionType)
                 .field(SecretManagerConfigKeys.accountIdentifier)
                 .field(SecretManagerConfigKeys.orgIdentifier)
                 .field(SecretManagerConfigKeys.projectIdentifier)
                 .field(SecretManagerConfigKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("identifierCheckIdx")
                 .field(SecretManagerConfigKeys.accountIdentifier)
                 .field(SecretManagerConfigKeys.orgIdentifier)
                 .field(SecretManagerConfigKeys.projectIdentifier)
                 .field(SecretManagerConfigKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("renewCheckIdx")
                 .field(SecretManagerConfigKeys.encryptionType)
                 .field(SecretManagerConfigKeys.nextTokenRenewIteration)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  private EncryptionType encryptionType;

  private boolean isDefault;

  @NotEmpty @FdIndex private String accountId;

  @SchemaIgnore @Transient private int numOfEncryptedValue;

  @SchemaIgnore @Transient private String encryptedBy;

  @SchemaIgnore private EmbeddedUser createdBy;

  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;

  @SchemaIgnore private long lastUpdatedAt;

  @FdIndex private Long nextTokenRenewIteration;

  @FdIndex private Long manuallyEnteredSecretEngineMigrationIteration;

  @JsonIgnore private NGSecretManagerMetadata ngMetadata;

  private UsageRestrictions usageRestrictions;

  private boolean scopedToAccount;

  private List<String> templatizedFields;

  public boolean isTemplatized() {
    return !isEmpty(templatizedFields);
  }

  public abstract void maskSecrets();

  @JsonIgnore public abstract SecretManagerType getType();

  @JsonIgnore public abstract List<SecretManagerCapabilities> getSecretManagerCapabilities();

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (SecretManagerConfigKeys.nextTokenRenewIteration.equals(fieldName)) {
      this.nextTokenRenewIteration = nextIteration;
      return;
    } else if (SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration.equals(fieldName)) {
      this.manuallyEnteredSecretEngineMigrationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (SecretManagerConfigKeys.nextTokenRenewIteration.equals(fieldName)) {
      return nextTokenRenewIteration;
    } else if (SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration.equals(fieldName)) {
      return manuallyEnteredSecretEngineMigrationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  @JsonIgnore
  public boolean isGlobalKms() {
    return false;
  }

  @Override
  @JsonIgnore
  public String getAccountIdentifier() {
    return Optional.ofNullable(ngMetadata).map(NGSecretManagerMetadata::getAccountIdentifier).orElse(null);
  }

  @Override
  @JsonIgnore
  public String getOrgIdentifier() {
    return Optional.ofNullable(ngMetadata).map(NGSecretManagerMetadata::getOrgIdentifier).orElse(null);
  }

  @Override
  @JsonIgnore
  public String getProjectIdentifier() {
    return Optional.ofNullable(ngMetadata).map(NGSecretManagerMetadata::getProjectIdentifier).orElse(null);
  }

  @Override
  @JsonIgnore
  public String getIdentifier() {
    return Optional.ofNullable(ngMetadata).map(NGSecretManagerMetadata::getIdentifier).orElse(null);
  }

  @UtilityClass
  public static final class SecretManagerConfigKeys {
    public static final String ID_KEY = "_id";
    public static final String name = "name";
    public static final String accountIdentifier = "ngMetadata." + NGSecretManagerMetadataKeys.accountIdentifier;
    public static final String orgIdentifier = "ngMetadata." + NGSecretManagerMetadataKeys.orgIdentifier;
    public static final String projectIdentifier = "ngMetadata." + NGSecretManagerMetadataKeys.projectIdentifier;
    public static final String identifier = "ngMetadata." + NGMetadataKeys.identifier;
  }
}
