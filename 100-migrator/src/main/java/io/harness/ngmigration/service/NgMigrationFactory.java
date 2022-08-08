/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.ngmigration.service.entity.AccountMigrationService;
import io.harness.ngmigration.service.entity.AppMigrationService;
import io.harness.ngmigration.service.entity.ArtifactStreamMigrationService;
import io.harness.ngmigration.service.entity.ConnectorMigrationService;
import io.harness.ngmigration.service.entity.DummyMigrationService;
import io.harness.ngmigration.service.entity.EnvironmentMigrationService;
import io.harness.ngmigration.service.entity.InfraMigrationService;
import io.harness.ngmigration.service.entity.ManifestMigrationService;
import io.harness.ngmigration.service.entity.PipelineMigrationService;
import io.harness.ngmigration.service.entity.SecretManagerMigrationService;
import io.harness.ngmigration.service.entity.SecretMigrationService;
import io.harness.ngmigration.service.entity.ServiceMigrationService;
import io.harness.ngmigration.service.entity.TemplateMigrationService;
import io.harness.ngmigration.service.entity.WorkflowMigrationService;

import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;

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
      default:
        throw new IllegalStateException();
    }
  }
}
