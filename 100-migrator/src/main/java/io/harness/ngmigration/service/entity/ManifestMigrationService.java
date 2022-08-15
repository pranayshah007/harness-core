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

import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.AppManifestSummary;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.ngManifestFactory.NgManifestFactory;
import io.harness.ngmigration.service.ngManifestFactory.NgManifestService;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
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
import org.apache.commons.lang3.StringUtils;

public class ManifestMigrationService extends NgMigrationService {
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private MigratorExpressionUtils migratorExpressionUtils;
  @Inject private NgManifestFactory manifestFactory;
  private final List<AppManifestKind> SUPPORTED_MANIFEST_KIND =
      Lists.newArrayList(AppManifestKind.VALUES, AppManifestKind.K8S_MANIFEST);

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new IllegalAccessError("Mapping not allowed for Manifests");
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
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {}

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    return null;
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return null;
  }

  public List<ManifestConfigWrapper> getManifests(Set<CgEntityId> manifestEntityIds, MigrationInputDTO inputDTO,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (isEmpty(manifestEntityIds)) {
      return Collections.emptyList();
    }

    List<ManifestConfigWrapper> ngManifests = new ArrayList<>();
    for (CgEntityId manifestEntityId : manifestEntityIds) {
      CgEntityNode manifestNode = entities.get(manifestEntityId);
      ApplicationManifest applicationManifest = (ApplicationManifest) manifestNode.getEntity();
      migratorExpressionUtils.render(applicationManifest);
      BaseProvidedInput manifestInput =
          inputDTO.getInputs() == null ? null : inputDTO.getInputs().get(manifestEntityId);
      ManifestProvidedEntitySpec entitySpec = null;
      if (manifestInput != null && manifestInput.getSpec() != null) {
        entitySpec = JsonUtils.treeToValue(manifestInput.getSpec(), ManifestProvidedEntitySpec.class);
      }
      NgManifestService ngManifestService = manifestFactory.getNgManifestService(applicationManifest);
      ManifestConfigWrapper manifestConfigWrapper =
          ngManifestService.getManifestConfigWrapper(applicationManifest, migratedEntities, entitySpec);
      ngManifests.add(manifestConfigWrapper);
    }
    return ngManifests;
  }

  // TODO: use scoped connectorRef ref
  public GitStore getGitStore(
      GitFileConfig gitFileConfig, ManifestProvidedEntitySpec manifestInput, String connectorRef) {
    GitStoreBuilder gitStoreBuilder =
        GitStore.builder()
            .commitId(ParameterField.createValueField(gitFileConfig.getCommitId()))
            .connectorRef(ParameterField.createValueField(connectorRef))
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
      gitStoreBuilder.branch(ParameterField.createValueField(gitFileConfig.getBranch()))
          .paths(ParameterField.createValueField(Collections.singletonList(gitFileConfig.getFilePath())));
    }
    return gitStoreBuilder.build();
  }
}
