/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import io.harness.ModuleType;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.services.LicenseService;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.repositories.SubscriptionDetailRepository;
import io.harness.subscription.constant.Prices;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.FfSubscriptionDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.entities.SubscriptionDetail;
import io.harness.subscription.handlers.StripeEventHandler;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.CustomerParams.CustomerParamsBuilder;
import io.harness.subscription.params.ItemParams;
import io.harness.subscription.params.SubscriptionParams;
import io.harness.subscription.services.SubscriptionService;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.val;
import org.apache.commons.validator.routines.EmailValidator;

@Singleton
public class SubscriptionServiceImpl implements SubscriptionService {
  private final StripeHelper stripeHelper;
  private final StripeCustomerRepository stripeCustomerRepository;
  private final SubscriptionDetailRepository subscriptionDetailRepository;
  private final LicenseService licenseService;
  private final FeatureFlagService featureFlagService;

  private final Map<String, StripeEventHandler> eventHandlers;

  @Inject
  public SubscriptionServiceImpl(StripeHelper stripeHelper, StripeCustomerRepository stripeCustomerRepository,
      SubscriptionDetailRepository subscriptionDetailRepository, LicenseService licenseService,
      FeatureFlagService featureFlagService, Map<String, StripeEventHandler> eventHandlers) {
    this.stripeHelper = stripeHelper;
    this.stripeCustomerRepository = stripeCustomerRepository;
    this.subscriptionDetailRepository = subscriptionDetailRepository;
    this.licenseService = licenseService;
    this.featureFlagService = featureFlagService;
    this.eventHandlers = eventHandlers;
  }

  @Override
  public PriceCollectionDTO listPrices(String accountIdentifier, ModuleType module) {
    isSelfServiceEnable(accountIdentifier);

    List<String> prices;

    switch (module.toString()) {
      case "CI":
        prices = Arrays.asList(Prices.CI_PRICES);
        break;
      case "CD":
        prices = Arrays.asList(Prices.CD_PRICES);
        break;
      case "CF":
        prices = Arrays.asList(Prices.FF_PRICES);
        break;
      default:
        throw new InvalidRequestException("Valid Module Type Required");
    }

    return stripeHelper.listPrices(prices);
  }

  @Override
  public InvoiceDetailDTO previewInvoice(String accountIdentifier, SubscriptionDTO subscriptionDTO) {
    isSelfServiceEnable(accountIdentifier);

    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifierAndCustomerId(
        accountIdentifier, subscriptionDTO.getCustomerId());
    if (stripeCustomer == null) {
      throw new InvalidRequestException("Cannot preview. Please finish customer information firstly");
    }

    SubscriptionParams params = SubscriptionParams.builder().build();
    params.setItems(subscriptionDTO.getItems());
    params.setCustomerId(stripeCustomer.getCustomerId());

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findByAccountIdentifierAndModuleType(
        accountIdentifier, subscriptionDTO.getModuleType());
    // Only preview proration when there is an active subscription
    if (subscriptionDetail != null && !subscriptionDetail.isIncomplete()) {
      params.setSubscriptionId(subscriptionDetail.getSubscriptionId());
    }

    return stripeHelper.previewInvoice(params);
  }

  @Override
  public InvoiceDetailDTO createFfSubscription(String accountIdentifier, FfSubscriptionDTO subscriptionDTO) {
    isSelfServiceEnable(accountIdentifier);

    // TODO: transaction control in case any race condition

    // verify customer exists
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      createStripeCustomer(accountIdentifier, new CustomerDTO("admin@harness.io", "Harness"));
      stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    }

    // Not allowed for creation if active subscriptionId exists
    SubscriptionDetail subscriptionDetail =
        subscriptionDetailRepository.findByAccountIdentifierAndModuleType(accountIdentifier, ModuleType.valueOf("CF"));
    if (subscriptionDetail != null) {
      if (!subscriptionDetail.isIncomplete()) {
        throw new InvalidRequestException("Cannot create a new subscription, since there is an active one.");
      }

      // cancel incomplete subscription
      cancelSubscription(subscriptionDetail.getAccountIdentifier(), subscriptionDetail.getSubscriptionId());
    }

    ArrayList<ItemParams> subscriptionItems = new ArrayList<>();

    val developerPriceId = stripeHelper.getPrice(
        Prices.getLookupKey("FF", subscriptionDTO.getEdition(), "DEVELOPERS", subscriptionDTO.getPaymentFreq()));
    subscriptionItems.add(new ItemParams(developerPriceId.getId(), (long) subscriptionDTO.getNumberOfDevelopers(),
        Prices.getLookupKey("FF", subscriptionDTO.getEdition(), "DEVELOPERS", subscriptionDTO.getPaymentFreq())));

    val mauPriceId = stripeHelper.getPrice(
        Prices.getLookupKey("FF", subscriptionDTO.getEdition(), "MAU", subscriptionDTO.getPaymentFreq()));
    subscriptionItems.add(new ItemParams(mauPriceId.getId(), (long) subscriptionDTO.getNumberOfMau(),
        Prices.getLookupKey("FF", subscriptionDTO.getEdition(), "MAU", subscriptionDTO.getPaymentFreq())));

