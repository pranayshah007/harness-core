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
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import org.apache.commons.lang3.StringUtils;

public class ManifestFileHandlerImpl extends FileHandler<ManifestFile> {
  private ApplicationManifest applicationManifest;

  public ManifestFileHandlerImpl(String serviceName, String envName, ApplicationManifest applicationManifest) {
    super(serviceName, envName);
    this.applicationManifest = applicationManifest;
  }

  @Override
  public CgBasicInfo getCgBasicInfo(ManifestFile entity) {
    return CgBasicInfo.builder()
        .accountId(applicationManifest.getAccountId())
        .appId(applicationManifest.getAppId())
        .id(applicationManifest.getUuid())
        .name(applicationManifest.getName())
        .type(NGMigrationEntityType.MANIFEST)
        .build();
  }

  @Override
  public String getFileUsage() {
    return FileUsage.MANIFEST_FILE.name();
  }

  @Override
  public String getIdentifier(MigrationContext context, ManifestFile manifestFile) {
    String prefix = getPrefix();
    MigrationInputDTO inputDTO = context.getInputDTO();
    String identifier = MigratorUtility.generateManifestIdentifier(
        prefix + manifestFile.getFileName(), inputDTO.getIdentifierCaseFormat());
    if (applicationManifest.getKind().equals(AppManifestKind.VALUES)) {
      identifier = MigratorUtility.generateManifestIdentifier(
          prefix + " ValuesOverride " + manifestFile.getFileName(), inputDTO.getIdentifierCaseFormat());
    }
    return identifier;
  }

  @Override
  public String getName(MigrationContext context, ManifestFile manifestFile) {
    String prefix = "";
    if (StringUtils.isNoneBlank(this.serviceName, this.envName)) {
      prefix = getPrefix();
    }
    MigrationInputDTO inputDTO = context.getInputDTO();
    String name = MigratorUtility.generateManifestIdentifier(
        prefix + " " + manifestFile.getFileName(), inputDTO.getIdentifierCaseFormat());
    if (applicationManifest.getKind().equals(AppManifestKind.VALUES)) {
      name = MigratorUtility.generateManifestIdentifier(
          prefix + " ValuesOverride " + manifestFile.getFileName(), inputDTO.getIdentifierCaseFormat());
    }
    return handleExtension(name);
  }

  @Override
  public String getContent(MigrationContext context, ManifestFile manifestFile) {
    return (String) MigratorExpressionUtils.render(
        context, manifestFile.getFileContent(), context.getInputDTO().getCustomExpressions());
  }

  @Override
  public String getFilePath(MigrationContext context, ManifestFile entity) {
    String filePathPrefix = getFilePathPrefix();
    return StringUtils.isBlank(filePathPrefix) ? "" : (filePathPrefix + getName(context, entity));
  }
}
