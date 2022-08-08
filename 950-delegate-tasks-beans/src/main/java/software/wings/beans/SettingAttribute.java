/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;
import static software.wings.settings.SettingVariableTypes.APP_DYNAMICS;
import static software.wings.settings.SettingVariableTypes.ARTIFACTORY;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.AZURE_ARTIFACTS_PAT;
import static software.wings.settings.SettingVariableTypes.BAMBOO;
import static software.wings.settings.SettingVariableTypes.BASTION_HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.BUG_SNAG;
import static software.wings.settings.SettingVariableTypes.CE_AWS;
import static software.wings.settings.SettingVariableTypes.CE_AZURE;
import static software.wings.settings.SettingVariableTypes.CE_GCP;
import static software.wings.settings.SettingVariableTypes.CUSTOM;
import static software.wings.settings.SettingVariableTypes.DATA_DOG;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.DYNA_TRACE;
import static software.wings.settings.SettingVariableTypes.ECR;
import static software.wings.settings.SettingVariableTypes.ELB;
import static software.wings.settings.SettingVariableTypes.ELK;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.GCR;
import static software.wings.settings.SettingVariableTypes.GCS;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GIT;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.INSTANA;
import static software.wings.settings.SettingVariableTypes.JENKINS;
import static software.wings.settings.SettingVariableTypes.JIRA;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;
import static software.wings.settings.SettingVariableTypes.LOGZ;
import static software.wings.settings.SettingVariableTypes.NEW_RELIC;
import static software.wings.settings.SettingVariableTypes.NEXUS;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.PCF;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.settings.SettingVariableTypes.PROMETHEUS;
import static software.wings.settings.SettingVariableTypes.RANCHER;
import static software.wings.settings.SettingVariableTypes.SERVICENOW;
import static software.wings.settings.SettingVariableTypes.SFTP;
import static software.wings.settings.SettingVariableTypes.SLACK;
import static software.wings.settings.SettingVariableTypes.SMB;
import static software.wings.settings.SettingVariableTypes.SMTP;
import static software.wings.settings.SettingVariableTypes.SPLUNK;
import static software.wings.settings.SettingVariableTypes.SPOT_INST;
import static software.wings.settings.SettingVariableTypes.STRING;
import static software.wings.settings.SettingVariableTypes.SUMO;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import static java.util.Arrays.stream;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.security.encryption.EncryptionType;
import io.harness.validation.Update;
import io.harness.yaml.BaseYaml;

