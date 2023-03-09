/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.onboarding.utils.Constants.ACCOUNT_SCOPED;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_ALL_LOCATION_FILE_NAME;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_LOCATION_URL_TYPE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_REQUIRED_ERROR_MESSAGE;
import static io.harness.idp.onboarding.utils.Constants.ORGANIZATION;
import static io.harness.idp.onboarding.utils.Constants.PROJECT;
import static io.harness.idp.onboarding.utils.Constants.SERVICE;
import static io.harness.idp.onboarding.utils.Constants.SLASH_DELIMITER;
import static io.harness.idp.onboarding.utils.Constants.SOURCE_FORMAT;
import static io.harness.idp.onboarding.utils.Constants.STATUS_UPDATE_REASON_FOR_ONBOARDING_COMPLETED;
import static io.harness.idp.onboarding.utils.Constants.SUCCESS_RESPONSE_STRING;
import static io.harness.idp.onboarding.utils.Constants.YAML_FILE_EXTENSION;
import static io.harness.idp.onboarding.utils.FileUtils.createDirectories;
import static io.harness.idp.onboarding.utils.FileUtils.writeObjectAsYamlInFile;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageCatalogLocationCreateRequest;
import io.harness.clients.BackstageCatalogResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.idp.gitintegration.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.onboarding.OnboardingModuleConfig;
import io.harness.idp.onboarding.beans.BackstageCatalogComponentEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogDomainEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogLocationEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogSystemEntity;
import io.harness.idp.onboarding.beans.CatalogInfraConnectorType;
import io.harness.idp.onboarding.beans.OnboardingAccessResult;
import io.harness.idp.onboarding.entities.CatalogConnector;
import io.harness.idp.onboarding.mappers.HarnessEntityToBackstageEntity;
import io.harness.idp.onboarding.mappers.HarnessOrgToBackstageDomain;
import io.harness.idp.onboarding.mappers.HarnessProjectToBackstageSystem;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.CatalogConnectorRepository;
import io.harness.idp.onboarding.services.OnboardingService;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EntitiesForImport;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportHarnessEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingAccessCheckResponse;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OnboardingServiceImpl implements OnboardingService {
  @Inject @Named("onboardingModuleConfig") OnboardingModuleConfig onboardingModuleConfig;
  @Inject @Named("PRIVILEGED") AccessControlClient accessControlClient;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject ConnectorProcessorFactory connectorProcessorFactory;
  @Inject CatalogConnectorRepository catalogConnectorRepository;
  @Inject BackstageCatalogResourceClient backstageCatalogResourceClient;
  @Inject GitIntegrationService gitIntegrationService;
  @Inject StatusInfoService statusInfoService;

  @Override
  public OnboardingAccessCheckResponse accessCheck(String accountIdentifier, String userId) {
    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>();
    PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder()
                                                .resourceScope(ResourceScope.of(accountIdentifier, null, null))
                                                .resourceType("IDP_SETTINGS")
                                                .permission("idp_idpsettings_manage")
                                                .build();
    permissionCheckDTOS.add(permissionCheckDTO);
    AccessCheckResponseDTO accessCheckResponseDTO =
        accessControlClient.checkForAccess(Principal.of(PrincipalType.USER, userId), permissionCheckDTOS);
    boolean accessPermitted = accessCheckResponseDTO.getAccessControlList().get(0).isPermitted();
    OnboardingAccessCheckResponse onboardingAccessCheckResponse = new OnboardingAccessCheckResponse();
    onboardingAccessCheckResponse.setAccess(
        accessPermitted ? OnboardingAccessResult.ALLOWED.name() : OnboardingAccessResult.NOT_ALLOWED.name());
    return onboardingAccessCheckResponse;
  }

  @Override
  public HarnessEntitiesResponse getHarnessEntities(
      String accountIdentifier, int page, int limit, String sort, String order, String searchTerm) {
    List<OrganizationDTO> organizationDTOS = getOrganizations(accountIdentifier);
    List<ProjectDTO> projectDTOS = getProjects(accountIdentifier);
    List<ServiceResponseDTO> serviceResponseDTOS = getServices(accountIdentifier);

    List<BackstageCatalogDomainEntity> catalogDomains = harnessOrgToBackstageDomain(organizationDTOS);
    List<BackstageCatalogSystemEntity> catalogSystems = harnessProjectToBackstageSystem(projectDTOS);
    List<BackstageCatalogComponentEntity> catalogComponents = harnessServiceToBackstageComponent(serviceResponseDTOS);

    HarnessEntitiesResponse harnessEntitiesResponse = new HarnessEntitiesResponse();

    harnessEntitiesResponse.setOrgCount(organizationDTOS.size());
    harnessEntitiesResponse.setProjectCount(projectDTOS.size());
    harnessEntitiesResponse.setServiceCount(serviceResponseDTOS.size());

    List<HarnessBackstageEntities> harnessEntitiesResponseHarnessBackstageEntities = new ArrayList<>();

    harnessEntitiesResponseHarnessBackstageEntities.addAll(BackstageCatalogDomainEntity.map(catalogDomains));
    harnessEntitiesResponseHarnessBackstageEntities.addAll(BackstageCatalogSystemEntity.map(catalogSystems));
    harnessEntitiesResponseHarnessBackstageEntities.addAll(BackstageCatalogComponentEntity.map(catalogComponents));
    harnessEntitiesResponse.setHarnessBackstageEntities(harnessEntitiesResponseHarnessBackstageEntities);

    PageResponse<HarnessBackstageEntities> harnessBackstageEntities =
        PageUtils.offsetAndLimit(harnessEntitiesResponseHarnessBackstageEntities, page, limit);

    harnessEntitiesResponse.setHarnessBackstageEntities(harnessBackstageEntities.getContent());

    return harnessEntitiesResponse;
  }

  @Override
  public ImportEntitiesResponse importHarnessEntities(
      String accountIdentifier, ImportHarnessEntitiesRequest importHarnessEntitiesRequest) {
    List<EntitiesForImport> idpSaveHarnessEntities = importHarnessEntitiesRequest.getEntities();
    CatalogConnectorInfo catalogConnectorInfo = importHarnessEntitiesRequest.getCatalogConnectorInfo();

    List<String> orgToImport = idpSaveHarnessEntities.stream()
                                   .filter(entitiesForImport -> entitiesForImport.getEntityType().equals(ORGANIZATION))
                                   .map(EntitiesForImport::getIdentifier)
                                   .collect(Collectors.toList());
    List<String> projectToImport = idpSaveHarnessEntities.stream()
                                       .filter(entitiesForImport -> entitiesForImport.getEntityType().equals(PROJECT))
                                       .map(EntitiesForImport::getIdentifier)
                                       .collect(Collectors.toList());
    List<String> serviceToImport = idpSaveHarnessEntities.stream()
                                       .filter(entitiesForImport -> entitiesForImport.getEntityType().equals(SERVICE))
                                       .map(EntitiesForImport::getIdentifier)
                                       .collect(Collectors.toList());

    if (orgToImport.size() + projectToImport.size() + serviceToImport.size() == 0) {
      throw new InvalidRequestException(ENTITY_REQUIRED_ERROR_MESSAGE);
    }

    Map<String, Map<String, List<String>>> orgProjectsServicesMapping = getOrgProjectsServicesMapping(serviceToImport);
    Map<String, List<String>> orgProjectsMapping = getOrgProjectsMapping(projectToImport);

    List<OrganizationDTO> organizationDTOS =
        !orgToImport.isEmpty() ? getOrganizations(accountIdentifier, orgToImport) : new ArrayList<>();
    List<ProjectDTO> projectDTOS = orgProjectsMapping.size() > 0
        ? getProjects(accountIdentifier, orgProjectsMapping.keySet(),
            orgProjectsMapping.values().stream().flatMap(Collection::stream).collect(Collectors.toList()))
        : new ArrayList<>();
    List<ServiceResponseDTO> serviceResponseDTOS = orgProjectsServicesMapping.size() > 0
        ? getServices(accountIdentifier, orgProjectsServicesMapping)
        : new ArrayList<>();

    if (organizationDTOS.size() != orgToImport.size() || projectDTOS.size() != projectToImport.size()
        || serviceResponseDTOS.size() != serviceToImport.size()) {
      throw new UnexpectedException();
    }

    List<BackstageCatalogDomainEntity> catalogDomains = harnessOrgToBackstageDomain(organizationDTOS);
    List<BackstageCatalogSystemEntity> catalogSystems = harnessProjectToBackstageSystem(projectDTOS);
    List<BackstageCatalogComponentEntity> catalogComponents = harnessServiceToBackstageComponent(serviceResponseDTOS);

    catalogConnectorInfo.getInfraConnector().setIdentifier(
        catalogConnectorInfo.getInfraConnector().getIdentifier().replace(ACCOUNT_SCOPED, ""));
    catalogConnectorInfo.getSourceConnector().setIdentifier(
        catalogConnectorInfo.getSourceConnector().getIdentifier().replace(ACCOUNT_SCOPED, ""));

    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(
        ConnectorType.fromString(catalogConnectorInfo.getSourceConnector().getType()));

    String catalogInfraConnectorType = connectorProcessor.getInfraConnectorType(
        accountIdentifier, catalogConnectorInfo.getInfraConnector().getIdentifier());

    saveIdpCatalogConnector(
        accountIdentifier, catalogConnectorInfo, CatalogInfraConnectorType.valueOf(catalogInfraConnectorType));

    String tmpPathForCatalogInfoYamlStore =
        onboardingModuleConfig.getTmpPathForCatalogInfoYamlStore() + SLASH_DELIMITER + accountIdentifier;
    String entitiesFolderPath = !catalogConnectorInfo.getPath().isEmpty()
        ? catalogConnectorInfo.getPath()
        : onboardingModuleConfig.getCatalogInfoLocationDefaultPath();

    String catalogInfoLocationParentPath = tmpPathForCatalogInfoYamlStore + entitiesFolderPath + SLASH_DELIMITER;

    String orgYamlPath = catalogInfoLocationParentPath + ORGANIZATION + SLASH_DELIMITER;
    String projectYamlPath = catalogInfoLocationParentPath + PROJECT + SLASH_DELIMITER;
    String serviceYamlPath = catalogInfoLocationParentPath + SERVICE + SLASH_DELIMITER;

    createDirectories(orgYamlPath, projectYamlPath, serviceYamlPath);

    String entityTargetParentPath = catalogConnectorInfo.getRepo() + SLASH_DELIMITER + SOURCE_FORMAT + SLASH_DELIMITER
        + catalogConnectorInfo.getBranch() + entitiesFolderPath + SLASH_DELIMITER;
    String orgEntityTargetPath = entityTargetParentPath + ORGANIZATION + SLASH_DELIMITER;
    String projectEntityTargetPath = entityTargetParentPath + PROJECT + SLASH_DELIMITER;
    String serviceEntityTargetPath = entityTargetParentPath + SERVICE + SLASH_DELIMITER;

    BackstageCatalogEntity backstageCatalogEntityInitial =
        getFirstAmongAll(catalogDomains, catalogSystems, catalogComponents);

    String yamlPath;
    String entityTargetPath;
    if (backstageCatalogEntityInitial instanceof BackstageCatalogDomainEntity) {
      yamlPath = orgYamlPath;
      entityTargetPath = orgEntityTargetPath;
    } else if (backstageCatalogEntityInitial instanceof BackstageCatalogSystemEntity) {
      yamlPath = projectYamlPath;
      entityTargetPath = projectEntityTargetPath;
    } else if (backstageCatalogEntityInitial instanceof BackstageCatalogComponentEntity) {
      yamlPath = serviceYamlPath;
      entityTargetPath = serviceEntityTargetPath;
    } else {
      throw new UnexpectedException();
    }

    List<String> filesToPush =
        writeEntityAsYamlInFile(Collections.singletonList(backstageCatalogEntityInitial), yamlPath);
    String target =
        prepareEntitiesTarget(Collections.singletonList(backstageCatalogEntityInitial), entityTargetPath).get(0);
    connectorProcessor.performPushOperation(accountIdentifier, catalogConnectorInfo, filesToPush);
    registerLocationInBackstage(BACKSTAGE_LOCATION_URL_TYPE, target);

    new Thread(() -> {
      List<String> locationTargets = new ArrayList<>(Collections.singleton(target));

      List<String> targets;

      filesToPush.addAll(writeEntityAsYamlInFile(catalogDomains, orgYamlPath));
      targets = prepareEntitiesTarget(catalogDomains, orgEntityTargetPath);
      locationTargets.addAll(targets);

      filesToPush.addAll(writeEntityAsYamlInFile(catalogSystems, projectYamlPath));
      targets = prepareEntitiesTarget(catalogSystems, projectEntityTargetPath);
      locationTargets.addAll(targets);

      filesToPush.addAll(writeEntityAsYamlInFile(catalogComponents, serviceYamlPath));
      targets = prepareEntitiesTarget(catalogComponents, serviceEntityTargetPath);
      locationTargets.addAll(targets);

      locationTargets.addAll(onboardingModuleConfig.getSampleEntities());

      BackstageCatalogLocationEntity backstageCatalogLocationEntity =
          buildBackstageCatalogLocationEntity(onboardingModuleConfig.getBackstageLocationEntityAllHarnessEntitiesName(),
              onboardingModuleConfig.getBackstageLocationEntityAllHarnessEntitiesDesc(), BACKSTAGE_LOCATION_URL_TYPE,
              locationTargets);
      writeObjectAsYamlInFile(backstageCatalogLocationEntity,
          catalogInfoLocationParentPath + BACKSTAGE_ALL_LOCATION_FILE_NAME + YAML_FILE_EXTENSION);
      filesToPush.add(catalogInfoLocationParentPath + BACKSTAGE_ALL_LOCATION_FILE_NAME + YAML_FILE_EXTENSION);
      String allTargetLocation = catalogConnectorInfo.getRepo() + SLASH_DELIMITER + SOURCE_FORMAT + SLASH_DELIMITER
          + catalogConnectorInfo.getBranch() + entitiesFolderPath + SLASH_DELIMITER + BACKSTAGE_ALL_LOCATION_FILE_NAME
          + YAML_FILE_EXTENSION;

      connectorProcessor.performPushOperation(accountIdentifier, catalogConnectorInfo, filesToPush);

      registerLocationInBackstage(BACKSTAGE_LOCATION_URL_TYPE, allTargetLocation);
      createCatalogInfraConnectorSecretInBackstageK8S(accountIdentifier, catalogConnectorInfo);

      saveStatusInfo(accountIdentifier, StatusType.ONBOARDING.name(), StatusInfo.CurrentStatusEnum.COMPLETED,
          STATUS_UPDATE_REASON_FOR_ONBOARDING_COMPLETED);
    }).start();

    return new ImportEntitiesResponse().status(SUCCESS_RESPONSE_STRING);
  }

  private List<OrganizationDTO> getOrganizations(String accountIdentifier) {
    PageResponse<OrganizationResponse> organizationResponse =
        getResponse(organizationClient.listAllOrganizations(accountIdentifier, new ArrayList<>()));
    return organizationResponse.getContent()
        .stream()
        .map(OrganizationResponse::getOrganization)
        .collect(Collectors.toList());
  }

  private List<ProjectDTO> getProjects(String accountIdentifier) {
    return getResponse(projectClient.getProjectList(accountIdentifier));
  }

  private List<ServiceResponseDTO> getServices(String accountIdentifier) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    AtomicReference<PageResponse<ServiceResponse>> services = new AtomicReference<>();
    int page = 0;
    int size = 100;
    do {
      services.set(getResponse(
          serviceResourceClient.listServicesForProject(page, size, accountIdentifier, null, null, null, null)));
      if (services.get() != null && isNotEmpty(services.get().getContent())) {
        serviceResponseDTOS.addAll(
            services.get().getContent().stream().map(ServiceResponse::getService).collect(Collectors.toList()));
      }
      page++;
    } while (services.get() != null && isNotEmpty(services.get().getContent()));
    return serviceResponseDTOS;
  }

  private List<BackstageCatalogDomainEntity> harnessOrgToBackstageDomain(List<OrganizationDTO> organizationDTOList) {
    HarnessOrgToBackstageDomain harnessOrgToBackstageDomainMapper =
        (HarnessOrgToBackstageDomain) getMapperByType(ORGANIZATION);
    return organizationDTOList.stream().map(harnessOrgToBackstageDomainMapper::map).collect(Collectors.toList());
  }

  private List<BackstageCatalogSystemEntity> harnessProjectToBackstageSystem(List<ProjectDTO> projectDTOList) {
    HarnessProjectToBackstageSystem harnessProjectToBackstageSystemMapper =
        (HarnessProjectToBackstageSystem) getMapperByType(PROJECT);
    return projectDTOList.stream().map(harnessProjectToBackstageSystemMapper::map).collect(Collectors.toList());
  }

  private List<BackstageCatalogComponentEntity> harnessServiceToBackstageComponent(
      List<ServiceResponseDTO> serviceResponseDTOList) {
    HarnessServiceToBackstageComponent harnessServiceToBackstageComponent =
        (HarnessServiceToBackstageComponent) getMapperByType(SERVICE);
    return serviceResponseDTOList.stream().map(harnessServiceToBackstageComponent::map).collect(Collectors.toList());
  }

  private HarnessEntityToBackstageEntity<?, ? extends BackstageCatalogEntity> getMapperByType(String type) {
    switch (type) {
      case ORGANIZATION:
        return new HarnessOrgToBackstageDomain();
      case PROJECT:
        return new HarnessProjectToBackstageSystem();
      case SERVICE:
        return new HarnessServiceToBackstageComponent();
      default:
        throw new UnsupportedOperationException(type + " type not supported for harness to backstage entity mapping");
    }
  }

  private Map<String, List<String>> getOrgProjectsMapping(List<String> harnessEntitiesProjects) {
    Map<String, List<String>> projectIdentifiers = new HashMap<>();
    harnessEntitiesProjects.forEach(project -> {
      String[] orgProject = project.split("\\|");
      if (projectIdentifiers.containsKey(orgProject[0])) {
        List<String> existingProjects = projectIdentifiers.get(orgProject[0]);
        existingProjects.add(orgProject[1]);
        projectIdentifiers.put(orgProject[0], existingProjects);
      } else {
        projectIdentifiers.put(orgProject[0], Collections.singletonList(orgProject[1]));
      }
    });
    return projectIdentifiers;
  }

  private Map<String, Map<String, List<String>>> getOrgProjectsServicesMapping(List<String> harnessEntitiesServices) {
    Map<String, Map<String, List<String>>> serviceIdentifiers = new HashMap<>();
    harnessEntitiesServices.forEach(service -> {
      String[] orgProjectService = service.split("\\|");
      if (serviceIdentifiers.containsKey(orgProjectService[0])) {
        Map<String, List<String>> existingProjectsServices = serviceIdentifiers.get(orgProjectService[0]);
        if (existingProjectsServices.containsKey(orgProjectService[1])) {
          List<String> existingServices = existingProjectsServices.get(orgProjectService[1]);
          existingServices.add(orgProjectService[2]);
          existingProjectsServices.put(orgProjectService[1], existingServices);
          serviceIdentifiers.put(orgProjectService[0], existingProjectsServices);
        } else {
          existingProjectsServices.put(orgProjectService[1], Collections.singletonList(orgProjectService[2]));
          serviceIdentifiers.put(orgProjectService[0], existingProjectsServices);
        }
      } else {
        serviceIdentifiers.put(
            orgProjectService[0], Map.of(orgProjectService[1], Collections.singletonList(orgProjectService[2])));
      }
    });
    return serviceIdentifiers;
  }

  private List<OrganizationDTO> getOrganizations(String accountIdentifier, List<String> identifiers) {
    List<OrganizationDTO> organizationDTOS = new ArrayList<>();
    PageResponse<OrganizationResponse> organizations;
    int page = 0;
    int size = 100;
    do {
      organizations =
          getResponse(organizationClient.listOrganization(accountIdentifier, identifiers, null, page, size, null));
      if (organizations != null && isNotEmpty(organizations.getContent())) {
        organizationDTOS.addAll(organizations.getContent()
                                    .stream()
                                    .map(OrganizationResponse::getOrganization)
                                    .collect(Collectors.toList()));
      }
      page++;
    } while (organizations != null && isNotEmpty(organizations.getContent()));
    return organizationDTOS;
  }

  private List<ProjectDTO> getProjects(
      String accountIdentifier, Set<String> organizationIdentifiers, List<String> identifiers) {
    List<ProjectDTO> projectDTOS = new ArrayList<>();
    PageResponse<ProjectResponse> projects;
    int page = 0;
    int size = 100;
    do {
      projects = getResponse(projectClient.listWithMultiOrg(
          accountIdentifier, organizationIdentifiers, false, identifiers, null, null, page, size, null));
      if (projects != null && isNotEmpty(projects.getContent())) {
        projectDTOS.addAll(
            projects.getContent().stream().map(ProjectResponse::getProject).collect(Collectors.toList()));
      }
      page++;
    } while (projects != null && isNotEmpty(projects.getContent()));
    return projectDTOS;
  }

  private List<ServiceResponseDTO> getServices(
      String accountIdentifier, Map<String, Map<String, List<String>>> orgProjectsServicesMapping) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    for (var serviceIdentifier : orgProjectsServicesMapping.entrySet()) {
      String org = serviceIdentifier.getKey();
      for (var projectService : serviceIdentifier.getValue().entrySet()) {
        PageResponse<ServiceResponse> services;
        int page = 0;
        int size = 100;
        do {
          services = getResponse(serviceResourceClient.listServicesForProject(
              page, size, accountIdentifier, org, projectService.getKey(), projectService.getValue(), null));
          if (services != null && isNotEmpty(services.getContent())) {
            serviceResponseDTOS.addAll(
                services.getContent().stream().map(ServiceResponse::getService).collect(Collectors.toList()));
          }
          page++;
        } while (services != null && isNotEmpty(services.getContent()));
      }
    }
    return serviceResponseDTOS;
  }

  private void saveIdpCatalogConnector(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      CatalogInfraConnectorType catalogInfraConnectorType) {
    CatalogConnector catalogConnector = new CatalogConnector();

    catalogConnector.setAccountId(accountIdentifier);
    catalogConnector.setIdentifier(catalogConnectorInfo.getInfraConnector().getIdentifier() + "_"
        + catalogConnectorInfo.getSourceConnector().getIdentifier());
    catalogConnector.setType(catalogInfraConnectorType);
    catalogConnector.setInfraConnector(catalogConnectorInfo.getInfraConnector());
    catalogConnector.setSourceConnector(catalogConnectorInfo.getSourceConnector());
    catalogConnector.setRepo(catalogConnectorInfo.getRepo());
    catalogConnector.setBranch(catalogConnectorInfo.getBranch());
    catalogConnector.setPath(catalogConnectorInfo.getPath());

    catalogConnectorRepository.save(catalogConnector);
  }

  private BackstageCatalogEntity getFirstAmongAll(List<? extends BackstageCatalogEntity>... backstageCatalogEntities) {
    for (List<? extends BackstageCatalogEntity> backstageCatalogEntity : backstageCatalogEntities) {
      for (BackstageCatalogEntity catalogEntity : backstageCatalogEntity) {
        return catalogEntity;
      }
    }
    throw new UnexpectedException();
  }

  private List<String> writeEntityAsYamlInFile(List<? extends BackstageCatalogEntity> entities, String prefixPath) {
    List<String> files = new ArrayList<>();
    entities.forEach(entity -> {
      writeObjectAsYamlInFile(entity, prefixPath + entity.getMetadata().getIdentifier() + YAML_FILE_EXTENSION);
      files.add(prefixPath + entity.getMetadata().getIdentifier() + YAML_FILE_EXTENSION);
    });
    return files;
  }

  private List<String> prepareEntitiesTarget(List<? extends BackstageCatalogEntity> entities, String prefixPath) {
    List<String> targets = new ArrayList<>();
    entities.forEach(entity -> targets.add(prefixPath + entity.getMetadata().getIdentifier() + YAML_FILE_EXTENSION));
    return targets;
  }

  private BackstageCatalogLocationEntity buildBackstageCatalogLocationEntity(
      String name, String description, String type, List<String> targets) {
    BackstageCatalogLocationEntity backstageCatalogLocationEntity = new BackstageCatalogLocationEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setName(name);
    metadata.setDescription(description);
    backstageCatalogLocationEntity.setMetadata(metadata);

    BackstageCatalogLocationEntity.Spec spec = new BackstageCatalogLocationEntity.Spec();
    spec.setType(type);
    spec.setTargets(targets);
    backstageCatalogLocationEntity.setSpec(spec);

    return backstageCatalogLocationEntity;
  }

  private void registerLocationInBackstage(String type, String allTarget) {
    try {
      getResponse(backstageCatalogResourceClient.createCatalogLocation(
          new BackstageCatalogLocationCreateRequest(type, allTarget)));
    } catch (Exception e) {
      log.error("Unable to register target location in backstage, ex = {}", e.getMessage(), e);
    }
  }

  private void createCatalogInfraConnectorSecretInBackstageK8S(
      String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo) {
    try {
      gitIntegrationService.createConnectorSecretsEnvVariable(accountIdentifier, null, null,
          catalogConnectorInfo.getInfraConnector().getIdentifier(),
          ConnectorType.fromString(catalogConnectorInfo.getInfraConnector().getType()));
    } catch (Exception e) {
      log.error("Unable to create infra connector secrets in backstage k8s, ex = {}", e.getMessage(), e);
    }
  }

  private void saveStatusInfo(
      String accountIdentifier, String type, StatusInfo.CurrentStatusEnum currentStatus, String reason) {
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(currentStatus);
    statusInfo.setReason(reason);
    statusInfoService.save(statusInfo, accountIdentifier, type);
  }
}
