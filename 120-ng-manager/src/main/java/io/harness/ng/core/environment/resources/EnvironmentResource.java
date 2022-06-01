/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@Api("/environments")
@Path("/environments")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class EnvironmentResource {
  private final EnvironmentService environmentService;

  @GET
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Gets a Environment by identifier", nickname = "getEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> get(@PathParam("environmentIdentifier") String environmentIdentifier,
      @QueryParam("accountId") String accountId, @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    Optional<Environment> environment =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, deleted);
    return ResponseDTO.newResponse(
        environment.get().getVersion().toString(), environment.map(EnvironmentMapper::writeDTO).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an Environment", nickname = "createEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> create(
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    Environment environmentEntity = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    Environment createdEnvironment = environmentService.create(environmentEntity);
    return ResponseDTO.newResponse(
        createdEnvironment.getVersion().toString(), EnvironmentMapper.writeDTO(createdEnvironment));
  }

  @DELETE
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Delete en environment by identifier", nickname = "deleteEnvironment")
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @PathParam("environmentIdentifier") String environmentIdentifier, @QueryParam("accountId") String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(environmentService.delete(accountId, orgIdentifier, projectIdentifier,
        environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @PUT
  @ApiOperation(value = "Update an environment by identifier", nickname = "updateEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> update(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Environment updatedEnvironment = environmentService.update(requestEnvironment);
    return ResponseDTO.newResponse(
        updatedEnvironment.getVersion().toString(), EnvironmentMapper.writeDTO(updatedEnvironment));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an environment by identifier", nickname = "upsertEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Environment upsertedEnvironment = environmentService.upsert(requestEnvironment);
    return ResponseDTO.newResponse(
        upsertedEnvironment.getVersion().toString(), EnvironmentMapper.writeDTO(upsertedEnvironment));
  }

  @GET
  @ApiOperation(value = "Gets environment list for a project", nickname = "getEnvironmentListForProject")
  public ResponseDTO<PageResponse<EnvironmentResponseDTO>> listEnvironmentsForProject(
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("100") int size,
      @QueryParam("accountId") String accountId, @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("envIdentifiers") List<String> envIdentifiers, @QueryParam("sort") List<String> sort) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    Pageable pageRequest;

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<EnvironmentResponseDTO> environmentList =
        environmentService.list(criteria, pageRequest).map(EnvironmentMapper::writeDTO);
    return ResponseDTO.newResponse(getNGPageResponse(environmentList));
  }
}
