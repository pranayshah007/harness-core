/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.security.PermissionAttribute.PermissionType;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.PostLoad;

@StoreIn(DbAliases.HARNESS)
@Entity(value = "roles", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "RoleKeys")
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._970_RBAC_CORE)
public class Role extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("roleType_accountId_1")
                 .field(RoleKeys.roleType)
                 .field(RoleKeys.accountId)
                 .build())
        .build();
  }

  @NotEmpty private String name;
  private String description;
  @NotEmpty private String accountId;

  private String appName;

  private List<Permission> permissions;
  private RoleType roleType;
  private boolean allApps;

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

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
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
   * Gets permissions.
   *
   * @return the permissions
   */
  public List<Permission> getPermissions() {
    return permissions;
  }

  /**
   * Sets permissions.
   *
   * @param permissions the permissions
   */
  public void setPermissions(List<Permission> permissions) {
    this.permissions = permissions;
  }

  /**
   * Adds permission to role.
   *
   * @param permission permission to add.
   */
  public void addPermission(Permission permission) {
    if (permissions == null) {
      permissions = new ArrayList<>();
    }
    permissions.add(permission);
  }

  public RoleType getRoleType() {
    return roleType;
  }

  public void setRoleType(RoleType roleType) {
    this.roleType = roleType;
  }

  public boolean isAllApps() {
    return allApps;
  }

  public void setAllApps(boolean allApps) {
    this.allApps = allApps;
  }

  /**
   * Getter for property 'accountId'.
   *
   * @return Value for property 'accountId'.
   */
  public String getAccountId() {
    return accountId;
  }

  /**
   * Setter for property 'accountId'.
   *
   * @param accountId Value to set for property 'accountId'.
   */
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

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

    Role role = (Role) o;

    if (allApps != role.allApps) {
      return false;
    }
    if (name != null ? !name.equals(role.name) : role.name != null) {
      return false;
    }
    if (description != null ? !description.equals(role.description) : role.description != null) {
      return false;
    }
    if (accountId != null ? !accountId.equals(role.accountId) : role.accountId != null) {
      return false;
    }
    return roleType == role.roleType;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
    result = 31 * result + (roleType != null ? roleType.hashCode() : 0);
    result = 31 * result + (allApps ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Role{"
        + "name='" + name + '\'' + ", description='" + description + '\'' + ", accountId='" + accountId + '\''
        + ", permissions=" + permissions + ", roleType=" + roleType + ", allApps=" + allApps + '}';
  }

  @Override
  public void onSave() {
    super.onSave();

    // No need to save permissions with standard roles
    if (roleType == RoleType.ACCOUNT_ADMIN || roleType == RoleType.APPLICATION_ADMIN
        || roleType == RoleType.PROD_SUPPORT || roleType == RoleType.NON_PROD_SUPPORT) {
      permissions = null;
    }
  }

  @PostLoad
  public void onLoad() {
    // Standard roles
    if (roleType == RoleType.ACCOUNT_ADMIN || roleType == RoleType.APPLICATION_ADMIN
        || roleType == RoleType.PROD_SUPPORT || roleType == RoleType.NON_PROD_SUPPORT) {
      setDescription(roleType.getDescription());
      Permission[] permissionsArray = roleType.getPermissions();
      if (permissionsArray != null) {
        permissions = asList(permissionsArray);
        permissions.forEach(permission -> {
          permission.setAccountId(getAccountId());
          permission.setAppId(getAppId());
          if (permission.getPermissionScope() == PermissionType.ENV && roleType == RoleType.PROD_SUPPORT) {
            permission.setEnvironmentType(EnvironmentType.PROD);
          } else if (permission.getPermissionScope() == PermissionType.ENV && roleType == RoleType.NON_PROD_SUPPORT) {
            permission.setEnvironmentType(EnvironmentType.NON_PROD);
          }
        });
      }
    }
  }

  public static final class Builder {
    private String name;
    private String description;
    private String accountId;
    private List<Permission> permissions;
    private RoleType roleType;
    private boolean allApps;
    private String uuid;
    private String appId;
    private String appName;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;

    private Builder() {}

    public static Builder aRole() {
      return new Builder();
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withPermissions(List<Permission> permissions) {
      this.permissions = permissions;
      return this;
    }

    public Builder withRoleType(RoleType roleType) {
      this.roleType = roleType;
      return this;
    }

    public Builder withAllApps(boolean allApps) {
      this.allApps = allApps;
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

    public Builder withAppName(String appName) {
      this.appName = appName;
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

    public Builder but() {
      return aRole()
          .withName(name)
          .withDescription(description)
          .withAccountId(accountId)
          .withPermissions(permissions)
          .withRoleType(roleType)
          .withUuid(uuid)
          .withAllApps(allApps)
          .withAppId(appId)
          .withAppName(appName)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt);
    }

    public Role build() {
      Role role = new Role();
      role.setName(name);
      role.setDescription(description);
      role.setAccountId(accountId);
      role.setPermissions(permissions);
      role.setRoleType(roleType);
      role.setAllApps(allApps);
      role.setUuid(uuid);
      role.setAppId(appId);
      role.setAppName(appName);
      role.setCreatedBy(createdBy);
      role.setCreatedAt(createdAt);
      role.setLastUpdatedBy(lastUpdatedBy);
      role.setLastUpdatedAt(lastUpdatedAt);
      return role;
    }
  }
}
