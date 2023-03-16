/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.beans.EnvironmentType.NON_PROD;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.ngmigration.NGMigrationEntityType.ENVIRONMENT;
import static software.wings.yaml.YamlHelper.trimYaml;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.entityinterface.TagAware;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "EnvironmentKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "environments", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Environment
    extends Base implements KeywordsAware, NameAccess, TagAware, AccountAccess, ApplicationAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(EnvironmentKeys.appId)
                 .field(EnvironmentKeys.name)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdCreatedAtAppIdId")
                 .field(EnvironmentKeys.accountId)
                 .descSortField(EnvironmentKeys.createdAt)
                 .rangeField(EnvironmentKeys.appId)
                 .rangeField("_id")
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_name")
                 .field(EnvironmentKeys.accountId)
                 .ascSortField(EnvironmentKeys.name)
                 .build())
        .build();
  }

  @NotEmpty @EntityName @Trimmed private String name;
  private String description;
  private String configMapYaml;
  private String helmValueYaml;
  private Map<String, String> configMapYamlByServiceTemplateId;
  private Map<String, String> helmValueYamlByServiceTemplateId;
  @NotNull private EnvironmentType environmentType = NON_PROD;
  @Transient private List<ServiceTemplate> serviceTemplates;
  @Transient private List<ConfigFile> configFiles;
  @Transient private Setup setup;
  @Transient private List<InfrastructureDefinition> infrastructureDefinitions;
  @Transient private int infraDefinitionsCount;
  @SchemaIgnore private Set<String> keywords;
  private String accountId;
  private boolean sample;

  private transient List<HarnessTagLink> tagLinks;

  /**
   * Gets name.
   *
   * @return the name
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  public String getConfigMapYaml() {
    return configMapYaml;
  }

  public void setConfigMapYaml(String configMapYaml) {
    this.configMapYaml = trimYaml(configMapYaml);
  }

  public Map<String, String> getConfigMapYamlByServiceTemplateId() {
    return configMapYamlByServiceTemplateId;
  }

  public void setConfigMapYamlByServiceTemplateId(Map<String, String> configMapYamlByServiceTemplateId) {
    this.configMapYamlByServiceTemplateId = configMapYamlByServiceTemplateId;
  }

  public String getHelmValueYaml() {
    return helmValueYaml;
  }

  public void setHelmValueYaml(String helmValueYaml) {
    this.helmValueYaml = helmValueYaml;
  }

  public Map<String, String> getHelmValueYamlByServiceTemplateId() {
    return helmValueYamlByServiceTemplateId;
  }

  public void setHelmValueYamlByServiceTemplateId(Map<String, String> helmValueYamlByServiceTemplateId) {
    this.helmValueYamlByServiceTemplateId = helmValueYamlByServiceTemplateId;
  }

  /**
   * Gets environment type.
   *
   * @return the environment type
   */
  public EnvironmentType getEnvironmentType() {
    return environmentType;
  }

  /**
   * Sets environment type.
   *
   * @param environmentType the environment type
   */
  public void setEnvironmentType(EnvironmentType environmentType) {
    this.environmentType = environmentType;
  }

  /**
   * Gets config files.
   *
   * @return the config files
   */
  public List<ConfigFile> getConfigFiles() {
    return configFiles;
  }

  /**
   * Sets config files.
   *
   * @param configFiles the config files
   */
  public void setConfigFiles(List<ConfigFile> configFiles) {
    this.configFiles = configFiles;
  }

  /**
   * Gets service templates.
   *
   * @return the service templates
   */
  public List<ServiceTemplate> getServiceTemplates() {
    return serviceTemplates;
  }

  /**
   * Sets service templates.
   *
   * @param serviceTemplates the service templates
   */
  public void setServiceTemplates(List<ServiceTemplate> serviceTemplates) {
    this.serviceTemplates = serviceTemplates;
  }

  /**
   * Gets setup.
   *
   * @return the setup
   */
  public Setup getSetup() {
    return setup;
  }

  /**
   * Sets setup.
   *
   * @param setup the setup
   */
  public void setSetup(Setup setup) {
    this.setup = setup;
  }

  public boolean isSample() {
    return sample;
  }

  public void setSample(boolean sample) {
    this.sample = sample;
  }

  public Environment cloneInternal() {
    return anEnvironment()
        .name(getName())
        .appId(getAppId())
        .accountId(getAccountId())
        .description(getDescription())
        .configMapYaml(getConfigMapYaml())
        .configMapYamlByServiceTemplateId(getConfigMapYamlByServiceTemplateId())
        .helmValueYaml(getHelmValueYaml())
        .helmValueYamlByServiceTemplateId(getHelmValueYamlByServiceTemplateId())
        .environmentType(getEnvironmentType())
        .build();
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description, environmentType.name()));
    return keywords;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  @JsonIgnore
  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .id(getUuid())
        .name(getName())
        .type(ENVIRONMENT)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private String description;
    private String configMapYaml;
    private Map<String, String> configMapYamlByServiceTemplateId;
    private String helmValueYaml;
    private Map<String, String> helmValueYamlByServiceTemplateId;
    private EnvironmentType environmentType = NON_PROD;
    private List<ConfigFile> configFiles;
    private String uuid;
    private String appId;
    private String accountId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private boolean sample;

    private Builder() {}

    /**
     * An environment builder.
     *
     * @return the builder
     */
    public static Builder anEnvironment() {
      return new Builder();
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * With description builder.
     *
     * @param description the description
     * @return the builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder configMapYaml(String configMapYaml) {
      this.configMapYaml = configMapYaml;
      return this;
    }

    public Builder configMapYamlByServiceTemplateId(Map<String, String> configMapYamlByServiceTemplateId) {
      this.configMapYamlByServiceTemplateId = configMapYamlByServiceTemplateId;
      return this;
    }

    public Builder helmValueYaml(String helmValueYaml) {
      this.helmValueYaml = helmValueYaml;
      return this;
    }

    public Builder helmValueYamlByServiceTemplateId(Map<String, String> helmValueYamlByServiceTemplateId) {
      this.helmValueYamlByServiceTemplateId = helmValueYamlByServiceTemplateId;
      return this;
    }

    /**
     * With environment type builder.
     *
     * @param environmentType the environment type
     * @return the builder
     */
    public Builder environmentType(EnvironmentType environmentType) {
      this.environmentType = environmentType;
      return this;
    }

    /**
     * With config files builder.
     *
     * @param configFiles the config files
     * @return the builder
     */
    public Builder configFiles(List<ConfigFile> configFiles) {
      this.configFiles = configFiles;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With account id builder.
     *
     * @param accountId the account id
     * @return the builder
     */
    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder sample(boolean sample) {
      this.sample = sample;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return anEnvironment()
          .name(name)
          .description(description)
          .configMapYaml(configMapYaml)
          .configMapYamlByServiceTemplateId(configMapYamlByServiceTemplateId)
          .helmValueYaml(helmValueYaml)
          .helmValueYamlByServiceTemplateId(helmValueYamlByServiceTemplateId)
          .environmentType(environmentType)
          .configFiles(configFiles)
          .uuid(uuid)
          .appId(appId)
          .accountId(accountId)
          .createdBy(createdBy)
          .createdAt(createdAt)
          .lastUpdatedBy(lastUpdatedBy)
          .lastUpdatedAt(lastUpdatedAt)
          .sample(sample);
    }

    /**
     * Build environment.
     *
     * @return the environment
     */
    public Environment build() {
      Environment environment = new Environment();
      environment.setName(name);
      environment.setDescription(description);
      environment.setConfigMapYaml(configMapYaml);
      environment.setConfigMapYamlByServiceTemplateId(configMapYamlByServiceTemplateId);
      environment.setHelmValueYaml(helmValueYaml);
      environment.setHelmValueYamlByServiceTemplateId(helmValueYamlByServiceTemplateId);
      environment.setEnvironmentType(environmentType);
      environment.setConfigFiles(configFiles);
      environment.setUuid(uuid);
      environment.setAppId(appId);
      environment.setAccountId(accountId);
      environment.setCreatedBy(createdBy);
      environment.setCreatedAt(createdAt);
      environment.setLastUpdatedBy(lastUpdatedBy);
      environment.setLastUpdatedAt(lastUpdatedAt);
      environment.setSample(sample);
      return environment;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private String description;
    private String configMapYaml;
    private Map<String, String> configMapYamlByServiceTemplateName;
    private String environmentType = "NON_PROD";
    private List<VariableOverrideYaml> variableOverrides = new ArrayList<>();

    @lombok.Builder
    public Yaml(String harnessApiVersion, String description, String configMapYaml,
        Map<String, String> configMapYamlByServiceTemplateName, String environmentType,
        List<VariableOverrideYaml> variableOverrides) {
      super(EntityType.ENVIRONMENT.name(), harnessApiVersion);
      this.description = description;
      this.configMapYaml = configMapYaml;
      this.configMapYamlByServiceTemplateName = configMapYamlByServiceTemplateName;
      this.environmentType = environmentType;
      this.variableOverrides = variableOverrides;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class VariableOverrideYaml extends NameValuePair.AbstractYaml {
    private String serviceName;

    @lombok.Builder
    public VariableOverrideYaml(
        String name, String value, String valueType, List<AllowedValueYaml> allowedValueYamls, String serviceName) {
      super(name, value, valueType, allowedValueYamls);
      this.serviceName = serviceName;
    }
  }

  @UtilityClass
  public static final class EnvironmentKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
  }
}
