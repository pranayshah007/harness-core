/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class K8sManifestHelmSourceRepoStoreService implements NgManifestService {
  @Inject ManifestMigrationService manifestMigrationService;

  @Override
  public List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList, CaseFormat identifierCaseFormat) {
    GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
    NgEntityDetail connector = NgManifestFactory.getGitConnector(migratedEntities, applicationManifest);
    if (connector == null) {
      return Collections.emptyList();
    }

    Service service =
        (Service) entities
            .get(
                CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(applicationManifest.getServiceId()).build())
            .getEntity();
    GitStore gitStore = manifestMigrationService.getGitStore(gitFileConfig, entitySpec, connector);
    String dirPath = StringUtils.isBlank(applicationManifest.getGitFileConfig().getFilePath())
        ? "/"
        : applicationManifest.getGitFileConfig().getFilePath();
    gitStore.setPaths(null);
    gitStore.setFolderPath(ParameterField.createValueField(dirPath));
    HelmChartManifest helmChartManifest =
        HelmChartManifest.builder()
            .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid(), identifierCaseFormat))
            .skipResourceVersioning(ParameterField.createValueField(
                Boolean.TRUE.equals(applicationManifest.getSkipVersioningForAllK8sObjects())))
            .helmVersion(service.getHelmVersion())
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build()))
            .build();

    helmChartManifest.setCommandFlags(getCommandFlags(applicationManifest));

    return Collections.singletonList(ManifestConfigWrapper.builder()
                                         .manifest(ManifestConfig.builder()
                                                       .identifier(MigratorUtility.generateIdentifier(
                                                           applicationManifest.getUuid(), identifierCaseFormat))
                                                       .type(ManifestConfigType.HELM_CHART)
                                                       .spec(helmChartManifest)
                                                       .build())
                                         .build());
  }
}
