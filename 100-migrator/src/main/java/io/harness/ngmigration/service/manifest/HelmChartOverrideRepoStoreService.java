/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.HelmChartConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class HelmChartOverrideRepoStoreService implements NgManifestService {
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
    NGYamlFile connectorYamlFile = migratedEntities.get(connectorId);
    SettingAttribute settingAttribute = (SettingAttribute) entities.get(connectorId).getEntity();

    String type = ManifestStoreType.HTTP;
    if (AMAZON_S3_HELM_REPO.equals(settingAttribute.getValue().getSettingType())) {
      type = ManifestStoreType.S3;
    }
    if (GCS_HELM_REPO.equals(settingAttribute.getValue().getSettingType())) {
      type = ManifestStoreType.GCS;
    }
    if (OCI_HELM_REPO.equals(settingAttribute.getValue().getSettingType())) {
      type = ManifestStoreType.OCI;
    }
    HelmRepoOverrideManifest helmRepoOverride =
        HelmRepoOverrideManifest.builder()
            .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid(), identifierCaseFormat))
            .connectorRef(ParameterField.createValueField(
                MigratorUtility.getIdentifierWithScope(connectorYamlFile.getNgEntityDetail())))
            .type(type)
            .build();
    return Collections.singletonList(ManifestConfigWrapper.builder()
                                         .manifest(ManifestConfig.builder()
                                                       .identifier(MigratorUtility.generateIdentifier(
                                                           applicationManifest.getUuid(), identifierCaseFormat))
                                                       .type(ManifestConfigType.HELM_REPO_OVERRIDE)
                                                       .spec(helmRepoOverride)
                                                       .build())
                                         .build());
  }
}
