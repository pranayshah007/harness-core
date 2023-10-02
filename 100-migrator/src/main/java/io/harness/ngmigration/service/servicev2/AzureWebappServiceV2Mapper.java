/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import static io.harness.cdng.manifest.ManifestConfigType.K8_MANIFEST;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.azure.config.yaml.StartupCommandConfiguration;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec.AzureWebAppServiceSpecBuilder;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.Service;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.service.intfc.WorkflowService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class AzureWebappServiceV2Mapper implements ServiceV2Mapper {
  @Override
  public ServiceDefinition getServiceDefinition(WorkflowService workflowService, MigrationContext migrationContext,
      Service service, List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<StartupScriptConfiguration> startupScriptConfigurations) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, Set<CgEntityId>> graph = migrationContext.getGraph();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(inputDTO, entities, graph, service, migratedEntities);
    AzureWebAppServiceSpecBuilder webAppServiceSpecBuilder = AzureWebAppServiceSpec.builder();
    if (primaryArtifact != null) {
      webAppServiceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    webAppServiceSpecBuilder.variables(
        MigratorUtility.getServiceVariables(migrationContext, service.getServiceVariables()));
    webAppServiceSpecBuilder.configFiles(configFiles);
    webAppServiceSpecBuilder.startupCommand(getStartupCommand(startupScriptConfigurations));
    webAppServiceSpecBuilder.applicationSettings(getApplicationSettings(manifests));
    webAppServiceSpecBuilder.connectionStrings(getConnectionStrings(manifests));

    return ServiceDefinition.builder()
        .type(ServiceDefinitionType.AZURE_WEBAPP)
        .serviceSpec(webAppServiceSpecBuilder.build())
        .build();
  }

  private ConnectionStringsConfiguration getConnectionStrings(List<ManifestConfigWrapper> manifests) {
    ConnectionStringsConfiguration result = null;
    if (isNotEmpty(manifests)) {
      ManifestConfig manifest = manifests.get(0).getManifest();
      StoreConfig clonedStoreConfig = getStoreConfig(manifest, 1);

      result = ConnectionStringsConfiguration.builder()
                   .store(StoreConfigWrapper.builder()
                              .type(manifest.getType() == K8_MANIFEST ? StoreConfigType.GIT : StoreConfigType.HARNESS)
                              .spec(clonedStoreConfig)
                              .build())
                   .build();
    }
    return result;
  }

  private ApplicationSettingsConfiguration getApplicationSettings(List<ManifestConfigWrapper> manifests) {
    ApplicationSettingsConfiguration result = null;
    if (isNotEmpty(manifests)) {
      ManifestConfig manifest = manifests.get(0).getManifest();
      StoreConfig clonedStoreConfig = getStoreConfig(manifest, 0);

      result = ApplicationSettingsConfiguration.builder()
                   .store(StoreConfigWrapper.builder()
                              .type(manifest.getType() == K8_MANIFEST ? StoreConfigType.GIT : StoreConfigType.HARNESS)
                              .spec(clonedStoreConfig)
                              .build())
                   .build();
    }
    return result;
  }

  @NotNull
  private static StoreConfig getStoreConfig(ManifestConfig manifest, int index) {
    StoreConfig storeConfig = manifest.getSpec().getStoreConfig();
    StoreConfig clonedStoreConfig = storeConfig.cloneInternal();

    if (manifest.getType() == K8_MANIFEST) {
      ParameterField<List<String>> paths = ((GitStore) storeConfig).getPaths();
      ((GitStore) clonedStoreConfig)
          .setPaths(ParameterField.createValueField(Collections.singletonList(paths.getValue().get(index))));
    } else {
      ParameterField<List<String>> files = ((HarnessStore) storeConfig).getFiles();
      ((HarnessStore) clonedStoreConfig)
          .setFiles(ParameterField.createValueField(Collections.singletonList(files.getValue().get(index))));
    }
    return clonedStoreConfig;
  }

  private StartupCommandConfiguration getStartupCommand(List<StartupScriptConfiguration> startupScriptConfigurations) {
    StartupCommandConfiguration result = null;
    if (isNotEmpty(startupScriptConfigurations) && null != startupScriptConfigurations.get(0).getStore()) {
      StoreConfigWrapper store = startupScriptConfigurations.get(0).getStore();
      result = StartupCommandConfiguration.builder().store(store).build();
    }
    return result;
  }
}
