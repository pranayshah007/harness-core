/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.TanzuApplicationServiceSpec;
import io.harness.cdng.service.beans.TanzuApplicationServiceSpec.TanzuApplicationServiceSpecBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.Service;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.service.intfc.WorkflowService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class PcfServiceV2Mapper implements ServiceV2Mapper {
  @Override
  public ServiceDefinition getServiceDefinition(WorkflowService workflowService, MigrationContext migrationContext,
      Service service, List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<NGYamlFile> startupScriptConfigurationFiles) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, Set<CgEntityId>> graph = migrationContext.getGraph();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(inputDTO, entities, graph, service, migratedEntities);

    TanzuApplicationServiceSpecBuilder serviceSpecBuilder = TanzuApplicationServiceSpec.builder();

    if (primaryArtifact != null) {
      serviceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    if (EmptyPredicate.isNotEmpty(manifests)) {
      serviceSpecBuilder.manifests(changeIdentifier(manifests, "pcf_"));
    }

    serviceSpecBuilder.variables(MigratorUtility.getServiceVariables(migrationContext, service.getServiceVariables()));
    serviceSpecBuilder.configFiles(configFiles);

    return ServiceDefinition.builder().type(ServiceDefinitionType.TAS).serviceSpec(serviceSpecBuilder.build()).build();
  }
}
