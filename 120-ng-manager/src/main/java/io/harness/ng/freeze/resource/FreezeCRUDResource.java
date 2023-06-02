/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.freeze.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.services.AccountService;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.PermissionTypes;
import io.harness.freeze.beans.request.FreezeFilterPropertiesDTO;
import io.harness.freeze.beans.response.FreezeBannerDetails;
import io.harness.freeze.beans.response.FreezeDetailedResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.FrozenExecutionDetails;
import io.harness.freeze.beans.response.GlobalFreezeBannerDetailsResponseDTO;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.entity.FreezeConstants;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeCRUDService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.repositories.FreezeConfigRepository;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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

@NextGenManagerAuth
@Api("/freeze")
@Path("/freeze")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Freeze CRUD", description = "This contains APIs related to Freeze CRUD")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@Slf4j
public class FreezeCRUDResource {
  private final FreezeCRUDService freezeCRUDService;
  private final AccessControlClient accessControlClient;
  private final FreezeConfigRepository freezeConfigRepository;
  private final NotificationHelper notificationHelper;
  private static final String DEPLOYMENTFREEZE = "DEPLOYMENTFREEZE";
  @Inject private AccountService accountService;
  @Inject NgExpressionHelper ngExpressionHelper;

  @POST
  @ApiOperation(value = "Creates a Freeze", nickname = "createFreeze")
  @NGAccessControlCheck(
      resourceType = DEPLOYMENTFREEZE, permission = PermissionTypes.DEPLOYMENT_FREEZE_MANAGE_PERMISSION)
  @Operation(operationId = "createFreeze", summary = "Create a Freeze",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Freeze Config")
      })
  public ResponseDTO<FreezeResponseDTO>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Freeze YAML", content = {
        @Content(examples = @ExampleObject(name = "Create", summary = "Sample Create Freeze YAML",
                     value = FreezeConstants.CREATE_API_YAML, description = "Sample Freeze YAML"))
      }) @NotNull String freezeYaml) {
    FreezeRBACHelper.checkAccess(accountId, projectId, orgId, freezeYaml, accessControlClient);
    return ResponseDTO.newResponse(freezeCRUDService.createFreezeConfig(freezeYaml, accountId, orgId, projectId));
  }

  @POST
  @Path("/manageGlobalFreeze")
  @ApiOperation(value = "Manage Global Freeze", nickname = "GlobalFreeze")
  @NGAccessControlCheck(
      resourceType = DEPLOYMENTFREEZE, permission = PermissionTypes.DEPLOYMENT_FREEZE_GLOBAL_PERMISSION)
  @Operation(operationId = "createGlobalFreeze", summary = "Create Global Freeze",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Global Freeze Config")
      })
  public ResponseDTO<FreezeResponseDTO>
  manageGlobalFreeze(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Freeze YAML") @NotNull String freezeYaml) {
    return ResponseDTO.newResponse(freezeCRUDService.manageGlobalFreezeConfig(freezeYaml, accountId, orgId, projectId));
  }

  @PUT
  @Path("/{freezeIdentifier}")
  @ApiOperation(value = "Updates a Freeze", nickname = "updateFreeze")
  @NGAccessControlCheck(
      resourceType = DEPLOYMENTFREEZE, permission = PermissionTypes.DEPLOYMENT_FREEZE_MANAGE_PERMISSION)
  @Operation(operationId = "updateFreeze", summary = "Updates a Freeze",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Freeze Config")
      })

  public ResponseDTO<FreezeResponseDTO>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Freeze Identifier.") @PathParam(
          "freezeIdentifier") @ResourceIdentifier String freezeIdentifier,
      @RequestBody(required = true, description = "Freeze YAML", content = {
        @Content(examples = @ExampleObject(name = "Update", summary = "Sample Update Freeze YAML",
                     value = FreezeConstants.UPDATE_API_YAML, description = "Sample Freeze YAML"))
      }) @NotNull String freezeYaml) {
    return ResponseDTO.newResponse(
        freezeCRUDService.updateFreezeConfig(freezeYaml, accountId, orgId, projectId, freezeIdentifier));
  }

  @POST
  @Path("/updateFreezeStatus")
  @ApiOperation(value = "Update the status of Freeze to active or inactive", nickname = "updateFreezeStatus")
  @Operation(operationId = "updateFreezeStatus", summary = "Update the status of Freeze to active or inactive",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Freeze Config")
      })
  public ResponseDTO<FreezeResponseWrapperDTO>
  updateFreezeStatus(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Comma seperated List of Freeze Identifiers") List<String> freezeIdentifiers,
      @Parameter(description = "Freeze YAML") @NotNull @QueryParam("status") FreezeStatus freezeStatus) {
    for (String identifier : freezeIdentifiers) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of(DEPLOYMENTFREEZE, identifier), PermissionTypes.DEPLOYMENT_FREEZE_MANAGE_PERMISSION);
    }
    return ResponseDTO.newResponse(
        freezeCRUDService.updateActiveStatus(freezeStatus, accountId, orgId, projectId, freezeIdentifiers));
  }

  @DELETE
  @Path("/{freezeIdentifier}")
  @ApiOperation(value = "Delete a Freeze", nickname = "deleteFreeze")
  @NGAccessControlCheck(
      resourceType = DEPLOYMENTFREEZE, permission = PermissionTypes.DEPLOYMENT_FREEZE_MANAGE_PERMISSION)
  @Operation(operationId = "deleteFreeze", summary = "Delete a Freeze",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Freeze Config")
      })
  public void
  delete(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Freeze Identifier.") @PathParam(
          "freezeIdentifier") @ResourceIdentifier String freezeIdentifier) {
    freezeCRUDService.deleteFreezeConfig(freezeIdentifier, accountId, orgId, projectId);
  }

  @POST
  @Path("/delete")
  @ApiOperation(value = "Deletes many Freezes", nickname = "deleteManyFreezes")
  @Operation(operationId = "deleteManyFreezes", summary = "Delete many Freezes",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Freeze Config")
      })
  public ResponseDTO<FreezeResponseWrapperDTO>
  deleteMany(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "List of Freeze Identifiers") List<String> freezeIdentifiers) {
    for (String identifier : freezeIdentifiers) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of("DEPLOYMENTFREEZE", identifier), PermissionTypes.DEPLOYMENT_FREEZE_MANAGE_PERMISSION);
    }

    return ResponseDTO.newResponse(
        freezeCRUDService.deleteFreezeConfigs(freezeIdentifiers, accountId, orgId, projectId));
  }

  @GET
  @Path("{freezeIdentifier}")
  @ApiOperation(value = "Get a Freeze", nickname = "getFreeze")
  @Operation(operationId = "getFreeze", summary = "Get a Freeze",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Freeze Config")
      })
  public ResponseDTO<FreezeDetailedResponseDTO>
  get(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Freeze Identifier.") @PathParam(
          "freezeIdentifier") @ResourceIdentifier String freezeIdentifier) {
    return ResponseDTO.newResponse(NGFreezeDtoMapper.prepareDetailedFreezeResponseDto(
        freezeCRUDService.getFreezeConfig(freezeIdentifier, accountId, orgId, projectId)));
  }

  @POST
  @Path("notification/{freezeIdentifier}")
  @ApiOperation(value = "Send Notification", nickname = "sendNotification")
  @Operation(operationId = "sendNotification", summary = "send Notification",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Send Notification")
      })
  @Hidden
  public boolean
  sendNotification(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Freeze Identifier.") @PathParam(
          "freezeIdentifier") @ResourceIdentifier String freezeIdentifier) throws IOException {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    FreezeConfigEntity freezeConfigEntity = null;
    if (freezeConfigEntityOptional.isPresent()) {
      freezeConfigEntity = freezeConfigEntityOptional.get();
    }
    notificationHelper.sendNotification(freezeConfigEntity.getYaml(), true, true, null, accountId, "", "", false);
    return true;
  }

  @GET
  @Path("/getGlobalFreeze")
  @ApiOperation(value = "Get Global Freeze Yaml", nickname = "getGlobalFreeze")
  @Operation(operationId = "getGlobalFreeze", summary = "Get Global Freeze Yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get Global Freeze Yaml")
      })
  public ResponseDTO<FreezeDetailedResponseDTO>
  getGlobalFreeze(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId) {
    return ResponseDTO.newResponse(NGFreezeDtoMapper.prepareDetailedFreezeResponseDto(
        freezeCRUDService.getGlobalFreeze(accountId, orgId, projectId)));
  }

  @GET
  @Path("/getGlobalFreezeWithBannerDetails")
  @ApiOperation(value = "Get Global Freeze Yaml with Banner Details", nickname = "getGlobalFreezeWithBannerDetails")
  @Operation(operationId = "getGlobalFreezeWithBannerDetails", summary = "Get Global Freeze Yaml with Banner Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get Global Freeze Yaml")
      })
  @Hidden
  public ResponseDTO<GlobalFreezeBannerDetailsResponseDTO>
  getGlobalFreezeWithBannerDetails(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId) {
    List<FreezeResponseDTO> freezeResponseDTOS =
        freezeCRUDService.getParentGlobalFreezeSummary(accountId, orgId, projectId);
    freezeResponseDTOS.add(freezeCRUDService.getGlobalFreeze(accountId, orgId, projectId));
    List<FreezeBannerDetails> activeOrUpcomingGlobalFreezes =
        freezeResponseDTOS.stream()
            .filter(freezeResponseDTO -> FreezeStatus.ENABLED.equals(freezeResponseDTO.getStatus()))
            .map(NGFreezeDtoMapper::prepareBanner)
            .collect(Collectors.toList());
    activeOrUpcomingGlobalFreezes =
        activeOrUpcomingGlobalFreezes.stream()
            .filter(activeOrUpcomingParentGlobalFreeze -> activeOrUpcomingParentGlobalFreeze.getWindow() != null)
            .collect(Collectors.toList());
    AccountDTO accountDTO = accountService.getAccount(accountId);
    if (accountDTO != null && accountDTO.getName() != null && activeOrUpcomingGlobalFreezes.size() > 0) {
      activeOrUpcomingGlobalFreezes.forEach(
          freezeBannerDetails -> freezeBannerDetails.setAccountName(accountDTO.getName()));
    }
    GlobalFreezeBannerDetailsResponseDTO globalFreezeBannerDetailsResponseDTO =
        GlobalFreezeBannerDetailsResponseDTO.builder()
            .activeOrUpcomingGlobalFreezes(activeOrUpcomingGlobalFreezes)
            .build();
    return ResponseDTO.newResponse(globalFreezeBannerDetailsResponseDTO);
  }

  @GET
  @Path("/getFrozenExecutionDetails")
  @ApiOperation(value = "Get list of freeze acted on a frozen execution", nickname = "getFrozenExecutionDetails")
  @Operation(operationId = "getFrozenExecutionDetails", summary = "Get list of freeze acted on a frozen execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of freeze acted on a frozen execution")
      })
  public ResponseDTO<FrozenExecutionDetails>
  getFrozenExecutionDetails(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                            @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    String baseUrl = ngExpressionHelper.getBaseUrl(accountId);
    return ResponseDTO.newResponse(
        freezeCRUDService.getFrozenExecutionDetails(accountId, orgId, projectId, planExecutionId, baseUrl));
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets Freeze Configs list ", nickname = "getFreezeList")
  @Operation(operationId = "getFreezeList", summary = "Gets Freeze list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Freeze for a Project")
      })
  public ResponseDTO<PageResponse<FreezeSummaryResponseDTO>>
  getFreezeList(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("10") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Freeze filters")
      FreezeFilterPropertiesDTO freezeFilterPropertiesDTO) {
    String searchTerm = freezeFilterPropertiesDTO == null ? null : freezeFilterPropertiesDTO.getSearchTerm();
    FreezeStatus status = freezeFilterPropertiesDTO == null ? null : freezeFilterPropertiesDTO.getFreezeStatus();
    Criteria criteria = FreezeFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
        searchTerm, FreezeType.MANUAL, status, freezeFilterPropertiesDTO.getStartTime(),
        freezeFilterPropertiesDTO.getEndTime());
    Pageable pageRequest;
    if (freezeFilterPropertiesDTO != null && isNotEmpty(freezeFilterPropertiesDTO.getFreezeIdentifiers())) {
      criteria.and(FreezeConfigEntityKeys.identifier).in(freezeFilterPropertiesDTO.getFreezeIdentifiers());
    }
    if (freezeFilterPropertiesDTO == null || isEmpty(freezeFilterPropertiesDTO.getSort())) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, freezeFilterPropertiesDTO.getSort());
    }
    Page<FreezeSummaryResponseDTO> freezeConfigEntities = freezeCRUDService.list(criteria, pageRequest);
    return ResponseDTO.newResponse(getNGPageResponse(freezeConfigEntities));
  }
}