import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.settings.validation.ConnectivityValidationAttributes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
@Data
@EqualsAndHashCode(of = {"uuid", "appId"}, callSuper = false)
@FieldNameConstants(innerTypeName = "SettingAttributeKeys")
@Entity(value = "settingAttributes")
@HarnessEntity(exportable = true)
public class SettingAttribute
    implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware,
               ApplicationAccess, NameAccess, PersistentRegularIterable, AccountAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdAppIdCategoryCreatedAt")
                 .field(SettingAttributeKeys.accountId)
                 .field(SettingAttributeKeys.appId)
                 .field(SettingAttributeKeys.category)
                 .descSortField(SettingAttributeKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("locate")
                 .unique(true)
                 .field(SettingAttributeKeys.accountId)
                 .field(SettingAttributeKeys.appId)
                 .field(SettingAttributeKeys.envId)
                 .field(SettingAttributeKeys.name)
                 .field(SettingAttributeKeys.value_type)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("acctCatTypeIdx")
                 .field(SettingAttributeKeys.accountId)
                 .field(SettingAttributeKeys.category)
                 .field(SettingAttributeKeys.value_type)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("acctValTypeIdx")
                 .field(SettingAttributeKeys.accountId)
                 .field(SettingAttributeKeys.value_type)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("typeNextIterationIdx")
                 .field(SettingAttributeKeys.value_type)
                 .field(SettingAttributeKeys.nextIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("secretsMigrationIdx")
                 .field(SettingAttributeKeys.value_type)
                 .field(SettingAttributeKeys.nextSecretMigrationIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("secretsMigrationPerAccountIdx")
                 .field(SettingAttributeKeys.value_type)
                 .field(SettingAttributeKeys.secretsMigrated)
                 .field(SettingAttributeKeys.accountId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdAppIdCategoryNameIdx")
                 .field(SettingAttributeKeys.accountId)
                 .field(SettingAttributeKeys.appId)
                 .field(SettingAttributeKeys.category)
                 .field(SettingAttributeKeys.name)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .build();
  }

  @NotEmpty private String envId = GLOBAL_ENV_ID;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @JsonIgnore @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @JsonIgnore
  @SchemaIgnore
  @Transient
  private transient String entityYamlPath; // TODO:: remove it with changeSet batching

  @JsonIgnore
  @SchemaIgnore
  public String getEntityYamlPath() {
    return entityYamlPath;
  }

  @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;

  @JsonIgnore
  @SchemaIgnore
  public boolean isSyncFromGit() {
    return syncFromGit;
  }

  @NotEmpty String accountId;
  @NotEmpty
  @EntityName(displayName = "Display Name")
  @Trimmed(message = "cannot have trailing whitespace")
  private String name;
  @Valid private SettingValue value;
  @Valid @Transient private ConnectivityValidationAttributes validationAttributes;
  private SettingCategory category = SettingCategory.SETTING;
  private List<String> appIds;
  private UsageRestrictions usageRestrictions;
  private transient long artifactStreamCount;
  private transient List<ArtifactStreamSummary> artifactStreams;
  private boolean sample;

  @FdIndex private Long nextIteration;
  private Long nextSecretMigrationIteration;
  private boolean secretsMigrated;
  private String connectivityError;

  @SchemaIgnore @Transient private transient EncryptionType encryptionType;

  @SchemaIgnore @Transient private transient String encryptedBy;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (SettingAttributeKeys.nextIteration.equals(fieldName)) {
      return nextIteration;
    }
    if (SettingAttributeKeys.nextSecretMigrationIteration.equals(fieldName)) {
      return nextSecretMigrationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (SettingAttributeKeys.nextIteration.equals(fieldName)) {
      this.nextIteration = nextIteration;
      return;
    }
    if (SettingAttributeKeys.nextSecretMigrationIteration.equals(fieldName)) {
      this.nextSecretMigrationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  public enum SettingCategory {
    CLOUD_PROVIDER(
        Lists.newArrayList(PHYSICAL_DATA_CENTER, AWS, AZURE, GCP, KUBERNETES_CLUSTER, PCF, SPOT_INST, RANCHER)),

    CONNECTOR(Lists.newArrayList(SMTP, JENKINS, BAMBOO, SPLUNK, ELK, LOGZ, SUMO, APP_DYNAMICS, INSTANA, NEW_RELIC,
        DYNA_TRACE, BUG_SNAG, DATA_DOG, APM_VERIFICATION, PROMETHEUS, ELB, SLACK, DOCKER, ECR, GCR, NEXUS, ARTIFACTORY,
        AMAZON_S3, GCS, GIT, SMB, JIRA, SFTP, SERVICENOW, CUSTOM)),

    SETTING(Lists.newArrayList(
        HOST_CONNECTION_ATTRIBUTES, BASTION_HOST_CONNECTION_ATTRIBUTES, STRING, WINRM_CONNECTION_ATTRIBUTES)),

    HELM_REPO(Lists.newArrayList(HTTP_HELM_REPO, AMAZON_S3_HELM_REPO, GCS_HELM_REPO, OCI_HELM_REPO)),

    AZURE_ARTIFACTS(Lists.newArrayList(AZURE_ARTIFACTS_PAT)),

    CE_CONNECTOR(Lists.newArrayList(CE_AWS, CE_GCP, CE_AZURE));

    @Getter private List<SettingVariableTypes> settingVariableTypes;

    SettingCategory(List<SettingVariableTypes> settingVariableTypes) {
      this.settingVariableTypes = settingVariableTypes;
    }

    public static SettingCategory getCategory(SettingVariableTypes settingVariableType) {
      return stream(SettingCategory.values())
          .filter(category -> category.settingVariableTypes.contains(settingVariableType))
          .findFirst()
          .orElse(null);
    }
  }

  public static final class Builder {
    private String envId = GLOBAL_ENV_ID;
    private String accountId;
    private String name;
    private SettingValue value;
    private ConnectivityValidationAttributes connectivityValidationAttributes;
    private SettingCategory category = SettingCategory.SETTING;
    private List<String> appIds;
    private String uuid;
    private String appId = GLOBAL_APP_ID;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private UsageRestrictions usageRestrictions;
    private boolean sample;
    private String connectivityError;

    private Builder() {}

    public static Builder aSettingAttribute() {
      return new Builder();
    }

    public Builder withEnvId(String envId) {
      this.envId = envId;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withValue(SettingValue value) {
      this.value = value;
      return this;
    }

    public Builder withCategory(SettingCategory category) {
      this.category = category;
      return this;
    }

    public Builder withAppIds(List<String> appIds) {
      this.appIds = appIds;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder withUsageRestrictions(UsageRestrictions usageRestrictions) {
      this.usageRestrictions = usageRestrictions;
      return this;
    }

    public Builder withConnectivityValidationAttributes(
        ConnectivityValidationAttributes connectivityValidationAttributes) {
      this.connectivityValidationAttributes = connectivityValidationAttributes;
      return this;
    }

    public Builder withSample(boolean sample) {
      this.sample = sample;
      return this;
    }

    public Builder withConnectivityError(String connectivityError) {
      this.connectivityError = connectivityError;
      return this;
    }

    public Builder but() {
      return aSettingAttribute()
          .withEnvId(envId)
          .withAccountId(accountId)
          .withName(name)
          .withValue(value)
          .withCategory(category)
          .withAppIds(appIds)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withUsageRestrictions(usageRestrictions)
          .withConnectivityValidationAttributes(connectivityValidationAttributes)
          .withSample(sample);
    }

    public SettingAttribute build() {
      SettingAttribute settingAttribute = new SettingAttribute();
      settingAttribute.setEnvId(envId);
      settingAttribute.setAccountId(accountId);
      settingAttribute.setName(name);
      settingAttribute.setValue(value);
      settingAttribute.setCategory(category);
      settingAttribute.setAppIds(appIds);
      settingAttribute.setUuid(uuid);
      settingAttribute.setAppId(appId);
      settingAttribute.setCreatedBy(createdBy);
      settingAttribute.setCreatedAt(createdAt);
      settingAttribute.setLastUpdatedBy(lastUpdatedBy);
      settingAttribute.setLastUpdatedAt(lastUpdatedAt);
      settingAttribute.setUsageRestrictions(usageRestrictions);
      settingAttribute.setValidationAttributes(connectivityValidationAttributes);
      settingAttribute.setSample(sample);
      settingAttribute.setConnectivityError(connectivityError);
      return settingAttribute;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends BaseYaml {
    private String name;
    private SettingValue.Yaml value;

    @lombok.Builder
    public Yaml(String name, SettingValue.Yaml value) {
      this.name = name;
      this.value = value;
    }
  }

  @UtilityClass
  public static final class SettingAttributeKeys {
    public static final String value_type = SettingAttributeKeys.value + ".type";
    public static final String isCEEnabled = SettingAttributeKeys.value + ".ccmConfig.cloudCostEnabled";
    public static final String referencedConnector = SettingAttributeKeys.value + ".connectorId";
  }

  @Nonnull
  public List<String> fetchRelevantSecretIds() {
    return value == null ? Collections.emptyList() : value.fetchRelevantEncryptedSecrets();
  }
}
