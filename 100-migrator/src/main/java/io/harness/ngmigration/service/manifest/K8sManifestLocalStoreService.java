/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.K8sManifest;
import io.harness.cdng.commons.StoreConfigType;
import io.harness.cdng.commons.StoreConfigWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.service.intfc.ApplicationManifestService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class K8sManifestLocalStoreService implements NgManifestService {
  @Inject ManifestMigrationService manifestMigrationService;
  @Inject ApplicationManifestService applicationManifestService;

  @Override
  public ManifestConfigWrapper getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList) {
    if (EmptyPredicate.isEmpty(yamlFileList)) {
      throw new InvalidRequestException("No manifest files found in inline manifests");
    }

    List<NGYamlFile> manifestFiles = yamlFileList.stream()
                                         .filter(file
                                             -> !MigratorUtility.endsWithIgnoreCase(
                                                 ((FileYamlDTO) file.getYaml()).getName(), "values.yaml", "values.yml"))
                                         .collect(Collectors.toList());
    List<NGYamlFile> valuesFiles = yamlFileList.stream()
                                       .filter(file
                                           -> MigratorUtility.endsWithIgnoreCase(
                                               ((FileYamlDTO) file.getYaml()).getName(), "values.yaml", "values.yml"))
                                       .collect(Collectors.toList());

    K8sManifest k8sManifest =
        K8sManifest.builder()
            .identifier(MigratorUtility.generateManifestIdentifier(applicationManifest.getUuid()))
            .skipResourceVersioning(
                ParameterField.createValueField(applicationManifest.getSkipVersioningForAllK8sObjects()))
            .valuesPaths(MigratorUtility.getFileStorePaths(valuesFiles))
            .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                       .type(StoreConfigType.HARNESS)
                                                       .spec(manifestMigrationService.getHarnessStore(manifestFiles))
                                                       .build()))
            .build();

    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                      .type(ManifestConfigType.K8_MANIFEST)
                      .spec(k8sManifest)
                      .build())
        .build();
  }
}
