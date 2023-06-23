/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import static io.harness.licensing.checks.ModuleLicenseState.ACTIVE_ENTERPRISE_PAID;
import static io.harness.licensing.checks.ModuleLicenseState.ACTIVE_ENTERPRISE_TRIAL;
import static io.harness.licensing.checks.ModuleLicenseState.ACTIVE_FREE;
import static io.harness.licensing.checks.ModuleLicenseState.ACTIVE_TEAM_PAID;
import static io.harness.licensing.checks.ModuleLicenseState.ACTIVE_TEAM_TRIAL;
import static io.harness.subscription.entities.SubscriptionDetail.INCOMPLETE;

import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.checks.ModuleLicenseState;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.repositories.SubscriptionDetailRepository;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.ItemDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.StripeBillingDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.entities.SubscriptionDetail;
import io.harness.subscription.handlers.StripeEventHandler;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.BillingParams;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.CustomerParams.CustomerParamsBuilder;
import io.harness.subscription.params.RecommendationRequest;
import io.harness.subscription.params.StripeItemRequest;
import io.harness.subscription.params.StripeSubscriptionRequest;
import io.harness.subscription.params.SubscriptionItemRequest;
import io.harness.subscription.params.SubscriptionRequest;
import io.harness.subscription.params.UsageKey;
import io.harness.subscription.services.SubscriptionService;
import io.harness.subscription.utils.NGFeatureFlagHelperService;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.net.ApiResource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;

@Slf4j
@Singleton
public class SubscriptionServiceImpl implements SubscriptionService {
  private final StripeHelper stripeHelper;
  private final ModuleLicenseRepository licenseRepository;
  private final StripeCustomerRepository stripeCustomerRepository;
  private final SubscriptionDetailRepository subscriptionDetailRepository;
  private final NGFeatureFlagHelperService nGFeatureFlagHelperService;
  private final TelemetryReporter telemetryReporter;
  private final AccountService accountService;

  private final Map<String, StripeEventHandler> eventHandlers;

  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  private static final String SUBSCRIPTION_NOT_FOUND_MESSAGE = "Subscription with subscriptionId %s does not exist.";
  private static final String CANNOT_PROVIDE_RECOMMENDATION =
      "Cannot provide recommendation for account %s. No active license detected for module %s.";
  private static final String PRICE_NOT_FOUND =
      "No price found with metadata: %s, type: , edition: %s, billed: %s, max: %s";
  private static final String EDITION_CHECK_FAILED =
      "Cannot create a subscription of %s edition. An active subscription of %s edition already exists.";
  private static final String QUANTITY_GREATER_THAN_MAX =
      "Quantity requested is greater than maximum quantity allowed.";
  private static final String INVALID_SUBSCRIPTION_ID = "Invalid subscriptionId: %s for account: %s";
  private static final String CUSTOMER_DOES_NOT_EXIST = "Stripe customer for account %s does not exist";
  private static final double RECOMMENDATION_MULTIPLIER = 1.2d;
  private static final long ONE = 1L;
  private static final String SUBSCRIPTION = "subscription";
  private static final String MODULE_METADATA_KEY = "module";

  @Inject
  public SubscriptionServiceImpl(StripeHelper stripeHelper, ModuleLicenseRepository licenseRepository,
      StripeCustomerRepository stripeCustomerRepository, SubscriptionDetailRepository subscriptionDetailRepository,
      NGFeatureFlagHelperService nGFeatureFlagHelperService, TelemetryReporter telemetryReporter,
      AccountService accountService, Map<String, StripeEventHandler> eventHandlers) {
    this.stripeHelper = stripeHelper;
    this.licenseRepository = licenseRepository;
    this.stripeCustomerRepository = stripeCustomerRepository;
    this.subscriptionDetailRepository = subscriptionDetailRepository;
    this.nGFeatureFlagHelperService = nGFeatureFlagHelperService;
    this.telemetryReporter = telemetryReporter;
    this.accountService = accountService;
    this.eventHandlers = eventHandlers;
  }

  @Override
  public EnumMap<UsageKey, Long> getRecommendationRc(
      String accountIdentifier, RecommendationRequest recommendationRequest) {
    ModuleType moduleType = recommendationRequest.getModuleType();
    Map<UsageKey, Long> usageMap = recommendationRequest.getUsageMap();

    List<ModuleLicense> currentLicenses =
        licenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);

