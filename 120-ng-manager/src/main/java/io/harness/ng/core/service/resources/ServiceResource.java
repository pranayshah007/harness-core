/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static software.wings.beans.Service.ServiceKeys;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Api("/services")
@Path("/services")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Deprecated
public class ServiceResource {
  private final ServiceEntityService serviceEntityService;
  private final ServiceEntityManagementService serviceEntityManagementService;

  private static final int MAX_LIMIT = 1000;

  @GET
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Gets a Service by identifier", nickname = "getService")
  public ResponseDTO<ServiceResponseDTO> get(@PathParam("serviceIdentifier") String serviceIdentifier,
      @QueryParam("accountId") String accountId, @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, deleted);
    if (!serviceEntity.isPresent()) {
      throw new NotFoundException(String.format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(serviceEntity.map(ServiceElementMapper::writeDTO).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create a Service", nickname = "createService")
  public ResponseDTO<ServiceResponseDTO> create(
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(createdService));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Services", nickname = "createServices")
  public ResponseDTO<PageResponse<ServiceResponseDTO>> createServices(@QueryParam("accountId") String accountId,
      @NotNull @Valid @Size(max = MAX_LIMIT) List<ServiceRequestDTO> serviceRequestDTOs) {
    List<ServiceEntity> serviceEntities =
        serviceRequestDTOs.stream()
            .map(serviceRequestDTO -> {
              ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
              return ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
            })
            .collect(Collectors.toList());
    Page<ServiceEntity> createdServices = serviceEntityService.bulkCreate(accountId, serviceEntities);
    return ResponseDTO.newResponse(getNGPageResponse(createdServices.map(ServiceElementMapper::writeDTO)));
  }

  @DELETE
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Delete a service by identifier", nickname = "deleteService")
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @PathParam("serviceIdentifier") String serviceIdentifier, @QueryParam("accountId") String accountId,
      @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(serviceEntityManagementService.deleteService(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, ifMatch, false));
  }

  @PUT
  @ApiOperation(value = "Update a service by identifier", nickname = "updateService")
  public ResponseDTO<ServiceResponseDTO> update(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceEntity updatedService = serviceEntityService.update(requestService);
    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(updatedService));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert a service by identifier", nickname = "upsertService")
  public ResponseDTO<ServiceResponseDTO> upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceEntity upsertedService = serviceEntityService.upsert(requestService, UpsertOptions.DEFAULT);
    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(upsertedService));
  }

  @GET
  @ApiOperation(value = "Gets Service list for a project", nickname = "getServiceListForProject")
  public ResponseDTO<PageResponse<ServiceResponseDTO>> listServicesForProject(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("100") @Max(MAX_LIMIT) int size, @QueryParam("accountId") String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers, @QueryParam("sort") List<String> sort) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    Pageable pageRequest;
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<ServiceResponseDTO> serviceList =
        serviceEntityService.list(criteria, pageRequest).map(ServiceElementMapper::writeDTO);
    return ResponseDTO.newResponse(getNGPageResponse(serviceList));
  }

  @GET
  @Path("helmCmdFlags")
  @ApiOperation(value = "Get Command flags based on Deployment Type", nickname = "helmCmdFlags")
  public ResponseDTO<Set<HelmCommandFlagType>> getHelmCommandFlags(
      @QueryParam("serviceSpecType") @NotNull String serviceSpecType,
      @QueryParam("version") @NotNull HelmVersion version, @QueryParam("storeType") @NotNull String storeType) {
    Set<HelmCommandFlagType> helmCmdFlags = new HashSet<>();
    for (HelmCommandFlagType helmCommandFlagType : HelmCommandFlagType.values()) {
      if (helmCommandFlagType.getServiceSpecTypes().contains(serviceSpecType)
          && helmCommandFlagType.getSubCommandType().getHelmVersions().contains(version)
          && helmCommandFlagType.getStoreTypes().contains(storeType)) {
        helmCmdFlags.add(helmCommandFlagType);
      }
    }
    return ResponseDTO.newResponse(helmCmdFlags);
  }
}
