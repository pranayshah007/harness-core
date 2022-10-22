package io.harness.ccm.remote.resources.governance;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import static io.harness.annotations.dev.HarnessTeam.CE;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.scheduler.SchedulerDTO;
import io.harness.ccm.views.dto.CreatePolicyEnforcementDTO;
import io.harness.ccm.views.dto.CreatePolicyExecutionDTO;
import io.harness.ccm.views.dto.CreatePolicyPackDTO;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.PolicyExecutionService;
import io.harness.exception.InvalidRequestException;
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
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

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

public class GovernancePolicyExecutionResource {
    private final CCMRbacHelper rbacHelper;
    private final PolicyExecutionService policyExecutionService;

    @Inject
    public GovernancePolicyExecutionResource(CCMRbacHelper rbacHelper, PolicyExecutionService policyExecutionService) {
        this.rbacHelper = rbacHelper;
        this.policyExecutionService = policyExecutionService;
    }

    @POST
    @Hidden
    @Path("execution")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Add a new policy execution api", nickname = "addPolicyExecution")
    @Operation(operationId = "addPolicyExecution", summary = "Add a new policy execution ",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.
                                    ApiResponse(responseCode = "default", description = "Returns newly created policy Execution")
                    })
    public ResponseDTO<PolicyExecution>
    create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
           @RequestBody(required = true, description = "Request body containing Policy Execution store object")
           @Valid CreatePolicyExecutionDTO createPolicyExecutionDTO) {
        // rbacHelper.checkPolicyExecutionEditPermission(accountId, null, null);
        PolicyExecution policyExecution = createPolicyExecutionDTO.getPolicyExecution();
        policyExecution.setAccountId(accountId);
        policyExecutionService.save(policyExecution);
        return ResponseDTO.newResponse(policyExecution.toDTO());
    }

    @POST
    @Path("execution/list")
    @ApiOperation(value = "Get execution for account", nickname = "getPolicyExecution")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getPolicyExecution", description = "Fetch PolicyExecution ", summary = "Fetch PolicyExecution for account",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                                    description = "Returns List of PolicyExecution", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
                    })
    public ResponseDTO<List<PolicyExecution>>
    listPolicyPack(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
                   @RequestBody(required = true,
                           description = "Request body containing policy pack object") @Valid CreatePolicyPackDTO createPolicyPackDTO) {
        // rbacHelper.checkPolicyExecutionPermission(accountId, null, null);

        return ResponseDTO.newResponse(policyExecutionService.list(accountId));
    }

}
