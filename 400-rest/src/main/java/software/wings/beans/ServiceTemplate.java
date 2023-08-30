/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE_TEMPLATE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.utils.ArtifactType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "serviceTemplates", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "ServiceTemplateKeys")
public class ServiceTemplate extends Base implements NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(ServiceTemplateKeys.appId)
                 .field(ServiceTemplateKeys.envId)
                 .field(ServiceTemplateKeys.name)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("app_env_createdAt")
                 .field(ServiceTemplateKeys.appId)
                 .field(ServiceTemplateKeys.envId)
                 .descSortField(ServiceTemplateKeys.createdAt)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("appId_serviceId_createdAt")
                .field(ServiceTemplateKeys.appId)
                .field(ServiceTemplateKeys.serviceId)
                .descSortField(ServiceTemplateKeys.createdAt)
                .build())

        .build();
  }

  public static final String SERVICE_ID_KEY = "serviceId";
  public static final String ENVIRONMENT_ID_KEY = "envId";
  public static final String APP_ID = "appId";

  @FdIndex @Getter @Setter private String accountId;
  @NotEmpty private String envId;
  @NotEmpty private String name;
  private String description;
  @NotEmpty private String serviceId;
  @Transient private List<ConfigFile> serviceConfigFiles = new ArrayList<>();
  @Transient private List<ServiceVariable> serviceVariables = new ArrayList<>();
  @Transient private ArtifactType serviceArtifactType;
  @Transient private List<ConfigFile> configFilesOverrides = new ArrayList<>();
  @Transient private List<ServiceVariable> serviceVariablesOverrides = new ArrayList<>();
  @Transient private String configMapYamlOverride;
  @Transient private String helmValueYamlOverride;
  @Transient private List<InfrastructureMapping> infrastructureMappings = new ArrayList<>();
  @Getter @Setter private transient ApplicationManifest valuesOverrideAppManifest;
  @Getter @Setter private transient ApplicationManifest helmChartOverride;
  @Getter @Setter private transient ManifestFile valuesOverrideManifestFile;
  @Getter @Setter private transient ApplicationManifest kustomizePatchesOverrideAppManifest;
  @Getter @Setter private transient ManifestFile kustomizePatchesOverrideManifestFile;
  @Getter @Setter private transient ApplicationManifest ocParamsOverrideAppManifest;
  @Getter @Setter private transient ManifestFile ocParamsOverrideFile;
  @Getter @Setter private transient ApplicationManifest appSettingOverrideManifest;
  @Getter @Setter private transient ApplicationManifest connStringsOverrideManifest;
  @Getter @Setter private transient ManifestFile appSettingsOverrideManifestFile;
  @Getter @Setter private transient ManifestFile connStringsOverrideManifestFile;

  private boolean defaultServiceTemplate;

  public ServiceTemplate cloneInternal() {
    return aServiceTemplate()
        .withAppId(getAppId())
        .withEnvId(getEnvId())
        .withDescription(getDescription())
        .withServiceId(getServiceId())
        .withDefaultServiceTemplate(isDefaultServiceTemplate())
        .withName(getName())
        .build();
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
        .type(SERVICE_TEMPLATE)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  /**
   * Gets name.
   *
   * @return the name
   */
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

  /**
   * Gets env id.
   *
   * @return the env id
   */
  public String getEnvId() {
    return envId;
  }

  /**
   * Sets env id.
   *
   * @param envId the env id
   */
  public void setEnvId(String envId) {
    this.envId = envId;
  }

  /**
   * Gets config files.
   *
   * @return the config files
   */
  public List<ConfigFile> getConfigFilesOverrides() {
    return configFilesOverrides;
  }

  /**
   * Sets config files.
   *
   * @param configFilesOverrides the config files
   */
  public void setConfigFilesOverrides(List<ConfigFile> configFilesOverrides) {
    this.configFilesOverrides = configFilesOverrides;
  }

  public String getConfigMapYamlOverride() {
    return configMapYamlOverride;
  }

  public void setConfigMapYamlOverride(String configMapYamlOverride) {
    this.configMapYamlOverride = configMapYamlOverride;
  }

  public String getHelmValueYamlOverride() {
    return helmValueYamlOverride;
  }

  public void setHelmValueYamlOverride(String helmValueYamlOverride) {
    this.helmValueYamlOverride = helmValueYamlOverride;
  }

  /**
   * Is default service template boolean.
   *
   * @return the boolean
   */
  public boolean isDefaultServiceTemplate() {
    return defaultServiceTemplate;
  }

  /**
   * Sets default service template.
   *
   * @param defaultServiceTemplate the default service template
   */
  public void setDefaultServiceTemplate(boolean defaultServiceTemplate) {
    this.defaultServiceTemplate = defaultServiceTemplate;
  }

  /**
   * Gets service id.
   *
   * @return the service id
   */
  public String getServiceId() {
    return serviceId;
  }

  /**
   * Sets service id.
   *
   * @param serviceId the service id
   */
  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  /**
   * Gets service variables.
   *
   * @return the service variables
   */
  public List<ServiceVariable> getServiceVariablesOverrides() {
    return serviceVariablesOverrides;
  }

  /**
   * Sets service variables.
   *
   * @param serviceVariablesOverrides the service variables
   */
  public void setServiceVariablesOverrides(List<ServiceVariable> serviceVariablesOverrides) {
    this.serviceVariablesOverrides = serviceVariablesOverrides;
  }

  /**
   * Gets service config files.
   *
   * @return the service config files
   */
  public List<ConfigFile> getServiceConfigFiles() {
    return serviceConfigFiles;
  }

  /**
   * Sets service config files.
   *
   * @param serviceConfigFiles the service config files
   */
  public void setServiceConfigFiles(List<ConfigFile> serviceConfigFiles) {
    this.serviceConfigFiles = serviceConfigFiles;
  }

  /**
   * Gets service variables.
   *
   * @return the service variables
   */
  public List<ServiceVariable> getServiceVariables() {
    return serviceVariables;
  }

  /**
   * Sets service variables.
   *
   * @param serviceVariables the service variables
   */
  public void setServiceVariables(List<ServiceVariable> serviceVariables) {
    this.serviceVariables = serviceVariables;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode()
        + Objects.hash(envId, name, description, serviceId, configFilesOverrides, serviceVariablesOverrides,
            defaultServiceTemplate);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }
    final ServiceTemplate other = (ServiceTemplate) obj;
    return Objects.equals(this.envId, other.envId) && Objects.equals(this.name, other.name)
        && Objects.equals(this.description, other.description) && Objects.equals(this.serviceId, other.serviceId)
        && Objects.equals(this.configFilesOverrides, other.configFilesOverrides)
        && Objects.equals(this.serviceVariablesOverrides, other.serviceVariablesOverrides)
        && Objects.equals(this.configMapYamlOverride, other.configMapYamlOverride)
        && Objects.equals(this.helmValueYamlOverride, other.helmValueYamlOverride)
        && Objects.equals(this.valuesOverrideAppManifest, other.valuesOverrideAppManifest)
        && Objects.equals(this.valuesOverrideManifestFile, other.valuesOverrideManifestFile)
        && Objects.equals(this.kustomizePatchesOverrideAppManifest, other.kustomizePatchesOverrideAppManifest)
        && Objects.equals(this.kustomizePatchesOverrideManifestFile, other.kustomizePatchesOverrideManifestFile)
        && Objects.equals(this.helmChartOverride, other.helmChartOverride)
        && Objects.equals(this.defaultServiceTemplate, other.defaultServiceTemplate)
        && Objects.equals(this.ocParamsOverrideAppManifest, other.ocParamsOverrideAppManifest)
        && Objects.equals(this.ocParamsOverrideFile, other.ocParamsOverrideFile);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("envId", envId)
        .add("name", name)
        .add("description", description)
        .add(SERVICE_ID_KEY, serviceId)
        .add("configFilesOverrides", configFilesOverrides)
        .add("serviceVariablesOverrides", serviceVariablesOverrides)
        .add("configMapYamlOverride", configMapYamlOverride)
        .add("helmValueYamlOverride", helmValueYamlOverride)
        .add("valuesOverrideAppManifest", valuesOverrideAppManifest)
        .add("valuesOverrideManifestFile", valuesOverrideManifestFile)
        .add("helmChartOverride", helmChartOverride)
        .add("defaultServiceTemplate", defaultServiceTemplate)
        .add("ocParamsOverrideAppManifest", ocParamsOverrideAppManifest)
        .add("ocParamsOverrideFile", ocParamsOverrideFile)
        .add("kustomizePatchesOverrideAppManifest", kustomizePatchesOverrideAppManifest)
        .add("kustomizePatchesOverrideManifestFile", kustomizePatchesOverrideManifestFile)
        .toString();
  }

  /**
   * Gets infrastructure mappings.
   *
   * @return the infrastructure mappings
   */
  public List<InfrastructureMapping> getInfrastructureMappings() {
    return infrastructureMappings;
  }

  /**
   * Sets infrastructure mappings.
   *
   * @param infrastructureMappings the infrastructure mappings
   */
  public void setInfrastructureMappings(List<InfrastructureMapping> infrastructureMappings) {
    this.infrastructureMappings = infrastructureMappings;
  }

  /**
   * Gets service artifact type.
   *
   * @return the service artifact type
   */
  public ArtifactType getServiceArtifactType() {
    return serviceArtifactType;
  }

  /**
   * Sets service artifact type.
   *
   * @param serviceArtifactType the service artifact type
   */
  public void setServiceArtifactType(ArtifactType serviceArtifactType) {
    this.serviceArtifactType = serviceArtifactType;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String envId;
    private String name;
    private String description;
    private String serviceId;
    private List<ConfigFile> serviceConfigFiles = new ArrayList<>();
    private List<ServiceVariable> serviceVariables = new ArrayList<>();
    private List<ConfigFile> configFilesOverrides = new ArrayList<>();
    private List<ServiceVariable> serviceVariablesOverrides = new ArrayList<>();
    private String configMapYamlOverride;
    private String helmValueYamlOverride;
    private ApplicationManifest valuesOverrideAppManifest;
    private ApplicationManifest helmChartOverride;
    private ManifestFile valuesOverrideManifestFile;
    private String uuid;
    private List<InfrastructureMapping> infrastructureMappings = new ArrayList<>();
    private String appId;
    private boolean defaultServiceTemplate;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String accountId;

    private Builder() {}

    /**
     * A service template builder.
     *
     * @return the builder
     */
    public static Builder aServiceTemplate() {
      return new Builder();
    }

    /**
     * With env id builder.
     *
     * @param envId the env id
     * @return the builder
     */
    public Builder withEnvId(String envId) {
      this.envId = envId;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With description builder.
     *
     * @param description the description
     * @return the builder
     */
    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * With service id builder.
     *
     * @param serviceId the service id
     * @return the builder
     */
    public Builder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    /**
     * With service config files builder.
     *
     * @param serviceConfigFiles the service config files
     * @return the builder
     */
    public Builder withServiceConfigFiles(List<ConfigFile> serviceConfigFiles) {
      this.serviceConfigFiles = serviceConfigFiles;
      return this;
    }

    /**
     * With service variables builder.
     *
     * @param serviceVariables the service variables
     * @return the builder
     */
    public Builder withServiceVariables(List<ServiceVariable> serviceVariables) {
      this.serviceVariables = serviceVariables;
      return this;
    }

    /**
     * With config files overrides builder.
     *
     * @param configFilesOverrides the config files overrides
     * @return the builder
     */
    public Builder withConfigFilesOverrides(List<ConfigFile> configFilesOverrides) {
      this.configFilesOverrides = configFilesOverrides;
      return this;
    }

    /**
     * With service variables overrides builder.
     *
     * @param serviceVariablesOverrides the service variables overrides
     * @return the builder
     */
    public Builder withServiceVariablesOverrides(List<ServiceVariable> serviceVariablesOverrides) {
      this.serviceVariablesOverrides = serviceVariablesOverrides;
      return this;
    }

    public Builder withConfigMapYamlOverride(String configMapYamlOverride) {
      this.configMapYamlOverride = configMapYamlOverride;
      return this;
    }

    public Builder withHelmValueYamlOverride(String helmValueYamlOverride) {
      this.helmValueYamlOverride = helmValueYamlOverride;
      return this;
    }

    public Builder withValuesOverrideAppManifest(ApplicationManifest valuesOverrideAppManifest) {
      this.valuesOverrideAppManifest = valuesOverrideAppManifest;
      return this;
    }

    public Builder withHelmChartOverride(ApplicationManifest helmChartOverride) {
      this.helmChartOverride = helmChartOverride;
      return this;
    }

    public Builder withValuesOverrideManifestFile(ManifestFile valuesOverrideManifestFile) {
      this.valuesOverrideManifestFile = valuesOverrideManifestFile;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With infrastructure mappings builder.
     *
     * @param infrastructureMappings the infrastructure mappings
     * @return the builder
     */
    public Builder withInfrastructureMappings(List<InfrastructureMapping> infrastructureMappings) {
      this.infrastructureMappings = infrastructureMappings;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With default service template builder.
     *
     * @param defaultServiceTemplate the default service template
     * @return the builder
     */
    public Builder withDefaultServiceTemplate(boolean defaultServiceTemplate) {
      this.defaultServiceTemplate = defaultServiceTemplate;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param accountId the last updated at
     * @return the builder
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aServiceTemplate()
          .withEnvId(envId)
          .withName(name)
          .withDescription(description)
          .withServiceId(serviceId)
          .withServiceConfigFiles(serviceConfigFiles)
          .withServiceVariables(serviceVariables)
          .withConfigFilesOverrides(configFilesOverrides)
          .withServiceVariablesOverrides(serviceVariablesOverrides)
          .withConfigMapYamlOverride(configMapYamlOverride)
          .withHelmValueYamlOverride(helmValueYamlOverride)
          .withValuesOverrideAppManifest(valuesOverrideAppManifest)
          .withValuesOverrideManifestFile(valuesOverrideManifestFile)
          .withHelmChartOverride(helmChartOverride)
          .withUuid(uuid)
          .withInfrastructureMappings(infrastructureMappings)
          .withAppId(appId)
          .withDefaultServiceTemplate(defaultServiceTemplate)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withAccountId(accountId);
    }

    /**
     * Build service template.
     *
     * @return the service template
     */
    public ServiceTemplate build() {
      ServiceTemplate serviceTemplate = new ServiceTemplate();
      serviceTemplate.setEnvId(envId);
      serviceTemplate.setName(name);
      serviceTemplate.setDescription(description);
      serviceTemplate.setServiceId(serviceId);
      serviceTemplate.setServiceConfigFiles(serviceConfigFiles);
      serviceTemplate.setServiceVariables(serviceVariables);
      serviceTemplate.setConfigFilesOverrides(configFilesOverrides);
      serviceTemplate.setServiceVariablesOverrides(serviceVariablesOverrides);
      serviceTemplate.setConfigMapYamlOverride(configMapYamlOverride);
      serviceTemplate.setHelmValueYamlOverride(helmValueYamlOverride);
      serviceTemplate.setValuesOverrideManifestFile(valuesOverrideManifestFile);
      serviceTemplate.setHelmChartOverride(helmChartOverride);
      serviceTemplate.setValuesOverrideAppManifest(valuesOverrideAppManifest);
      serviceTemplate.setUuid(uuid);
      serviceTemplate.setInfrastructureMappings(infrastructureMappings);
      serviceTemplate.setAppId(appId);
      serviceTemplate.setDefaultServiceTemplate(defaultServiceTemplate);
      serviceTemplate.setCreatedBy(createdBy);
      serviceTemplate.setCreatedAt(createdAt);
      serviceTemplate.setLastUpdatedBy(lastUpdatedBy);
      serviceTemplate.setLastUpdatedAt(lastUpdatedAt);
      serviceTemplate.setAccountId(accountId);
      return serviceTemplate;
    }
  }

  @UtilityClass
  public static final class ServiceTemplateKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String envId = "envId";
    public static final String serviceId = "serviceId";
    public static final String createdAt = "createdAt";
  }
}