    if (currentLicenses.isEmpty()) {
      String errorMessage = String.format(CANNOT_PROVIDE_RECOMMENDATION, accountIdentifier, moduleType);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    ModuleLicense moduleLicense = ModuleLicenseHelper.getLatestLicense(currentLicenses);
    ModuleLicenseState latestLicenseState = ModuleLicenseHelper.getCurrentModuleState(currentLicenses);

    EnumMap<UsageKey, Long> recommendedValues = new EnumMap<>(UsageKey.class);

    if (ACTIVE_ENTERPRISE_PAID == latestLicenseState || ACTIVE_TEAM_PAID == latestLicenseState
        || ACTIVE_FREE == latestLicenseState) {
      EnumMap<UsageKey, Long> usageLimitMap = ModuleLicenseHelper.getUsageLimits(moduleLicense);
      usageMap.forEach((UsageKey usageKey, Long usageValue) -> {
        long usageLimiteValue = usageLimitMap.get(usageKey);
        long recommendation = (long) (Math.max(usageValue, usageLimiteValue) * RECOMMENDATION_MULTIPLIER);
        recommendedValues.put(usageKey, recommendation);
      });
    } else if (ACTIVE_ENTERPRISE_TRIAL == latestLicenseState || ACTIVE_TEAM_TRIAL == latestLicenseState) {
      usageMap.forEach((usageKey, usageValue) -> {
        long recommendation = (long) (usageValue * RECOMMENDATION_MULTIPLIER);
        recommendedValues.put(usageKey, recommendation);
      });
    } else {
      String errorMessage = String.format(CANNOT_PROVIDE_RECOMMENDATION, accountIdentifier, moduleType);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return recommendedValues;
  }

  @Override
  public EnumMap<UsageKey, Long> getRecommendation(String accountIdentifier, long numberOfMAUs, long numberOfUsers) {
    List<ModuleLicense> currentLicenses =
        licenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, ModuleType.CF);

    if (currentLicenses.isEmpty()) {
      String errorMessage = String.format(CANNOT_PROVIDE_RECOMMENDATION, accountIdentifier, ModuleType.CF);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    CFModuleLicense cfLicense = (CFModuleLicense) ModuleLicenseHelper.getLatestLicense(currentLicenses);
    ModuleLicenseState latestLicenseState = ModuleLicenseHelper.getCurrentModuleState(currentLicenses);

    EnumMap<UsageKey, Long> recommendedValues = new EnumMap<>(UsageKey.class);

    if (ACTIVE_ENTERPRISE_PAID.equals(latestLicenseState) || ACTIVE_TEAM_PAID.equals(latestLicenseState)
        || ACTIVE_FREE.equals(latestLicenseState)) {
      double recommendedUsers = Math.max(cfLicense.getNumberOfUsers(), numberOfUsers) * RECOMMENDATION_MULTIPLIER;
      double recommendedMAUs = Math.max(cfLicense.getNumberOfClientMAUs(), numberOfMAUs) * RECOMMENDATION_MULTIPLIER;

      recommendedValues.put(UsageKey.NUMBER_OF_USERS, (long) recommendedUsers);
      recommendedValues.put(UsageKey.NUMBER_OF_MAUS, (long) recommendedMAUs);
    } else if (ACTIVE_ENTERPRISE_TRIAL.equals(latestLicenseState) || ACTIVE_TEAM_TRIAL.equals(latestLicenseState)) {
      double recommendedUsers = numberOfUsers * RECOMMENDATION_MULTIPLIER;
      double recommendedMAUs = numberOfMAUs * RECOMMENDATION_MULTIPLIER;

      recommendedValues.put(UsageKey.NUMBER_OF_USERS, (long) recommendedUsers);
      recommendedValues.put(UsageKey.NUMBER_OF_MAUS, (long) recommendedMAUs);
    } else {
      String errorMessage = String.format(CANNOT_PROVIDE_RECOMMENDATION, accountIdentifier, ModuleType.CF);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return recommendedValues;
  }

  @Override
  public PriceCollectionDTO listPrices(String accountIdentifier, ModuleType module) {
    isSelfServiceEnable();

    return stripeHelper.getPrices(module);
  }

  @Override
  public InvoiceDetailDTO previewInvoice(String accountIdentifier, SubscriptionDTO subscriptionDTO) {
    isSelfServiceEnable();

    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(
          "Cannot preview invoice for account %s. Please finish customer information firstly.", accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    StripeSubscriptionRequest params = StripeSubscriptionRequest.builder().build();
    params.setItems(subscriptionDTO.getItems());
    params.setCustomerId(stripeCustomer.getCustomerId());

    List<SubscriptionDetail> subscriptionDetailList =
        subscriptionDetailRepository.findByAccountIdentifierAndPaymentFrequency(
            accountIdentifier, subscriptionDTO.getPaymentFrequency().toString());
    if (!subscriptionDetailList.isEmpty()) {
      SubscriptionDetail subscriptionDetail = subscriptionDetailList.get(0);
      if (subscriptionDetail != null && !subscriptionDetail.isIncomplete()) {
        params.setSubscriptionId(subscriptionDetail.getSubscriptionId());
      }
    }

    return stripeHelper.previewInvoice(params);
  }

  @Override
  public void payInvoice(String invoiceId, String accountIdentifier) {
    log.info("Account {} is paying for invoice {}", accountIdentifier, invoiceId);
    stripeHelper.payInvoice(invoiceId, accountIdentifier);
  }

  private StripeItemRequest buildSubscriptionItemRequest(
      SubscriptionItemRequest subscriptionItemRequest, SubscriptionRequest subscriptionRequest) {
    Optional<Price> price = stripeHelper.getPrice(subscriptionRequest, subscriptionItemRequest);

    if (!price.isPresent()) {
      String errorMessage = String.format(PRICE_NOT_FOUND, subscriptionRequest.getModuleType(),
          subscriptionItemRequest.getType(), subscriptionRequest.getEdition(),
          subscriptionRequest.getPaymentFrequency(), subscriptionItemRequest.getQuantity());
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    StripeItemRequest stripeItemRequest;
    if (subscriptionItemRequest.isQuantityIncludedInPrice()) {
      stripeItemRequest =
          StripeItemRequest.Builder.newInstance().withPriceId(price.get().getId()).withQuantity(ONE).build();
    } else {
      stripeItemRequest = StripeItemRequest.Builder.newInstance()
                              .withPriceId(price.get().getId())
                              .withQuantity(subscriptionItemRequest.getQuantity())
                              .build();
    }

    return stripeItemRequest;
  }

  private StripeSubscriptionRequest buildStripeSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, String customerId) {
    ArrayList<StripeItemRequest> subscriptionItems = new ArrayList<>();

    subscriptionRequest.getItems().forEach((SubscriptionItemRequest item) -> {
      subscriptionItems.add(buildSubscriptionItemRequest(item, subscriptionRequest));
    });

    // create Subscription
    return StripeSubscriptionRequest.builder()
        .accountIdentifier(subscriptionRequest.getAccountIdentifier())
        .moduleType(subscriptionRequest.getModuleType().toString())
        .customerId(customerId)
        .items(subscriptionItems)
        .paymentFrequency(subscriptionRequest.getPaymentFrequency())
        .build();
  }

  private void checkEdition(String accountId, String edition) {
    List<ModuleLicense> licenses = licenseRepository.findByAccountIdentifier(accountId);
    String editionToCheck =
        edition.equalsIgnoreCase(Edition.TEAM.toString()) ? Edition.ENTERPRISE.toString() : Edition.TEAM.toString();
    if (licenses.stream()
            .filter(l -> l.isActive())
            .anyMatch(l -> l.getEdition().toString().equalsIgnoreCase(editionToCheck))) {
      String errorMessage = String.format(EDITION_CHECK_FAILED, edition, editionToCheck);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
  }

  @Override
  public SubscriptionDetailDTO createSubscription(String accountIdentifier, SubscriptionRequest subscriptionRequest) {
    sendTelemetryEvent("Subscription Creation Initiated", subscriptionRequest.getCustomer().getBillingEmail(),
        accountIdentifier, subscriptionRequest.getModuleType().toString());
    log.info(
        "Account {} is creating a subscription for module {}", accountIdentifier, subscriptionRequest.getModuleType());

    isSelfServiceEnable();

    AccountDTO account = accountService.getAccount(accountIdentifier);
    if (!account.isProductLed()) {
      String errorMessage = String.format(
          "This account %s does not seem to be Product-Led and creating subscriptions for Sales-Led account is not supported at the moment. Please try again with the right account.",
          accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    List<ModuleLicense> moduleLicenses =
        licenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, subscriptionRequest.getModuleType());
    if (moduleLicenses.stream().anyMatch(moduleLicense
            -> moduleLicense.isActive() && moduleLicense.getLicenseType() != null
                && moduleLicense.getLicenseType().equals(LicenseType.PAID))) {
      String errorMessage = "Cannot create a new subscription, since there is an active one.";
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      createStripeCustomer(accountIdentifier, subscriptionRequest.getCustomer());
      stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    } else {
      updateStripeCustomer(accountIdentifier, stripeCustomer.getCustomerId(), subscriptionRequest.getCustomer());
    }

    List<SubscriptionDetail> subscriptionDetailList =
        subscriptionDetailRepository.findByAccountIdentifierAndPaymentFrequency(
            accountIdentifier, subscriptionRequest.getPaymentFrequency());
    if (!subscriptionDetailList.isEmpty()) {
      SubscriptionDetail subscriptionDetail = subscriptionDetailList.get(0);
      if (!subscriptionDetail.isIncomplete()) {
        StripeSubscriptionRequest stripeSubscriptionRequest =
            buildStripeSubscriptionRequest(subscriptionRequest, stripeCustomer.getCustomerId());

        SubscriptionDetailDTO subscription = stripeHelper.retrieveSubscription(
            StripeSubscriptionRequest.builder().subscriptionId(subscriptionDetail.getSubscriptionId()).build());

        stripeSubscriptionRequest.setSubscriptionId(subscription.getSubscriptionId());

        return stripeHelper.addToSubscription(stripeSubscriptionRequest, subscription);
      }

      cancelSubscription(subscriptionDetail.getAccountIdentifier(), subscriptionDetail.getSubscriptionId(),
          subscriptionRequest.getModuleType());
    }

    StripeSubscriptionRequest param =
        buildStripeSubscriptionRequest(subscriptionRequest, stripeCustomer.getCustomerId());
    SubscriptionDetailDTO subscription = stripeHelper.createSubscription(param);

    subscriptionDetailRepository.save(SubscriptionDetail.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .customerId(stripeCustomer.getCustomerId())
                                          .subscriptionId(subscription.getSubscriptionId())
                                          .status(INCOMPLETE)
                                          .latestInvoice(subscription.getLatestInvoice())
                                          .paymentFrequency(subscriptionRequest.getPaymentFrequency())
                                          .build());

    log.info("Account {} has created subscription {} for module {}", accountIdentifier,
        subscription.getSubscriptionId(), subscriptionRequest.getModuleType());
    return subscription;
  }

  @Override
  public SubscriptionDetailDTO updateSubscription(
      String accountIdentifier, String subscriptionId, SubscriptionDTO subscriptionDTO) {
    sendTelemetryEvent(
        "Subscription Modification Initiated", null, accountIdentifier, subscriptionDTO.getModuleType().toString());

    isSelfServiceEnable();

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (checkSubscriptionInValid(subscriptionDetail, accountIdentifier)) {
      String errorMessage = String.format(INVALID_SUBSCRIPTION_ID, subscriptionId, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    // TODO: verify priceId is acceptable to update, could utilize local price cache

    StripeSubscriptionRequest param = StripeSubscriptionRequest.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .subscriptionId(subscriptionDetail.getSubscriptionId())
                                          .paymentMethodId(subscriptionDTO.getPaymentMethodId())
                                          .items(subscriptionDTO.getItems())
                                          .build();
    return stripeHelper.updateSubscription(param);
  }

  @Override
  public void cancelSubscription(String accountIdentifier, String subscriptionId, ModuleType moduleType) {
    isSelfServiceEnable();

    log.info("Account {} is cancelling subscription {} for module {}", accountIdentifier, subscriptionId, moduleType);

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (checkSubscriptionInValid(subscriptionDetail, accountIdentifier)) {
      String errorMessage = String.format(INVALID_SUBSCRIPTION_ID, subscriptionId, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    sendTelemetryEvent("Subscription Cancellation Initiated", null, accountIdentifier, moduleType.getDisplayName());

    SubscriptionDetailDTO subscription =
        stripeHelper.retrieveSubscription(StripeSubscriptionRequest.builder().subscriptionId(subscriptionId).build());

    if (subscription == null) {
      String errorMessage = String.format("Locally saved subscription %s does not exist in Stripe.", subscriptionId);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }

    List<ItemDTO> items = subscription.getItems();
    items.removeIf(subscriptionItem
        -> !subscriptionItem.getPrice().getMetaData().containsKey(MODULE_METADATA_KEY)
            || subscriptionItem.getPrice().getMetaData().containsValue(moduleType.toString()));

    if (!subscriptionDetail.isActive() || items.isEmpty()) {
      stripeHelper.cancelSubscription(
          StripeSubscriptionRequest.builder().subscriptionId(subscriptionDetail.getSubscriptionId()).build());
      subscriptionDetailRepository.deleteBySubscriptionId(subscriptionId);
    } else {
      List<StripeItemRequest> itemParams = new ArrayList<>();
      items.forEach((ItemDTO subscriptionItem) -> {
        itemParams.add(
            StripeItemRequest.Builder.newInstance().withPriceId(subscriptionItem.getPrice().getPriceId()).build());
      });
      stripeHelper.updateSubscription(
          StripeSubscriptionRequest.builder().subscriptionId(subscriptionId).items(itemParams).build());
    }
  }

  @Override
  public void cancelAllSubscriptions(String accountIdentifier) {
    isSelfServiceEnable();

    List<SubscriptionDetail> subscriptionDetailList =
        subscriptionDetailRepository.findByAccountIdentifier(accountIdentifier);

    if (!subscriptionDetailList.isEmpty()) {
      SubscriptionDetail subscriptionDetail = subscriptionDetailList.get(0);
      if (subscriptionDetail.isActive()) {
        stripeHelper.cancelSubscription(
            StripeSubscriptionRequest.builder().subscriptionId(subscriptionDetail.getSubscriptionId()).build());
      }
    }
  }

  @Override
  public SubscriptionDetailDTO getSubscription(String accountIdentifier, String subscriptionId) {
    isSelfServiceEnable();

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (subscriptionDetail != null) {
      return stripeHelper.retrieveSubscription(
          StripeSubscriptionRequest.builder().subscriptionId(subscriptionDetail.getSubscriptionId()).build());
    } else {
      String errorMessage = String.format(SUBSCRIPTION_NOT_FOUND_MESSAGE, subscriptionId);
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }
  }

  @Override
  public boolean checkSubscriptionExists(String subscriptionId) {
    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    return subscriptionDetail != null;
  }

  @Override
  public List<SubscriptionDetailDTO> listSubscriptions(String accountIdentifier) {
    isSelfServiceEnable();

    List<SubscriptionDetail> subscriptions = subscriptionDetailRepository.findByAccountIdentifier(accountIdentifier);

    return subscriptions.stream()
        .map(detail
            -> stripeHelper.retrieveSubscription(
                StripeSubscriptionRequest.builder().subscriptionId(detail.getSubscriptionId()).build()))
        .collect(Collectors.toList());
  }

  @Override
  public CustomerDetailDTO createStripeCustomer(String accountIdentifier, CustomerDTO customerDTO) {
    isSelfServiceEnable();

    if (!EmailValidator.getInstance().isValid(customerDTO.getBillingEmail())) {
      String errorMessage = String.format("Billing email %s is invalid", customerDTO.getBillingEmail());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    if (Strings.isNullOrEmpty(customerDTO.getCompanyName())) {
      String errorMessage = String.format("Company name %s is invalid", customerDTO.getBillingEmail());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    if (stripeCustomerRepository.findByAccountIdentifier(accountIdentifier) != null) {
      String errorMessage = String.format("Stripe customer already exists for account %s", accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    CustomerDetailDTO customerDetailDTO =
        stripeHelper.createCustomer(CustomerParams.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .address(customerDTO.getAddress())
                                        .billingContactEmail(customerDTO.getBillingEmail())
                                        .name(customerDTO.getCompanyName())
                                        .build());

    // Save customer information at local After succeed
    saveCustomerLocally(accountIdentifier, null, customerDetailDTO);
    return customerDetailDTO;
  }

  @Override
  public CustomerDetailDTO updateStripeCustomer(String accountIdentifier, String customerId, CustomerDTO customerDTO) {
    isSelfServiceEnable();

    StripeCustomer stripeCustomer =
        stripeCustomerRepository.findByAccountIdentifierAndCustomerId(accountIdentifier, customerId);
    if (stripeCustomer == null) {
      String errorMessage = String.format("Customer %s doesn't exists", customerId);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    CustomerParamsBuilder builder = CustomerParams.builder();
    builder.customerId(stripeCustomer.getCustomerId());
    if (EmailValidator.getInstance().isValid(customerDTO.getBillingEmail())) {
      builder.billingContactEmail(customerDTO.getBillingEmail());
    }

    if (!Strings.isNullOrEmpty(customerDTO.getCompanyName())) {
      builder.name(customerDTO.getCompanyName());
    }

    if (customerDTO.getAddress() != null) {
      builder.address(customerDTO.getAddress());
    }

    CustomerDetailDTO customerDetailDTO = stripeHelper.updateCustomer(builder.build());

    // Update customer information at local After succeed
    saveCustomerLocally(accountIdentifier, stripeCustomer.getId(), customerDetailDTO);
    return customerDetailDTO;
  }

  @Override
  public CustomerDetailDTO getStripeCustomer(String accountIdentifier) {
    isSelfServiceEnable();

    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return stripeHelper.getCustomer(stripeCustomer.getCustomerId());
  }

  @Override
  public CustomerDetailDTO updateStripeBilling(String accountIdentifier, StripeBillingDTO stripeBillingDTO) {
    isSelfServiceEnable();

    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    BillingParams params = BillingParams.builder().build();
    params.setLine1(stripeBillingDTO.getLine1());
    params.setLine2(stripeBillingDTO.getLine2());
    params.setCity(stripeBillingDTO.getCity());
    params.setState(stripeBillingDTO.getState());
    params.setCountry(stripeBillingDTO.getCountry());
    params.setZipCode(stripeBillingDTO.getZipCode());

    params.setCreditCardId(stripeBillingDTO.getCreditCardId());

    params.setCustomerId(stripeCustomer.getCustomerId());

    return stripeHelper.updateBilling(params);
  }

  @Override
  public String createClientSecret(String accountIdentifier, String billingEmail) {
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);

    String customerId;

    if (stripeCustomer == null) {
      AccountDTO account = accountService.getAccount(accountIdentifier);
      customerId = createStripeCustomer(accountIdentifier,
          CustomerDTO.builder().companyName(account.getCompanyName()).billingEmail(billingEmail).build())
                       .getCustomerId();
    } else {
      customerId = stripeCustomer.getCustomerId();
    }

    return stripeHelper.retrieveClientSecret(customerId);
  }

  @Override
  public PaymentMethodCollectionDTO listPaymentMethods(String accountIdentifier) {
    isSelfServiceEnable();

    // TODO: Might not needed any more because we request every time user input a payment method
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    return stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId());
  }

  @Override
  public void syncStripeEvent(String eventString) {
    Event event = ApiResource.GSON.fromJson(eventString, Event.class);
    StripeEventHandler stripeEventHandler = eventHandlers.get(event.getType());
    if (stripeEventHandler == null) {
      String errorMessage = String.format("Event type %s is not supported", event.getType());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    stripeEventHandler.handleEvent(event);
  }

  private void saveCustomerLocally(String accountIdentifier, String id, CustomerDetailDTO customerDetailDTO) {
    stripeCustomerRepository.save(StripeCustomer.builder()
                                      .id(id)
                                      .accountIdentifier(accountIdentifier)
                                      .billingEmail(customerDetailDTO.getBillingEmail())
                                      .companyName(customerDetailDTO.getCompanyName())
                                      .customerId(customerDetailDTO.getCustomerId())
                                      .build());
  }

  private boolean checkSubscriptionInValid(SubscriptionDetail subscriptionDetail, String accountIdentifier) {
    return subscriptionDetail == null || !subscriptionDetail.getAccountIdentifier().equals(accountIdentifier);
  }

  private void isSelfServiceEnable() {
    if (deployMode.equals("KUBERNETES_ONPREM")) {
      String errorMessage = "Self Service is not available for OnPrem deployments.";
      log.error(errorMessage);
      throw new UnsupportedOperationException(errorMessage);
    }
  }

  private void sendTelemetryEvent(String event, String email, String accountId, String module) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("module", module);
    telemetryReporter.sendTrackEvent(event, email, accountId, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(), SUBSCRIPTION);
  }
}
