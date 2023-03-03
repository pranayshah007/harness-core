/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.ngmigration.NGMigrationEntityType.APPLICATION;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.LogKeyUtils;
import io.harness.persistence.NameAccess;

import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.entityinterface.TagAware;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.yaml.BaseEntityYaml;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Application bean class.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(_957_CG_BEANS)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "applications", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "ApplicationKeys")
public class Application extends Base implements KeywordsAware, NameAccess, TagAware, AccountAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("yaml")
                 .field(ApplicationKeys.accountId)
                 .field(ApplicationKeys.name)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdCreatedAt")
                 .field(ApplicationKeys.accountId)
                 .descSortField(ApplicationKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("rbacCoverIndex")
                 .field(ApplicationKeys.accountId)
                 .field(ApplicationKeys.uuid)
                 .build())
        .build();
  }

  public static final String LOG_KEY_FOR_ID = LogKeyUtils.calculateLogKeyForId(Application.class);

  @Override
  public String logKeyForId() {
    return LOG_KEY_FOR_ID;
  }

  @NotEmpty private String name;
  private String description;

  @NotEmpty private String accountId;

  @Transient private List<Service> services = new ArrayList<>();
  @Transient private List<Environment> environments = new ArrayList<>();

  private transient Setup setup;
  @Transient private List<WorkflowExecution> recentExecutions;
  @Transient private List<Notification> notifications;
  @Transient private long nextDeploymentOn;
  @Getter @Setter private Set<String> keywords;

  @Getter @Setter private transient YamlGitConfig yamlGitConfig;

  private transient Map<String, String> defaults = new HashMap<>();
  private boolean sample;

  @Getter @Setter private Boolean isManualTriggerAuthorized;
  @Getter @Setter private Boolean areWebHookSecretsMandated;
  @Getter @Setter private Boolean disableTriggers;

  public boolean isSample() {
    return sample;
  }

  public void setSample(boolean sample) {
    this.sample = sample;
  }

  public Map<String, String> getDefaults() {
    return defaults;
  }

  public void setDefaults(Map<String, String> defaults) {
    this.defaults = defaults;
  }

  @Getter @Setter private transient List<HarnessTagLink> tagLinks;

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
        .type(APPLICATION)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

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

  /**
   * Gets services.
   *
   * @return the services
   */
  public List<Service> getServices() {
    return services;
  }

  /**
   * Sets services.
   *
   * @param services the services
   */
  public void setServices(List<Service> services) {
    if (services == null) {
      this.services = new ArrayList<>();
      return;
    }
    this.services = services;
  }

  /**
   * Gets environments.
   *
   * @return the environments
   */
  public List<Environment> getEnvironments() {
    return environments;
  }

  /**
   * Sets environments.
   *
   * @param environments the environments
   */
  public void setEnvironments(List<Environment> environments) {
    if (environments == null) {
      this.environments = new ArrayList<>();
      return;
    }
    this.environments = environments;
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

  /**
   * Gets recent executions.
   *
   * @return the recent executions
   */
  public List<WorkflowExecution> getRecentExecutions() {
    return recentExecutions;
  }

  /**
   * Sets recent executions.
   *
   * @param recentExecutions the recent executions
   */
  public void setRecentExecutions(List<WorkflowExecution> recentExecutions) {
    this.recentExecutions = recentExecutions;
  }

  /**
   * Gets notifications.
   *
   * @return the notifications
   */
  public List<Notification> getNotifications() {
    return notifications;
  }

  /**
   * Sets notifications.
   *
   * @param notifications the notifications
   */
  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  /**
   * Gets next deployment on.
   *
   * @return the next deployment on
   */
  public long getNextDeploymentOn() {
    return nextDeploymentOn;
  }

  /**
   * Sets next deployment on.
   *
   * @param nextDeploymentOn the next deployment on
   */
  public void setNextDeploymentOn(long nextDeploymentOn) {
    this.nextDeploymentOn = nextDeploymentOn;
  }

  /**
   * Getter for property 'accountId'.
   *
   * @return Value for property 'accountId'.
   */
  @Override
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
  public int hashCode() {
    return 31 * super.hashCode()
        + Objects.hash(
            name, description, accountId, services, environments, recentExecutions, notifications, nextDeploymentOn);
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
    final Application other = (Application) obj;
    return Objects.equals(this.name, other.name) && Objects.equals(this.description, other.description)
        && Objects.equals(this.accountId, other.accountId) && Objects.equals(this.services, other.services)
        && Objects.equals(this.environments, other.environments)
        && Objects.equals(this.recentExecutions, other.recentExecutions)
        && Objects.equals(this.notifications, other.notifications)
        && Objects.equals(this.nextDeploymentOn, other.nextDeploymentOn);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("description", description)
        .add("accountId", accountId)
        .add("services", services)
        .add("environments", environments)
        .add("setup", setup)
        .add("recentExecutions", recentExecutions)
        .add("notifications", notifications)
        .add("nextDeploymentOn", nextDeploymentOn)
        .add("yamlGitConfig", yamlGitConfig)
        .toString();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private String description;
    private String accountId;
    private List<Service> services = new ArrayList<>();
    private List<Environment> environments = new ArrayList<>();
    private Setup setup;
    private List<WorkflowExecution> recentExecutions;
    private List<Notification> notifications;
    private long nextDeploymentOn;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private Map<String, String> defaults;
    private YamlGitConfig yamlGitConfig;
    private boolean sample;
    private Boolean isManualTriggerAuthorized;
    private Boolean areWebHookSecretsMandated;
    private Boolean disableTriggers;

    private Builder() {}

    public static Builder anApplication() {
      return new Builder();
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder services(List<Service> services) {
      this.services = services;
      return this;
    }

    public Builder environments(List<Environment> environments) {
      this.environments = environments;
      return this;
    }

    public Builder setup(Setup setup) {
      this.setup = setup;
      return this;
    }

    public Builder recentExecutions(List<WorkflowExecution> recentExecutions) {
      this.recentExecutions = recentExecutions;
      return this;
    }

    public Builder notifications(List<Notification> notifications) {
      this.notifications = notifications;
      return this;
    }

    public Builder nextDeploymentOn(long nextDeploymentOn) {
      this.nextDeploymentOn = nextDeploymentOn;
      return this;
    }

    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder defaults(Map<String, String> defaults) {
      this.defaults = defaults;
      return this;
    }

    public Builder yamlGitConfig(YamlGitConfig yamlGitConfig) {
      this.yamlGitConfig = yamlGitConfig;
      return this;
    }

    public Builder sample(boolean sample) {
      this.sample = sample;
      return this;
    }

    public Builder isManualTriggerAuthorized(Boolean isManualTriggerAuthorized) {
      this.isManualTriggerAuthorized = isManualTriggerAuthorized;
      return this;
    }

    public Builder areWebHookSecretsMandated(Boolean areWebHookSecretsMandated) {
      this.areWebHookSecretsMandated = areWebHookSecretsMandated;
      return this;
    }

    public Builder disableTriggers(Boolean disableTriggers) {
      this.disableTriggers = disableTriggers;
      return this;
    }

    public Builder but() {
      return anApplication()
          .name(name)
          .description(description)
          .accountId(accountId)
          .services(services)
          .environments(environments)
          .setup(setup)
          .recentExecutions(recentExecutions)
          .notifications(notifications)
          .nextDeploymentOn(nextDeploymentOn)
          .uuid(uuid)
          .appId(appId)
          .createdBy(createdBy)
          .createdAt(createdAt)
          .lastUpdatedBy(lastUpdatedBy)
          .lastUpdatedAt(lastUpdatedAt)
          .yamlGitConfig(yamlGitConfig)
          .sample(sample)
          .isManualTriggerAuthorized(isManualTriggerAuthorized)
          .areWebHookSecretsMandated(areWebHookSecretsMandated)
          .disableTriggers(disableTriggers);
    }

    public Application build() {
      Application application = new Application();
      application.setName(name);
      application.setDescription(description);
      application.setAccountId(accountId);
      application.setServices(services);
      application.setEnvironments(environments);
      application.setSetup(setup);
      application.setRecentExecutions(recentExecutions);
      application.setNotifications(notifications);
      application.setNextDeploymentOn(nextDeploymentOn);
      application.setUuid(uuid);
      application.setAppId(appId);
      application.setCreatedBy(createdBy);
      application.setCreatedAt(createdAt);
      application.setLastUpdatedBy(lastUpdatedBy);
      application.setLastUpdatedAt(lastUpdatedAt);
      application.setDefaults(defaults);
      application.setYamlGitConfig(yamlGitConfig);
      application.setSample(sample);
      application.setIsManualTriggerAuthorized(isManualTriggerAuthorized);
      application.setAreWebHookSecretsMandated(areWebHookSecretsMandated);
      application.setDisableTriggers(disableTriggers);
      return application;
    }
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> kw = KeywordsAware.super.generateKeywords();
    kw.addAll(asList(name, description));
    return kw;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private String description;
    private Boolean isManualTriggerAuthorized;
    private Boolean areWebHookSecretsMandated;
    private Boolean disableTriggers;
    private Boolean isGitSyncEnabled;
    private String gitConnector;
    private String branchName;
    private String repoName;

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, String description, Boolean isGitSyncEnabled,
        String gitConnector, String branchName, String repoName) {
      super(type, harnessApiVersion);
      this.description = description;
      this.isGitSyncEnabled = isGitSyncEnabled;
      this.gitConnector = gitConnector;
      this.branchName = branchName;
      this.repoName = repoName;
    }
  }

  @UtilityClass
  public static final class ApplicationKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
  }
}
