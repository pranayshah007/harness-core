/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;

/**
 * This has been deprecated in favor of {@link software.wings.beans.security.UserGroup#notificationSettings}
 */
@StoreIn(DbAliases.HARNESS)
@Entity(value = "notificationGroups", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "NotificationGroupKeys")
@Deprecated
public class NotificationGroup extends Base implements NotificationReceiverInfo, NameAccess, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_yaml")
                 .unique(true)
                 .field(NotificationGroupKeys.accountId)
                 .field(NotificationGroupKeys.name)
                 .build())
        .build();
  }

  public static final String NAME_KEY = "name";

  @NotEmpty private String accountId;
  @NotNull private String name;
  private boolean editable = true;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    NotificationGroup that = (NotificationGroup) o;
    return editable == that.editable && defaultNotificationGroupForAccount == that.defaultNotificationGroupForAccount
        && Objects.equals(accountId, that.accountId) && Objects.equals(name, that.name)
        && Objects.equals(roles, that.roles) && Objects.equals(addressesByChannelType, that.addressesByChannelType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), accountId, name, editable, roles, defaultNotificationGroupForAccount, addressesByChannelType);
  }

  // roles to notify
  @Reference(idOnly = true, ignoreMissing = true) private List<Role> roles = new ArrayList<>();

  private boolean defaultNotificationGroupForAccount;
  @NotNull private Map<NotificationChannelType, List<String>> addressesByChannelType = new HashMap<>();

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
   * Gets addresses by channel type.
   *
   * @return the addresses by channel type
   */
  @Override
  public Map<NotificationChannelType, List<String>> getAddressesByChannelType() {
    return addressesByChannelType;
  }

  @Nullable
  @Override
  @JsonIgnore
  public SlackNotificationSetting getSlackConfig() {
    return null;
  }

  @Nullable
  @Override
  @JsonIgnore
  public String getPagerDutyIntegrationKey() {
    return null;
  }

  @Nullable
  @Override
  @JsonIgnore
  public String getMicrosoftTeamsWebhookUrl() {
    return null;
  }

  @Override
  @JsonIgnore
  public List<String> getEmailAddresses() {
    return Collections.emptyList();
  }

  /**
   * Sets addresses by channel type.
   *
   * @param addressesByChannelType the addresses by channel type
   */
  public void setAddressesByChannelType(Map<NotificationChannelType, List<String>> addressesByChannelType) {
    this.addressesByChannelType = addressesByChannelType;
  }

  /**
   * Gets account id.
   *
   * @return the account id
   */
  public String getAccountId() {
    return accountId;
  }

  /**
   * Sets account id.
   *
   * @param accountId the account id
   */
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  /**
   * Editable or not
   * @return
   */
  public boolean isEditable() {
    return editable;
  }

  /**
   *
   * @param editable
   */
  public void setEditable(boolean editable) {
    this.editable = editable;
  }

  public boolean isDefaultNotificationGroupForAccount() {
    return defaultNotificationGroupForAccount;
  }

  public void setDefaultNotificationGroupForAccount(boolean defaultNotificationGroupForAccount) {
    this.defaultNotificationGroupForAccount = defaultNotificationGroupForAccount;
  }

  /**
   *
   * Gets roles.
   *
   * @return the roles
   */
  public List<Role> getRoles() {
    return roles;
  }

  /**
   * Sets roles.
   *
   * @param roles the roles
   */
  public void setRoles(List<Role> roles) {
    this.roles = roles;
  }

  /**
   * The type Notification group builder.
   */
  public static final class NotificationGroupBuilder {
    private String accountId;
    private String name;
    private List<Role> roles = new ArrayList<>();
    private Map<NotificationChannelType, List<String>> addressesByChannelType = new HashMap<>();
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private boolean editable;
    private boolean defaultNotificationGroupForAccount;

    private NotificationGroupBuilder() {}

    /**
     * Add addresses by channel type notification group builder.
     *
     * @param notificationChannelType the notification channel type
     * @param addresses               the addresses
     * @return the notification group builder
     */
    public NotificationGroupBuilder addAddressesByChannelType(
        NotificationChannelType notificationChannelType, List<String> addresses) {
      this.addressesByChannelType.put(notificationChannelType, addresses);
      return this;
    }

    /**
     * A notification group notification group builder.
     *
     * @return the notification group builder
     */
    public static NotificationGroupBuilder aNotificationGroup() {
      return new NotificationGroupBuilder();
    }

    /**
     * With account id notification group builder.
     *
     * @param accountId the account id
     * @return the notification group builder
     */
    public NotificationGroupBuilder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * With
     * @param roles
     * @return the notification group builder
     */
    public NotificationGroupBuilder withRoles(List<Role> roles) {
      this.roles = roles;
      return this;
    }

    /**
     * With Role
     * @param role Role
     * @return the notification group builder
     */
    public NotificationGroupBuilder withRole(Role role) {
      this.roles.add(role);
      return this;
    }

    /**
     * With name notification group builder.
     *
     * @param name the name
     * @return the notification group builder
     */
    public NotificationGroupBuilder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With addresses by channel type notification group builder.
     *
     * @param addressesByChannelType the addresses by channel type
     * @return the notification group builder
     */
    public NotificationGroupBuilder withAddressesByChannelType(
        Map<NotificationChannelType, List<String>> addressesByChannelType) {
      this.addressesByChannelType = addressesByChannelType;
      return this;
    }

    /**
     * With uuid notification group builder.
     *
     * @param uuid the uuid
     * @return the notification group builder
     */
    public NotificationGroupBuilder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id notification group builder.
     *
     * @param appId the app id
     * @return the notification group builder
     */
    public NotificationGroupBuilder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With created by notification group builder.
     *
     * @param createdBy the created by
     * @return the notification group builder
     */
    public NotificationGroupBuilder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at notification group builder.
     *
     * @param createdAt the created at
     * @return the notification group builder
     */
    public NotificationGroupBuilder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With last updated by notification group builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the notification group builder
     */
    public NotificationGroupBuilder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at notification group builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the notification group builder
     */
    public NotificationGroupBuilder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public NotificationGroupBuilder withEditable(boolean editable) {
      this.editable = editable;
      return this;
    }

    public NotificationGroupBuilder withDefaultNotificationGroupForAccount(boolean defaultNotificationGroupForAccount) {
      this.defaultNotificationGroupForAccount = defaultNotificationGroupForAccount;
      return this;
    }

    /**
     * Build notification group.
     *
     * @return the notification group
     */
    public NotificationGroup build() {
      NotificationGroup notificationGroup = new NotificationGroup();
      notificationGroup.setAccountId(accountId);
      notificationGroup.setRoles(roles);
      notificationGroup.setName(name);
      notificationGroup.setAddressesByChannelType(addressesByChannelType);
      notificationGroup.setUuid(uuid);
      notificationGroup.setAppId(appId);
      notificationGroup.setCreatedBy(createdBy);
      notificationGroup.setCreatedAt(createdAt);
      notificationGroup.setLastUpdatedBy(lastUpdatedBy);
      notificationGroup.setLastUpdatedAt(lastUpdatedAt);
      notificationGroup.setEditable(editable);
      notificationGroup.setDefaultNotificationGroupForAccount(defaultNotificationGroupForAccount);
      return notificationGroup;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private List<NotificationGroupAddressYaml> addresses;
    private String defaultNotificationGroupForAccount;

    @Builder
    public Yaml(String type, String harnessApiVersion, List<NotificationGroupAddressYaml> addresses,
        String defaultNotificationGroupForAccount) {
      super(type, harnessApiVersion);
      this.addresses = addresses;
      this.defaultNotificationGroupForAccount = defaultNotificationGroupForAccount;
    }
  }
}
