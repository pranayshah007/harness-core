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
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.TasManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class TanzuManifestRemoteStoreService implements NgManifestService {
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

    TasManifest tasManifest =
        TasManifest.builder()
            .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid(), identifierCaseFormat))
            .cfCliVersion(CfCliVersionNG.V7)
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder()
                    .type(StoreConfigType.GIT)
                    .spec(manifestMigrationService.getGitStore(gitFileConfig, entitySpec, connector))
                    .build()))
            .build();
    return Collections.singletonList(ManifestConfigWrapper.builder()
                                         .manifest(ManifestConfig.builder()
                                                       .identifier(MigratorUtility.generateIdentifier(
                                                           applicationManifest.getUuid(), identifierCaseFormat))
                                                       .type(ManifestConfigType.TAS_MANIFEST)
                                                       .spec(tasManifest)
                                                       .build())
                                         .build());
  }
}
