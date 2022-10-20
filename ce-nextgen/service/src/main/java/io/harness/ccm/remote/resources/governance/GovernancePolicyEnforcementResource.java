package io.harness.ccm.remote.resources.governance;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import io.harness.accesscontrol.AccountIdentifier;
import static io.harness.annotations.dev.HarnessTeam.CE;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreatePolicyDTO;
import io.harness.ccm.views.dto.CreatePolicyEnforcementDTO;
import io.harness.ccm.views.dto.CreatePolicyPackDTO;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyEnforcementService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.PublicApi;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
import java.util.List;

@Slf4j
@Service
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json"})
@Tag(name = "PolicyEnforcement", description = "This contains APIs related to Policy Enforcement ")
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
//@NextGenManagerAuth
@PublicApi
public class GovernancePolicyEnforcementResource {
    private final PolicyEnforcementService policyEnforcementService;
    private final CCMRbacHelper rbacHelper;
    private final GovernancePolicyService policyService;
    private final PolicyPackService policyPackService;


    @Inject

    public GovernancePolicyEnforcementResource(PolicyEnforcementService policyEnforcementService, CCMRbacHelper rbacHelper, GovernancePolicyService governancePolicyService, PolicyPackService policyPackService) {
        this.policyEnforcementService = policyEnforcementService;
        this.rbacHelper = rbacHelper;
        this.policyService = governancePolicyService;
        this.policyPackService = policyPackService;
    }
    @POST
    @Hidden
    @Path("enforcement")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Add a new policy Enforcement api", nickname = "addPolicyEnforcement")
    @Operation(operationId = "addPolicyEnforcement", summary = "Add a new policy Enforcement ",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.
                                    ApiResponse(responseCode = "default", description = "Returns newly created policy")
                    })
    public ResponseDTO<PolicyEnforcement>
    create(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
           @RequestBody(required = true,
                   description = "Request body containing Policy store object") @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
        PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
        policyEnforcement.setAccountId(accountId);
        policyEnforcementService.check(accountId,policyEnforcement.getPolicyIds(),policyEnforcement.getPolicyPackIDs());
        policyEnforcementService.save(policyEnforcement);
        return ResponseDTO.newResponse(policyEnforcement.toDTO());
    }

    @PUT
    @Hidden
    @Path("enforcement")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a Policy enforcement", nickname = "updateEnforcement")
    @LogAccountIdentifier
    @Operation(operationId = "updateEnforcement", description = "Update a Policy enforcement", summary = "Update a Policy enforcement",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update a existing Policy enforcement",
                                    content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
                    })
    public ResponseDTO<PolicyEnforcement>
    updatePolicy(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
                 @RequestBody(required = true,
                         description = "Request body containing policy enforcement object") @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
        PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
        policyEnforcement.setAccountId(accountId);
        for(String identifiers: policyEnforcement.getPolicyIds() )
        {
            policyService.listid(accountId,identifiers);
        }
        for(String identifiers: policyEnforcement.getPolicyPackIDs() )
        {
            policyPackService.listid(accountId,identifiers);
        }
        policyEnforcementService.update(policyEnforcement);
        return ResponseDTO.newResponse(policyEnforcement.toDTO());
    }
    @DELETE
    @Hidden
    @Path("enforcement/{enforcementID}")
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Delete a policy", nickname = "deletePolicy")
    @LogAccountIdentifier
    @Operation(operationId = "deletePolicyEnforcement", description = "Delete a Policy enforcement for the given a ID.",
            summary = "Delete a policy enforcement",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.
                                    ApiResponse(description = "A boolean whether the delete was successful or not",
                                    content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
                    })
    public ResponseDTO<Boolean>
    delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
           @PathParam("enforcementID") @Parameter(
                   required = true, description = "Unique identifier for the policy enforcement") @NotNull @Valid String uuid) {
        policyEnforcementService.listid(accountId,uuid);
        boolean result = policyEnforcementService.delete(accountId, uuid);
        return ResponseDTO.newResponse(result);
    }



    @POST
    @Path("enforcement/list")
    @ApiOperation(value = "Get enforcement list", nickname = "getPolicyEnforcement")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getPolicyEnforcement", description = "Fetch Policy Enforcement ", summary = "Fetch Policy Enforcement for account",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                                    description = "Returns List of policies  Enforcement", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
                    })
    public ResponseDTO<List<PolicyEnforcement>>
    listPolicyPack(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
                   @RequestBody(
                           required = true, description = "Request body containing  Policy Enforcement  object") @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {

        return ResponseDTO.newResponse(policyEnforcementService.list(accountId));
    }

}
