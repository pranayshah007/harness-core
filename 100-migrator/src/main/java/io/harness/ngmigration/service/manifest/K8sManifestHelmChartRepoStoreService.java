/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest.HelmChartManifestBuilder;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class K8sManifestHelmChartRepoStoreService implements NgManifestService {
  @Inject ManifestMigrationService manifestMigrationService;

  @Override
  public List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList, CaseFormat identifierCaseFormat) {
    HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();
    CgEntityId connectorId =
        CgEntityId.builder().type(NGMigrationEntityType.CONNECTOR).id(helmChartConfig.getConnectorId()).build();
    if (!migratedEntities.containsKey(connectorId)) {
      log.error(
          String.format("We could not migrate the following manifest %s as we could not find the helm connector %s",
              applicationManifest.getUuid(), helmChartConfig.getConnectorId()));
      return Collections.emptyList();
    }
    SettingAttribute settingAttribute = (SettingAttribute) entities.get(connectorId).getEntity();
    String name = StringUtils.isBlank(applicationManifest.getName()) ? applicationManifest.getUuid()
                                                                     : applicationManifest.getName();

    Service service =
        (Service) entities
            .get(
                CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(applicationManifest.getServiceId()).build())
            .getEntity();
    String identifier = MigratorUtility.generateIdentifier(name, identifierCaseFormat);
    HelmChartManifestBuilder helmChartManifest =
        HelmChartManifest.builder()
            .identifier(identifier)
            .helmVersion(service.getHelmVersion())
            .skipResourceVersioning(ParameterField.createValueField(
                Boolean.TRUE.equals(applicationManifest.getSkipVersioningForAllK8sObjects())))
            .chartName(ParameterField.createValueField(helmChartConfig.getChartName()))
            .chartVersion(ParameterField.createValueField("<+input>"));

    helmChartManifest.commandFlags(getCommandFlags(applicationManifest));

    if (HTTP_HELM_REPO.equals(settingAttribute.getValue().getSettingType())) {
      NGYamlFile connectorYamlFile = migratedEntities.get(
          CgEntityId.builder().id(settingAttribute.getUuid()).type(NGMigrationEntityType.CONNECTOR).build());
      NgEntityDetail connector = connectorYamlFile.getNgEntityDetail();
      helmChartManifest
          .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                     .type(StoreConfigType.HTTP)
                                                     .spec(HttpStoreConfig.builder()
                                                               .connectorRef(ParameterField.createValueField(
                                                                   MigratorUtility.getIdentifierWithScope(connector)))
                                                               .build())
                                                     .build()))
          .build();
    }
    if (settingAttribute.getValue().getSettingType().equals(AMAZON_S3_HELM_REPO)) {
      AmazonS3HelmRepoConfig repoConfig = (AmazonS3HelmRepoConfig) settingAttribute.getValue();
      NGYamlFile connectorYamlFile = migratedEntities.get(
          CgEntityId.builder().id(repoConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build());
      helmChartManifest
          .store(ParameterField.createValueField(
              StoreConfigWrapper.builder()
                  .type(StoreConfigType.S3)
                  .spec(S3StoreConfig.builder()
                            .connectorRef(ParameterField.createValueField(
                                MigratorUtility.getIdentifierWithScope(connectorYamlFile.getNgEntityDetail())))
                            .region(ParameterField.createValueField(repoConfig.getRegion()))
                            .bucketName(ParameterField.createValueField(repoConfig.getBucketName()))
                            .folderPath(ParameterField.createValueField(helmChartConfig.getBasePath()))
                            .build())
                  .build()))
          .build();
    }

    if (settingAttribute.getValue().getSettingType().equals(GCS_HELM_REPO)) {
      GCSHelmRepoConfig repoConfig = (GCSHelmRepoConfig) settingAttribute.getValue();
      NGYamlFile connectorYamlFile = migratedEntities.get(
          CgEntityId.builder().id(repoConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build());
      helmChartManifest
          .store(ParameterField.createValueField(
              StoreConfigWrapper.builder()
                  .type(StoreConfigType.GCS)
                  .spec(GcsStoreConfig.builder()
                            .connectorRef(ParameterField.createValueField(
                                MigratorUtility.getIdentifierWithScope(connectorYamlFile.getNgEntityDetail())))
                            .bucketName(ParameterField.createValueField(repoConfig.getBucketName()))
                            .folderPath(ParameterField.createValueField(helmChartConfig.getBasePath()))
                            .build())
                  .build()))
          .build();
    }

    return Collections.singletonList(ManifestConfigWrapper.builder()
                                         .manifest(ManifestConfig.builder()
                                                       .identifier(identifier)
                                                       .type(ManifestConfigType.HELM_CHART)
                                                       .spec(helmChartManifest.build())
                                                       .build())
                                         .build());
  }
}
