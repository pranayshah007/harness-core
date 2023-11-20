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

import software.wings.beans.container.UserDataSpecification;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class AsgStartupScriptFileHandlerImpl extends FileHandler<UserDataSpecification> {
  private byte[] fileContent;

  public AsgStartupScriptFileHandlerImpl(String serviceName, String envName, byte[] fileContent) {
    super(serviceName, envName);
    this.fileContent = fileContent;
  }

  @Override
  public CgBasicInfo getCgBasicInfo(UserDataSpecification entity) {
    return CgBasicInfo.builder()
        .accountId(entity.getAccountId())
        .appId(entity.getAppId())
        .id(entity.getUuid())
        .name(entity.getMigrationEntityName())
        .type(NGMigrationEntityType.AMI_STARTUP_SCRIPT)
        .build();
  }

  @Override
  public String getFileUsage() {
    return FileUsage.CONFIG.name();
  }

  @Override
  public String getIdentifier(MigrationContext context, UserDataSpecification entity) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    return MigratorUtility.generateManifestIdentifier(getPrefix() + serviceName, inputDTO.getIdentifierCaseFormat());
  }

  @Override
  public String getName(MigrationContext context, UserDataSpecification entity) {
    String name = MigratorUtility.generateName(serviceName + ".sh");
    return handleExtension(name);
  }

  @Override
  public String getContent(MigrationContext context, UserDataSpecification manifestFile) {
    return new String(fileContent, UTF_8);
  }

  @Override
  public String getFilePath(MigrationContext context, UserDataSpecification entity) {
    String filePathPrefix = getFilePathPrefix();
    return StringUtils.isBlank(filePathPrefix) ? "" : (filePathPrefix + getName(context, entity));
  }
}
