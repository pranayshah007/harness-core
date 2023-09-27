/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static io.serializer.HObjectMapper.configureObjectMapperForNG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.persistence.NameAccess;
import io.harness.serializer.jackson.PipelineJacksonModule;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import com.google.inject.Inject;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
public abstract class NgMigrationService {
  public static final MediaType TEXT_PLAIN = MediaType.parse("text/plain");
  public static final ObjectMapper MIGRATION_DEFAULT_OBJECT_MAPPER =
      configureObjectMapperForNG(Jackson.newObjectMapper()).registerModule(new PipelineJacksonModule());

  @Inject MigratorMappingService migratorMappingService;

  public abstract MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile);

  public abstract DiscoveryNode discover(NGMigrationEntity entity);

  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    return new BaseSummary(entities.size());
  }

  public abstract DiscoveryNode discover(String accountId, String appId, String entityId);

  public static String getYamlString(NGYamlFile yamlFile) {
    final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES).enable(Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS))
                                         .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                         .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                                         .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                         .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
                                         .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                                         .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
                                         .enable(SerializationFeature.INDENT_OUTPUT);
    YAML_MAPPER.registerModule(new PipelineJacksonModule());
    return NGYamlUtils.getYamlString(yamlFile.getYaml(), MIGRATION_DEFAULT_OBJECT_MAPPER, YAML_MAPPER);
  }

  public static String getYamlStringV2(NGYamlFile yamlFile) {
    return getYamlStringV2(yamlFile.getYaml());
  }

  public static String getYamlStringV2(YamlDTO yamlFile) {
    final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory().enable(Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS))
                                         .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                         .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                                         .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                         .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
                                         .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                                         .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
                                         .enable(SerializationFeature.INDENT_OUTPUT);
    YAML_MAPPER.registerModule(new PipelineJacksonModule());
    return NGYamlUtils.getYamlString(yamlFile, MIGRATION_DEFAULT_OBJECT_MAPPER, YAML_MAPPER);
  }

  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return null;
  }

  public abstract YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId);

  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean canMigrateAll) {
    return canMigrateAll;
  }

  public NGYamlFile getExistingYaml(MigrationContext context, CgEntityId entityId) {
    MigrationInputDTO inputDTO = context.getInputDTO();
    Map<CgEntityId, CgEntityNode> entities = context.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = context.getMigratedEntities();
    CgEntityNode cgEntityNode = entities.get(entityId);
    CgBasicInfo cgBasicInfo = cgEntityNode.getEntity().getCgBasicInfo();
    NgEntityDetail ngEntityDetail = getNGEntityDetail(inputDTO, entities, entityId);
    boolean mappingExist = migratorMappingService.doesMappingExist(cgBasicInfo, ngEntityDetail);
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .ngEntityDetail(ngEntityDetail)
            .cgBasicInfo(cgBasicInfo)
            .filename(entityId.getType().getYamlFolderName() + "/" + ngEntityDetail.getIdentifier() + ".yaml")
            .type(entityId.getType())
            .build();
    if (mappingExist) {
      try {
        YamlDTO yamlDTO = getNGEntity(
            entities, migratedEntities, cgEntityNode, ngEntityDetail, inputDTO.getDestinationAccountIdentifier());
        if (yamlDTO == null) {
          return null;
        }
        ngYamlFile.setExists(true);
        ngYamlFile.setYaml(yamlDTO);
        return ngYamlFile;
      } catch (Exception ex) {
        log.error("Failed to retrieve NG Entity. ", ex);
      }
    }
    return null;
  }

  public YamlGenerationDetails getYamls(MigrationContext migrationContext, CgEntityId root, CgEntityId entityId) {
    if (!isNGEntityExists(migrationContext)
        || !canMigrate(entityId, root, migrationContext.getInputDTO().isMigrateReferencedEntities())) {
      return null;
    }
    if (migrationContext.getMigratedEntities().containsKey(entityId)) {
      return null;
    }
    return generateYaml(migrationContext, entityId);
  }

  public NgEntityDetail getNGEntityDetail(
      MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities, CgEntityId entityId) {
    CgEntityNode cgEntityNode = entities.get(entityId);
    String name = "";
    if (cgEntityNode.getEntity() instanceof NameAccess) {
      name = ((NameAccess) cgEntityNode.getEntity()).getName();
    }
    name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, name);
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    return NgEntityDetail.builder()
        .identifier(identifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .build();
  }

  protected abstract YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail,
      String accountIdentifier);

  protected abstract boolean isNGEntityExists(MigrationContext migrationContext);

  protected <T> MigrationImportSummaryDTO handleResp(NGYamlFile yamlFile, Response<ResponseDTO<T>> resp)
      throws IOException {
    return MigratorUtility.handleEntityMigrationResp(yamlFile, resp);
  }

  protected MigrationImportSummaryDTO migrateFile(NGClient ngClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile)
      throws IOException {
    FileYamlDTO fileYamlDTO = (FileYamlDTO) yamlFile.getYaml();
    RequestBody identifier = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getIdentifier());
    RequestBody name = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getName());
    RequestBody fileUsage = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getFileUsage());
    RequestBody type = RequestBody.create(TEXT_PLAIN, "FILE");
    RequestBody parentIdentifier = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getRootIdentifier());
    RequestBody mimeType = RequestBody.create(TEXT_PLAIN, "txt");
    RequestBody content = RequestBody.create(
        MediaType.parse("application/octet-stream"), StringUtils.defaultIfBlank(fileYamlDTO.getContent(), ""));

    Response<ResponseDTO<FileDTO>> resp;
    try {
      resp = ngClient
                 .createFileInFileStore(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                     inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(), content, name, identifier, fileUsage,
                     type, parentIdentifier, mimeType)
                 .execute();
      log.info("File store creation Response details {} {}", resp.code(), resp.message());
    } catch (IOException e) {
      log.error("Failed to create file", e);
      return MigrationImportSummaryDTO.builder()
          .errors(
              Collections.singletonList(ImportError.builder()
                                            .message("There was an error creating the inline manifest in file store")
                                            .entity(yamlFile.getCgBasicInfo())
                                            .build()))
          .build();
    }
    return handleResp(yamlFile, resp);
  }

  protected NGYamlFile getFolder(String name, String identifier, String projectIdentifier, String orgIdentifier) {
    return NGYamlFile.builder()
        .type(NGMigrationEntityType.FILE_STORE)
        .filename(null)
        .yaml(FileYamlDTO.builder()
                  .identifier(identifier)
                  .fileUsage("FOLDER")
                  .name(name)
                  .rootIdentifier("Root")
                  .filePath("")
                  .depth(0)
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .entityType(NGMigrationEntityType.FILE_STORE)
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .build();
  }
}
