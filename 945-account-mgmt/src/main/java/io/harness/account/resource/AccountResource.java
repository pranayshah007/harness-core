package io.harness.account.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.account.AccountClient;
import io.harness.account.AccountConfig;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.RestClientUtils;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@OwnedBy(HarnessTeam.GTM)
@Api("accounts")
@Path("accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts", description = "This contains APIs related to accounts as defined in Harness")
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
@NextGenManagerAuth
@AuthRule(permissionType = LOGGED_IN)
public class AccountResource {
  private final AccountClient accountClient;
  private final AccountConfig accountConfig;

  @Inject
  public AccountResource(AccountClient accountClient, AccountConfig accountConfig) {
    this.accountClient = accountClient;
    this.accountConfig = accountConfig;
  }

  @GET
  @Path("{accountIdentifier}")
  @ApiOperation(value = "Get Account", nickname = "getAccountNG")
  @Operation(operationId = "getAccountNG", summary = "Gets an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns an account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<AccountDTO>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
      "accountIdentifier") @AccountIdentifier String accountIdentifier) {
    AccountDTO accountDTO = RestClientUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));

    accountDTO.setCluster(accountConfig.getDeploymentClusterName());

    return ResponseDTO.newResponse(accountDTO);
  }

  @PUT
  @Path("{accountIdentifier}/name")
  @ApiOperation(value = "Update Account Name", nickname = "updateAccountNameNG")
  @Operation(operationId = "updateAccountNameNG", summary = "Update Account Name",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns an account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<AccountDTO>
  updateAccountName(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
                        "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is details of the Account. Name is mandatory.") AccountDTO dto) {
    AccountDTO accountDTO = RestClientUtils.getResponse(accountClient.updateAccountName(accountIdentifier, dto));

    return ResponseDTO.newResponse(accountDTO);
  }

  @PUT
  @Path("{accountIdentifier}/default-experience")
  @ApiOperation(value = "Update Default Experience", nickname = "updateAccountDefaultExperienceNG")
  @Operation(operationId = "updateAccountDefaultExperienceNG", summary = "Update Default Experience",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns an account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<AccountDTO>
  updateDefaultExperience(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
                              "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description = "This is details of the Account. DefaultExperience is mandatory") AccountDTO dto) {
    AccountDTO accountDTO = RestClientUtils.getResponse(accountClient.updateDefaultExperience(accountIdentifier, dto));

    return ResponseDTO.newResponse(accountDTO);
  }
}
