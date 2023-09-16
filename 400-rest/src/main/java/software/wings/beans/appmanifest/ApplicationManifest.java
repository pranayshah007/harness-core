/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;

import static java.lang.String.join;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.manifest.CustomSourceConfig;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.beans.GitFileConfig;
import software.wings.beans.helm.HelmChartConfig;
import software.wings.beans.helm.HelmChartConfig.HelmChartConfigKeys;
import software.wings.beans.helm.HelmCommandFlagConfig;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ApplicationManifestKeys")
@StoreIn(DbAliases.HARNESS)
@Entity("applicationManifests")
@HarnessEntity(exportable = true)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class ApplicationManifest extends Base implements AccountAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appManifestIdx2")
                 .field(BaseKeys.appId)
                 .field(ApplicationManifestKeys.envId)
                 .field(ApplicationManifestKeys.serviceId)
                 .field(ApplicationManifestKeys.kind)
                 .build(),
            CompoundMongoIndex.builder()
                .name("accountId_connectorId")
                .field(ApplicationManifestKeys.accountId)
                .field(join(".", ApplicationManifestKeys.helmChartConfig, HelmChartConfigKeys.connectorId))
                .build())
        .build();
  }

  public static final String ID = "_id";
  public static final String CREATED_AT = "createdAt";

  private String accountId;
  private String serviceId;
  private String envId;
  private String name;
  private AppManifestKind kind;
  @NonNull private StoreType storeType;
  private GitFileConfig gitFileConfig;
  private HelmChartConfig helmChartConfig;
  private KustomizeConfig kustomizeConfig;
  private CustomSourceConfig customSourceConfig;
  @Nullable private HelmCommandFlagConfig helmCommandFlag;
  private String helmValuesYamlFilePaths;

  private Boolean pollForChanges;
  @Transient private String serviceName;

  public enum ManifestCollectionStatus { UNSTABLE, COLLECTING, STABLE }
  private ManifestCollectionStatus collectionStatus;
  private String perpetualTaskId;
  private int failedAttempts;
  private Boolean skipVersioningForAllK8sObjects;
  private String validationMessage;
  private Boolean enableCollection;

  public ApplicationManifest cloneInternal() {
    ApplicationManifest manifest = ApplicationManifest.builder()
                                       .accountId(this.accountId)
                                       .serviceId(this.serviceId)
                                       .envId(this.envId)
                                       .storeType(this.storeType)
                                       .gitFileConfig(this.gitFileConfig)
                                       .kind(this.kind)
                                       .helmChartConfig(helmChartConfig)
                                       .kustomizeConfig(KustomizeConfig.cloneFrom(this.kustomizeConfig))
                                       .customSourceConfig(CustomSourceConfig.cloneFrom(this.customSourceConfig))
                                       .pollForChanges(this.pollForChanges)
                                       .skipVersioningForAllK8sObjects(this.skipVersioningForAllK8sObjects)
                                       .helmCommandFlag(HelmCommandFlagConfig.cloneFrom(this.helmCommandFlag))
                                       .helmValuesYamlFilePaths(this.helmValuesYamlFilePaths)
                                       .enableCollection(enableCollection)
                                       .name(this.name)
                                       .build();
    manifest.setAppId(this.appId);
    return manifest;
  }

  @Override
  public String getMigrationEntityName() {
    return getUuid();
  }

  @JsonIgnore
  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .id(getUuid())
        .name(getName())
        .type(MANIFEST)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  public enum AppManifestSource { SERVICE, ENV, ENV_SERVICE }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static final class Yaml extends BaseEntityYaml {
    private String storeType;
    private GitFileConfig gitFileConfig;
    private HelmChartConfig helmChartConfig;
    private KustomizeConfig kustomizeConfig;
    private CustomSourceConfig customSourceConfig;
    private Boolean skipVersioningForAllK8sObjects;
    private HelmCommandFlagConfig helmCommandFlag;
    private String helmValuesYamlFilePaths;
    private Boolean enableCollection;

    @Builder
    public Yaml(String type, String harnessApiVersion, String storeType, GitFileConfig gitFileConfig,
        HelmChartConfig helmChartConfig, KustomizeConfig kustomizeConfig, CustomSourceConfig customSourceConfig,
        HelmCommandFlagConfig helmCommandFlag, Boolean skipVersioningForAllK8sObjects, String helmValuesYamlFilePaths,
        Boolean enableCollection) {
      super(type, harnessApiVersion);
      this.storeType = storeType;
      this.gitFileConfig = gitFileConfig;
      this.helmChartConfig = helmChartConfig;
      this.kustomizeConfig = kustomizeConfig;
      this.customSourceConfig = customSourceConfig;
      this.skipVersioningForAllK8sObjects = skipVersioningForAllK8sObjects;
      this.helmCommandFlag = helmCommandFlag;
      this.helmValuesYamlFilePaths = helmValuesYamlFilePaths;
      this.enableCollection = enableCollection;
    }
  }
}
