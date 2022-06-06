/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.validator.EntityNameValidator.ALLOWED_CHARS_SERVICE_VARIABLE_MESSAGE;
import static io.harness.data.validator.EntityNameValidator.ALLOWED_CHARS_SERVICE_VARIABLE_STRING;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.encryption.Encrypted;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.security.encryption.EncryptionType;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 9/14/16.
 */
@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"encryptedValue", "encryptedBy"})
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ServiceVariableKeys")
@Entity(value = "serviceVariables", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class ServiceVariable implements EncryptableSetting, PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                        UpdatedAtAware, UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("app_entityId")
                 .field(ServiceVariableKeys.appId)
                 .field(ServiceVariableKeys.entityId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("app_env_templateId")
                 .field(ServiceVariableKeys.appId)
                 .field(ServiceVariableKeys.envId)
                 .field(ServiceVariableKeys.templateId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appEntityIdx")
                 .field(ServiceVariableKeys.appId)
                 .field(ServiceVariableKeys.entityId)
                 .descSortField(ServiceVariableKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("serviceVariableUniqueIdx")
                 .unique(true)
                 .field(ServiceVariableKeys.entityId)
                 .field(ServiceVariableKeys.templateId)
                 .field(ServiceVariableKeys.overrideType)
                 .field(ServiceVariableKeys.instances)
                 .field(ServiceVariableKeys.expression)
                 .field(ServiceVariableKeys.type)
                 .field(ServiceVariableKeys.name)
                 .build())
        .build();
  }
  /**
   * The constant DEFAULT_TEMPLATE_ID.
   */
  public static final String DEFAULT_TEMPLATE_ID = "__TEMPLATE_ID";
  public static final String ENCRYPTED_VALUE_KEY = "encryptedValue";

  private String templateId = DEFAULT_TEMPLATE_ID;

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;

  @NotEmpty(groups = {Create.class}) private String envId;

  @NotNull(groups = {Create.class}) private EntityType entityType;

  @NotEmpty(groups = {Create.class}) private String entityId;

  private String parentServiceVariableId;

  @Transient private ServiceVariable overriddenServiceVariable;

  private OverrideType overrideType;

  private List<String> instances;
  private String expression;

  @SchemaIgnore private String accountId;

  @NotEmpty(groups = {Create.class})
  @EntityName(charSetString = ALLOWED_CHARS_SERVICE_VARIABLE_STRING, message = ALLOWED_CHARS_SERVICE_VARIABLE_MESSAGE)
  private String name;

  @Encrypted(fieldName = "value", isReference = true) private char[] value;

  private ServiceVariableType type;

  // NOTE: This field is used for service variables of type artifact.
  private List<String> allowedList;

  @FdIndex @SchemaIgnore private String encryptedValue;

  @SchemaIgnore @Transient private String secretTextName;

  @JsonIgnore @SchemaIgnore @Transient private boolean decrypted;

  @SchemaIgnore @Transient private String serviceId;

  @SchemaIgnore @Transient private transient EncryptionType encryptionType;

  @SchemaIgnore @Transient private transient String encryptedBy;

  private transient List<ArtifactStreamSummary> artifactStreamSummaries;

  @Override
  @SchemaIgnore
  public SettingVariableTypes getSettingType() {
    return SettingVariableTypes.SERVICE_VARIABLE;
  }

  public void setSettingType(SettingVariableTypes type) {
    //
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public List<java.lang.reflect.Field> getEncryptedFields() {
    if (type != ServiceVariableType.ENCRYPTED_TEXT) {
      return Collections.emptyList();
    }

    return EncryptionReflectUtils.getEncryptedFields(this.getClass());
  }

  public enum OverrideType {
    /**
     * All  override type.
     */
    ALL,
    /**
     * Instances override type.
     */
    INSTANCES,
    /**
     * Custom override type.
     */
    CUSTOM
  }

  public ServiceVariable cloneInternal() {
    ServiceVariable serviceVariable = builder()
                                          .accountId(getAccountId())
                                          .envId(getEnvId())
                                          .entityId(getEntityId())
                                          .entityType(getEntityType())
                                          .templateId(getTemplateId())
                                          .name(getName())
                                          .value(getValue())
                                          .type(getType())
                                          .encryptedValue(getEncryptedValue())
                                          .expression(getExpression())
                                          .overrideType(getOverrideType())
                                          .build();

    serviceVariable.setAppId(getAppId());
    if (isNotEmpty(getAllowedList())) {
      serviceVariable.setAllowedList(new ArrayList<>(getAllowedList()));
    }
    return serviceVariable;
  }

  @UtilityClass
  public static final class ServiceVariableKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String entityId = "entityId";
    public static final String envId = "envId";
    public static final String templateId = "templateId";
  }
}
