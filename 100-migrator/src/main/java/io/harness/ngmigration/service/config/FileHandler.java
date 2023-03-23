/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.config;

import io.harness.encryption.Scope;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public abstract class FileHandler<T> {
  protected String serviceName;
  protected String envName;

  FileHandler(String serviceName, String envName) {
    this.serviceName = serviceName;
    this.envName = envName;
  }

  public String getRootIdentifier(MigrationContext context, T entity) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    String rootIdentifier = "Root";
    if (StringUtils.isNotBlank(serviceName) && StringUtils.isBlank(envName)) {
      rootIdentifier = MigratorUtility.generateIdentifier(serviceName, inputDTO.getIdentifierCaseFormat());
    }
    if (StringUtils.isBlank(serviceName) && StringUtils.isNotBlank(envName)) {
      rootIdentifier = MigratorUtility.generateIdentifier(envName, inputDTO.getIdentifierCaseFormat());
    }
    return rootIdentifier;
  }

  public String getFilePathPrefix() {
    String filePathPrefix = "";
    if (StringUtils.isNotBlank(serviceName) && StringUtils.isBlank(envName)) {
      filePathPrefix = String.format("/%s/", serviceName);
    }
    if (StringUtils.isBlank(serviceName) && StringUtils.isNotBlank(envName)) {
      filePathPrefix = String.format("/%s/", envName);
    }
    return filePathPrefix;
  }

  public String getPrefix() {
    StringBuilder prefixBuilder = new StringBuilder();
    if (StringUtils.isNotBlank(envName)) {
      prefixBuilder.append(envName).append(' ');
    }
    if (StringUtils.isNotBlank(serviceName)) {
      prefixBuilder.append(serviceName).append(' ');
    }
    return prefixBuilder.toString();
  }

  public String getProjectIdentifier(MigrationContext context) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    return MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
  }

  public String getOrgIdentifier(MigrationContext context) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    return MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
  }

  public NgEntityDetail getNGEntityDetail(MigrationContext context, T entity) {
    return NgEntityDetail.builder()
        .entityType(NGMigrationEntityType.FILE_STORE)
        .identifier(getIdentifier(context, entity))
        .orgIdentifier(getOrgIdentifier(context))
        .projectIdentifier(getProjectIdentifier(context))
        .build();
  }

  public FileYamlDTO getFileYamlDTO(MigrationContext context, T entity) {
    return FileYamlDTO.builder()
        .identifier(getIdentifier(context, entity))
        .fileUsage(getFileUsage())
        .name(getName(context, entity))
        .content(getContent(context, entity))
        .rootIdentifier(getRootIdentifier(context, entity))
        .filePath(getFilePath(context, entity))
        .depth(Integer.MAX_VALUE)
        .orgIdentifier(getOrgIdentifier(context))
        .projectIdentifier(getProjectIdentifier(context))
        .build();
  }

  public List<NGYamlFile> getFolders(MigrationContext context, T entity) {
    return new ArrayList<>();
  }

  public abstract CgBasicInfo getCgBasicInfo(T entity);

  public abstract String getFileUsage();

  public abstract String getIdentifier(MigrationContext context, T entity);

  public abstract String getName(MigrationContext context, T entity);

  public abstract String getContent(MigrationContext context, T entity);

  public abstract String getFilePath(MigrationContext context, T entity);

  protected String handleExtension(String name) {
    if (MigratorUtility.endsWithIgnoreCase(name, "yaml", "yml")) {
      name = MigratorUtility.endsWithIgnoreCase(name, "yaml") ? name.substring(0, name.length() - 4) + ".yaml"
                                                              : name.substring(0, name.length() - 3) + ".yml";
    }
    if (MigratorUtility.endsWithIgnoreCase(name, "json")) {
      name = name.substring(0, name.length() - 4) + ".json";
    }
    return name;
  }
}
