/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static software.wings.beans.EntityVersion.Builder.anEntityVersion;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;
import software.wings.beans.EntityVersion;
import software.wings.beans.template.TemplateMetadata;
import software.wings.beans.template.dto.ImportedTemplateDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 11/16/16.
 */
@StoreIn(DbAliases.HARNESS)
@Entity(value = "serviceCommands", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "ServiceCommandKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ServiceCommand extends Base {
  public static final String TEMPLATE_UUID_KEY = "templateUuid";

  private String name;
  @FdIndex private String serviceId;
  private Map<String, EntityVersion> envIdVersionMap = new HashMap<>();
  private Integer defaultVersion;

  private boolean targetToAllEnv = true;

  @FdIndex @Getter @Setter private String accountId;

  @Transient private Command command;

  @Transient @JsonIgnore private boolean setAsDefault;

  @Transient private String notes;

  @JsonIgnore private double order;

  @FdIndex @SchemaIgnore private String templateUuid;

  @SchemaIgnore private String templateVersion;

  @SchemaIgnore private ImportedTemplateDetails importedTemplateDetails;

  @SchemaIgnore private TemplateMetadata templateMetadata;

  public TemplateMetadata getTemplateMetadata() {
    return templateMetadata;
  }

  public void setTemplateMetadata(TemplateMetadata templateMetadata) {
    this.templateMetadata = templateMetadata;
  }

  @SchemaIgnore
  public String getTemplateVersion() {
    return templateVersion;
  }

  public void setTemplateVersion(String templateVersion) {
    this.templateVersion = templateVersion;
  }

  @SchemaIgnore
  public String getTemplateUuid() {
    return templateUuid;
  }

  public void setTemplateUuid(String templateUuid) {
    this.templateUuid = templateUuid;
  }

  @SchemaIgnore
  public ImportedTemplateDetails getImportedTemplateDetails() {
    return importedTemplateDetails;
  }

  public void setImportedTemplateDetails(ImportedTemplateDetails importedTemplateDetails) {
    this.importedTemplateDetails = importedTemplateDetails;
  }

  /**
   * Getter for property 'name'.
   *
   * @return Value for property 'name'.
   */
  public String getName() {
    return name;
  }

  /**
   * Setter for property 'name'.
   *
   * @param name Value to set for property 'name'.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Getter for property 'serviceId'.
   *
   * @return Value for property 'serviceId'.
   */
  public String getServiceId() {
    return serviceId;
  }

  /**
   * Setter for property 'serviceId'.
   *
   * @param serviceId Value to set for property 'serviceId'.
   */
  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  /**
   * Getter for property 'envIdVersionMap'.
   *
   * @return Value for property 'envIdVersionMap'.
   */
  public Map<String, EntityVersion> getEnvIdVersionMap() {
    return envIdVersionMap;
  }

  /**
   * Setter for property 'envIdVersionMap'.
   *
   * @param envIdVersionMap Value to set for property 'envIdVersionMap'.
   */
  public void setEnvIdVersionMap(Map<String, EntityVersion> envIdVersionMap) {
    this.envIdVersionMap = envIdVersionMap;
  }

  /**
   * Getter for property 'defaultVersion'.
   *
   * @return Value for property 'defaultVersion'.
   */
  public Integer getDefaultVersion() {
    return defaultVersion;
  }

  /**
   * Setter for property 'defaultVersion'.
   *
   * @param defaultVersion Value to set for property 'defaultVersion'.
   */
  public void setDefaultVersion(Integer defaultVersion) {
    this.defaultVersion = defaultVersion;
  }

  /**
   * Getter for property 'command'.
   *
   * @return Value for property 'command'.
   */
  public Command getCommand() {
    return command;
  }

  /**
   * Setter for property 'command'.
   *
   * @param command Value to set for property 'command'.
   */
  public void setCommand(Command command) {
    this.command = command;
  }

  /**
   * Getter for property 'setAsDefault'.
   *
   * @return Value for property 'setAsDefault'.
   */
  @JsonIgnore
  public boolean getSetAsDefault() {
    return setAsDefault;
  }

  /**
   * Setter for property 'setAsDefault'.
   *
   * @param setAsDefault Value to set for property 'setAsDefault'.
   */
  @JsonProperty
  public void setSetAsDefault(boolean setAsDefault) {
    this.setAsDefault = setAsDefault;
  }

  /**
   * Getter for property 'notes'.
   *
   * @return Value for property 'notes'.
   */
  @JsonIgnore
  public String getNotes() {
    return notes;
  }

  /**
   * Setter for property 'notes'.
   *
   * @param notes Value to set for property 'notes'.
   */
  @JsonProperty
  public void setNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Getter for property 'targetToAllEnv'.
   *
   * @return Value for property 'targetToAllEnv'.
   */
  public boolean isTargetToAllEnv() {
    return targetToAllEnv;
  }

  /**
   * Setter for property 'targetToAllEnv'.
   *
   * @param targetToAllEnv Value to set for property 'targetToAllEnv'.
   */
  public void setTargetToAllEnv(boolean targetToAllEnv) {
    this.targetToAllEnv = targetToAllEnv;
  }

  public double getOrder() {
    return order;
  }

  public void setOrder(double order) {
    this.order = order;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hash(name, serviceId, envIdVersionMap, defaultVersion, targetToAllEnv);
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
    ServiceCommand other = (ServiceCommand) obj;
    return Objects.equals(name, other.name) && Objects.equals(serviceId, other.serviceId)
        && Objects.equals(envIdVersionMap, other.envIdVersionMap)
        && Objects.equals(defaultVersion, other.defaultVersion) && Objects.equals(targetToAllEnv, other.targetToAllEnv);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("serviceId", serviceId)
        .add("envIdVersionMap", envIdVersionMap)
        .add("defaultVersion", defaultVersion)
        .add("targetToAllEnv", targetToAllEnv)
        .toString();
  }

  @JsonIgnore
  public int getVersionForEnv(String envId) {
    if (targetToAllEnv || envIdVersionMap.get(envId) != null) {
      return Optional.ofNullable(envIdVersionMap.get(envId))
          .orElse(anEntityVersion().withVersion(defaultVersion).build())
          .getVersion();
    } else {
      return 0;
    }
  }

  public ServiceCommand cloneInternal() {
    return aServiceCommand()
        .withName(getName())
        .withTargetToAllEnv(targetToAllEnv)
        .withTemplateVersion(templateVersion)
        .withTemplateUuid(templateUuid)
        .withCommand(getCommand().cloneInternal())
        .build();
  }

  public static final class Builder {
    private String name;
    private String accountId;
    private String serviceId;
    private Map<String, EntityVersion> envIdVersionMap = new HashMap<>();
    private Integer defaultVersion;
    private boolean targetToAllEnv = true;
    private boolean setAsDefault;
    private Command command;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String templateUuid;
    private String templateVersion;
    private ImportedTemplateDetails importedTemplateDetails;
    private TemplateMetadata templateMetadata;

    private Builder() {}

    public static Builder aServiceCommand() {
      return new Builder();
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public Builder withEnvIdVersionMap(Map<String, EntityVersion> envIdVersionMap) {
      this.envIdVersionMap = envIdVersionMap;
      return this;
    }

    public Builder withDefaultVersion(Integer defaultVersion) {
      this.defaultVersion = defaultVersion;
      return this;
    }

    public Builder withTargetToAllEnv(boolean targetToAllEnv) {
      this.targetToAllEnv = targetToAllEnv;
      return this;
    }

    public Builder withSetAsDefault(boolean setAsDefault) {
      this.setAsDefault = setAsDefault;
      return this;
    }

    public Builder withCommand(Command command) {
      this.command = command;
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

    public Builder withTemplateUuid(String templateUuid) {
      this.templateUuid = templateUuid;
      return this;
    }

    public Builder withTemplateVersion(String templateVersion) {
      this.templateVersion = templateVersion;
      return this;
    }

    public Builder withImportedTemplateDetails(ImportedTemplateDetails importedTemplateDetails) {
      this.importedTemplateDetails = importedTemplateDetails;
      return this;
    }

    public Builder withTemplateMetadata(TemplateMetadata templateMetadata) {
      this.templateMetadata = templateMetadata;
      return this;
    }

    public Builder but() {
      return aServiceCommand()
          .withName(name)
          .withAccountId(accountId)
          .withServiceId(serviceId)
          .withEnvIdVersionMap(envIdVersionMap)
          .withDefaultVersion(defaultVersion)
          .withTargetToAllEnv(targetToAllEnv)
          .withCommand(command)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withSetAsDefault(setAsDefault)
          .withImportedTemplateDetails(importedTemplateDetails);
    }

    public ServiceCommand build() {
      ServiceCommand serviceCommand = new ServiceCommand();
      serviceCommand.setName(name);
      serviceCommand.setAccountId(accountId);
      serviceCommand.setServiceId(serviceId);
      serviceCommand.setEnvIdVersionMap(envIdVersionMap);
      serviceCommand.setDefaultVersion(defaultVersion);
      serviceCommand.setCommand(command);
      serviceCommand.setUuid(uuid);
      serviceCommand.setAppId(appId);
      serviceCommand.setCreatedBy(createdBy);
      serviceCommand.setCreatedAt(createdAt);
      serviceCommand.setLastUpdatedBy(lastUpdatedBy);
      serviceCommand.setLastUpdatedAt(lastUpdatedAt);
      serviceCommand.setSetAsDefault(setAsDefault);
      serviceCommand.targetToAllEnv = targetToAllEnv;
      serviceCommand.setTemplateUuid(templateUuid);
      serviceCommand.setTemplateVersion(templateVersion);
      serviceCommand.setImportedTemplateDetails(importedTemplateDetails);
      serviceCommand.setTemplateMetadata(templateMetadata);
      return serviceCommand;
    }
  }
}
