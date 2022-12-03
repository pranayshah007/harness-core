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
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.AppManifestSummary;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.manifest.NgManifestFactory;
import io.harness.ngmigration.service.manifest.NgManifestService;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Environment;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.ApplicationManifestService;

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
  private static final List<AppManifestKind> SUPPORTED_MANIFEST_KIND =
      Lists.newArrayList(AppManifestKind.VALUES, AppManifestKind.K8S_MANIFEST);

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
    return DiscoveryNode.builder().children(children).entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(applicationManifestService.getById(appId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    ApplicationManifest applicationManifest = (ApplicationManifest) entity;
    if (StoreType.Remote.equals(applicationManifest.getStoreType())) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(Collections.singletonList("Only remote manifests are supported currently for migration"))
          .build();
    }
    if (!SUPPORTED_MANIFEST_KIND.contains(applicationManifest.getKind())) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(Collections.singletonList(
              String.format("Only %s type of manifests are currently supported with migration",
                  SUPPORTED_MANIFEST_KIND.stream().map(AppManifestKind::name).collect(Collectors.joining(", ")))))
          .build();
    }
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(auth, ngClient, inputDTO, yamlFile);
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    ApplicationManifest applicationManifest = (ApplicationManifest) entities.get(entityId).getEntity();
    List<NGYamlFile> yamlFiles = getYamlFilesForManifest(applicationManifest, inputDTO, entities);
    String serviceId = applicationManifest.getServiceId();
    String envId = applicationManifest.getEnvId();
    if (StringUtils.isNoneBlank(serviceId, envId)) {
      // We need to generate the overrides
      NGYamlFile override =
          ServiceVariableMigrationService.getBlankServiceOverride(inputDTO, migratedEntities, envId, serviceId, null);
      NGYamlFile existingOverride =
          ServiceVariableMigrationService.findExistingOverride(entities, migratedEntities, envId, serviceId);
      if (existingOverride != null) {
        override = existingOverride;
      } else {
        yamlFiles.add(override);
        migratedEntities.putIfAbsent(entityId, override);
      }
      NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          ((NGServiceOverrideConfig) override.getYaml()).getServiceOverrideInfoConfig();

      List<ManifestConfigWrapper> manifestConfigWrapperList =
          getManifests(Collections.singleton(entityId), inputDTO, entities, migratedEntities);
      if (EmptyPredicate.isNotEmpty(manifestConfigWrapperList)) {
        serviceOverrideInfoConfig.getManifests().addAll(manifestConfigWrapperList);
      }
      return yamlFiles;
    }
    return yamlFiles;
  }

  private List<NGYamlFile> getYamlFilesForManifest(
      ApplicationManifest applicationManifest, MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities) {
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
    return getYamlFiles(inputDTO, applicationManifest, manifestFiles, envName, serviceName);
  }

  private List<NGYamlFile> getYamlFiles(MigrationInputDTO inputDTO, ApplicationManifest applicationManifest,
      List<ManifestFile> manifestFiles, String envName, String serviceName) {
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }
    StringBuilder prefixBuilder = new StringBuilder();
    if (StringUtils.isNotBlank(envName)) {
      prefixBuilder.append(envName).append(' ');
    }
    if (StringUtils.isNotBlank(serviceName)) {
      prefixBuilder.append(serviceName).append(' ');
    }
    String prefix = prefixBuilder.toString();
    String fileUsage = FileUsage.MANIFEST_FILE.name();
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    return manifestFiles.stream()
        .map(manifestFile -> {
          // TODO: Fix the identifier & name
          String identifier = MigratorUtility.generateManifestIdentifier(prefix + manifestFile.getFileName());
          if (applicationManifest.getKind().equals(AppManifestKind.VALUES)) {
            identifier =
                MigratorUtility.generateManifestIdentifier(prefix + " ValuesOverride " + manifestFile.getFileName());
          }
          String name = identifier;
          if (MigratorUtility.endsWithIgnoreCase(identifier, "yaml", "yml")) {
            name = MigratorUtility.endsWithIgnoreCase(identifier, "yaml")
                ? identifier.substring(0, identifier.length() - 4) + ".yaml"
                : identifier.substring(0, identifier.length() - 3) + ".yml";
          }
          String content =
              (String) MigratorExpressionUtils.render(manifestFile.getFileContent(), inputDTO.getCustomExpressions());
          return NGYamlFile.builder()
              .type(NGMigrationEntityType.MANIFEST)
              .filename(null)
              .yaml(FileYamlDTO.builder()
                        .identifier(identifier)
                        .fileUsage(fileUsage)
                        .name(name)
                        .content(content)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build())
              .ngEntityDetail(NgEntityDetail.builder()
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build())
              .cgBasicInfo(CgBasicInfo.builder()
                               .accountId(applicationManifest.getAccountId())
                               .appId(applicationManifest.getAppId())
                               .id(applicationManifest.getUuid())
                               .name(applicationManifest.getName())
                               .type(NGMigrationEntityType.MANIFEST)
                               .build())
              .build();
        })
        .collect(Collectors.toList());
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  public List<ManifestConfigWrapper> getManifests(Set<CgEntityId> manifestEntityIds, MigrationInputDTO inputDTO,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (isEmpty(manifestEntityIds)) {
      return new ArrayList<>();
    }

    List<ManifestConfigWrapper> ngManifests = new ArrayList<>();
    for (CgEntityId manifestEntityId : manifestEntityIds) {
      CgEntityNode manifestNode = entities.get(manifestEntityId);
      ApplicationManifest applicationManifest = (ApplicationManifest) manifestNode.getEntity();
      MigratorExpressionUtils.render(applicationManifest, inputDTO.getCustomExpressions());
      BaseProvidedInput manifestInput =
          inputDTO.getOverrides() == null ? null : inputDTO.getOverrides().get(manifestEntityId);
      ManifestProvidedEntitySpec entitySpec = null;
      if (manifestInput != null && manifestInput.getSpec() != null) {
        entitySpec = JsonUtils.treeToValue(manifestInput.getSpec(), ManifestProvidedEntitySpec.class);
      }
      List<NGYamlFile> files = getYamlFilesForManifest(applicationManifest, inputDTO, entities);
      NgManifestService ngManifestService = manifestFactory.getNgManifestService(applicationManifest);

      List<ManifestConfigWrapper> manifestConfigWrapper = ngManifestService.getManifestConfigWrapper(
          applicationManifest, entities, migratedEntities, entitySpec, files);
      ngManifests.addAll(manifestConfigWrapper);
    }
    return ngManifests;
  }

  public GitStore getGitStore(
      GitFileConfig gitFileConfig, ManifestProvidedEntitySpec manifestInput, NgEntityDetail connector) {
    GitStoreBuilder gitStoreBuilder =
        GitStore.builder()
            .commitId(ParameterField.createValueField(gitFileConfig.getCommitId()))
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
            .gitFetchType(gitFileConfig.isUseBranch() ? FetchType.BRANCH : FetchType.COMMIT)
            .repoName(ParameterField.createValueField(gitFileConfig.getRepoName()));
    if (manifestInput != null) {
      if (StringUtils.isNotBlank(manifestInput.getBranch())) {
        gitStoreBuilder.branch(ParameterField.createValueField(manifestInput.getBranch()));
      }
      if (StringUtils.isNotBlank(manifestInput.getFolderPath())) {
        gitStoreBuilder.paths(
            ParameterField.createValueField(Collections.singletonList(manifestInput.getFolderPath())));
      } else if (EmptyPredicate.isNotEmpty(manifestInput.getPaths())) {
        gitStoreBuilder.paths(ParameterField.createValueField(manifestInput.getPaths()));
      } else {
        gitStoreBuilder.paths(ParameterField.createValueField(Collections.singletonList(gitFileConfig.getFilePath())));
      }
    } else {
      gitStoreBuilder.branch(ParameterField.createValueField(gitFileConfig.getBranch()));
      if (StringUtils.isBlank(gitFileConfig.getFilePath())) {
        gitStoreBuilder.folderPath(ParameterField.createValueField("/"));
      } else {
        gitStoreBuilder.paths(ParameterField.createValueField(Collections.singletonList(gitFileConfig.getFilePath())));
      }
    }
    return gitStoreBuilder.build();
  }

  public HarnessStore getHarnessStore(List<NGYamlFile> files) {
    return HarnessStore.builder().files(MigratorUtility.getFileStorePaths(files)).build();
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType().equals(NGMigrationEntityType.SERVICE);
  }
}