    if (subscriptionDTO.isPremiumSupport()) {
      val supportPriceId = stripeHelper.getPrice(Prices.PREMIUM_SUPPORT);
      subscriptionItems.add(new ItemParams(supportPriceId.getId(), 1L, Prices.PREMIUM_SUPPORT));
    }

    // subscriptionItems.add(new ItemParams(priceId));

    // create Subscription
    SubscriptionParams param = SubscriptionParams.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .moduleType("CF")
                                   .customerId(stripeCustomer.getCustomerId())
                                   .items(subscriptionItems)
                                   .paymentFrequency(subscriptionDTO.getPaymentFreq())
                                   .build();
    SubscriptionDetailDTO subscription = stripeHelper.createSubscription(param);

    // Save locally with basic information after succeed
    subscriptionDetailRepository.save(SubscriptionDetail.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .customerId(stripeCustomer.getCustomerId())
                                          .subscriptionId(subscription.getSubscriptionId())
                                          .status(subscription.getStatus())
                                          .latestInvoice(subscription.getLatestInvoice())
                                          .moduleType(ModuleType.CF)
                                          .build());

    val invoice = stripeHelper.getUpcomingInvoice(stripeCustomer.getCustomerId());
    invoice.setClientSecret(subscription.getClientSecret());

    return invoice;
  }

  @Override
  public SubscriptionDetailDTO createSubscription(String accountIdentifier, SubscriptionDTO subscriptionDTO) {
    isSelfServiceEnable(accountIdentifier);

    // TODO: transaction control in case any race condition

    // verify customer exists
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifierAndCustomerId(
        accountIdentifier, subscriptionDTO.getCustomerId());
    if (stripeCustomer == null) {
      throw new InvalidRequestException("Cannot create subscription. Please finish customer information firstly");
    }

    // Not allowed for creation if active subscriptionId exists
    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findByAccountIdentifierAndModuleType(
        accountIdentifier, subscriptionDTO.getModuleType());
    if (subscriptionDetail != null) {
      if (!subscriptionDetail.isIncomplete()) {
        throw new InvalidRequestException("Cannot create a new subscription, since there is an active one.");
      }

      // cancel incomplete subscription
      cancelSubscription(subscriptionDetail.getAccountIdentifier(), subscriptionDetail.getSubscriptionId());
    }

    // create Subscription
    SubscriptionParams param = SubscriptionParams.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .moduleType(subscriptionDTO.getModuleType().name())
                                   .customerId(stripeCustomer.getCustomerId())
                                   .paymentMethodId(subscriptionDTO.getPaymentMethodId())
                                   .items(subscriptionDTO.getItems())
                                   .build();
    SubscriptionDetailDTO subscription = stripeHelper.createSubscription(param);

    // Save locally with basic information after succeed
    subscriptionDetailRepository.save(SubscriptionDetail.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .customerId(stripeCustomer.getCustomerId())
                                          .subscriptionId(subscription.getSubscriptionId())
                                          .status(subscription.getStatus())
                                          .latestInvoice(subscription.getLatestInvoice())
                                          .moduleType(subscriptionDTO.getModuleType())
                                          .build());
    return subscription;
  }

  @Override
  public SubscriptionDetailDTO updateSubscription(
      String accountIdentifier, String subscriptionId, SubscriptionDTO subscriptionDTO) {
    isSelfServiceEnable(accountIdentifier);

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (checkSubscriptionInValid(subscriptionDetail, accountIdentifier)) {
      throw new InvalidRequestException("Invalid subscriptionId");
    }
    // TODO: verify priceId is acceptable to update, could utilize local price cache

    SubscriptionParams param = SubscriptionParams.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .subscriptionId(subscriptionDetail.getSubscriptionId())
                                   .paymentMethodId(subscriptionDTO.getPaymentMethodId())
                                   .items(subscriptionDTO.getItems())
                                   .build();
    return stripeHelper.updateSubscription(param);
  }

  @Override
  public void cancelSubscription(String accountIdentifier, String subscriptionId) {
    isSelfServiceEnable(accountIdentifier);

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (checkSubscriptionInValid(subscriptionDetail, accountIdentifier)) {
      throw new InvalidRequestException("Invalid subscriptionId");
    }

    stripeHelper.cancelSubscription(
        SubscriptionParams.builder().subscriptionId(subscriptionDetail.getSubscriptionId()).build());
    subscriptionDetailRepository.deleteBySubscriptionId(subscriptionId);
  }

  @Override
  public SubscriptionDetailDTO getSubscription(String accountIdentifier, String subscriptionId) {
    isSelfServiceEnable(accountIdentifier);

    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    if (checkSubscriptionInValid(subscriptionDetail, accountIdentifier)) {
      throw new InvalidRequestException("Invalid subscriptionId");
    }

    return stripeHelper.retrieveSubscription(SubscriptionParams.builder().subscriptionId(subscriptionId).build());
  }

  @Override
  public boolean checkSubscriptionExists(String subscriptionId) {
    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(subscriptionId);
    return subscriptionDetail != null;
  }

  @Override
  public List<SubscriptionDetailDTO> listSubscriptions(String accountIdentifier, ModuleType moduleType) {
    isSelfServiceEnable(accountIdentifier);

    List<SubscriptionDetail> subscriptions = new ArrayList<>();
    if (moduleType == null) {
      subscriptions = subscriptionDetailRepository.findByAccountIdentifier(accountIdentifier);
    } else {
      SubscriptionDetail subscriptionDetail =
          subscriptionDetailRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
      if (subscriptionDetail != null) {
        subscriptions.add(subscriptionDetail);
      }
    }

    return subscriptions.stream()
        .map(detail
            -> stripeHelper.retrieveSubscription(
                SubscriptionParams.builder().subscriptionId(detail.getSubscriptionId()).build()))
        .collect(Collectors.toList());
  }

  @Override
  public CustomerDetailDTO createStripeCustomer(String accountIdentifier, CustomerDTO customerDTO) {
    isSelfServiceEnable(accountIdentifier);

    if (!EmailValidator.getInstance().isValid(customerDTO.getBillingEmail())) {
      throw new InvalidRequestException("Billing email is invalid");
    }
    if (Strings.isNullOrEmpty(customerDTO.getCompanyName())) {
      throw new InvalidRequestException("Company name is invalid");
    }
    // TODO: double check if accountIdnetifer already bind to one customer

    CustomerDetailDTO customerDetailDTO =
        stripeHelper.createCustomer(CustomerParams.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .billingContactEmail(customerDTO.getBillingEmail())
                                        .name(customerDTO.getCompanyName())
                                        .build());

    // Save customer information at local After succeed
    saveCustomerLocally(accountIdentifier, null, customerDetailDTO);
    return customerDetailDTO;
  }

  @Override
  public CustomerDetailDTO updateStripeCustomer(String accountIdentifier, String customerId, CustomerDTO customerDTO) {
    isSelfServiceEnable(accountIdentifier);

    StripeCustomer stripeCustomer =
        stripeCustomerRepository.findByAccountIdentifierAndCustomerId(accountIdentifier, customerId);
    if (stripeCustomer == null) {
      throw new InvalidRequestException("Customer doesn't exists");
    }

    CustomerParamsBuilder builder = CustomerParams.builder();
    builder.customerId(stripeCustomer.getCustomerId());
    if (EmailValidator.getInstance().isValid(customerDTO.getBillingEmail())) {
      builder.billingContactEmail(customerDTO.getBillingEmail());
    }

    if (!Strings.isNullOrEmpty(customerDTO.getCompanyName())) {
      builder.name(customerDTO.getCompanyName());
    }
    CustomerDetailDTO customerDetailDTO = stripeHelper.updateCustomer(builder.build());

    // Update customer information at local After succeed
    saveCustomerLocally(accountIdentifier, stripeCustomer.getId(), customerDetailDTO);
    return customerDetailDTO;
  }

  @Override
  public CustomerDetailDTO getStripeCustomer(String accountIdentifier, String customerId) {
    isSelfServiceEnable(accountIdentifier);

    StripeCustomer stripeCustomer =
        stripeCustomerRepository.findByAccountIdentifierAndCustomerId(accountIdentifier, customerId);
    if (stripeCustomer == null) {
      throw new InvalidRequestException("Customer doesn't exists");
    }

    return stripeHelper.getCustomer(stripeCustomer.getCustomerId());
  }

  //  @Override
  //  public List<CustomerDetailDTO> listStripeCustomers(String accountIdentifier) {
  //    isSelfServiceEnable(accountIdentifier);
  //
  //    // TODO: Might not needed any more due to one customer to one account
  //    List<StripeCustomer> stripeCustomers = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
  //    return stripeCustomers.stream().map(s -> toCustomerDetailDTO(s)).collect(Collectors.toList());
  //  }

  @Override
  public PaymentMethodCollectionDTO listPaymentMethods(String accountIdentifier, String customerId) {
    isSelfServiceEnable(accountIdentifier);

    // TODO: Might not needed any more because we request every time user input a payment method
    StripeCustomer stripeCustomer =
        stripeCustomerRepository.findByAccountIdentifierAndCustomerId(accountIdentifier, customerId);
    if (stripeCustomer == null) {
      throw new InvalidRequestException("Customer doesn't exists");
    }
    return stripeHelper.listPaymentMethods(customerId);
  }

  @Override
  public void syncStripeEvent(String eventString) {
    Event event = ApiResource.GSON.fromJson(eventString, Event.class);
    StripeEventHandler stripeEventHandler = eventHandlers.get(event.getType());
    if (stripeEventHandler == null) {
      throw new InvalidRequestException("Event type is not supported");
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

  private void isSelfServiceEnable(String accountIdentifier) {
    if (!featureFlagService.isEnabled(FeatureName.SELF_SERVICE_ENABLED, accountIdentifier)) {
      throw new UnsupportedOperationException("Self Service is currently unavailable");
    }
  }
}
