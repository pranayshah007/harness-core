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
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class K8sManifestRemoteStoreService implements NgManifestService {
  @Inject ManifestMigrationService manifestMigrationService;

  @Override
  public List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList) {
    // TODO: get store from migrated connector entity
    GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
    NgEntityDetail connector = NgManifestFactory.getGitConnector(migratedEntities, applicationManifest);
    if (connector == null) {
      return Collections.emptyList();
    }

    // Note: Special case handling for ECS task services
    if (StringUtils.isNotBlank(gitFileConfig.getServiceSpecFilePath())
        || StringUtils.isNotBlank(gitFileConfig.getTaskSpecFilePath())) {
      return handleForEcs(applicationManifest, gitFileConfig, connector);
    }

    K8sManifest k8sManifest =
        K8sManifest
            .builder()
            // TODO: There needs to be a logic to build identifier of the manifest
            .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
            .skipResourceVersioning(
                ParameterField.createValueField(applicationManifest.getSkipVersioningForAllK8sObjects()))
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder()
                    .type(StoreConfigType.GIT)
                    .spec(manifestMigrationService.getGitStore(gitFileConfig, entitySpec, connector))
                    .build()))
            .build();
    return Collections.singletonList(
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder()
                          .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                          .type(ManifestConfigType.K8_MANIFEST)
                          .spec(k8sManifest)
                          .build())
            .build());
  }

  // Note: Special case handling for ECS task services
  private List<ManifestConfigWrapper> handleForEcs(
      ApplicationManifest applicationManifest, GitFileConfig gitFileConfig, NgEntityDetail connector) {
    List<ManifestConfigWrapper> manifests = new ArrayList<>();
    if (StringUtils.isNotBlank(gitFileConfig.getTaskSpecFilePath())) {
      final String identifier = "ecsTaskDefinition";
      manifests.add(ManifestConfigWrapper.builder()
                        .manifest(ManifestConfig.builder()
                                      .identifier("ecsTaskDefinition")
                                      .type(ManifestConfigType.ECS_TASK_DEFINITION)
                                      .spec(getManifest(
                                          gitFileConfig, connector, gitFileConfig.getTaskSpecFilePath(), identifier))
                                      .build())
                        .build());
    }
    if (StringUtils.isNotBlank(gitFileConfig.getServiceSpecFilePath())) {
      final String identifier = "ecsServiceDefinition";
      manifests.add(ManifestConfigWrapper.builder()
                        .manifest(ManifestConfig.builder()
                                      .identifier(identifier)
                                      .type(ManifestConfigType.ECS_SERVICE_DEFINITION)
                                      .spec(getManifest(
                                          gitFileConfig, connector, gitFileConfig.getServiceSpecFilePath(), identifier))
                                      .build())
                        .build());
    }
    return manifests;
  }

  // Note: Special case handling for ECS task services
  private static K8sManifest getManifest(
      GitFileConfig gitFileConfig, NgEntityDetail connector, String path, String identifier) {
    GitStore gitStore =
        GitStore.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
            .gitFetchType(gitFileConfig.isUseBranch() ? FetchType.BRANCH : FetchType.COMMIT)
            .paths(ParameterField.createValueField(Collections.singletonList(path)))
            .build();

    if (StringUtils.isNotBlank(gitFileConfig.getCommitId())) {
      gitStore.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
    }

    if (StringUtils.isNotBlank(gitFileConfig.getBranch())) {
      gitStore.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
    }

    return K8sManifest.builder()
        .identifier(identifier)
        .store(ParameterField.createValueField(
            StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build()))
        .build();
  }
}
