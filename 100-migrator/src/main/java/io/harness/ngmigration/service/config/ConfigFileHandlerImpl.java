/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.config;

import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.ConfigFile;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import org.apache.commons.lang3.StringUtils;

public class ConfigFileHandlerImpl extends FileHandler<ConfigFile> {
  private byte[] fileContent;

  public ConfigFileHandlerImpl(String serviceName, String envName, byte[] fileContent) {
    super(serviceName, envName);
    this.fileContent = fileContent;
  }

  @Override
  public CgBasicInfo getCgBasicInfo(ConfigFile entity) {
    return CgBasicInfo.builder()
        .accountId(entity.getAccountId())
        .appId(entity.getAppId())
        .id(entity.getUuid())
        .name(entity.getName())
        .type(NGMigrationEntityType.CONFIG_FILE)
        .build();
  }

  @Override
  public String getFileUsage() {
    return FileUsage.CONFIG.name();
  }

  @Override
  public String getIdentifier(MigrationContext context, ConfigFile configFile) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    return MigratorUtility.generateManifestIdentifier(
        getPrefix() + configFile.getRelativeFilePath(), inputDTO.getIdentifierCaseFormat());
  }

  @Override
  public String getName(MigrationContext context, ConfigFile configFile) {
    String name = MigratorUtility.generateName(configFile.getRelativeFilePath());
    return handleExtension(name);
  }

  @Override
  public String getContent(MigrationContext context, ConfigFile manifestFile) {
    return new String(fileContent);
  }

  @Override
  public String getFilePath(MigrationContext context, ConfigFile entity) {
    String filePathPrefix = getFilePathPrefix();
    return StringUtils.isBlank(filePathPrefix) ? "" : (filePathPrefix + getName(context, entity));
  }
}
