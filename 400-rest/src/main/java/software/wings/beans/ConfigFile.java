/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.EntityVersion.Builder.anEntityVersion;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.security.encryption.EncryptionType;
import io.harness.validation.Create;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.yaml.YamlType;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by anubhaw on 4/12/16.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ConfigFileKeys")
@Entity(value = "configFiles", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(CDC)
@TargetModule(_957_CG_BEANS)
public class ConfigFile extends BaseFile implements EncryptableSetting {
  public static final String DEFAULT_TEMPLATE_ID = "__TEMPLATE_ID";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("entityId_1_templateId_1_relativeFilePath_1_OType_1_instances_1_OExpression_1")
                 .unique(true)
                 .field(ConfigFileKeys.entityId)
                 .field(ConfigFileKeys.templateId)
                 .field(ConfigFileKeys.relativeFilePath)
                 .field(ConfigFileKeys.configOverrideType)
                 .field(ConfigFileKeys.instances)
                 .field(ConfigFileKeys.configOverrideExpression)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("app_template_entityId")
                 .field(BaseFileKeys.appId)
                 .field(ConfigFileKeys.templateId)
                 .field(ConfigFileKeys.entityId)
                 .build())
        .build();
  }

  @FormDataParam("templateId") @DefaultValue(DEFAULT_TEMPLATE_ID) private String templateId;

  @FormDataParam("envId") @NotEmpty(groups = {Create.class}) private String envId;

  @FormDataParam("entityType") @NotNull(groups = {Create.class}) private EntityType entityType;

  @FormDataParam("entityId") @NotEmpty(groups = {Create.class}) private String entityId;

  @FormDataParam("description") private String description;

  @FormDataParam("parentConfigFileId") private String parentConfigFileId;

  @FormDataParam("relativeFilePath") private String relativeFilePath;

  @FormDataParam("targetToAllEnv") private boolean targetToAllEnv;

  @FormDataParam("defaultVersion") private int defaultVersion;

  @Default private Map<String, EntityVersion> envIdVersionMap = new HashMap<>();

  @JsonIgnore @FormDataParam("envIdVersionMapString") private String envIdVersionMapString;

  @Transient @FormDataParam("setAsDefault") private boolean setAsDefault;

  @Transient @FormDataParam("notes") private String notes;

  private String overridePath;

  @FormDataParam("configOverrideType") private ConfigOverrideType configOverrideType;
  @FormDataParam("configOverrideExpression") private String configOverrideExpression;

  @FormDataParam("instances") private List<String> instances;

  @Transient private ConfigFile overriddenConfigFile;

  @FormDataParam("encrypted") private boolean encrypted;

  @FormDataParam("encryptedFileId") private String encryptedFileId;

  @SchemaIgnore @Transient private String secretFileName;

  @SchemaIgnore @Transient private String serviceId;

  @SchemaIgnore @Transient private transient EncryptionType encryptionType;

  @SchemaIgnore private transient String encryptedBy;

  /**
   * Gets version for env.
   *
   * @param envId the env id
   * @return the version for env
   */
  @JsonIgnore
  public int getVersionForEnv(String envId) {
    EntityVersion defualtVersion = anEntityVersion().withVersion(defaultVersion).build();
    if (envIdVersionMap == null || envIdVersionMap.get(envId) == null) {
      return defualtVersion.getVersion();
    }

    return envIdVersionMap.get(envId).getVersion();
  }

  /**
   * The enum Config override type.
   */
  public enum ConfigOverrideType {
    /**
     * All config override type.
     */
    ALL,
    /**
     * Instances config override type.
     */
    INSTANCES,
    /**
     * Custom config override type.
     */
    CUSTOM
  }

  public ConfigFile cloneInternal() {
    ConfigFile configFile = ConfigFile.builder()
                                .description(getDescription())
                                .envId(getEnvId())
                                .entityType(getEntityType())
                                .entityId(getEntityId())
                                .templateId(getTemplateId())
                                .relativeFilePath(getRelativeFilePath())
                                .targetToAllEnv(isTargetToAllEnv())
                                .encrypted(isEncrypted())
                                .configOverrideExpression(getConfigOverrideExpression())
                                .configOverrideType(getConfigOverrideType())
                                .encryptedFileId(getEncryptedFileId())
                                .build();
    configFile.setAccountId(getAccountId());
    configFile.setAppId(getAppId());
    configFile.setFileName(getFileName());
    return configFile;
  }

  @Override
  @SchemaIgnore
  public SettingVariableTypes getSettingType() {
    return SettingVariableTypes.CONFIG_FILE;
  }

  public void setSettingType(SettingVariableTypes type) {
    //
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public List<java.lang.reflect.Field> getEncryptedFields() {
    return Collections.emptyList();
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public boolean isDecrypted() {
    return false;
  }

  @Override
  public void setDecrypted(boolean decrypted) {
    //
  }

  public ConfigFileDto toDto() {
    Map<String, Integer> envVersionMap = getEnvIdVersionMap().entrySet().stream().collect(
        Collectors.toMap(e -> e.getKey(), e -> e.getValue().getVersion()));
    return new ConfigFileDto(
        getUuid(), getRelativeFilePath(), envVersionMap, getSize(), isEncrypted(), getDefaultVersion());
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends BaseEntityYaml {
    private String targetFilePath;
    private boolean encrypted;
    private String fileName;
    private String description;
    private String checksum;
    private String checksumType;
    private boolean targetToAllEnv;
    private List<String> targetEnvs = new ArrayList<>();

    public Yaml(String harnessApiVersion) {
      super(YamlType.CONFIG_FILE.name(), harnessApiVersion);
    }

    @Builder
    public Yaml(String harnessApiVersion, String targetFilePath, boolean encrypted, String fileName, String description,
        String checksum, String checksumType, boolean targetToAllEnv, List<String> targetEnvs) {
      super(YamlType.CONFIG_FILE.name(), harnessApiVersion);
      this.targetFilePath = targetFilePath;
      this.encrypted = encrypted;
      this.fileName = fileName;
      this.description = description;
      this.checksum = checksum;
      this.checksumType = checksumType;
      this.targetToAllEnv = targetToAllEnv;
      this.targetEnvs = targetEnvs;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class OverrideYaml extends BaseEntityYaml {
    private String serviceName;
    private String targetFilePath;
    private String fileName;
    private String checksum;
    private String checksumType;
    private boolean encrypted;

    public OverrideYaml(String harnessApiVersion) {
      super(YamlType.CONFIG_FILE_OVERRIDE.name(), harnessApiVersion);
    }

    @Builder
    public OverrideYaml(String harnessApiVersion, String serviceName, String targetFilePath, String fileName,
        String checksum, String checksumType, boolean encrypted) {
      super(YamlType.CONFIG_FILE_OVERRIDE.name(), harnessApiVersion);
      this.serviceName = serviceName;
      this.targetFilePath = targetFilePath;
      this.fileName = fileName;
      this.checksum = checksum;
      this.checksumType = checksumType;
      this.encrypted = encrypted;
    }
  }
}
