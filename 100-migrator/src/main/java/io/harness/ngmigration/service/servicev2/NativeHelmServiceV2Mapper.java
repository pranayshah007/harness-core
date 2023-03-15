/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec.NativeHelmServiceSpecBuilder;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.yaml.core.variables.NGVariable;

import software.wings.beans.Service;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class NativeHelmServiceV2Mapper implements ServiceV2Mapper {
  @Override
  public ServiceDefinition getServiceDefinition(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, Service service, Map<CgEntityId, NGYamlFile> migratedEntities,
      List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<StartupScriptConfiguration> startupScriptConfigurations) {
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(inputDTO, entities, graph, service, migratedEntities);
    NativeHelmServiceSpecBuilder helmServiceSpecBuilder = NativeHelmServiceSpec.builder();
    List<NGVariable> variables = MigratorUtility.getVariables(
        service.getServiceVariables(), migratedEntities, inputDTO.getIdentifierCaseFormat());
    if (primaryArtifact != null) {
      helmServiceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    helmServiceSpecBuilder.manifests(manifests);
    helmServiceSpecBuilder.configFiles(configFiles);
    helmServiceSpecBuilder.variables(variables);
    return ServiceDefinition.builder()
        .type(ServiceDefinitionType.NATIVE_HELM)
        .serviceSpec(helmServiceSpecBuilder.build())
        .build();
  }
}
