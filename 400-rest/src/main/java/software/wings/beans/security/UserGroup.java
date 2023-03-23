/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.security;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.ngmigration.NGMigrationEntityType.USER_GROUP;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.structure.CollectionUtils;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.beans.Base;
import software.wings.beans.NotificationChannelType;
import software.wings.beans.NotificationReceiverInfo;
import software.wings.beans.User;
import software.wings.beans.UserGroupEntityReference;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.sso.SSOType;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * User bean class.
 *
 * @author Rishi
 */
@OwnedBy(PL)
@JsonInclude(NON_EMPTY)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "UserGroupKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "userGroups", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class UserGroup extends Base implements NotificationReceiverInfo, AccountAccess, NameAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdAndImportedByScim")
                 .field(UserGroupKeys.accountId)
                 .field(UserGroupKeys.importedByScim)
                 .build(),
            CompoundMongoIndex.builder()
                .name("accountAndMemberIds")
                .field(UserGroupKeys.accountId)
                .field(UserGroupKeys.memberIds)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdAndName")
                .field(UserGroupKeys.accountId)
                .field(UserGroupKeys.name)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdAndFilterTypeAndAppIds")
                .field(UserGroupKeys.accountId)
                .field(UserGroupKeys.appFilterType)
                .field(UserGroupKeys.appIds)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountId_linkedSsoId_isSsoLinked")
                .field(UserGroupKeys.accountId)
                .field(UserGroupKeys.linkedSsoId)
                .field(UserGroupKeys.isSsoLinked)
                .build())
        .build();
  }

  public static final String MEMBER_IDS_KEY = "memberIds";
  public static final String NAME_KEY = "name";
  public static final String ACCOUNT_ID_KEY = "accountId";
  public static final String IS_DEFAULT_KEY = "isDefault";
  public static final String NOTIFICATION_SETTINGS_KEY = "notificationSettings";

  /**
   * The constant DEFAULT_READ_ONLY_USER_GROUP_NAME.
   */
  public static final String DEFAULT_READ_ONLY_USER_GROUP_NAME = "ReadOnlyUserGroup";
  /**
   * The constant DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME.
   */
  public static final String DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME = "Non-Production Support";
  /**
   * The constant DEFAULT_PROD_SUPPORT_USER_GROUP_NAME.
   */
  public static final String DEFAULT_PROD_SUPPORT_USER_GROUP_NAME = "Production Support";
  /**
   * The constant DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME.
   */
  public static final String DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME = "Account Administrator";

  public static final Set<String> DEFAULT_USER_GROUPS =
      ImmutableSet.of(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME, DEFAULT_PROD_SUPPORT_USER_GROUP_NAME,
          DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME, DEFAULT_READ_ONLY_USER_GROUP_NAME);

  @NotEmpty @FdIndex private String name;
  private String description;

  private boolean isSsoLinked;
  private SSOType linkedSsoType;
  private String linkedSsoId;
  private String linkedSsoDisplayName;
  private String ssoGroupId;
  private String ssoGroupName;

  private boolean importedByScim;

  private String accountId;
  private List<String> memberIds;

  @Transient private List<User> members;

  private Set<AppPermission> appPermissions;
  private AccountPermissions accountPermissions;

  @Nullable private NotificationSettings notificationSettings;

  private boolean isDefault;

  private Set<UserGroupEntityReference> parents = new HashSet<>();

  // TODO: Should use Builder at the class level itself.
  @Builder
  public UserGroup(String name, String description, String accountId, List<String> memberIds, List<User> members,
      Set<AppPermission> appPermissions, AccountPermissions accountPermissions, String uuid, String appId,
      EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath,
      boolean isSsoLinked, SSOType linkedSsoType, String linkedSsoId, String linkedSsoDisplayName, String ssoGroupId,
      String ssoGroupName, NotificationSettings notificationSettings, boolean isDefault, boolean importedByScim,
      Set<UserGroupEntityReference> parents) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.description = description;
    this.accountId = accountId;
    this.memberIds = memberIds;
    this.members = members;
    this.appPermissions = appPermissions;
    this.accountPermissions = accountPermissions;
    this.isSsoLinked = isSsoLinked;
    this.linkedSsoType = linkedSsoType;
    this.linkedSsoId = linkedSsoId;
    this.linkedSsoDisplayName = linkedSsoDisplayName;
    this.ssoGroupId = ssoGroupId;
    this.ssoGroupName = ssoGroupName;
    this.notificationSettings = notificationSettings;
    this.isDefault = isDefault;
    this.importedByScim = importedByScim;
    this.parents = parents;
  }

  public UserGroup buildUserGroupAudit() {
    List<String> memberEmails = new ArrayList<>();
    if (isNotEmpty(members)) {
      for (User user : members) {
        memberEmails.add(user.getEmail());
      }
    }
    return UserGroup.builder()
        .name(name)
        .description(description)
        .memberIds(memberEmails)
        .appPermissions(appPermissions)
        .accountPermissions(accountPermissions)
        .isSsoLinked(isSsoLinked)
        .linkedSsoType(linkedSsoType)
        .linkedSsoId(linkedSsoId)
        .linkedSsoDisplayName(linkedSsoDisplayName)
        .notificationSettings(notificationSettings)
        .isDefault(isDefault)
        .ssoGroupId(ssoGroupId)
        .ssoGroupName(ssoGroupName)
        .importedByScim(importedByScim)
        .parents(parents)
        .build();
  }

  public UserGroup cloneWithNewName(final String newName, final String newDescription) {
    return UserGroup.builder()
        .uuid(generateUuid())
        .appId(appId)
        .createdBy(null)
        .createdAt(0)
        .lastUpdatedBy(null)
        .lastUpdatedAt(0)
        // @@@ .keywords(getKeywords())
        .entityYamlPath(getEntityYamlPath())
        .name(newName)
        .description(newDescription)
        .accountId(accountId)
        .memberIds(memberIds)
        .members(members)
        .appPermissions(appPermissions)
        .accountPermissions(accountPermissions)
        .isSsoLinked(isSsoLinked)
        .linkedSsoType(linkedSsoType)
        .linkedSsoId(linkedSsoId)
        .linkedSsoDisplayName(linkedSsoDisplayName)
        .notificationSettings(notificationSettings)
        .isDefault(isDefault)
        .ssoGroupId(ssoGroupId)
        .ssoGroupName(ssoGroupName)
        .importedByScim(importedByScim)
        .parents(Collections.emptySet())
        .build();
  }

  public List<User> getMembers() {
    return CollectionUtils.emptyIfNull(members);
  }

  public void addParent(@NotNull UserGroupEntityReference entityReference) {
    parents.add(entityReference);
  }

  public void removeParent(@NotNull UserGroupEntityReference entityReference) {
    parents.remove(entityReference);
  }

  @Override
  @JsonIgnore
  public Map<NotificationChannelType, List<String>> getAddressesByChannelType() {
    return null != notificationSettings ? notificationSettings.getAddressesByChannelType() : Collections.emptyMap();
  }

  @Nullable
  @Override
  @JsonIgnore
  public SlackNotificationSetting getSlackConfig() {
    return null != notificationSettings ? notificationSettings.getSlackConfig() : null;
  }

  @Nullable
  @Override
  @JsonIgnore
  public String getPagerDutyIntegrationKey() {
    return null != notificationSettings ? notificationSettings.getPagerDutyIntegrationKey() : null;
  }

  @Nullable
  @Override
  @JsonIgnore
  public String getMicrosoftTeamsWebhookUrl() {
    return null != notificationSettings ? notificationSettings.getMicrosoftTeamsWebhookUrl() : null;
  }

  @Override
  @JsonIgnore
  public List<String> getEmailAddresses() {
    return null != notificationSettings ? notificationSettings.getEmailAddresses() : Collections.emptyList();
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
        .type(USER_GROUP)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  private boolean hasMember(String userId) {
    return isNotEmpty(memberIds) && memberIds.contains(userId);
  }

  public boolean hasMember(User user) {
    return user != null && hasMember(user.getUuid());
  }

  @UtilityClass
  public static final class UserGroupKeys {
    // Temporary
    public static final String appFilterType = "appPermissions.appFilter.filterType";
    public static final String appIds = "appPermissions.appFilter.ids";
  }
}
