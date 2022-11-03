/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.rbac.CCMRbacHelper;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_PACK_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_PACK_DELETE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_PACK_UPDATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.remote.resources.TelemetryConstants.POLICY_PACK_NAME;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreatePolicyPackDTO;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.harness.telemetry.Category;
import static io.harness.telemetry.Destination.AMPLITUDE;
import io.harness.telemetry.TelemetryReporter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("governance")
@Path("governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
//@NextGenManagerAuth
@PublicApi
@Service
@OwnedBy(CE)
@Slf4j
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })

public class GovernancePolicyPackResource {
  public static final String ACCOUNT_ID = "accountId";
  private final CCMRbacHelper rbacHelper;
  private final PolicyPackService policyPackService;
  private final GovernancePolicyService policyService;
  private final TelemetryReporter telemetryReporter;
  @Inject CENextGenConfiguration configuration;

  @Inject
  public GovernancePolicyPackResource(
      PolicyPackService policyPackService, CCMRbacHelper rbacHelper, GovernancePolicyService policyService
  , TelemetryReporter telemetryReporter) {
    this.rbacHelper = rbacHelper;
    this.policyPackService = policyPackService;
    this.policyService = policyService;
    this.telemetryReporter = telemetryReporter;
  }

  @POST
  @Path("policyPack")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new policy set", nickname = "addPolicyPack")
  @Operation(operationId = "addPolicyPack", summary = "Add a policy pack ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created policy pack")
      })
  public ResponseDTO<PolicyPack>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Policy pack object") @Valid CreatePolicyPackDTO createPolicyPackDTO) {
    // rbacHelper.checkPolicyPackEditPermission(accountId, null, null);
    if(createPolicyPackDTO==null)
    {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyPack policyPack = createPolicyPackDTO.getPolicyPack();
    if (policyPackService.listName(accountId, policyPack.getName(), true) != null) {
      throw new InvalidRequestException("Policy pack with this name already exits");
    }
    if (!policyPack.getIsOOTB()) {
      policyPack.setAccountId(accountId);
    }
    else {
      policyPack.setAccountId("");
    }
    policyService.check(policyPack.getPoliciesIdentifier());
    policyPackService.save(policyPack);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_PACK_NAME,policyPack.getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_PACK_CREATED,null,accountId, properties
            , Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(policyPack.toDTO());
  }

  @PUT
  @Path("policyPack")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update an existing policy pack", nickname = "updatePolicyPack")
  @LogAccountIdentifier
  @Operation(operationId = "updatePolicyPack", description = "Update a Policy set", summary = "Update a Policy set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update a existing Policy pack",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<PolicyPack>
  updatePolicy(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Policy pack object") @Valid CreatePolicyPackDTO createPolicyPackDTO) {
    //  rbacHelper.checkPolicyPackEditPermission(accountId, null, null);
    if(createPolicyPackDTO==null)
    {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyPack policyPack = createPolicyPackDTO.getPolicyPack();
    policyPack.toDTO();
    policyPack.setAccountId(accountId);
    policyPackService.listName(accountId, policyPack.getName(), false);
    policyService.check(policyPack.getPoliciesIdentifier());
    policyPackService.update(policyPack);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_PACK_NAME,policyPack.getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_PACK_UPDATED,null,accountId, properties
            , Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(policyPack);
  }

  @PUT
  @Hidden
  @Path("policyPackOOTB")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update an existing policy set", nickname = "updateOOTBPolicyPack")
  @LogAccountIdentifier
  @Operation(operationId = "updateOOTBPolicyPack", description = "Update a  OOTB Policy Pack", summary = "Update a  OOTB Policy Pack",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update an existing OOTB Policy pack",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<PolicyPack>
  updatePolicy(@RequestBody(required = true,
      description = "Request body containing Policy pack object") @Valid CreatePolicyPackDTO createPolicyPackDTO) {
    //  rbacHelper.checkPolicyPackEditPermission(accountId, null, null);
    if(createPolicyPackDTO==null)
    {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyPack policyPack = createPolicyPackDTO.getPolicyPack();
    policyPack.toDTO();
    policyPack.setAccountId("");
    policyPackService.listName("", policyPack.getName(), false);
    policyService.check(policyPack.getPoliciesIdentifier());
    policyPackService.update(policyPack);
    return ResponseDTO.newResponse(policyPack);
  }

  @POST
  @Path("policyPack/list/{id}")
  @ApiOperation(value = "Get policies for pack", nickname = "getPoliyPacks")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPoliyPacks", description = "Fetch policies packs ", summary = "Fetch policy packs for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns List of policy packs", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<Policy>>
  listPolicy(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing policy packs object") @Valid CreatePolicyPackDTO createPolicyPackDTO,
      @PathParam("id") @Parameter(
          required = true, description = "Unique identifier for the policy packs") @NotNull @Valid String uuid) {
    // rbacHelper.checkPolicyPackViewPermission(accountId, null, null);
    PolicyPack query = createPolicyPackDTO.getPolicyPack();
    List<Policy> Policies = new ArrayList<>();
    policyPackService.listName(accountId, query.getName(), false);
    policyService.check(query.getPoliciesIdentifier());

    return ResponseDTO.newResponse(Policies);
  }

  @POST
  @Path("policypack/list")
  @ApiOperation(value = "list all policy packs", nickname = "listPolicyPacks")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "listPolicyPacks", description = "list Policy Packs ",
      summary = "Fetch policy packs for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of policy packs",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<PolicyPack>>
  listPolicyPack(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing policy pack object") @Valid CreatePolicyPackDTO createPolicyPackDTO) {
    // rbacHelper.checkPolicyPackPermission(accountId, null, null);
    if(createPolicyPackDTO==null)
    {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyPack policyPack= createPolicyPackDTO.getPolicyPack();
    return ResponseDTO.newResponse(policyPackService.list(accountId,policyPack));
  }

  @DELETE
  @Path("policypack/{policyPackId}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a policy set", nickname = "deletePolicyPack")
  @LogAccountIdentifier
  @Operation(operationId = "deletePolicyPack", description = "Delete a Policy set for the given a ID.",
      summary = "Delete a policy set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("policyPackId") @Parameter(
          required = true, description = "Unique identifier for the policy") @NotNull @Valid String name) {
    // rbacHelper.checkPolicyPackDeletePermission(accountId, null, null);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_PACK_NAME,name);
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_PACK_DELETE,null,accountId, properties
            , Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    boolean result = policyPackService.delete(accountId, policyPackService.listName(accountId, name, false).getUuid());
    return ResponseDTO.newResponse(result);
  }

  @DELETE
  @Hidden
  @Path("policypackOOTB/{uuid}")
  @Timed
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "delete OOTB Policy Pack", nickname = "deleteOOTBPolicyPack")
  @LogAccountIdentifier
  @Operation(operationId = "deleteOOTBPolicyPack", description = "delete OOTB Policy Pack for the given a ID.",
      summary = "delete OOTB Policy Pack",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  deleteOOTB(@PathParam("uuid") @Parameter(
      required = true, description = "Unique identifier for the Policy Pack") @NotNull @Valid String uuid) {
    boolean result = policyPackService.deleteOOTB(uuid);
    return ResponseDTO.newResponse(result);
  }
}
