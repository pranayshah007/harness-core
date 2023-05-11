/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.Scope.PROJECT;
import static io.harness.ngmigration.utils.MigratorUtility.containsEcsTask;
import static io.harness.ngmigration.utils.NGMigrationConstants.SERVICE_COMMAND_TEMPLATE_SEPARATOR;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_LAMBDA;
import static software.wings.api.DeploymentType.AZURE_WEBAPP;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.ngmigration.NGMigrationEntityType.AMI_STARTUP_SCRIPT;
import static software.wings.ngmigration.NGMigrationEntityType.ARTIFACT_STREAM;
import static software.wings.ngmigration.NGMigrationEntityType.CONFIG_FILE;
import static software.wings.ngmigration.NGMigrationEntityType.CONTAINER_TASK;
import static software.wings.ngmigration.NGMigrationEntityType.ECS_SERVICE_SPEC;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.EcsTaskDefinitionManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.TypeSummary;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.ServiceSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.manifest.ValuesYamlFromHelmRepoManifestService;
import io.harness.ngmigration.service.servicev2.ServiceV2Factory;
import io.harness.ngmigration.service.servicev2.ServiceV2Mapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.utils.YamlPipelineUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceMigrationService extends NgMigrationService {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ManifestMigrationService manifestMigrationService;
  @Inject private EcsServiceSpecMigrationService ecsServiceSpecMigrationService;
  @Inject private ContainerTaskMigrationService containerTaskMigrationService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ServiceResourceClient serviceResourceClient;
  @Inject private AmiStartupScriptMigrationService amiStartupScriptMigrationService;
  @Inject ConfigService configService;
  @Inject ConfigFileMigrationService configFileMigrationService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    NGServiceV2InfoConfig serviceConfig = ((NGServiceConfig) yamlFile.getYaml()).getNgServiceV2InfoConfig();
    String orgIdentifier = yamlFile.getNgEntityDetail().getOrgIdentifier();
    String projectIdentifier = yamlFile.getNgEntityDetail().getProjectIdentifier();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(SERVICE.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(serviceConfig.getIdentifier())
        .scope(MigratorMappingService.getScope(orgIdentifier, projectIdentifier))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, serviceConfig.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> artifactTypeSummary = entities.stream()
                                                .map(entity -> ((Service) entity.getEntity()).getArtifactType())
                                                .filter(Objects::nonNull)
                                                .collect(groupingBy(ArtifactType::name, counting()));
    Map<String, Long> deploymentTypeSummary = entities.stream()
                                                  .map(entity -> ((Service) entity.getEntity()).getDeploymentType())
                                                  .filter(Objects::nonNull)
                                                  .collect(groupingBy(DeploymentType::name, counting()));
    Map<String, TypeSummary> deploymentsSummary = new HashMap<>();
    deploymentTypeSummary.forEach((key, value) -> {
      deploymentsSummary.put(key,
          TypeSummary.builder()
              .status(
                  ServiceV2Factory.getServiceV2Mapper(DeploymentType.valueOf(key), null, false).isMigrationSupported()
                      ? SupportStatus.SUPPORTED
                      : SupportStatus.UNSUPPORTED)
              .count(value)
              .build());
    });
    return new ServiceSummary(entities.size(), deploymentTypeSummary, artifactTypeSummary, deploymentsSummary);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Service service = (Service) entity;
    String serviceId = service.getUuid();
    CgEntityId serviceEntityId = CgEntityId.builder().type(SERVICE).id(serviceId).build();
    CgEntityNode serviceEntityNode = CgEntityNode.builder()
                                         .id(serviceId)
                                         .type(SERVICE)
                                         .appId(service.getAppId())
                                         .entityId(serviceEntityId)
                                         .entity(service)
                                         .build();
    List<ArtifactStream> artifactStreams =
        artifactStreamService.getArtifactStreamsForService(service.getAppId(), serviceId);
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.listAppManifests(service.getAppId(), serviceId);
    List<ConfigFile> configFiles =
        configService.getConfigFilesForEntity(service.getAppId(), DEFAULT_TEMPLATE_ID, serviceId);
    Set<CgEntityId> children = new HashSet<>();
    if (isNotEmpty(artifactStreams)) {
      children.addAll(
          artifactStreams.stream()
              .map(artifactStream -> CgEntityId.builder().id(artifactStream.getUuid()).type(ARTIFACT_STREAM).build())
              .collect(Collectors.toSet()));
    }
    if (isNotEmpty(applicationManifests)) {
      children.addAll(applicationManifests.stream()
                          .map(manifest -> CgEntityId.builder().id(manifest.getUuid()).type(MANIFEST).build())
                          .collect(Collectors.toList()));
    }

    // Only consider config files that override for all environments
    if (isNotEmpty(configFiles)) {
      children.addAll(configFiles.stream()
                          .map(configFile -> CgEntityId.builder().id(configFile.getUuid()).type(CONFIG_FILE).build())
                          .collect(Collectors.toList()));
    }
    if (isNotEmpty(service.getServiceVariables())) {
      children.addAll(
          service.getServiceVariables()
              .stream()
              .filter(Objects::nonNull)
              .filter(serviceVariable -> ServiceVariableType.ENCRYPTED_TEXT.equals(serviceVariable.getType()))
              .map(serviceVariable -> CgEntityId.builder().id(serviceVariable.getEncryptedValue()).type(SECRET).build())
              .collect(Collectors.toList()));
    }
    if (ECS == service.getDeploymentType() && processInlineServiceSpec(applicationManifests)) {
      EcsServiceSpecification ecsServiceSpecification =
          serviceResourceService.getEcsServiceSpecification(service.getAppId(), serviceId);
      if (ecsServiceSpecification != null) {
        children.add(CgEntityId.builder().id(ecsServiceSpecification.getUuid()).type(ECS_SERVICE_SPEC).build());
      }
    }

    if (ECS == service.getDeploymentType() && processTaskDefs(applicationManifests)) {
      ContainerTask containerTask =
          serviceResourceService.getContainerTaskByDeploymentType(service.getAppId(), serviceId, ECS.name());
      if (containerTask != null) {
        children.add(CgEntityId.builder().id(containerTask.getUuid()).type(CONTAINER_TASK).build());
      }
    }

    if (AMI == service.getDeploymentType() || AZURE_WEBAPP == service.getDeploymentType()) {
      UserDataSpecification userDataSpecification =
          serviceResourceService.getUserDataSpecification(service.getAppId(), serviceId);
      if (null != userDataSpecification) {
        children.add(CgEntityId.builder().id(userDataSpecification.getUuid()).type(AMI_STARTUP_SCRIPT).build());
      }
    }

    if (isNotEmpty(service.getServiceCommands())) {
      List<ServiceCommand> serviceCommands = service.getServiceCommands();
      List<CgEntityId> serviceCommandTemplates =
          serviceCommands.stream()
              .map(sc
                  -> CgEntityId.builder()
                         .id(serviceId + SERVICE_COMMAND_TEMPLATE_SEPARATOR + sc.getName())
                         .type(SERVICE_COMMAND_TEMPLATE)
                         .build())
              .collect(Collectors.toList());
      children.addAll(serviceCommandTemplates);
    }

    return DiscoveryNode.builder().entityNode(serviceEntityNode).children(children).build();
  }

  private static boolean processInlineServiceSpec(List<ApplicationManifest> applicationManifests) {
    if (EmptyPredicate.isEmpty(applicationManifests)) {
      return true;
    }
    return applicationManifests.stream()
        .filter(manifest -> manifest.getGitFileConfig() != null)
        .noneMatch(manifest -> StringUtils.isNotBlank(manifest.getGitFileConfig().getServiceSpecFilePath()));
  }

  private static boolean processTaskDefs(List<ApplicationManifest> applicationManifests) {
    if (EmptyPredicate.isEmpty(applicationManifests)) {
      return true;
    }
    return applicationManifests.stream()
        .filter(manifest -> manifest.getGitFileConfig() != null)
        .noneMatch(manifest -> StringUtils.isNotBlank(manifest.getGitFileConfig().getTaskSpecFilePath()));
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceResourceService.getWithDetails(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      log.info("Skipping creation of service as it already exists");
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Service was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    ServiceRequestDTO serviceRequestDTO =
        ServiceRequestDTO.builder()
            .description(null)
            .identifier(yamlFile.getNgEntityDetail().getIdentifier())
            .name(((NGServiceConfig) yamlFile.getYaml()).getNgServiceV2InfoConfig().getName())
            .orgIdentifier(yamlFile.getNgEntityDetail().getOrgIdentifier())
            .projectIdentifier(yamlFile.getNgEntityDetail().getProjectIdentifier())
            .yaml(getYamlString(yamlFile))
            .build();
    Response<ResponseDTO<ServiceResponse>> resp =
        ngClient
            .createService(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                JsonUtils.asTree(serviceRequestDTO))
            .execute();
    log.info("Service creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, Set<CgEntityId>> graph = migrationContext.getGraph();
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    Service service = (Service) migrationContext.getEntities().get(entityId).getEntity();
    String name =
        MigratorUtility.generateName(migrationContext.getInputDTO().getOverrides(), entityId, service.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(migrationContext.getInputDTO().getOverrides(),
        entityId, name, migrationContext.getInputDTO().getIdentifierCaseFormat());
    String projectIdentifier = MigratorUtility.getProjectIdentifier(PROJECT, migrationContext.getInputDTO());
    String orgIdentifier = MigratorUtility.getOrgIdentifier(PROJECT, migrationContext.getInputDTO());

    MigratorExpressionUtils.render(migrationContext, service, migrationContext.getInputDTO().getCustomExpressions());
    Set<CgEntityId> manifests = migrationContext.getGraph()
                                    .get(entityId)
                                    .stream()
                                    .filter(cgEntityId -> cgEntityId.getType() == MANIFEST)
                                    .collect(Collectors.toSet());
    Set<CgEntityId> serviceDefs = graph.get(entityId)
                                      .stream()
                                      .filter(cgEntityId -> cgEntityId.getType() == ECS_SERVICE_SPEC)
                                      .collect(Collectors.toSet());
    Set<CgEntityId> taskDefs = graph.get(entityId)
                                   .stream()
                                   .filter(cgEntityId -> cgEntityId.getType() == CONTAINER_TASK)
                                   .collect(Collectors.toSet());
    Set<CgEntityId> startupScriptDefs = graph.get(entityId)
                                            .stream()
                                            .filter(cgEntityId -> cgEntityId.getType() == AMI_STARTUP_SCRIPT)
                                            .collect(Collectors.toSet());
    Set<CgEntityId> configFileIds =
        migrationContext.getEntities()
            .values()
            .stream()
            .filter(entry -> CONFIG_FILE == entry.getType())
            .map(entry -> (ConfigFile) entry.getEntity())
            .filter(configFile -> configFile.getEntityType() == EntityType.SERVICE)
            .filter(ConfigFile::isTargetToAllEnv)
            .filter(configFile -> StringUtils.equals(configFile.getEntityId(), service.getUuid()))
            .map(configFile -> CgEntityId.builder().type(CONFIG_FILE).id(configFile.getUuid()).build())
            .collect(Collectors.toSet());

    List<NGYamlFile> files = new ArrayList<>();

    ServiceV2Mapper serviceMapper = ServiceV2Factory.getService2Mapper(service, containsEcsTask(taskDefs, entities));
    LambdaSpecification lambdaSpecification = getLambdaSpecification(inputDTO, service);
    List<NGYamlFile> childYamlFiles = serviceMapper.getChildYamlFiles(migrationContext, service, lambdaSpecification);
    List<ManifestConfigWrapper> manifestConfigWrapperList = new ArrayList<>(manifestMigrationService.getManifests(
        migrationContext, manifests, service, inputDTO.getIdentifierCaseFormat()));
    manifestConfigWrapperList = mergeHelmChartOverrideManifestsIfApplicable(manifestConfigWrapperList);
    if (isNotEmpty(childYamlFiles)) {
      files.addAll(childYamlFiles);
      List<ManifestConfigWrapper> lambdaManifests = getLambdaManifests(inputDTO, childYamlFiles);
      manifestConfigWrapperList.addAll(lambdaManifests);
    }

    List<ManifestConfigWrapper> ecsServiceSpecs =
        ecsServiceSpecMigrationService.getServiceSpec(migrationContext, serviceDefs);
    List<ManifestConfigWrapper> taskDefSpecs = containerTaskMigrationService.getTaskSpecs(migrationContext, taskDefs);
    List<StartupScriptConfiguration> startupScriptConfigurations =
        amiStartupScriptMigrationService.getStartupScriptConfiguration(migrationContext, startupScriptDefs);
    manifestConfigWrapperList.addAll(taskDefSpecs);
    manifestConfigWrapperList.addAll(ecsServiceSpecs);

    List<ConfigFileWrapper> configFileWrapperList =
        configFileMigrationService.getConfigFiles(migrationContext, configFileIds);

    ServiceDefinition serviceDefinition = serviceMapper.getServiceDefinition(
        migrationContext, service, manifestConfigWrapperList, configFileWrapperList, startupScriptConfigurations);
    if (serviceDefinition == null) {
      return YamlGenerationDetails.builder()
          .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                     .reason("Unsupported Service")
                                                     .cgBasicInfo(service.getCgBasicInfo())
                                                     .type(entityId.getType())
                                                     .build()))
          .build();
    }

    NGServiceConfig serviceYaml = NGServiceConfig.builder()
                                      .ngServiceV2InfoConfig(NGServiceV2InfoConfig.builder()
                                                                 .name(name)
                                                                 .description(service.getDescription())
                                                                 .gitOpsEnabled(false)
                                                                 .identifier(identifier)
                                                                 .tags(MigratorUtility.getTags(service.getTagLinks()))
                                                                 .useFromStage(null)
                                                                 .serviceDefinition(serviceDefinition)
                                                                 .build())
                                      .build();
    NGYamlFile ngYamlFile = NGYamlFile.builder()
                                .filename(String.format("service/%s/%s.yaml", service.getAppId(), service.getName()))
                                .yaml(serviceYaml)
                                .type(SERVICE)
                                .ngEntityDetail(NgEntityDetail.builder()
                                                    .entityType(SERVICE)
                                                    .identifier(identifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build())
                                .cgBasicInfo(service.getCgBasicInfo())
                                .build();
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    files.add(ngYamlFile);
    files.add(getFolder(name, identifier, projectIdentifier, orgIdentifier));
    return YamlGenerationDetails.builder().yamlFileList(files).build();
  }

  private List<ManifestConfigWrapper> mergeHelmChartOverrideManifestsIfApplicable(
      List<ManifestConfigWrapper> manifestConfigWrapperList) {
    if (isNotEmpty(manifestConfigWrapperList) && manifestConfigWrapperList.size() == 2) {
      ManifestConfigWrapper manifestConfigWrapper1 = manifestConfigWrapperList.get(0);
      ManifestConfigWrapper manifestConfigWrapper2 = manifestConfigWrapperList.get(1);

      if (isHelmChartManifest(manifestConfigWrapper1) && isHelmChartManifest(manifestConfigWrapper2)) {
        if (isValuesOverrideHelmRepoStoreManifest(manifestConfigWrapper1)
            || isValuesOverrideHelmRepoStoreManifest(manifestConfigWrapper2)) {
          ManifestConfigWrapper helmChartManifest;
          ManifestConfigWrapper helmRepoOverrideManifest;
          if (isValuesOverrideHelmRepoStoreManifest(manifestConfigWrapper1)) {
            helmRepoOverrideManifest = manifestConfigWrapper1;
            helmChartManifest = manifestConfigWrapper2;
          } else {
            helmRepoOverrideManifest = manifestConfigWrapper2;
            helmChartManifest = manifestConfigWrapper1;
          }
          ((HelmChartManifest) helmChartManifest.getManifest().getSpec())
              .setValuesPaths(((HelmChartManifest) helmRepoOverrideManifest.getManifest().getSpec()).getValuesPaths());
          return Arrays.asList(helmChartManifest);
        }
      }
    }
    return manifestConfigWrapperList;
  }

  private boolean isValuesOverrideHelmRepoStoreManifest(ManifestConfigWrapper manifestConfigWrapper1) {
    return manifestConfigWrapper1.getManifest().getIdentifier().endsWith(
        ValuesYamlFromHelmRepoManifestService.HELM_REPO_STORE);
  }

  private boolean isHelmChartManifest(ManifestConfigWrapper manifestConfigWrapper1) {
    return manifestConfigWrapper1 != null && manifestConfigWrapper1.getManifest() != null
        && manifestConfigWrapper1.getManifest().getType() == ManifestConfigType.HELM_CHART;
  }

  private List<ManifestConfigWrapper> getLambdaManifests(MigrationInputDTO inputDTO, List<NGYamlFile> childYamlFiles) {
    List<ManifestConfigWrapper> manifests = new ArrayList<>();

    childYamlFiles.forEach(file -> {
      String fileName = "/" + ((FileYamlDTO) file.getYaml()).getName();
      ManifestConfigWrapper manifestConfigWrapper =
          ManifestConfigWrapper.builder()
              .manifest(
                  ManifestConfig.builder()
                      .type(ManifestConfigType.AWS_LAMBDA)
                      .identifier(MigratorUtility.generateFileIdentifier(fileName, inputDTO.getIdentifierCaseFormat()))
                      .spec(EcsTaskDefinitionManifest.builder()
                                .identifier(ManifestConfigType.AWS_LAMBDA.getDisplayName())
                                .store(ParameterField.createValueField(
                                    StoreConfigWrapper.builder()
                                        .type(StoreConfigType.HARNESS)
                                        .spec(HarnessStore.builder()
                                                  .files(ParameterField.createValueField(
                                                      Collections.singletonList(fileName)))
                                                  .build())
                                        .build()))
                                .build())
                      .build())
              .build();
      manifests.add(manifestConfigWrapper);
    });

    return manifests;
  }

  private LambdaSpecification getLambdaSpecification(MigrationInputDTO inputDTO, Service service) {
    if (service.getDeploymentType() == AWS_LAMBDA) {
      return serviceResourceService.getLambdaSpecification(service.getAppId(), service.getUuid());
    } else {
      return null;
    }
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      ServiceResponse response =
          NGRestUtils.getResponse(serviceResourceClient.getService(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      if (response == null || response.getService() == null) {
        return null;
      }
      ServiceResponseDTO responseDTO = response.getService();
      return YamlPipelineUtils.read(responseDTO.getYaml(), NGServiceConfig.class);
    } catch (Exception ex) {
      log.warn("Error when getting service - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
