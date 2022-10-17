/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static javax.ws.rs.core.UriBuilder.fromPath;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.ServiceEntityValidationHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.ProjectEnvironmentsApi;
import io.harness.spec.server.ng.model.EnvironmentRequest;
import io.harness.spec.server.ng.model.EnvironmentResponse;
import io.harness.spec.server.ng.model.ServiceOverrideRequest;
import io.harness.spec.server.ng.model.ServiceOverrideResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ProjectEnvironmentsApiImpl implements ProjectEnvironmentsApi {
  public static final int FIRST_PAGE = 1;
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ServiceOverrideService serviceOverrideService;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final ServiceEntityValidationHelper serviceEntityValidationHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final EnvironmentsResourceApiUtils environmentsResourceApiUtils;
  @Override
  public Response createEnvironment(EnvironmentRequest environmentRequest, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(environmentRequest);
    if (environmentRequest.getType() == null) {
      throw new InvalidRequestException(
          "Type for an environment cannot be empty. Possible values: " + Arrays.toString(EnvironmentType.values()));
    }
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentRequest.getType().toString());
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(ENVIRONMENT, null, environmentAttributes), ENVIRONMENT_CREATE_PERMISSION);
    Environment environmentEntity =
        environmentsResourceApiUtils.toEnvironmentEntity(account, environmentRequest, org, project);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(environmentEntity.getOrgIdentifier(),
        environmentEntity.getProjectIdentifier(), environmentEntity.getAccountId());
    Environment createdEnvironment = environmentService.create(environmentEntity);

    return Response.status(Response.Status.CREATED)
        .entity(environmentsResourceApiUtils.toEnvironmentResponseWrapper(createdEnvironment))
        .tag(createdEnvironment.getVersion().toString())
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = ENVIRONMENT, permission = "core_environment_delete")
  public Response deleteEnvironment(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, @AccountIdentifier String account) {
    environmentService.delete(account, org, project, environment, isNumeric("ifMatch") ? parseLong("ifMatch") : null);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @NGAccessControlCheck(resourceType = ENVIRONMENT, permission = "core_environment_view")
  @Override
  public Response getEnvironment(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, @AccountIdentifier String account) {
    Optional<Environment> environmentOptional = environmentService.get(account, org, project, environment, false);
    String version = "0";
    if (environmentOptional.isPresent()) {
      version = environmentOptional.get().getVersion().toString();
      if (EmptyPredicate.isEmpty(environmentOptional.get().getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environmentOptional.get());
        environmentOptional.get().setYaml(environmentsResourceApiUtils.toYaml(ngEnvironmentConfig));
      }
    } else {
      throw new NotFoundException(String.format(
          "Environment with identifier [%s] in project [%s], org [%s] not found", environment, project, org));
    }

    Environment optionalEnvironment = environmentOptional.get();
    return Response.ok()
        .entity(environmentsResourceApiUtils.toEnvironmentResponseWrapper(optionalEnvironment))
        .tag(version)
        .build();
  }

  @Override
  public Response getEnvironments(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String account, Integer page, Integer limit, String searchTerm, List<String> environmentIds,
      String sort, String order, Boolean isAccessList) {
    if (isAccessList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(PROJECT, project),
          VIEW_PROJECT_PERMISSION, "Unauthorized to list environments");
      Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(account, org, project, false);

      if (isNotEmpty(environmentIds)) {
        criteria.and(EnvironmentKeys.identifier).in(environmentIds);
      }

      List<EnvironmentResponse> environmentList = environmentService.listAccess(criteria)
                                                      .stream()
                                                      .map(environmentsResourceApiUtils::toEnvironmentResponseWrapper)
                                                      .collect(Collectors.toList());

      List<PermissionCheckDTO> permissionCheckDTOS =
          environmentList.stream()
              .map(environmentsResourceApiUtils::environmentResponseToPermissionCheckDTO)
              .collect(Collectors.toList());
      List<AccessControlDTO> accessControlList =
          accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();

      List<EnvironmentResponse> filterEnvironmentList =
          filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList);
      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks = addLinksHeader(responseBuilder,
          format("/v1/orgs/%s/projects/%s/services)", org, project), filterEnvironmentList.size(), page, limit);
      return responseBuilderWithLinks.entity(filterEnvironmentList).build();
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, null),
        ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to list environments");
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(account, org, project, false, searchTerm);
    Pageable pageRequest;

    if (isNotEmpty(environmentIds)) {
      criteria.and(EnvironmentKeys.identifier).in(environmentIds);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      String sortQuery = environmentsResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
    environmentEntities.forEach(environment -> {
      if (EmptyPredicate.isEmpty(environment.getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
        environment.setYaml(environmentsResourceApiUtils.toYaml(ngEnvironmentConfig));
      }
    });

    Page<EnvironmentResponse> environmentResponsePage =
        environmentEntities.map(environmentsResourceApiUtils::toEnvironmentResponseWrapper);
    List<EnvironmentResponse> environmentResponseList = environmentResponsePage.getContent();

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks = addLinksHeader(responseBuilder,
        format("/v1/orgs/%s/projects/%s/services)", org, project), environmentResponseList.size(), page, limit);

    return responseBuilderWithLinks.entity(environmentResponseList).build();
  }

  @Override
  public Response updateEnvironment(EnvironmentRequest environmentRequest, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String environment, @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(environmentRequest);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, environment), ENVIRONMENT_UPDATE_PERMISSION);

    Environment requestEnvironment =
        environmentsResourceApiUtils.toEnvironmentEntity(account, environmentRequest, org, project);
    Environment updatedEnvironment = environmentService.update(requestEnvironment);
    return Response.ok()
        .entity(environmentsResourceApiUtils.toEnvironmentResponseWrapper(updatedEnvironment))
        .tag(updatedEnvironment.getVersion().toString())
        .build();
  }

  @Override
  public Response createEnvServiceOverride(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, String service, ServiceOverrideRequest serviceOverrideRequest,
      @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(serviceOverrideRequest);

    NGServiceOverridesEntity serviceOverridesEntity = environmentsResourceApiUtils.toServiceOverridesEntity(
        account, serviceOverrideRequest, org, project, environment, service);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getAccountId());
    environmentValidationHelper.checkThatEnvExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getEnvironmentRef());
    serviceEntityValidationHelper.checkThatServiceExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getServiceRef());
    checkForServiceOverrideUpdateAccess(account, serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
        serviceOverridesEntity.getServiceRef());
    validateServiceOverrides(serviceOverridesEntity);

    NGServiceOverridesEntity createdServiceOverride = serviceOverrideService.upsert(serviceOverridesEntity);
    return Response.status(Response.Status.CREATED)
        .entity(environmentsResourceApiUtils.toServiceOverrideResponse(createdServiceOverride))
        .build();
  }

  @Override
  public Response deleteEnvServiceOverride(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, String service, @AccountIdentifier String account) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);
    environmentValidationHelper.checkThatEnvExists(account, org, project, environment);
    serviceEntityValidationHelper.checkThatServiceExists(account, org, project, service);
    // check access for service and env
    checkForServiceOverrideUpdateAccess(account, org, project, environment, service);
    serviceOverrideService.delete(account, org, project, environment, service);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getEnvServiceOverride(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, String service, @AccountIdentifier String account) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);
    environmentValidationHelper.checkThatEnvExists(account, org, project, environment);
    if (isNotEmpty(service)) {
      serviceEntityValidationHelper.checkThatServiceExists(account, org, project, service);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(ENVIRONMENT, environment), ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to view environment");
    Optional<NGServiceOverridesEntity> serviceOverridesEntity =
        serviceOverrideService.get(account, org, project, environment, service);
    if (serviceOverridesEntity.isPresent()) {
      if (EmptyPredicate.isEmpty(serviceOverridesEntity.get().getYaml())) {
        NGServiceOverrideConfig ngServiceOverrideConfig =
            NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity.get());
        serviceOverridesEntity.get().setYaml(ServiceOverridesMapper.toYaml(ngServiceOverrideConfig));
      }
    } else {
      throw new NotFoundException(String.format(
          "Service Override for Environment with identifier [%s] and Service with identifier [%s] in project [%s], org [%s] not found",
          environment, service, project, org));
    }
    return Response.ok()
        .entity(environmentsResourceApiUtils.toServiceOverrideResponse(serviceOverridesEntity.get()))
        .build();
  }

  @Override
  public Response getEnvServiceOverrides(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, String sort, @AccountIdentifier String account, String order,
      Integer page, Integer limit) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);
    environmentValidationHelper.checkThatEnvExists(account, org, project, environment);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(ENVIRONMENT, environment), ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to view environment");

    Criteria criteria =
        environmentFilterHelper.createCriteriaForGetServiceOverrides(account, org, project, environment, null);
    Pageable pageRequest;

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.createdAt));
    } else {
      String sortQuery = environmentsResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    Page<NGServiceOverridesEntity> serviceOverridesEntities = serviceOverrideService.list(criteria, pageRequest);
    Page<ServiceOverrideResponse> serviceResponsePage =
        serviceOverridesEntities.map(environmentsResourceApiUtils::toServiceOverrideResponse);
    List<ServiceOverrideResponse> serviceOverrideResponseList = serviceResponsePage.getContent();

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks = addLinksHeader(responseBuilder,
        format("/v1/orgs/%s/projects/%s/services)", org, project), serviceOverrideResponseList.size(), page, limit);

    return responseBuilderWithLinks.entity(serviceOverrideResponseList).build();
  }

  @Override
  public Response updateEnvServiceOverride(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String environment, String service, ServiceOverrideRequest serviceOverrideRequest,
      @AccountIdentifier String account) {
    throwExceptionForNoRequestDTO(serviceOverrideRequest);

    NGServiceOverridesEntity serviceOverridesEntity = environmentsResourceApiUtils.toServiceOverridesEntity(
        account, serviceOverrideRequest, org, project, environment, service);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getAccountId());
    environmentValidationHelper.checkThatEnvExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getEnvironmentRef());
    serviceEntityValidationHelper.checkThatServiceExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getServiceRef());
    checkForServiceOverrideUpdateAccess(account, serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
        serviceOverridesEntity.getServiceRef());
    validateServiceOverrides(serviceOverridesEntity);

    NGServiceOverridesEntity createdServiceOverride = serviceOverrideService.upsert(serviceOverridesEntity);
    return Response.ok().entity(environmentsResourceApiUtils.toServiceOverrideResponse(createdServiceOverride)).build();
  }

  private void throwExceptionForNoRequestDTO(EnvironmentRequest dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }
  private void validateServiceOverrides(NGServiceOverridesEntity serviceOverridesEntity) {
    final NGServiceOverrideConfig serviceOverrideConfig =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity);
    if (serviceOverrideConfig.getServiceOverrideInfoConfig() != null) {
      final NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          serviceOverrideConfig.getServiceOverrideInfoConfig();

      if (isEmpty(serviceOverrideInfoConfig.getManifests()) && isEmpty(serviceOverrideInfoConfig.getConfigFiles())
          && isEmpty(serviceOverrideInfoConfig.getVariables())
          && serviceOverrideInfoConfig.getApplicationSettings() == null
          && serviceOverrideInfoConfig.getConnectionStrings() == null) {
        final Optional<NGServiceOverridesEntity> optionalNGServiceOverrides =
            serviceOverrideService.get(serviceOverridesEntity.getAccountId(), serviceOverridesEntity.getOrgIdentifier(),
                serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
                serviceOverridesEntity.getServiceRef());
        if (!optionalNGServiceOverrides.isPresent()) {
          throw new InvalidRequestException("No overrides found in request");
        }
      }
      environmentsResourceApiUtils.checkDuplicateManifestIdentifiersWithIn(serviceOverrideInfoConfig.getManifests());
      environmentsResourceApiUtils.checkDuplicateConfigFilesIdentifiersWithIn(
          serviceOverrideInfoConfig.getConfigFiles());
    }
  }

  public void checkForServiceOverrideUpdateAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    final List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                   .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                   .resourceIdentifier(environmentRef)
                                   .resourceType(ENVIRONMENT)
                                   .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                   .build());
    permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                   .permission(SERVICE_UPDATE_PERMISSION)
                                   .resourceIdentifier(serviceRef)
                                   .resourceType(SERVICE)
                                   .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                   .build());

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionCheckDTOList);
    accessCheckResponse.getAccessControlList().forEach(accessControlDTO -> {
      if (!accessControlDTO.isPermitted()) {
        String errorMessage;
        errorMessage = String.format("Missing permission %s on %s", accessControlDTO.getPermission(),
            accessControlDTO.getResourceType().toLowerCase());
        if (!StringUtils.isEmpty(accessControlDTO.getResourceIdentifier())) {
          errorMessage =
              errorMessage.concat(String.format(" with identifier %s", accessControlDTO.getResourceIdentifier()));
        }
        throw new InvalidRequestException(errorMessage, WingsException.USER);
      }
    });
  }

  private void throwExceptionForNoRequestDTO(ServiceOverrideRequest serviceOverrideRequest) {
    if (serviceOverrideRequest == null) {
      throw new InvalidRequestException("No request body for Service overrides sent in the API");
    }
  }
  private List<EnvironmentResponse> filterEnvironmentResponseByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<EnvironmentResponse> environmentList) {
    List<EnvironmentResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      EnvironmentResponse environmentResponse = environmentList.get(i);
      if (accessControlDTO.isPermitted()
          && environmentResponse.getEnvironment().getSlug().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(environmentResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= FIRST_PAGE) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
}
