/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static software.wings.ngmigration.NGMigrationEntityType.SECRET;

import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.secrets.SecretFactory;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import retrofit2.Response;

@Slf4j
public class MigratorUtility {
  public static String generateManifestIdentifier(String name) {
    return generateIdentifier(name);
  }

  public static String generateIdentifier(String name) {
    String generated = CaseUtils.toCamelCase(name.replaceAll("[^A-Za-z0-9]", " ").trim(), false, ' ');
    return Character.isDigit(generated.charAt(0)) ? "_" + generated : generated;
  }

  public static ParameterField<String> getParameterField(String value) {
    if (StringUtils.isBlank(value)) {
      return ParameterField.createValueField("");
    }
    return ParameterField.createValueField(value);
  }

  public static void sort(List<NGYamlFile> files) {
    files.sort(Comparator.comparingInt(MigratorUtility::toInt));
  }

  // This is for sorting entities while creating
  private static int toInt(NGYamlFile file) {
    switch (file.getType()) {
      case APPLICATION:
        return 0;
      case SECRET_MANAGER:
        return 1;
      case SECRET:
        return SecretFactory.isStoredInHarnessSecretManager(file) ? Integer.MIN_VALUE : 5;
      case CONNECTOR:
        return 10;
      case MANIFEST:
        return 15;
      case SERVICE:
        return 20;
      case ENVIRONMENT:
        return 25;
      case INFRA:
        return 35;
      case PIPELINE:
        return 50;
      default:
        throw new InvalidArgumentsException("Unknown type found: " + file.getType());
    }
  }

  public static Scope getDefaultScope(Map<NGMigrationEntityType, InputDefaults> inputDefaultsMap,
      NGMigrationEntityType entityType, Scope defaultScope) {
    if (inputDefaultsMap == null || !inputDefaultsMap.containsKey(entityType)) {
      return defaultScope;
    }
    return inputDefaultsMap.get(entityType).getScope() != null ? inputDefaultsMap.get(entityType).getScope()
                                                               : defaultScope;
  }

  public static Scope getScope(NgEntityDetail entityDetail) {
    String orgId = entityDetail.getOrgIdentifier();
    String projectId = entityDetail.getProjectIdentifier();
    if (StringUtils.isAllBlank(orgId, projectId)) {
      return Scope.ACCOUNT;
    }
    if (StringUtils.isNotBlank(projectId)) {
      return Scope.PROJECT;
    }
    return Scope.ORG;
  }

  public static SecretRefData getSecretRef(Map<CgEntityId, NGYamlFile> migratedEntities, String secretId) {
    if (secretId == null) {
      return null;
    }
    CgEntityId secretEntityId = CgEntityId.builder().id(secretId).type(SECRET).build();
    if (!migratedEntities.containsKey(secretEntityId)) {
      return SecretRefData.builder().identifier("__PLEASE_FIX_ME__").scope(Scope.PROJECT).build();
    }
    NgEntityDetail migratedSecret = migratedEntities.get(secretEntityId).getNgEntityDetail();
    return SecretRefData.builder()
        .identifier(migratedSecret.getIdentifier())
        .scope(MigratorUtility.getScope(migratedSecret))
        .build();
  }

  public static String getIdentifierWithScope(NgEntityDetail entityDetail) {
    String orgId = entityDetail.getOrgIdentifier();
    String projectId = entityDetail.getProjectIdentifier();
    String identifier = entityDetail.getIdentifier();
    if (StringUtils.isAllBlank(orgId, projectId)) {
      return "account." + identifier;
    }
    if (StringUtils.isNotBlank(projectId)) {
      return identifier;
    }
    return "org." + identifier;
  }

  public static List<NGVariable> getVariables(
      List<ServiceVariable> serviceVariables, Map<CgEntityId, NGYamlFile> migratedEntities) {
    List<NGVariable> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(serviceVariables)) {
      serviceVariables.forEach(serviceVariable -> {
        if (serviceVariable.getType().equals(ServiceVariableType.ENCRYPTED_TEXT)) {
          variables.add(SecretNGVariable.builder()
                            .type(NGVariableType.SECRET)
                            .value(ParameterField.createValueField(
                                MigratorUtility.getSecretRef(migratedEntities, serviceVariable.getEncryptedValue())))
                            .name(serviceVariable.getName())
                            .build());
        } else {
          variables.add(StringNGVariable.builder()
                            .type(NGVariableType.STRING)
                            .name(serviceVariable.getName())
                            .value(ParameterField.createValueField(String.valueOf(serviceVariable.getValue())))
                            .build());
        }
      });
    }
    return variables;
  }

  public static void createFile(String auth, NGClient ngClient, MigrationInputDTO inputDTO, FileYamlDTO fileYamlDTO) {
    RequestBody identifier = RequestBody.create(MediaType.parse("text/plain"), fileYamlDTO.getIdentifier());
    RequestBody name = RequestBody.create(MediaType.parse("text/plain"), fileYamlDTO.getName());
    RequestBody fileUsage = RequestBody.create(MediaType.parse("text/plain"), fileYamlDTO.getFileUsage());
    RequestBody type = RequestBody.create(MediaType.parse("text/plain"), "FILE");
    RequestBody parentIdentifier = RequestBody.create(MediaType.parse("text/plain"), "Root");
    RequestBody mimeType = RequestBody.create(MediaType.parse("text/plain"), "txt");
    RequestBody content = RequestBody.create(MediaType.parse("application/octet-stream"), fileYamlDTO.getContent());

    Response<ResponseDTO<FileDTO>> resp = null;
    try {
      resp = ngClient
                 .createFileInFileStore(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                     inputDTO.getProjectIdentifier(), content, name, identifier, fileUsage, type, parentIdentifier,
                     mimeType)
                 .execute();
      log.info("Connector creation Response details {} {}", resp.code(), resp.message());
    } catch (IOException e) {
      log.error("Failed to create file", e);
    }
  }
}
