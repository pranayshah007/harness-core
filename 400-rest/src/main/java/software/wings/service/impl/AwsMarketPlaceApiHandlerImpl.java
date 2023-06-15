/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployMode;
import io.harness.exception.WingsException;
import io.harness.licensing.Edition;

import software.wings.app.MainConfiguration;
import software.wings.beans.MarketPlace;
import software.wings.beans.UserInvite;
import software.wings.beans.marketplace.MarketPlaceConstants;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.dl.WingsPersistence;
import software.wings.licensing.LicenseService;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.SecretManager;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.MarketPlaceConfig;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AwsMarketPlaceApiHandler;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.marketplace.MarketPlaceService;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.marketplaceentitlement.AWSMarketplaceEntitlementClient;
import com.amazonaws.services.marketplaceentitlement.model.GetEntitlementsRequest;
import com.amazonaws.services.marketplaceentitlement.model.GetEntitlementsResult;
import com.amazonaws.services.marketplacemetering.AWSMarketplaceMeteringClientBuilder;
import com.amazonaws.services.marketplacemetering.model.AWSMarketplaceMeteringException;
import com.amazonaws.services.marketplacemetering.model.ResolveCustomerRequest;
import com.amazonaws.services.marketplacemetering.model.ResolveCustomerResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsMarketPlaceApiHandlerImpl implements AwsMarketPlaceApiHandler {
  @Inject private AccountService accountService;
  @Inject private UserService userService;
  @Inject private MainConfiguration configuration;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LicenseService licenseService;
  @Inject private SecretManager secretManager;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private MarketPlaceService marketPlaceService;
  private final String MANUALLY_PROVISIONED_MESSAGE =
      "Instructions to get started should be emailed shortly. If not, please contact Harness at support@harness.io";
  private static final String INFO = "INFO";
  private static final String REDIRECT_ACTION_LOGIN = "LOGIN";
  private final String MESSAGESTATUS = "SUCCESS";
  private final String AWS_FREE_TRIAL_DIMENSION = "AWSMPFreeTrial";
  private final String HARNESS_TEST_2_PRODUCT_CODE = "9g3jcw6iic4tpylipcti8vwcx";
  private final Integer MINIMUM_DIMENSION_V2_LENGTH = 3;
  private final String KILO_CONVERSION = "K";
  private final String MILLION_CONVERSION = "M";
  @Override
  public Response processAWSMarktPlaceOrder(String token) {
    /**
     * If request gets routed to the free cluster, reject the request rightaway
     */
    if (configuration.isTrialRegistrationAllowed()) {
      final String message = "Invalid cluster, please contact Harness at support@harness.io, customertoken=" + token;
      return generateMessageResponse(message, "ERROR", null, null);
    }

    if (DeployMode.isOnPrem(configuration.getDeployMode().name())) {
      final String message =
          "MarketPlace is disabled in On-Prem, please contact Harness at support@harness.io, customertoken=" + token
          + ", deploymode=" + configuration.getDeployMode();
      return generateMessageResponse(message, "ERROR", null, null);
    }

    ResolveCustomerRequest resolveCustomerRequest = new ResolveCustomerRequest().withRegistrationToken(token);
    final MarketPlaceConfig marketPlaceConfig = configuration.getMarketPlaceConfig();

    BasicAWSCredentials awsCreds =
        new BasicAWSCredentials(marketPlaceConfig.getAwsAccessKey(), marketPlaceConfig.getAwsSecretKey());

    ResolveCustomerResult resolveCustomerResult;
    try {
      resolveCustomerResult = AWSMarketplaceMeteringClientBuilder.standard()
                                  .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                                  .withRegion(Regions.US_EAST_1)
                                  .build()
                                  .resolveCustomer(resolveCustomerRequest);
    } catch (AWSMarketplaceMeteringException e) {
      log.error("Failed to resolveCustomer for customerToken:[{}]", token, e);
      return generateMessageResponse("Failed to authenticate user with AWS", "ERROR", null, null);
    }
    if (null == resolveCustomerResult) {
      final String message =
          "Customer order from AWS could not be resolved, please contact Harness at support@harness.io" + token;
      log.error(message);
      return generateMessageResponse(message, "ERROR", null, null);
    }
    log.info("ResolveCustomerResult=[{}]", resolveCustomerResult);

    String customerIdentifierCode = resolveCustomerResult.getCustomerIdentifier();
    String productCode = resolveCustomerResult.getProductCode();

    // V2 Product codes use dimension string to retrieve license info
    List<String> awsMarketPlaceV2ProductCodes = new ArrayList();
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceFfProductCode());
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceCiProductCode());
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceStoProductCode());
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceSrmProductCode());
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceCdProductCode());
    awsMarketPlaceV2ProductCodes.add(marketPlaceConfig.getAwsMarketPlaceCcmProductCode());
    awsMarketPlaceV2ProductCodes.add(HARNESS_TEST_2_PRODUCT_CODE);

    if (!marketPlaceConfig.getAwsMarketPlaceProductCode().equals(productCode)
        && !marketPlaceConfig.getAwsMarketPlaceCeProductCode().equals(productCode)
        && (!awsMarketPlaceV2ProductCodes.contains(productCode))) {
      final String message =
          "Customer order from AWS could not be resolved, please contact Harness at support@harness.io";
      log.error("Invalid AWS productcode received:[{}],", productCode);
      return generateMessageResponse(message, "ERROR", null, null);
    }

    GetEntitlementsRequest entitlementRequest = new GetEntitlementsRequest();
    entitlementRequest.setProductCode(productCode);
    Map<String, List<String>> entitlementFilter = new HashMap();

    List<String> customerIdentifier = new ArrayList();
    customerIdentifier.add(customerIdentifierCode);
    entitlementFilter.put("CUSTOMER_IDENTIFIER", customerIdentifier);
    entitlementRequest.setFilter(entitlementFilter);

    AWSMarketplaceEntitlementClient oClient =
        (AWSMarketplaceEntitlementClient) AWSMarketplaceEntitlementClient.builder()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
            .build();

    GetEntitlementsResult entitlements = oClient.getEntitlements(entitlementRequest);
    log.info("oEntitlementResult=[{}]", entitlements);
    String dimension = entitlements.getEntitlements().get(0).getDimension();
    Integer orderQuantity = getOrderQuantity(dimension);
    log.info("Dimension=[{}]", dimension);
    log.info("Order Quantity=[{}]", orderQuantity);

    if (isManuallyProvisioned(dimension)) {
      log.info("Manually provision license for Dimension=[{}], EntitlementResult=[{}]", dimension, entitlements);
      return generateMessageResponse(MANUALLY_PROVISIONED_MESSAGE, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);
    }

    if (awsMarketPlaceV2ProductCodes.contains(productCode)) {
      orderQuantity = getDimensionQuantity(dimension);

      if (!isDimensionV2Provisionable(dimension, orderQuantity)) {
        log.info(
            "Dimensions provided does not include required all information. Dimension=[{}], EntitlementResult=[{}]",
            dimension, entitlements);
        return generateMessageResponse(MANUALLY_PROVISIONED_MESSAGE, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);
      }
    }

    Date expirationDate = entitlements.getEntitlements().get(0).getExpirationDate();
    String licenseType = getLicenseType(dimension);
    Optional<MarketPlace> marketPlaceMaybe =
        marketPlaceService.fetchMarketplace(customerIdentifierCode, MarketPlaceType.AWS);

    boolean existingCustomer = false;
    MarketPlace marketPlace;
    if (marketPlaceMaybe.isPresent()) {
      marketPlace = marketPlaceMaybe.get();
      log.info("Existing customer, not creating a new account");
      if (marketPlace.getAccountId() != null) {
        existingCustomer = true;
      } else {
        log.info(
            "MarketPlace customer:[{}] does not have an account associated with him, will treat him as a new customer",
            customerIdentifierCode);
      }
    } else {
      marketPlace = MarketPlace.builder()
                        .type(MarketPlaceType.AWS)
                        .customerIdentificationCode(customerIdentifierCode)
                        .token(token)
                        .orderQuantity(orderQuantity)
                        .expirationDate(expirationDate)
                        .productCode(productCode)
                        .licenseType(licenseType)
                        .dimension(dimension)
                        .build();
      log.info("New MarketPlace=[{}]", marketPlace);
      wingsPersistence.save(marketPlace);
    }

    if (existingCustomer && !marketPlace.getProductCode().equals(productCode)) {
      log.info(
          "This is an existing customer:[{}], purchasing another module. Existing product code [{}], adding product code [{}]. Desired dimension: [{}]",
          customerIdentifierCode, marketPlace.getProductCode(), productCode, dimension);
      final String message = String.format(
          "Looks like you already have a license. Please reach out to harness@support.io to add the additional module. License details: Quantity: %d, License expiration: %s",
          orderQuantity, DateFormat.getDateInstance(DateFormat.SHORT).format(expirationDate));
      return generateMessageResponse(message, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);

    } else if (existingCustomer
        && (!marketPlace.getOrderQuantity().equals(orderQuantity)
            || (!marketPlace.getExpirationDate().equals(expirationDate)))) {
      log.info(
          "This is an existing customer:[{}], updating orderQuantity from [{}] to [{}], updating expirationDate from [{}] to [{}]",
          customerIdentifierCode, marketPlace.getOrderQuantity(), orderQuantity, marketPlace.getExpirationDate(),
          expirationDate);
      final String message = String.format(
          "Looks like you already have a license. Please reach out to harness@support.io to update the existing license. License details: Quantity: %d, License expiration: %s.",
          orderQuantity, DateFormat.getDateInstance(DateFormat.SHORT).format(expirationDate));
      return generateMessageResponse(message, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);

      // TODO: Implement/verify updateLicenseForProduct
      // licenseService.updateLicenseForProduct(
      //     marketPlace.getProductCode(), marketPlace.getAccountId(), orderQuantity, expirationDate.getTime(),
      //     dimension);

      // final String message = String.format("License details: Service Instances: %d, License expiration: %s",
      //     orderQuantity, DateFormat.getDateInstance(DateFormat.SHORT).format(expirationDate));
      // return generateMessageResponse(message, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);

    } else if (!existingCustomer) {
      /**
       * This is a brand new customer
       */

      UserInvite userInvite = userService.createUserInviteForMarketPlace();
      log.info("New User Invite=[{}]", userInvite);

      String marketPlaceToken = getMarketPlaceToken(marketPlace, userInvite);

      URI redirectUrl = null;
      try {
        redirectUrl = new URI(authenticationUtils.getBaseUrl()
            + ("auth/#/invite?inviteId=" + userInvite.getUuid() + "&marketPlaceToken=" + marketPlaceToken)
            + getUTMUrlParams(marketPlaceConfig, productCode));
      } catch (URISyntaxException e) {
        throw new WingsException(e);
      }
      log.info("Redirect URL=[{}]", redirectUrl);

      return Response.seeOther(redirectUrl).build();

    } else {
      final String message = String.format(
          "License details: Service Instances: %d, License expiration: %s. If any issues, please contact Harness at support@harness.io",
          orderQuantity, DateFormat.getDateInstance(DateFormat.SHORT).format(expirationDate));
      return generateMessageResponse(message, INFO, REDIRECT_ACTION_LOGIN, MESSAGESTATUS);
    }
  }

  @VisibleForTesting
  public String getMarketPlaceToken(MarketPlace marketPlace, UserInvite userInvite) {
    Map<String, String> claims = new HashMap<>();
    claims.put(MarketPlaceConstants.USERINVITE_ID_CLAIM_KEY, userInvite.getUuid());
    claims.put(MarketPlaceConstants.MARKETPLACE_ID_CLAIM_KEY, marketPlace.getUuid());
    return secretManager.generateJWTToken(claims, JWT_CATEGORY.MARKETPLACE_SIGNUP);
  }

  private String getLicenseType(String dimension) {
    String licenseType = "PAID";
    if (AWS_FREE_TRIAL_DIMENSION.equals(dimension)) {
      licenseType = "TRIAL";
    }
    return licenseType;
  }

  private Response generateMessageResponse(String message, String type, String action, String status) {
    URI redirectUrl = null;
    try {
      redirectUrl = new URI(authenticationUtils.getBaseUrl()
          + "#/fallback?message=" + URLEncoder.encode(message, "UTF-8") + "&type=" + type
          + (action != null ? "&action=" + action : "") + (status != null ? "&status=" + status : ""));
    } catch (URISyntaxException | UnsupportedEncodingException e) {
      throw new WingsException(e);
    }
    return Response.seeOther(redirectUrl).build();
  }

  private Integer getOrderQuantity(String dimension) {
    switch (dimension) {
      case MarketPlaceConstants.AWS_MARKETPLACE_50_INSTANCES:
        return 50;
      case MarketPlaceConstants.AWS_MARKETPLACE_200_INSTANCES:
        return 200;
      case MarketPlaceConstants.AWS_MARKETPLACE_500_INSTANCES:
        return 500;
      case MarketPlaceConstants.AWS_MARKETPLACE_750_INSTANCES:
        return 750;
      case MarketPlaceConstants.AWS_MARKETPLACE_1000_INSTANCES:
        return 1000;
      case MarketPlaceConstants.AWS_MARKETPLACE_1500_INSTANCES:
        return 1500;
      case MarketPlaceConstants.AWS_MARKETPLACE_2500_INSTANCES:
        return 2500;
      default:
        return 50;
    }
  }

  // User will manually provision working closely with Harness
  private boolean isManuallyProvisioned(String dimension) {
    boolean shouldManuallyProvision = false;
    String escapeProvisionKey = "x";
    if (StringUtils.isNotBlank(dimension) && dimension.toLowerCase().endsWith(escapeProvisionKey)) {
      shouldManuallyProvision = true;
    }
    return shouldManuallyProvision;
  }

  // Will only try to provision license when the dimension follows these rules
  public boolean isDimensionV2Provisionable(String dimension, Integer quantity) {
    boolean isProvisionReady = false;
    Edition plan = licenseService.getDimensionPlan(dimension);

    if (StringUtils.isNotBlank(dimension) && dimension.split("_").length >= MINIMUM_DIMENSION_V2_LENGTH && plan != null
        && quantity != null) {
      isProvisionReady = true;
    }
    return isProvisionReady;
  }

  // Gets quantity from dimension string
  public Integer getDimensionQuantity(String dimension) {
    Integer quantity = 0;
    // split string from underscore
    String[] result = dimension.split("_");

    String tempQuantityStr = "0";
    if (result.length >= MINIMUM_DIMENSION_V2_LENGTH) {
      tempQuantityStr = result[result.length - 1];
    }

    // Handle K (1000) and M (1000000) units
    if (tempQuantityStr.contains(KILO_CONVERSION)) {
      tempQuantityStr = tempQuantityStr.replace(KILO_CONVERSION, "000");
    }

    if (tempQuantityStr.contains(MILLION_CONVERSION)) {
      tempQuantityStr = tempQuantityStr.replace(MILLION_CONVERSION, "000000");
    }

    try {
      if (Integer.parseInt(tempQuantityStr) > 0) {
        quantity = Integer.parseInt(tempQuantityStr);
      }

    } catch (Exception e) {
      log.error("Failed to get quantity for dimension:[{}]", dimension, e);
    }

    return quantity;
  }

  private String getUTMUrlParams(MarketPlaceConfig marketPlaceConfig, String productCode) {
    // TODO: Add for CE (Chaos)
    String UTMUrlParam = "";

    if (marketPlaceConfig.getAwsMarketPlaceCdProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=cd&utm_campaign=23-4-6-cd-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    } else if (marketPlaceConfig.getAwsMarketPlaceCiProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=ci&utm_campaign=23-4-6-ci-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    } else if (marketPlaceConfig.getAwsMarketPlaceFfProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=ff&utm_campaign=23-4-6-ff-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    } else if (marketPlaceConfig.getAwsMarketPlaceCcmProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=ce&utm_campaign=23-4-6-ccm-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    } else if (marketPlaceConfig.getAwsMarketPlaceStoProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=sto&utm_campaign=23-4-6-srm-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    } else if (marketPlaceConfig.getAwsMarketPlaceSrmProductCode().equals(productCode)) {
      UTMUrlParam =
          "&module=sto&utm_campaign=23-4-6-srm-plg-marketplace-partner-aws&utm_medium=marketplace&utm_source=partner&utm_content=sign-up";
    }

    return UTMUrlParam;
  }
}