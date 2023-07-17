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
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.manifest.CustomSourceConfig;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class K8sManifestCustomStoreService implements NgManifestService {
  @Inject ManifestMigrationService manifestMigrationService;

  @Override
  public List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList, CaseFormat identifierCaseFormat) {
    CustomSourceConfig customSourceConfig = applicationManifest.getCustomSourceConfig();
    K8sManifest k8sManifest =
        K8sManifest.builder()
            .identifier(MigratorUtility.generateManifestIdentifier(applicationManifest.getUuid(), identifierCaseFormat))
            .skipResourceVersioning(ParameterField.createValueField(
                Boolean.TRUE.equals(applicationManifest.getSkipVersioningForAllK8sObjects())))
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder()
                    .type(StoreConfigType.CUSTOM_REMOTE)
                    .spec(CustomRemoteStoreConfig.builder()
                              .extractionScript(ParameterField.createValueField(customSourceConfig.getScript()))
                              .filePath(ParameterField.createValueField(customSourceConfig.getPath()))
                              .delegateSelectors(
                                  MigratorUtility.getDelegateSelectors(customSourceConfig.getDelegateSelectors()))
                              .build())
                    .build()))
            .build();

    return Collections.singletonList(ManifestConfigWrapper.builder()
                                         .manifest(ManifestConfig.builder()
                                                       .identifier(MigratorUtility.generateIdentifier(
                                                           applicationManifest.getUuid(), identifierCaseFormat))
                                                       .type(ManifestConfigType.K8_MANIFEST)
                                                       .spec(k8sManifest)
                                                       .build())
                                         .build());
  }
}
