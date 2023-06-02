/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.currency;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.core.currency.CurrencyPreferenceService;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyDTO;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api(value = "currency-preference")
@Path("currency-preference")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Currency Preferences",
    description = "Select destination currency to view different cloud provider currencies in destination currency")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class CurrencyPreferenceResource {
  private static final String CLOUD_SERVICE_PROVIDER_KEY = "cloudServiceProvider";
  private static final String DESTINATION_CURRENCY_KEY = "destinationCurrency";
  private static final String SOURCE_CURRENCY_KEY = "sourceCurrency";

  private final CurrencyPreferenceService currencyPreferenceService;
  private final CCMRbacHelper ccmRbacHelper;

  @Inject
  public CurrencyPreferenceResource(
      final CurrencyPreferenceService currencyPreferenceService, final CCMRbacHelper ccmRbacHelper) {
    this.currencyPreferenceService = currencyPreferenceService;
    this.ccmRbacHelper = ccmRbacHelper;
  }

  @GET
  @Path("currencies")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get all destination currencies", nickname = "getAllCurrencies")
  @Operation(operationId = "getCurrencies", description = "Get all available destination currencies",
      summary = "Get all available destination currencies present in the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of all available destination currencies present",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CurrencyDTO>
  getAllCurrencies() {
    return ResponseDTO.newResponse(currencyPreferenceService.getCurrencies());
  }

  @GET
  @Path("conversion-factors")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get conversion factors for an account", nickname = "getCurrencyConversionFactors")
  @Operation(operationId = "getConversionFactor",
      description = "Get conversion factors of all different cloud providers present",
      summary = "Get conversion factors of all different cloud providers present in the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a list which will have conversion factors of different cloud providers",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CurrencyConversionFactorDTO>
  getCurrencyConversionFactors(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam(DESTINATION_CURRENCY_KEY) @Parameter(
          required = true, description = "Selected destination currency") Currency destinationCurrency) {
    ccmRbacHelper.checkCurrencyPreferenceViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(
        currencyPreferenceService.getCurrencyConversionFactorData(accountId, destinationCurrency));
  }

  @GET
  @Path("destination-conversion-factor")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get destination currency conversion factor for an account",
      nickname = "getDestinationCurrencyConversionFactor")
  @Operation(operationId = "getDestinationCurrencyConversionFactor",
      description = "Get destination currency conversion factor for a specific cloud provider",
      summary = "Get destination currency conversion factor for a specific cloud provider present in the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description =
                        "Returns a double value which will represent the destination currency conversion factor for a "
                + "specific cloud provider",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Double>
  getDestinationCurrencyConversionFactor(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam(CLOUD_SERVICE_PROVIDER_KEY) @Parameter(
          required = true, description = "Cloud Service Provider") CloudServiceProvider cloudServiceProvider,
      @NotNull @Valid @QueryParam(SOURCE_CURRENCY_KEY) @Parameter(
          required = true, description = "Source currency") Currency sourceCurrency) {
    // Right now, this API we are using only in node-pool recommendation page.
    return ResponseDTO.newResponse(currencyPreferenceService.getDestinationCurrencyConversionFactor(
        accountId, cloudServiceProvider, sourceCurrency));
  }

  @POST
  @Path("conversion-factors")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Create conversion factors for an account", nickname = "createCurrencyConversionFactors")
  @Operation(operationId = "createCurrencyConversionFactors",
      description = "Create conversion factors of all different cloud providers present",
      summary = "Create conversion factors of all different cloud providers present in the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a list which will have conversion factors of different cloud providers",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  createCurrencyConversionFactors(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam(DESTINATION_CURRENCY_KEY) @Parameter(
          required = true, description = "Selected destination currency") Currency destinationCurrency,
      @RequestBody(required = true, description = "Request body containing Currency Conversion factors")
      @Valid CurrencyConversionFactorDTO currencyConversionFactorDTO) {
    ccmRbacHelper.checkCurrencyPreferenceEditPermission(accountId, null, null);
    final boolean isCreatedCurrencyConversionFactors = currencyPreferenceService.createCurrencyConversionFactors(
        accountId, destinationCurrency, currencyConversionFactorDTO);
    if (isCreatedCurrencyConversionFactors) {
      currencyPreferenceService.updateCEMetadataCurrencyPreferenceRecord(accountId, destinationCurrency);
    }
    return ResponseDTO.newResponse(isCreatedCurrencyConversionFactors);
  }
}
