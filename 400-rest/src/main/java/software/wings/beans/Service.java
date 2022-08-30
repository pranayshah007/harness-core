/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.k8s.model.HelmVersion;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.pcf.model.CfCliVersion;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.api.DeploymentType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.entityinterface.TagAware;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeAware;
import software.wings.utils.ArtifactType;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.annotations.Version;

/**
 * Component bean class.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "ServiceKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "services", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(_957_CG_BEANS)
public class Service extends Base
    implements KeywordsAware, NameAccess, TagAware, AccountAccess, CustomDeploymentTypeAware, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(ServiceKeys.appId)
                 .field(ServiceKeys.name)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountCreatedAtIndex")
                 .field(ServiceKeys.accountId)
                 .descSortField(ServiceKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_deploymentTypeIndex")
                 .field(ServiceKeys.accountId)
                 .field(ServiceKeys.deploymentType)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_name")
                 .field(ServiceKeys.accountId)
                 .ascSortField(ServiceKeys.name)
                 .build())
        .build();
  }
  public static final String GLOBAL_SERVICE_NAME_FOR_YAML = "__all_service__";
  public static final String APP_ID = "appId";
  public static final String ID = "_id";

  @Trimmed(message = "should not contain leading and trailing spaces") @EntityName @NotEmpty private String name;
  private String description;
  private ArtifactType artifactType;
  private DeploymentType deploymentType;
  private String serviceId;
  private String configMapYaml;
  private String helmValueYaml;

  @Version private long version;

  @Reference(idOnly = true, ignoreMissing = true) private AppContainer appContainer;

  @Transient private List<ConfigFile> configFiles = new ArrayList<>();
  @Transient private List<ServiceVariable> serviceVariables = new ArrayList<>();
  @Transient private List<ArtifactStream> artifactStreams = new ArrayList<>();
  @Transient private List<ServiceCommand> serviceCommands = new ArrayList<>();

  @Transient private Activity lastDeploymentActivity;
  @Transient private Activity lastProdDeploymentActivity;
  @Transient private Setup setup;

  @SchemaIgnore private Set<String> keywords;

  private boolean isK8sV2;
  private boolean isPcfV2;
  private HelmVersion helmVersion;
  private CfCliVersion cfCliVersion;
  @FdIndex private String accountId;
  @FdIndex private List<String> artifactStreamIds;
  @Transient private List<ArtifactStreamBinding> artifactStreamBindings;
  private boolean sample;

  private transient List<HarnessTagLink> tagLinks;

  private String deploymentTypeTemplateId;
  private transient String customDeploymentName;
  private Boolean artifactFromManifest;

  @Builder
  public Service(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, Set<String> keywords, String entityYamlPath, String name, String description,
      ArtifactType artifactType, DeploymentType deploymentType, String configMapYaml, String helmValueYaml,
      long version, AppContainer appContainer, List<ConfigFile> configFiles, List<ServiceVariable> serviceVariables,
      List<ArtifactStream> artifactStreams, List<ServiceCommand> serviceCommands, Activity lastDeploymentActivity,
      Activity lastProdDeploymentActivity, Setup setup, boolean isK8sV2, String accountId,
      List<String> artifactStreamIds, boolean sample, boolean isPcfV2, HelmVersion helmVersion,
      CfCliVersion cfCliVersion, String deploymentTypeTemplateId, String customDeploymentName,
      Boolean artifactFromManifest) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.description = description;
    this.artifactType = artifactType;
    this.deploymentType = deploymentType;
    this.configMapYaml = configMapYaml;
    this.helmValueYaml = helmValueYaml;
    this.version = version;
    this.appContainer = appContainer;
    this.configFiles = configFiles == null ? new ArrayList<>() : configFiles;
    this.serviceVariables = serviceVariables == null ? new ArrayList<>() : serviceVariables;
    this.artifactStreams = artifactStreams == null ? new ArrayList<>() : artifactStreams;
    this.serviceCommands = serviceCommands == null ? new ArrayList<>() : serviceCommands;
    this.lastDeploymentActivity = lastDeploymentActivity;
    this.lastProdDeploymentActivity = lastProdDeploymentActivity;
    this.setup = setup;
    this.keywords = keywords;
    this.isK8sV2 = isK8sV2;
    this.isPcfV2 = isPcfV2;
    this.accountId = accountId;
    this.artifactStreamIds = artifactStreamIds;
    this.sample = sample;
    this.helmVersion = helmVersion;
    this.cfCliVersion = cfCliVersion;
    this.deploymentTypeTemplateId = deploymentTypeTemplateId;
    this.customDeploymentName = customDeploymentName;
    this.artifactFromManifest = artifactFromManifest;
  }

  // TODO: check what to do with artifactStreamIds and artifactStreamBindings
  public Service cloneInternal() {
    return Service.builder()
        .appId(getAppId())
        .accountId(getAccountId())
        .name(name)
        .description(description)
        .artifactType(artifactType)
        .deploymentType(deploymentType)
        .configMapYaml(configMapYaml)
        .helmValueYaml(helmValueYaml)
        .appContainer(appContainer)
        .isK8sV2(isK8sV2)
        .isPcfV2(isPcfV2)
        .helmVersion(helmVersion)
        .cfCliVersion(cfCliVersion)
        .deploymentTypeTemplateId(deploymentTypeTemplateId)
        .customDeploymentName(customDeploymentName)
        .artifactFromManifest(artifactFromManifest)
        .build();
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description));
    if (artifactType != null) {
      keywords.add(artifactType.name());
    }
    return keywords;
  }

  @Override
  public void setDeploymentTypeName(String theCustomDeploymentName) {
    customDeploymentName = theCustomDeploymentName;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private String description;
    private String artifactType;
    private String deploymentType;
    private String configMapYaml;
    private String applicationStack;
    private String helmVersion;
    private Boolean artifactFromManifest;
    private String cfCliVersion;
    private List<NameValuePair.Yaml> configVariables = new ArrayList<>();

    /*
     Support for Custom Deployment
      */
    private String deploymentTypeTemplateUri;
    private String deploymentTypeTemplateVersion;

    @lombok.Builder
    public Yaml(String harnessApiVersion, String description, String artifactType, String deploymentType,
        String configMapYaml, String applicationStack, List<NameValuePair.Yaml> configVariables, String helmVersion,
        String cfCliVersion, String deploymentTypeTemplateUri, String deploymentTypeTemplateVersion,
        Boolean artifactFromManifest) {
      super(EntityType.SERVICE.name(), harnessApiVersion);
      this.description = description;
      this.artifactType = artifactType;
      this.deploymentType = deploymentType;
      this.configMapYaml = configMapYaml;
      this.applicationStack = applicationStack;
      this.configVariables = configVariables;
      this.helmVersion = helmVersion;
      this.cfCliVersion = cfCliVersion;
      this.deploymentTypeTemplateUri = deploymentTypeTemplateUri;
      this.deploymentTypeTemplateVersion = deploymentTypeTemplateVersion;
      this.artifactFromManifest = artifactFromManifest;
    }
  }

  @UtilityClass
  public static final class ServiceKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String deploymentType = "deploymentType";
    public static final String serviceId = "serviceId";
    public static final String accountId = "accountId";
  }
}
