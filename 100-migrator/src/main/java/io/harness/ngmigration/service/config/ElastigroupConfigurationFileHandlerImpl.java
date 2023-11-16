/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class ElastigroupConfigurationFileHandlerImpl extends FileHandler<InfrastructureDefinition> {
  private byte[] fileContent;

  public ElastigroupConfigurationFileHandlerImpl(String serviceName, String envName, byte[] fileContent) {
    super(serviceName, envName);
    this.fileContent = fileContent;
  }

  @Override
  public CgBasicInfo getCgBasicInfo(InfrastructureDefinition entity) {
    return CgBasicInfo.builder()
        .accountId(entity.getAccountId())
        .appId(entity.getAppId())
        .id(entity.getUuid())
        .name(entity.getName())
        .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
        .build();
  }

  @Override
  public String getFileUsage() {
    return FileUsage.CONFIG.name();
  }

  @Override
  public String getIdentifier(MigrationContext context, InfrastructureDefinition entity) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    return MigratorUtility.generateManifestIdentifier(
        getPrefix() + entity.getName(), inputDTO.getIdentifierCaseFormat());
  }

  @Override
  public String getName(MigrationContext context, InfrastructureDefinition entity) {
    String name = MigratorUtility.generateName(entity.getName());
    return handleExtension(name);
  }

  @Override
  public String getContent(MigrationContext context, InfrastructureDefinition manifestFile) {
    return new String(fileContent, UTF_8);
  }

  @Override
  public String getFilePath(MigrationContext context, InfrastructureDefinition entity) {
    String filePathPrefix = getFilePathPrefix();
    return StringUtils.isBlank(filePathPrefix) ? "" : (filePathPrefix + getName(context, entity));
  }
}
