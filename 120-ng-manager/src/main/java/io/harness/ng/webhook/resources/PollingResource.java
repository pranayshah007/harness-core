/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.dto.PollingResponseDTO;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.polling.PollingResponseHandler;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.polling.service.intfc.PollingService;
import io.harness.security.annotations.InternalApi;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("polling")
@Path("polling")
@Produces({"application/json", "text/yaml", "text/html"})
@InternalApi
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PollingResource {
  private KryoSerializer kryoSerializer;
  private PollingResponseHandler pollingResponseHandler;
  private PollingService pollingService;

  @POST
  @Path("delegate-response/{perpetualTaskId}")
  @ApiOperation(hidden = true, value = "Communication APIs for polling framework.", nickname = "processPollingResult")
  public void processPollingResultNg(@PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, byte[] serializedExecutionResponse) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      PollingDelegateResponse executionResponse =
          (PollingDelegateResponse) kryoSerializer.asObject(serializedExecutionResponse);
      pollingResponseHandler.handlePollingResponse(perpetualTaskId, accountId, executionResponse);
    }
  }

  @POST
  @Path("subscribe")
  @ApiOperation(hidden = true, value = "Subscribe API for polling framework.", nickname = "subscribePolling")
  public ResponseDTO<PollingResponseDTO> subscribe(byte[] pollingItem) {
    String pollingDocId = pollingService.subscribe((PollingItem) kryoSerializer.asObject(pollingItem));
    return ResponseDTO.newResponse(
        PollingResponseDTO.builder()
            .pollingResponse(kryoSerializer.asBytes(PollingDocument.newBuilder().setPollingDocId(pollingDocId).build()))
            .build());
  }

  @POST
  @Path("unsubscribe")
  @ApiOperation(hidden = true, value = "Unsubscribe API for polling framework.", nickname = "unsubscribePolling")
  public Boolean unsubscribe(byte[] pollingItem) {
    return pollingService.unsubscribe((PollingItem) kryoSerializer.asObject(pollingItem));
  }
}
