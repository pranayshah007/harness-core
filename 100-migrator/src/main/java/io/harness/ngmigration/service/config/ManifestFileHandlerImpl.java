/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.config;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
    String kind = handleKind(applicationManifest);
    if (StringUtils.isNotBlank(kind)) {
      identifier = MigratorUtility.generateManifestIdentifier(
          prefix + kind + manifestFile.getFileName(), inputDTO.getIdentifierCaseFormat());
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
    String fileName = manifestFile.getFileName();
    if (fileName.contains("/")) {
      fileName = Lists.reverse(Lists.newArrayList(fileName.split("/"))).get(0);
    }
    String name =
        MigratorUtility.generateManifestIdentifier(prefix + " " + fileName, inputDTO.getIdentifierCaseFormat());
    String kind = handleKind(applicationManifest);
    if (StringUtils.isNotBlank(kind)) {
      name = MigratorUtility.generateManifestIdentifier(prefix + kind + fileName, inputDTO.getIdentifierCaseFormat());
    }
    return handleExtension(name);
  }

  private static String handleKind(ApplicationManifest applicationManifest) {
    if (applicationManifest.getKind().equals(AppManifestKind.VALUES)) {
      return " ValuesOverride ";
    }
    if (applicationManifest.getKind().equals(AppManifestKind.OC_PARAMS)) {
      return " OpenShift ";
    }
    if (applicationManifest.getKind().equals(AppManifestKind.KUSTOMIZE_PATCHES)) {
      return " Kustomize ";
    }
    return null;
  }

  @Override
  public String getContent(MigrationContext context, ManifestFile manifestFile) {
    return (String) MigratorExpressionUtils.render(
        context, manifestFile.getFileContent(), context.getInputDTO().getCustomExpressions());
  }

  @Override
  public String getFilePath(MigrationContext context, ManifestFile entity) {
    String filePathPrefix = getFilePathPrefix();
    String[] folders = entity.getFileName().split("/");
    if (folders.length > 1) {
      folders = Arrays.copyOfRange(folders, 0, folders.length - 1);
      for (String folder : folders) {
        filePathPrefix = String.format("%s%s/", filePathPrefix, folder);
      }
    }
    return StringUtils.isBlank(filePathPrefix) ? "" : (filePathPrefix + getName(context, entity));
  }

  @Override
  public List<NGYamlFile> getFolders(MigrationContext context, ManifestFile manifestFile) {
    String[] folders = manifestFile.getFileName().split("/");
    if (folders.length <= 1) {
      return Collections.emptyList();
    }
    folders = Arrays.copyOfRange(folders, 0, folders.length - 1);
    if (EmptyPredicate.isEmpty(folders)) {
      return Collections.emptyList();
    }
    List<FileYamlDTO> yamlDTOS = new ArrayList<>();
    String rootIdentifier = super.getRootIdentifier(context, manifestFile);
    String pathPrefix = getFilePathPrefix();
    pathPrefix = StringUtils.isBlank(pathPrefix) ? "/" : pathPrefix;
    int i = 1;
    for (String folder : folders) {
      String identifier = getIdentifierFolder(context, rootIdentifier, folder);
      yamlDTOS.add(FileYamlDTO.builder()
                       .identifier(identifier)
                       .fileUsage("FOLDER")
                       .name(folder)
                       .rootIdentifier(rootIdentifier)
                       .filePath(pathPrefix + folder)
                       .depth(i)
                       .orgIdentifier(getOrgIdentifier(context))
                       .projectIdentifier(getProjectIdentifier(context))
                       .build());

      i++;
      rootIdentifier = identifier;
      pathPrefix = String.format("%s%s/", pathPrefix, folder);
    }

    return yamlDTOS.stream()
        .map(yamlDTO
            -> NGYamlFile.builder()
                   .type(NGMigrationEntityType.FILE_STORE)
                   .filename(null)
                   .yaml(yamlDTO)
                   .ngEntityDetail(NgEntityDetail.builder()
                                       .entityType(NGMigrationEntityType.FILE_STORE)
                                       .identifier(yamlDTO.getIdentifier())
                                       .orgIdentifier(yamlDTO.getOrgIdentifier())
                                       .projectIdentifier(yamlDTO.getProjectIdentifier())
                                       .build())
                   .build())
        .collect(Collectors.toList());
  }

  static String getIdentifierFolder(MigrationContext context, String rootIdentifier, String folder) {
    return MigratorUtility.generateIdentifier(
        rootIdentifier + " " + folder + " Dir", context.getInputDTO().getIdentifierCaseFormat());
  }

  @Override
  public String getRootIdentifier(MigrationContext context, ManifestFile manifestFile) {
    String rootIdentifier = super.getRootIdentifier(context, manifestFile);
    if (Objects.equals(rootIdentifier, "Root")) {
      return rootIdentifier;
    }

    MigrationInputDTO inputDTO = context.getInputDTO();
    if (StringUtils.isNotBlank(serviceName) && StringUtils.isBlank(envName)) {
      String fileName = manifestFile.getFileName();
      String[] folders = fileName.split("/");
      if (folders.length > 1) {
        rootIdentifier = MigratorUtility.generateIdentifier(serviceName, inputDTO.getIdentifierCaseFormat());
        folders = Arrays.copyOfRange(folders, 0, folders.length - 1);
        String pathPrefix = getFilePathPrefix();
        pathPrefix = StringUtils.isBlank(pathPrefix) ? "/" : pathPrefix;
        for (String folder : folders) {
          rootIdentifier = getIdentifierFolder(context, rootIdentifier, folder);
          pathPrefix = String.format("%s%s/", pathPrefix, folder);
        }
      }
    }
    return rootIdentifier;
  }
}
