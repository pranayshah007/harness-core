/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.licensing.accesscontrol.LicenseAccessControlPermissions.VIEW_LICENSE_PERMISSION;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.licensing.accesscontrol.ResourceTypes;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.FfSubscriptionDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.services.SubscriptionService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(value = "subscriptions")
@Path("subscriptions")
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Subscriptions", description = "This contains APIs related to license subscriptions as defined in Harness")
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
@Hidden
@NextGenManagerAuth
public class SubscriptionResource {
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String CUSTOMER_ID = "customerId";
  @Inject private SubscriptionService subscriptionService;

  @GET
  @Path("/prices")
  @ApiOperation(value = "Retrieves product prices", nickname = "retrieveProductPrices")
  @Operation(operationId = "retrieveProductPrices", summary = "Retrieves product prices",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns product prices")
      })
  // TODO define new resource type and permission for billing functions, define new role e.g. "Billing Admin" and have
  // proper pemission assgined
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<PriceCollectionDTO>
  retrieveProductPrices(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Module Type") @NotNull @QueryParam("moduleType") ModuleType moduleType) {
    return ResponseDTO.newResponse(subscriptionService.listPrices(accountIdentifier, moduleType));
  }

  @POST
  @ApiOperation(value = "Creates a feature flag subscription", nickname = "createFfSubscription")
  @Operation(operationId = "createFfSubscription", summary = "Creates a feature flag subscription",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns subscription details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<InvoiceDetailDTO>
  createFfSubscription(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is the details of the Subscription Request.") @NotNull
      @Valid FfSubscriptionDTO subscriptionDTO) {
    return ResponseDTO.newResponse(subscriptionService.createFfSubscription(accountIdentifier, subscriptionDTO));
  }

  //  @POST
  //  @ApiOperation(value = "Creates a subscription", nickname = "createSubscription")
  //  @Operation(operationId = "createSubscription", summary = "Creates a subscription",
  //      responses =
  //      {
  //        @io.swagger.v3.oas.annotations.responses.
  //        ApiResponse(responseCode = "default", description = "Returns subscription details")
  //      })
  //  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  //  public ResponseDTO<SubscriptionDetailDTO>
  //  createSubscription(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
  //                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
  //      @io.swagger.v3.oas.annotations.parameters.RequestBody(
  //          required = true, description = "This is the details of the Subscription Request.") @NotNull
  //      @Valid SubscriptionDTO subscriptionDTO) {
  //    return ResponseDTO.newResponse(subscriptionService.createSubscription(accountIdentifier, subscriptionDTO));
  //  }

  @PUT
  @Path("/{subscriptionId}")
  @ApiOperation(value = "Updates a subscription", nickname = "updateSubscription")
  @Operation(operationId = "updateSubscription", summary = "Updates a subscription",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns subscription details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<SubscriptionDetailDTO>
  updateSubscription(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @PathParam(
          SUBSCRIPTION_ID) String subscriptionId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is the details of the Subscription Request.") @NotNull
      @Valid SubscriptionDTO subscriptionDTO) {
    return ResponseDTO.newResponse(
        subscriptionService.updateSubscription(accountIdentifier, subscriptionId, subscriptionDTO));
  }

  @DELETE
  @Path("/{subscriptionId}")
  @ApiOperation(value = "Cancel a subscription", nickname = "cancelSubscription")
  @Operation(operationId = "cancelSubscription", summary = "Cancel a subscription",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns subscription details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<Void>
  cancelSubscription(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Subscription Identifier for the Entity") @NotNull @PathParam(
          SUBSCRIPTION_ID) String subscriptionId) {
    subscriptionService.cancelSubscription(accountIdentifier, subscriptionId);
    return ResponseDTO.newResponse();
  }

  @GET
  @Path("/{subscriptionId}")
  @ApiOperation(value = "Retrieves a subscription", nickname = "retrieveSubscription")
  @Operation(operationId = "retrieveSubscription", summary = "Retrieves a subscription",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns subscription details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<SubscriptionDetailDTO>
  retrieveSubscription(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Subscription Identifier for the Entity") @NotNull @PathParam(
          SUBSCRIPTION_ID) String subscriptionId) {
    return ResponseDTO.newResponse(subscriptionService.getSubscription(accountIdentifier, subscriptionId));
  }

  @GET
  @ApiOperation(value = "Lists the subscriptions", nickname = "listSubscriptions")
  @Operation(operationId = "listSubscriptions", summary = "Lists the subscriptions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns List of the subscription details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<List<SubscriptionDetailDTO>>
  listSubscriptions(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Module type for the Entity") @QueryParam(
          "moduleType") ModuleType moduleType) {
    return ResponseDTO.newResponse(subscriptionService.listSubscriptions(accountIdentifier, moduleType));
  }

  @POST
  @Path("/invoices/preview")
  @ApiOperation(value = "Retrieves the upcoming Invoice details", nickname = "retrieveUpcomingInvoice")
  @Operation(operationId = "retrieveUpcomingInvoice", summary = "Retrieves the upcoming Invoice details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns upcoming invoice")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<InvoiceDetailDTO>
  retrieveUpcomingInvoice(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is the details of the Subscription Request.") @NotNull
      @Valid SubscriptionDTO subscriptionDTO) {
    return ResponseDTO.newResponse(subscriptionService.previewInvoice(accountIdentifier, subscriptionDTO));
  }

  @POST
  @Path("/customers")
  @ApiOperation(value = "Creates the customer", nickname = "createCustomer")
  @Operation(operationId = "createCustomer", summary = "Creates the customer",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns customer details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<CustomerDetailDTO>
  createCustomer(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is the information of the Stripe Customer Request.") @NotNull
      @Valid CustomerDTO customerDTO) {
    return ResponseDTO.newResponse(subscriptionService.createStripeCustomer(accountIdentifier, customerDTO));
  }

  @PUT
  @Path("/customers/{customerId}")
  @ApiOperation(value = "Updates the customer", nickname = "updateCustomer")
  @Operation(operationId = "updateCustomer", summary = "Update the customer",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns customer details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<CustomerDetailDTO>
  updateCustomer(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Customer Identifier for the Entity") @NotNull @PathParam(
          CUSTOMER_ID) String customerId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "This is the information of the Stripe Customer Request.") @NotNull
      @Valid CustomerDTO customerDTO) {
    return ResponseDTO.newResponse(
        subscriptionService.updateStripeCustomer(accountIdentifier, customerId, customerDTO));
  }

  @GET
  @Path("/customers/{customerId}")
  @ApiOperation(value = "Retrieves the customer", nickname = "retrieveCustomer")
  @Operation(operationId = "retrieveCustomer", summary = "Retrieves the customer",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns customer details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<CustomerDetailDTO>
  retrieveCustomer(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Customer Identifier for the Entity") @NotNull @PathParam(
          CUSTOMER_ID) String customerId) {
    return ResponseDTO.newResponse(subscriptionService.getStripeCustomer(accountIdentifier, customerId));
  }

  //  @GET
  //  @Path("/customers")
  //  @ApiOperation(value = "Lists all customers", nickname = "listCustomers")
  //  @Operation(operationId = "listCustomers", summary = "Lists all customers",
  //      responses =
  //      {
  //        @io.swagger.v3.oas.annotations.responses.
  //        ApiResponse(responseCode = "default", description = "Returns customer details")
  //      })
  //  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  //  public ResponseDTO<List<CustomerDetailDTO>>
  //  listCustomers(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
  //      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
  //    return ResponseDTO.newResponse(subscriptionService.listStripeCustomers(accountIdentifier));
  //  }

  @GET
  @Path("/payment_methods")
  @ApiOperation(value = "Lists all payment methods for the customer", nickname = "listPaymentMethods")
  @Operation(operationId = "listPaymentMethods", summary = "Lists all payment methods for the customer",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns payment details")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  public ResponseDTO<PaymentMethodCollectionDTO>
  listPaymentMethods(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Customer Identifier for the Entity") @NotNull @QueryParam(
          "customerId") String customerId) {
    return ResponseDTO.newResponse(subscriptionService.listPaymentMethods(accountIdentifier, customerId));
  }

  @GET
  @Path("/exists")
  @PublicApi
  public RestResponse<Boolean> checkSubscriptionExists(@NotNull @QueryParam(SUBSCRIPTION_ID) String subscriptionId) {
    return new RestResponse(subscriptionService.checkSubscriptionExists(subscriptionId));
  }

  @POST
  @Path("/sync_event")
  @PublicApi
  public RestResponse<Void> syncStripeEvent(@NotNull String stripeEvent) {
    subscriptionService.syncStripeEvent(stripeEvent);
    return new RestResponse();
  }
}
