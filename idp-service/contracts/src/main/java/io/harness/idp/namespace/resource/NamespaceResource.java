package io.harness.idp.namespace.resource;


import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;

@Api("/accounts/{accountId}")
@Path("/accounts/{accountId}")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
        {
                @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
                , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
        })
@OwnedBy(HarnessTeam.IDP)
public interface NamespaceResource {
    @GET
    @Path("/namespace")
    @ApiOperation(value = "Gets backstage NameSpace from accountID", nickname = "getBackstageAccountId")
    String getNamespace(@PathParam("accountId") @NotEmpty String accountId);
}
