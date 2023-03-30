/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.service.entity.AccountMigrationService;
import io.harness.ngmigration.service.entity.AmiStartupScriptMigrationService;
import io.harness.ngmigration.service.entity.AppMigrationService;
import io.harness.ngmigration.service.entity.ArtifactStreamMigrationService;
import io.harness.ngmigration.service.entity.ConfigFileMigrationService;
import io.harness.ngmigration.service.entity.ConnectorMigrationService;
import io.harness.ngmigration.service.entity.ContainerTaskMigrationService;
import io.harness.ngmigration.service.entity.DummyMigrationService;
import io.harness.ngmigration.service.entity.EcsServiceSpecMigrationService;
import io.harness.ngmigration.service.entity.ElastigroupConfigurationMigrationService;
import io.harness.ngmigration.service.entity.EnvironmentMigrationService;
import io.harness.ngmigration.service.entity.FileStoreMigrationService;
import io.harness.ngmigration.service.entity.InfraMigrationService;
import io.harness.ngmigration.service.entity.InfraProvisionerMigrationService;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.service.entity.PipelineMigrationService;
import io.harness.ngmigration.service.entity.SecretManagerMigrationService;
import io.harness.ngmigration.service.entity.SecretManagerTemplateMigrationService;
import io.harness.ngmigration.service.entity.SecretMigrationService;
import io.harness.ngmigration.service.entity.ServiceCommandTemplateMigrationService;
import io.harness.ngmigration.service.entity.ServiceMigrationService;
import io.harness.ngmigration.service.entity.ServiceVariableMigrationService;
import io.harness.ngmigration.service.entity.TemplateMigrationService;
import io.harness.ngmigration.service.entity.TriggerMigrationService;
import io.harness.ngmigration.service.entity.UserGroupMigrationService;
import io.harness.ngmigration.service.entity.WorkflowMigrationService;

import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDC)
public class NgMigrationFactory {
  @Inject PipelineMigrationService pipelineMigrationService;
  @Inject WorkflowMigrationService workflowMigrationService;
  @Inject ConnectorMigrationService connectorMigrationService;
  @Inject ServiceMigrationService serviceMigrationService;
  @Inject ArtifactStreamMigrationService artifactStreamMigrationService;
  @Inject SecretMigrationService secretMigrationService;
  @Inject SecretManagerMigrationService secretManagerMigrationService;
  @Inject EnvironmentMigrationService environmentMigrationService;
  @Inject InfraMigrationService infraMigrationService;
  @Inject ManifestMigrationService manifestMigrationService;
  @Inject DummyMigrationService dummyMigrationService;
  @Inject AppMigrationService appMigrationService;
  @Inject AccountMigrationService accountMigrationService;
  @Inject TemplateMigrationService templateMigrationService;
  @Inject ServiceVariableMigrationService serviceVariableMigrationService;
  @Inject ConfigFileMigrationService configFileMigrationService;
  @Inject EcsServiceSpecMigrationService ecsServiceSpecMigrationService;
  @Inject AmiStartupScriptMigrationService amiServiceSpecMigrationService;
  @Inject ElastigroupConfigurationMigrationService elastigroupConfigurationMigrationService;
  @Inject ContainerTaskMigrationService containerTaskMigrationService;
  @Inject ServiceCommandTemplateMigrationService serviceCommandTemplateMigrationService;
  @Inject InfraProvisionerMigrationService infraProvisionerMigrationService;
  @Inject TriggerMigrationService triggerMigrationService;
  @Inject SecretManagerTemplateMigrationService secretManagerTemplateMigrationService;
  @Inject UserGroupMigrationService userGroupMigrationService;
  @Inject FileStoreMigrationService fileStoreMigrationService;

  public NgMigrationService getMethod(NGMigrationEntityType type) {
    switch (type) {
      case DUMMY_HEAD:
        return dummyMigrationService;
      case ACCOUNT:
        return accountMigrationService;
      case APPLICATION:
        return appMigrationService;
      case PIPELINE:
        return pipelineMigrationService;
      case WORKFLOW:
        return workflowMigrationService;
      case CONNECTOR:
        return connectorMigrationService;
      case TEMPLATE:
        return templateMigrationService;
      case SERVICE_COMMAND_TEMPLATE:
        return serviceCommandTemplateMigrationService;
      case SERVICE:
        return serviceMigrationService;
      case ARTIFACT_STREAM:
        return artifactStreamMigrationService;
      case SECRET:
        return secretMigrationService;
      case SECRET_MANAGER:
        return secretManagerMigrationService;
      case ENVIRONMENT:
        return environmentMigrationService;
      case INFRA:
        return infraMigrationService;
      case MANIFEST:
        return manifestMigrationService;
      case SERVICE_VARIABLE:
        return serviceVariableMigrationService;
      case CONFIG_FILE:
        return configFileMigrationService;
      case ECS_SERVICE_SPEC:
        return ecsServiceSpecMigrationService;
      case AMI_STARTUP_SCRIPT:
        return amiServiceSpecMigrationService;
      case ELASTIGROUP_CONFIGURATION:
        return elastigroupConfigurationMigrationService;
      case CONTAINER_TASK:
        return containerTaskMigrationService;
      case INFRA_PROVISIONER:
        return infraProvisionerMigrationService;
      case TRIGGER:
        return triggerMigrationService;
      case SECRET_MANAGER_TEMPLATE:
        return secretManagerTemplateMigrationService;
      case USER_GROUP:
        return userGroupMigrationService;
      case FILE_STORE:
        return fileStoreMigrationService;
      default:
        throw new IllegalStateException();
    }
  }
}
