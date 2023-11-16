/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.ELASTIC_GROUP_ACCOUNT_IDS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.AsgServiceSpec;
import io.harness.cdng.service.beans.AsgServiceSpec.AsgServiceSpecBuilder;
import io.harness.cdng.service.beans.ElastigroupServiceSpec;
import io.harness.cdng.service.beans.ElastigroupServiceSpec.ElastigroupServiceSpecBuilder;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.WorkflowService;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class AmiServiceV2Mapper implements ServiceV2Mapper {
  @Override
  public ServiceDefinition getServiceDefinition(WorkflowService workflowService, MigrationContext migrationContext,
      Service service, List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<NGYamlFile> startupScriptConfigurations) {
    if (ELASTIC_GROUP_ACCOUNT_IDS.contains(migrationContext.getAccountId())) {
      WorkflowExecution workflowExecution = workflowService.getLastSuccessfulWorkflowExecution(
          service.getAccountId(), service.getAppId(), service.getUuid());
      if (workflowExecution != null) {
        Workflow workflow = workflowService.readWorkflow(service.getAppId(), workflowExecution.getWorkflowId());
        List<GraphNode> steps = MigratorUtility.getSteps(workflow);
        if (isNotEmpty(steps)) {
          for (GraphNode step : steps) {
            if (ServiceV2Factory.checkForASG(step.getType())) {
              return getAsgServiceDefinition(
                  migrationContext, service, manifests, configFiles, startupScriptConfigurations);
            }
          }
        }
      }
      return getElasticGroupServiceDefinition(
          migrationContext, service, manifests, configFiles, startupScriptConfigurations);
    }
    return getAsgServiceDefinition(migrationContext, service, manifests, configFiles, startupScriptConfigurations);
  }

  private ServiceDefinition getAsgServiceDefinition(MigrationContext migrationContext, Service service,
      List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<NGYamlFile> startupScriptConfigurations) {
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(migrationContext.getInputDTO(),
        migrationContext.getEntities(), migrationContext.getGraph(), service, migrationContext.getMigratedEntities());
    AsgServiceSpecBuilder asgServiceSpecBuilder = AsgServiceSpec.builder();
    if (primaryArtifact != null) {
      asgServiceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    if (isNotEmpty(manifests)) {
      asgServiceSpecBuilder.manifests(changeIdentifier(manifests, "asg_"));
    }
    if (isNotEmpty(startupScriptConfigurations)) {
      asgServiceSpecBuilder.userData(getUserDataConfigFileWrapper(startupScriptConfigurations.get(0)));
    }
    asgServiceSpecBuilder.variables(
        MigratorUtility.getServiceVariables(migrationContext, service.getServiceVariables()));
    asgServiceSpecBuilder.configFiles(configFiles);
    return ServiceDefinition.builder()
        .type(ServiceDefinitionType.ASG)
        .serviceSpec(asgServiceSpecBuilder.build())
        .build();
  }

  private ServiceDefinition getElasticGroupServiceDefinition(MigrationContext migrationContext, Service service,
      List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<NGYamlFile> startupScriptConfigurationFiles) {
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(migrationContext.getInputDTO(),
        migrationContext.getEntities(), migrationContext.getGraph(), service, migrationContext.getMigratedEntities());
    ElastigroupServiceSpecBuilder elastigroupServiceSpecBuilder = ElastigroupServiceSpec.builder();
    if (primaryArtifact != null) {
      elastigroupServiceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    if (isNotEmpty(manifests)) {
      elastigroupServiceSpecBuilder.manifests(changeIdentifier(manifests, "ecs_group_"));
    }
    elastigroupServiceSpecBuilder.variables(
        MigratorUtility.getServiceVariables(migrationContext, service.getServiceVariables()));
    elastigroupServiceSpecBuilder.configFiles(configFiles);
    if (isNotEmpty(startupScriptConfigurationFiles)) {
      elastigroupServiceSpecBuilder.startupScript(getConfigFileWrapper(startupScriptConfigurationFiles.get(0)));
    }

    return ServiceDefinition.builder()
        .type(ServiceDefinitionType.ELASTIGROUP)
        .serviceSpec(elastigroupServiceSpecBuilder.build())
        .build();
  }
}
