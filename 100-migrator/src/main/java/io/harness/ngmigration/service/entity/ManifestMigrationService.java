/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.AppManifestSummary;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.config.ManifestFileHandlerImpl;
import io.harness.ngmigration.service.manifest.NgManifestFactory;
import io.harness.ngmigration.service.manifest.NgManifestService;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Environment;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ManifestMigrationService extends NgMigrationService {
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private NgManifestFactory manifestFactory;
  @Inject private ServiceResourceService serviceResourceService;

  @Inject private ServiceVariableMigrationService serviceVariableMigrationService;
  @Inject private SecretRefUtils secretRefUtils;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (isEmpty(entities)) {
      return null;
    }
    Map<String, Long> kindSummary = entities.stream()
                                        .map(entity -> (ApplicationManifest) entity.getEntity())
                                        .collect(groupingBy(entity -> entity.getKind().name(), counting()));
    Map<String, Long> storeSummary = entities.stream()
                                         .map(entity -> (ApplicationManifest) entity.getEntity())
                                         .collect(groupingBy(entity -> entity.getStoreType().name(), counting()));
    return AppManifestSummary.builder()
        .count(entities.size())
        .kindSummary(kindSummary)
        .storeSummary(storeSummary)
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    ApplicationManifest appManifest = (ApplicationManifest) entity;
    CgEntityId cgEntityId = CgEntityId.builder().id(appManifest.getUuid()).type(NGMigrationEntityType.MANIFEST).build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(appManifest)
                                    .appId(appManifest.getAppId())
                                    .id(appManifest.getUuid())
                                    .type(NGMigrationEntityType.MANIFEST)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    if (appManifest.getGitFileConfig() != null && isNotEmpty(appManifest.getGitFileConfig().getConnectorId())) {
      children.add(CgEntityId.builder()
                       .id(appManifest.getGitFileConfig().getConnectorId())
                       .type(NGMigrationEntityType.CONNECTOR)
                       .build());
    }
    if (appManifest.getHelmChartConfig() != null && isNotEmpty(appManifest.getHelmChartConfig().getConnectorId())) {
      children.add(CgEntityId.builder()
                       .id(appManifest.getHelmChartConfig().getConnectorId())
                       .type(NGMigrationEntityType.CONNECTOR)
                       .build());
    }
    if (StringUtils.isNoneBlank(appManifest.getEnvId(), appManifest.getServiceId())) {
      children.addAll(Lists.newArrayList(
          CgEntityId.builder().id(appManifest.getServiceId()).type(NGMigrationEntityType.SERVICE).build(),
          CgEntityId.builder().id(appManifest.getEnvId()).type(NGMigrationEntityType.ENVIRONMENT).build()));
    }
    List<ManifestFile> manifestFiles =
        applicationManifestService.listManifestFiles(appManifest.getUuid(), appManifest.getAppId());
    if (EmptyPredicate.isNotEmpty(manifestFiles)) {
      for (ManifestFile manifestFile : manifestFiles) {
        children.addAll(secretRefUtils.getSecretRefFromExpressions(
            appManifest.getAccountId(), MigratorExpressionUtils.extractAll(manifestFile.getFileContent())));
      }
    }
    return DiscoveryNode.builder().children(children).entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(applicationManifestService.getById(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    ApplicationManifest applicationManifest = (ApplicationManifest) entities.get(entityId).getEntity();
    List<NGYamlFile> yamlFiles = getYamlFilesForManifest(migrationContext, applicationManifest);
    String serviceId = applicationManifest.getServiceId();
    String envId = applicationManifest.getEnvId();
    if (StringUtils.isNoneBlank(serviceId, envId)
        && serviceVariableMigrationService.doReferenceExists(migratedEntities, envId, serviceId)) {
      // We need to generate the overrides
      NGYamlFile override =
          ServiceVariableMigrationService.getBlankServiceOverride(migrationContext, envId, serviceId, null);
      NGYamlFile existingOverride =
          ServiceVariableMigrationService.findExistingOverride(migrationContext, envId, serviceId);
      if (existingOverride != null) {
        override = existingOverride;
      } else {
        yamlFiles.add(override);
        migratedEntities.putIfAbsent(entityId, override);
      }
      NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          ((NGServiceOverrideConfig) override.getYaml()).getServiceOverrideInfoConfig();

      List<ManifestConfigWrapper> manifestConfigWrapperList = getManifests(migrationContext,
          Collections.singleton(entityId), null, migrationContext.getInputDTO().getIdentifierCaseFormat());
      if (EmptyPredicate.isNotEmpty(manifestConfigWrapperList)) {
        serviceOverrideInfoConfig.getManifests().addAll(manifestConfigWrapperList);
      }
      return YamlGenerationDetails.builder().yamlFileList(yamlFiles).build();
    }
    return YamlGenerationDetails.builder().yamlFileList(yamlFiles).build();
  }

  private List<NGYamlFile> getYamlFilesForManifest(
      MigrationContext migrationContext, ApplicationManifest applicationManifest) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    String serviceId = applicationManifest.getServiceId();
    String envId = applicationManifest.getEnvId();
    List<ManifestFile> manifestFiles =
        applicationManifestService.listManifestFiles(applicationManifest.getUuid(), applicationManifest.getAppId());
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }
    CgEntityNode serviceNode =
        entities.get(CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(serviceId).build());
    String serviceName = "";
    String envName = "";
    if (serviceNode != null && serviceNode.getEntity() != null) {
      Service service = (Service) serviceNode.getEntity();
      serviceName = service.getName();
    }
    CgEntityNode envNode = entities.get(CgEntityId.builder().type(NGMigrationEntityType.ENVIRONMENT).id(envId).build());
    if (envNode != null && envNode.getEntity() != null) {
      Environment environment = (Environment) envNode.getEntity();
      envName = environment.getName();
    }
    return getYamlFiles(migrationContext, applicationManifest, manifestFiles, envName, serviceName);
  }

  private List<NGYamlFile> getYamlFiles(MigrationContext migrationContext, ApplicationManifest applicationManifest,
      List<ManifestFile> manifestFiles, String envName, String serviceName) {
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }
    List<NGYamlFile> files = new ArrayList<>();
    ManifestFileHandlerImpl manifestFileHandler =
        new ManifestFileHandlerImpl(serviceName, envName, applicationManifest);
    for (ManifestFile manifestFile : manifestFiles) {
      files.add(NGYamlFile.builder()
                    .type(NGMigrationEntityType.MANIFEST)
                    .filename(null)
                    .yaml(manifestFileHandler.getFileYamlDTO(migrationContext, manifestFile))
                    .ngEntityDetail(manifestFileHandler.getNGEntityDetail(migrationContext, manifestFile))
                    .cgBasicInfo(manifestFileHandler.getCgBasicInfo(manifestFile))
                    .build());
      files.addAll(manifestFileHandler.getFolders(migrationContext, manifestFile));
    }
    return files;
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  public List<ManifestConfigWrapper> getManifests(MigrationContext migrationContext, Set<CgEntityId> manifestEntityIds,
      Service service, CaseFormat identifierCaseFormat) {
    if (isEmpty(manifestEntityIds)) {
      return new ArrayList<>();
    }
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    List<ManifestConfigWrapper> ngManifests = new ArrayList<>();
    for (CgEntityId manifestEntityId : manifestEntityIds) {
      CgEntityNode manifestNode = entities.get(manifestEntityId);
      ApplicationManifest applicationManifest = (ApplicationManifest) manifestNode.getEntity();
      if (null != applicationManifest && null == service && isNotEmpty(applicationManifest.getServiceId())) {
        service = serviceResourceService.get(applicationManifest.getAppId(), applicationManifest.getServiceId());
      }
      MigratorExpressionUtils.render(migrationContext, applicationManifest, inputDTO.getCustomExpressions());
      BaseProvidedInput manifestInput =
          inputDTO.getOverrides() == null ? null : inputDTO.getOverrides().get(manifestEntityId);
      ManifestProvidedEntitySpec entitySpec = null;
      if (manifestInput != null && manifestInput.getSpec() != null) {
        entitySpec = JsonUtils.treeToValue(manifestInput.getSpec(), ManifestProvidedEntitySpec.class);
      }
      List<NGYamlFile> files = getYamlFilesForManifest(migrationContext, applicationManifest);
      if (EmptyPredicate.isNotEmpty(files)) {
        files = files.stream()
                    .filter(file -> !"FOLDER".equalsIgnoreCase(((FileYamlDTO) file.getYaml()).getFileUsage()))
                    .collect(Collectors.toList());
      }
      NgManifestService ngManifestService = manifestFactory.getNgManifestService(applicationManifest, service);
      List<ManifestConfigWrapper> manifestConfigWrapper = ngManifestService.getManifestConfigWrapper(
          applicationManifest, entities, migratedEntities, entitySpec, files, identifierCaseFormat);
      ngManifests.addAll(manifestConfigWrapper);
    }
    return ngManifests;
  }

  public GitStore getGitStore(
      GitFileConfig gitFileConfig, ManifestProvidedEntitySpec manifestInput, NgEntityDetail connector) {
    GitStore gitStore =
        GitStore.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
            .gitFetchType(StringUtils.isNotBlank(gitFileConfig.getBranch()) ? FetchType.BRANCH : FetchType.COMMIT)
            .build();
    if (StringUtils.isNotBlank(gitFileConfig.getRepoName())) {
      gitStore.setRepoName(ParameterField.createValueField(gitFileConfig.getRepoName()));
    }
    if (manifestInput != null) {
      if (StringUtils.isNotBlank(manifestInput.getBranch())) {
        gitStore.setBranch(ParameterField.createValueField(manifestInput.getBranch()));
      }
      if (StringUtils.isNotBlank(manifestInput.getFolderPath())) {
        gitStore.setPaths(MigratorUtility.splitWithComma(manifestInput.getFolderPath()));
      } else if (EmptyPredicate.isNotEmpty(manifestInput.getPaths())) {
        gitStore.setPaths(ParameterField.createValueField(manifestInput.getPaths()));
      } else {
        gitStore.setPaths(MigratorUtility.splitWithComma(gitFileConfig.getFilePath()));
      }
    } else {
      if (StringUtils.isNotBlank(gitFileConfig.getBranch())) {
        gitStore.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
      } else {
        gitStore.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
      }
      if (StringUtils.isBlank(gitFileConfig.getFilePath())) {
        gitStore.setFolderPath(ParameterField.createValueField("/"));
      } else {
        gitStore.setPaths(MigratorUtility.splitWithComma(gitFileConfig.getFilePath()));
      }
    }
    return gitStore;
  }

  public GitStore getGitStoreWithFilePaths(GitFileConfig gitFileConfig, NgEntityDetail connector) {
    GitStore gitStore =
        GitStore.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
            .gitFetchType(StringUtils.isNotBlank(gitFileConfig.getBranch()) ? FetchType.BRANCH : FetchType.COMMIT)
            .build();
    if (StringUtils.isNotBlank(gitFileConfig.getRepoName())) {
      gitStore.setRepoName(ParameterField.createValueField(gitFileConfig.getRepoName()));
    }

    if (StringUtils.isNotBlank(gitFileConfig.getBranch())) {
      gitStore.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
    } else {
      gitStore.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
    }
    if (isNotEmpty(gitFileConfig.getFilePathList())) {
      gitStore.setPaths(ParameterField.createValueField(gitFileConfig.getFilePathList()));
    }
    return gitStore;
  }

  public HarnessStore getHarnessStore(List<NGYamlFile> files) {
    return HarnessStore.builder().files(MigratorUtility.getFileStorePaths(files)).build();
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType().equals(NGMigrationEntityType.SERVICE);
  }
}
