/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.helpers.impl;

import io.harness.ModuleType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.subscription.dto.AddressDto;
import io.harness.subscription.dto.CardDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.CustomerDetailDTO.CustomerDetailDTOBuilder;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.ItemDTO;
import io.harness.subscription.dto.NextActionDetailDTO;
import io.harness.subscription.dto.PaymentIntentDetailDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PendingUpdateDetailDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.PriceDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.dto.TierMode;
import io.harness.subscription.dto.TiersDTO;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.BillingParams;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.StripeItemRequest;
import io.harness.subscription.params.StripeSubscriptionRequest;
import io.harness.subscription.params.SubscriptionItemRequest;
import io.harness.subscription.params.SubscriptionRequest;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Address;
import com.stripe.model.Card;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerRetrieveParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoiceUpcomingParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.PriceSearchParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class StripeHelperImpl implements StripeHelper {
  private StripeHandlerImpl stripeHandler;
  private final TelemetryReporter telemetryReporter;
  private List<String> subscriptionExpandList = Arrays.asList("latest_invoice.payment_intent");
  private static final String ACCOUNT_IDENTIFIER_KEY = "accountIdentifier";
  private static final String CUSTOMER_EMAIL_KEY = "customer_email";
  private static final String SEARCH_MODULE_TYPE_EDITION_BILLED_MAX =
      "metadata['module']:'%s' AND metadata['type']:'%s' AND metadata['edition']:'%s' AND metadata['billed']:'%s' AND metadata['max']:'%s'";
  private static final String SEARCH_MODULE_TYPE_EDITION_BILLED =
      "metadata['module']:'%s' AND metadata['type']:'%s' AND metadata['edition']:'%s' AND metadata['billed']:'%s'";

  @Inject
  public StripeHelperImpl(TelemetryReporter telemetryReporter) {
    this.telemetryReporter = telemetryReporter;
    this.stripeHandler = new StripeHandlerImpl(this.telemetryReporter);
  }

  @Override
  public CustomerDetailDTO createCustomer(CustomerParams customerParams) {
    CustomerCreateParams.Address address = CustomerCreateParams.Address.builder()
                                               .setCity(customerParams.getAddress().getCity())
                                               .setCountry(customerParams.getAddress().getCountry())
                                               .setLine1(customerParams.getAddress().getLine1())
                                               .setLine2(customerParams.getAddress().getLine2())
                                               .setPostalCode(customerParams.getAddress().getPostalCode())
                                               .setState(customerParams.getAddress().getState())
                                               .build();

    CustomerCreateParams params =
        CustomerCreateParams.builder()
            .setAddress(address)
            .setEmail(customerParams.getBillingContactEmail())
            .setName(customerParams.getName())
            .setMetadata(ImmutableMap.of(ACCOUNT_IDENTIFIER_KEY, customerParams.getAccountIdentifier()))
            .build();

    Customer customer = stripeHandler.createCustomer(params);
    return toCustomerDetailDTO(customer);
  }

  @Override
  public CustomerDetailDTO updateCustomer(CustomerParams customerParams) {
    if (Strings.isNullOrEmpty(customerParams.getCustomerId())) {
      throw new InvalidArgumentsException("Customer id is missing");
    }
    CustomerUpdateParams.Builder paramsBuilder = CustomerUpdateParams.builder();
    paramsBuilder.addAllExpand(Lists.newArrayList("sources"));

    if (!Strings.isNullOrEmpty(customerParams.getAccountIdentifier())) {
      paramsBuilder.setName(customerParams.getName());
    }
    if (!Strings.isNullOrEmpty(customerParams.getBillingContactEmail())) {
      paramsBuilder.setEmail(customerParams.getBillingContactEmail());
    }
    if (!Strings.isNullOrEmpty(customerParams.getAccountIdentifier())) {
      paramsBuilder.setMetadata(ImmutableMap.of("accountId", customerParams.getAccountIdentifier()));
    }
    if (customerParams.getAddress() != null) {
      AddressDto addressDto = customerParams.getAddress();
      paramsBuilder.setAddress(CustomerUpdateParams.Address.builder()
                                   .setLine1(addressDto.getLine1())
                                   .setLine2(addressDto.getLine2())
                                   .setCity(addressDto.getCity())
                                   .setState(addressDto.getState())
                                   .setPostalCode(addressDto.getPostalCode())
                                   .setCountry(addressDto.getCountry())
                                   .build());
    }

    Customer customer = stripeHandler.updateCustomer(customerParams.getCustomerId(), paramsBuilder.build());
    return toCustomerDetailDTO(customer);
  }

  @Override
  public CustomerDetailDTO updateBilling(BillingParams billingParams) {
    CustomerUpdateParams.Builder paramsBuilder = CustomerUpdateParams.builder();
    CustomerUpdateParams.Address.Builder newAddress = new CustomerUpdateParams.Address.Builder();

    if (!Strings.isNullOrEmpty(billingParams.getCity())) {
      newAddress.setCity(billingParams.getCity());
    }
    if (!Strings.isNullOrEmpty(billingParams.getCountry())) {
      newAddress.setCountry(billingParams.getCountry());
    }
    if (!Strings.isNullOrEmpty(billingParams.getState())) {
      newAddress.setState(billingParams.getState());
    }
    if (!Strings.isNullOrEmpty(billingParams.getLine1())) {
      newAddress.setLine1(billingParams.getLine1());
    }
    if (!Strings.isNullOrEmpty(billingParams.getLine2())) {
      newAddress.setLine2(billingParams.getLine2());
    }
    if (!Strings.isNullOrEmpty(billingParams.getZipCode())) {
      newAddress.setPostalCode(billingParams.getZipCode());
    }

    if (!Strings.isNullOrEmpty(billingParams.getCreditCardId())) {
      paramsBuilder.setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                                           .setDefaultPaymentMethod(billingParams.getCreditCardId())
                                           .build());
    }

    paramsBuilder.setAddress(newAddress.build());

    stripeHandler.linkPaymentMethodToCustomer(billingParams.getCustomerId(), billingParams.getCreditCardId());

    Customer customer = stripeHandler.updateCustomer(billingParams.getCustomerId(), paramsBuilder.build());
    return toCustomerDetailDTO(customer);
  }

  @Override
  public CustomerDetailDTO getCustomer(String customerId) {
    CustomerRetrieveParams params =
        CustomerRetrieveParams.builder().addAllExpand(Lists.newArrayList("sources")).build();
    return toCustomerDetailDTO(stripeHandler.retrieveCustomer(customerId, params));
  }

  @Override
  public PriceCollectionDTO getPrices(ModuleType moduleType) {
    PriceSearchParams params = PriceSearchParams.builder()
                                   .setQuery(String.format("metadata['module']:'%s'", moduleType.toString()))
                                   .addAllExpand(Lists.newArrayList("data.tiers"))
                                   .setLimit(100L)
                                   .build();

    List<Price> priceResults = stripeHandler.searchPrices(params).getData();

    return toPriceCollectionDTO(priceResults);
  }

  @Override
  public Optional<Price> getPrice(
      SubscriptionRequest subscriptionRequest, SubscriptionItemRequest subscriptionItemRequest) {
    String searchString = subscriptionItemRequest.isQuantityIncludedInPrice()
        ? buildSearchString(SEARCH_MODULE_TYPE_EDITION_BILLED_MAX, subscriptionRequest, subscriptionItemRequest)
        : buildSearchString(SEARCH_MODULE_TYPE_EDITION_BILLED, subscriptionRequest, subscriptionItemRequest);

    PriceSearchParams params =
        PriceSearchParams.builder().setQuery(searchString).addAllExpand(Lists.newArrayList("data.tiers")).build();

    return stripeHandler.searchPrices(params).getData().stream().findFirst();
  }

  private String buildSearchString(String searchStringBase, SubscriptionRequest subscriptionRequest,
      SubscriptionItemRequest subscriptionItemRequest) {
    return String.format(searchStringBase, subscriptionRequest.getModuleType().toString(),
        subscriptionItemRequest.getType(), subscriptionRequest.getEdition(), subscriptionRequest.getPaymentFrequency(),
        subscriptionItemRequest.getQuantity().toString());
  }

  @Override
  public Price getPrice(String lookupKey) {
    List<String> lookupKeyList = new ArrayList<>();
    lookupKeyList.add(lookupKey);

    PriceListParams params = PriceListParams.builder()
                                 .setActive(true)
                                 .addAllLookupKeys(lookupKeyList)
                                 .addAllExpand(Lists.newArrayList("data.tiers"))
                                 .build();

    return stripeHandler.listPrices(params).getData().get(0);
  }

  @Override
  public PriceCollectionDTO listPrices(List<String> lookupKeys) {
    PriceListParams params = PriceListParams.builder()
                                 .setActive(true)
                                 .addAllLookupKeys(lookupKeys)
                                 .addAllExpand(Lists.newArrayList("data.tiers"))
                                 .build();
    PriceCollection priceCollection = stripeHandler.listPrices(params);
    return toPriceCollectionDTO(priceCollection);
  }

  @Override
  public SubscriptionDetailDTO createSubscription(StripeSubscriptionRequest stripeSubscriptionRequest) {
    SubscriptionCreateParams.Builder creationParamsBuilder = SubscriptionCreateParams.builder();
    creationParamsBuilder.setCustomer(stripeSubscriptionRequest.getCustomerId())
        .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
        .addAllExpand(subscriptionExpandList)
        .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.ALWAYS_INVOICE)
        .setCollectionMethod(SubscriptionCreateParams.CollectionMethod.SEND_INVOICE)
        .setDaysUntilDue(7L);

    // Register subscription items
    stripeSubscriptionRequest.getItems().forEach(item
        -> creationParamsBuilder.addItem(SubscriptionCreateParams.Item.builder()
                                             .setPrice(item.getPriceId())
                                             .setQuantity(item.getQuantity())
                                             .build()));

    // Add metadata
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ACCOUNT_IDENTIFIER_KEY, stripeSubscriptionRequest.getAccountIdentifier());
    metadata.put(CUSTOMER_EMAIL_KEY, stripeSubscriptionRequest.getCustomerEmail());
    creationParamsBuilder.setMetadata(metadata);

    // Set payment method
    if (!Strings.isNullOrEmpty(stripeSubscriptionRequest.getPaymentMethodId())) {
      creationParamsBuilder.setDefaultPaymentMethod(stripeSubscriptionRequest.getPaymentMethodId());
    }

    Subscription subscription =
        stripeHandler.createSubscription(creationParamsBuilder.build(), stripeSubscriptionRequest.getModuleType());
    return toSubscriptionDetailDTO(subscription);
  }

  @Override
  public SubscriptionDetailDTO addToSubscription(
      StripeSubscriptionRequest subscriptionRequest, SubscriptionDetailDTO subscription) {
    subscription.getItems().stream().forEach(subscriptionItem -> {
      subscriptionRequest.getItems().add(StripeItemRequest.Builder.newInstance()
                                             .withQuantity(subscriptionItem.getQuantity())
                                             .withPriceId(subscriptionItem.getPrice().getPriceId())
                                             .build());
    });

    return updateSubscription(subscriptionRequest);
  }

  @Override
  public SubscriptionDetailDTO updateSubscription(StripeSubscriptionRequest stripeSubscriptionRequest) {
    Subscription subscription = stripeHandler.retrieveSubscription(stripeSubscriptionRequest.getSubscriptionId());

    // Collect item information in new subscription
    Map<String, StripeItemRequest> newItems = new HashMap<>();
    stripeSubscriptionRequest.getItems().forEach(item -> newItems.put(item.getPriceId(), item));

    // Go through current subscription and update.
    SubscriptionUpdateParams.Builder updateParamBuilder = SubscriptionUpdateParams.builder();
    updateParamBuilder.setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
        .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.ALLOW_INCOMPLETE)
        .addAllExpand(subscriptionExpandList);
    if (!newItems.isEmpty()) {
      List<SubscriptionItem> data = subscription.getItems().getData();
      // update or delete existing item in subscription
      for (SubscriptionItem item : data) {
        if (newItems.containsKey(item.getPrice().getId())) {
          updateParamBuilder.addItem(SubscriptionUpdateParams.Item.builder()
                                         .setId(item.getId())
                                         .setQuantity(newItems.get(item.getPrice().getId()).getQuantity())
                                         .build());
          newItems.remove(item.getPrice().getId());
        } else {
          updateParamBuilder.addItem(
              SubscriptionUpdateParams.Item.builder().setId(item.getId()).setDeleted(true).build());
        }
      }

      // add left new items in subscription
      for (StripeItemRequest newItem : newItems.values()) {
        updateParamBuilder.addItem(SubscriptionUpdateParams.Item.builder()
                                       .setPrice(newItem.getPriceId())
                                       .setQuantity(newItem.getQuantity())
                                       .build());
      }
    }

    Subscription updatedSubscription = stripeHandler.updateSubscription(stripeSubscriptionRequest.getSubscriptionId(),
        updateParamBuilder.build(), stripeSubscriptionRequest.getModuleType());

    SubscriptionDetailDTO subscriptionDetailDTO = toSubscriptionDetailDTO(updatedSubscription);

    stripeHandler.putInvoiceMetadata(updatedSubscription.getLatestInvoice(), ACCOUNT_IDENTIFIER_KEY,
        stripeSubscriptionRequest.getAccountIdentifier());

    return subscriptionDetailDTO;
  }

  @Override
  public SubscriptionDetailDTO updateSubscriptionDefaultPayment(StripeSubscriptionRequest stripeSubscriptionRequest) {
    SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                                                .setDefaultPaymentMethod(stripeSubscriptionRequest.getPaymentMethodId())
                                                .addAllExpand(subscriptionExpandList)
                                                .build();
    return toSubscriptionDetailDTO(stripeHandler.updateSubscription(
        stripeSubscriptionRequest.getSubscriptionId(), updateParams, stripeSubscriptionRequest.getModuleType()));
  }

  @Override
  public void cancelSubscription(StripeSubscriptionRequest stripeSubscriptionRequest) {
    stripeHandler.cancelSubscription(
        stripeSubscriptionRequest.getSubscriptionId(), stripeSubscriptionRequest.getModuleType());
  }

  @Override
  public SubscriptionDetailDTO retrieveSubscription(StripeSubscriptionRequest stripeSubscriptionRequest) {
    return toSubscriptionDetailDTO(
        stripeHandler.retrieveSubscription(stripeSubscriptionRequest.getSubscriptionId(), subscriptionExpandList));
  }

  @Override
  public InvoiceDetailDTO getUpcomingInvoice(String customerId) {
    if (Strings.isNullOrEmpty(customerId)) {
      throw new InvalidArgumentsException("Customer ID Required to retrieve an upcoming invoice.");
    }

    Map<String, Object> invoiceParams = new HashMap<>();
    invoiceParams.put("customer", customerId);

    Invoice invoice = stripeHandler.retrieveUpcomingInvoice(invoiceParams);

    return toInvoiceDetailDTO(invoice);
  }
  @Override
  public InvoiceDetailDTO previewInvoice(StripeSubscriptionRequest stripeSubscriptionRequest) {
    InvoiceUpcomingParams.Builder upcomingParamBuilder = InvoiceUpcomingParams.builder();

    if (!Strings.isNullOrEmpty(stripeSubscriptionRequest.getCustomerId())) {
      upcomingParamBuilder.setCustomer(stripeSubscriptionRequest.getCustomerId());
    }

    if (!Strings.isNullOrEmpty(stripeSubscriptionRequest.getSubscriptionId())) {
      Subscription subscription = stripeHandler.retrieveSubscription(stripeSubscriptionRequest.getSubscriptionId());

      // Delete existed items in subscription
      List<SubscriptionItem> data = subscription.getItems().getData();
      data.forEach(oldItem
          -> upcomingParamBuilder.addSubscriptionItem(
              InvoiceUpcomingParams.SubscriptionItem.builder().setId(oldItem.getId()).setDeleted(true).build()));

      // set subscription id and proration behavior
      upcomingParamBuilder.setSubscription(stripeSubscriptionRequest.getSubscriptionId())
          .setSubscriptionProrationBehavior(InvoiceUpcomingParams.SubscriptionProrationBehavior.CREATE_PRORATIONS);
    }

    // Add new items
    stripeSubscriptionRequest.getItems().forEach(newItem
        -> upcomingParamBuilder.addSubscriptionItem(InvoiceUpcomingParams.SubscriptionItem.builder()
                                                        .setPrice(newItem.getPriceId())
                                                        .setQuantity(newItem.getQuantity())
                                                        .build()));

    return toInvoiceDetailDTO(stripeHandler.previewInvoice(stripeSubscriptionRequest.getCustomerId(),
        stripeSubscriptionRequest.getSubscriptionId(), upcomingParamBuilder.build()));
  }

  @Override
  public void payInvoice(String invoiceId, String accountIdentifier) {
    stripeHandler.payInvoice(invoiceId, accountIdentifier);
  }

  @Override
  public Card deleteCard(String customerIdentifier, String creditCardIdentifier) {
    return stripeHandler.deleteCard(customerIdentifier, creditCardIdentifier);
  }

  @Override
  public PaymentMethodCollectionDTO listPaymentMethods(String customerId) {
    PaymentMethodCollection paymentMethodCollection = stripeHandler.retrievePaymentMethodsUnderCustomer(customerId);
    return toPaymentMethodCollectionDTO(paymentMethodCollection);
  }

  @Override
  public InvoiceDetailDTO finalizeInvoice(String invoiceId) {
    return toInvoiceDetailDTO(stripeHandler.finalizeInvoice(invoiceId));
  }

  private InvoiceDetailDTO toInvoiceDetailDTO(Invoice invoice) {
    if (invoice == null) {
      return null;
    }

    InvoiceDetailDTO dto = InvoiceDetailDTO.builder()
                               .subscriptionId(invoice.getSubscription())
                               .totalAmount(invoice.getTotal())
                               .amountDue(invoice.getAmountDue())
                               .periodEnd(invoice.getPeriodEnd())
                               .periodStart(invoice.getPeriodStart())
                               .nextPaymentAttempt(invoice.getNextPaymentAttempt())
                               .invoiceId(invoice.getId())
                               .paymentIntent(toPaymentIntentDetailDTO(invoice.getPaymentIntentObject()))
                               .items(new ArrayList<>())
                               .build();
    for (InvoiceLineItem item : invoice.getLines().getData()) {
      ItemDTO itemDTO = ItemDTO.builder()
                            .amount(item.getAmount())
                            .quantity(item.getQuantity())
                            .description(item.getDescription())
                            .proration(item.getProration())
                            .price(toPriceDTO(item.getPrice()))
                            .build();
      dto.getItems().add(itemDTO);
    }

    return dto;
  }

  private PaymentIntentDetailDTO toPaymentIntentDetailDTO(PaymentIntent paymentIntent) {
    if (paymentIntent == null) {
      return null;
    }

    return PaymentIntentDetailDTO.builder()
        .clientSecret(paymentIntent.getClientSecret())
        .id(paymentIntent.getId())
        .status(paymentIntent.getStatus())
        .nextAction(toNextActionDetailDTO(paymentIntent.getNextAction()))
        .build();
  }

  private NextActionDetailDTO toNextActionDetailDTO(PaymentIntent.NextAction nextAction) {
    if (nextAction == null) {
      return null;
    }

    return NextActionDetailDTO.builder().type(nextAction.getType()).useStripeSdk(nextAction.getUseStripeSdk()).build();
  }

  private AddressDto toAddressDto(Address address) {
    if (address == null) {
      return null;
    }

    return AddressDto.builder()
        .city(address.getCity())
        .country(address.getCountry())
        .city(address.getCity())
        .line1(address.getLine1())
        .line2(address.getLine2())
        .postalCode(address.getPostalCode())
        .state(address.getState())
        .build();
  }

  private CustomerDetailDTO toCustomerDetailDTO(Customer customer) {
    AddressDto address = toAddressDto(customer.getAddress());

    CustomerDetailDTOBuilder builder = CustomerDetailDTO.builder();
    builder.customerId(customer.getId())
        .address(address)
        .billingEmail(customer.getEmail())
        .companyName(customer.getName());
    // display default payment method
    if (customer.getInvoiceSettings() != null) {
      builder.defaultSource(customer.getInvoiceSettings().getDefaultPaymentMethod());
    }
    return builder.build();
  }

  private CardDTO toCardDTO(PaymentMethod paymentMethod) {
    PaymentMethod.Card card = paymentMethod.getCard();
    CardDTO cardDTO = CardDTO.builder()
                          .addressCountry(card.getCountry())
                          .brand(card.getBrand())
                          .expireMonth(card.getExpMonth())
                          .expireYear(card.getExpYear())
                          .id(paymentMethod.getId())
                          .last4(card.getLast4())
                          .funding(card.getFunding())
                          .build();

    if (paymentMethod.getBillingDetails() != null) {
      cardDTO.setName(paymentMethod.getBillingDetails().getName());
      cardDTO.setAddressCity(paymentMethod.getBillingDetails().getAddress().getCity());
      cardDTO.setAddressCountry(paymentMethod.getBillingDetails().getAddress().getCountry());
      cardDTO.setAddressLine1(paymentMethod.getBillingDetails().getAddress().getLine1());
      cardDTO.setAddressLine2(paymentMethod.getBillingDetails().getAddress().getLine2());
      cardDTO.setAddressState(paymentMethod.getBillingDetails().getAddress().getState());
      cardDTO.setAddressZip(paymentMethod.getBillingDetails().getAddress().getPostalCode());
    }
    return cardDTO;
  }

  private PriceCollectionDTO toPriceCollectionDTO(List<Price> priceList) {
    PriceCollectionDTO priceCollectionDTO = PriceCollectionDTO.builder().prices(new ArrayList<>()).build();

    List<Price> data = priceList;
    for (Price price : data) {
      priceCollectionDTO.getPrices().add(toPriceDTO(price));
    }
    return priceCollectionDTO;
  }

  private PriceCollectionDTO toPriceCollectionDTO(PriceCollection priceCollection) {
    PriceCollectionDTO priceCollectionDTO = PriceCollectionDTO.builder().prices(new ArrayList<>()).build();

    List<Price> data = priceCollection.getData();
    for (Price price : data) {
      priceCollectionDTO.getPrices().add(toPriceDTO(price));
    }
    return priceCollectionDTO;
  }

  private PriceDTO toPriceDTO(Price price) {
    PriceDTO priceDTO = PriceDTO.builder()
                            .productId(price.getProduct())
                            .lookupKey(price.getLookupKey())
                            .isActive(price.getActive())
                            .currency(price.getCurrency())
                            .priceId(price.getId())
                            .metaData(price.getMetadata())
                            .build();

    if (Strings.isNullOrEmpty(price.getTiersMode())) {
      priceDTO.setUnitAmount(price.getUnitAmount());
    } else {
      priceDTO.setTierMode(TierMode.fromString(price.getTiersMode()));
      if (price.getTiers() != null) {
        priceDTO.setTiersDTO(price.getTiers()
                                 .stream()
                                 .map(t -> TiersDTO.builder().unitAmount(t.getUnitAmount()).upTo(t.getUpTo()).build())
                                 .collect(Collectors.toList()));
      }
    }
    return priceDTO;
  }

  private List<ItemDTO> toItemDTOList(SubscriptionItemCollection subscriptionItemCollection) {
    List<ItemDTO> itemDTOList = new LinkedList<>();
    subscriptionItemCollection.getData().forEach(subscriptionItem -> {
      itemDTOList.add(ItemDTO.builder()
                          .quantity(subscriptionItem.getQuantity())
                          .price(toPriceDTO(subscriptionItem.getPrice()))
                          .build());
    });
    return itemDTOList;
  }

  private SubscriptionDetailDTO toSubscriptionDetailDTO(Subscription subscription) {
    SubscriptionDetailDTO dto = SubscriptionDetailDTO.builder()
                                    .items(toItemDTOList(subscription.getItems()))
                                    .subscriptionId(subscription.getId())
                                    .accountIdentifier(subscription.getMetadata().get(ACCOUNT_IDENTIFIER_KEY))
                                    .customerId(subscription.getCustomer())
                                    .status(subscription.getStatus())
                                    .latestInvoice(subscription.getLatestInvoice())
                                    .cancelAt(subscription.getCancelAt())
                                    .canceledAt(subscription.getCanceledAt())
                                    .pendingUpdate(toPendingUpdateDetailDTO(subscription.getPendingUpdate()))
                                    .latestInvoiceDetail(toInvoiceDetailDTO(subscription.getLatestInvoiceObject()))
                                    .build();

    if (subscription.getLatestInvoiceObject() != null
        && subscription.getLatestInvoiceObject().getPaymentIntentObject() != null) {
      dto.setClientSecret(subscription.getLatestInvoiceObject().getPaymentIntentObject().getClientSecret());
    }
    return dto;
  }

  private PendingUpdateDetailDTO toPendingUpdateDetailDTO(Subscription.PendingUpdate pendingUpdate) {
    if (pendingUpdate == null) {
      return null;
    }
    return PendingUpdateDetailDTO.builder().expiresAt(pendingUpdate.getExpiresAt()).build();
  }

  private PaymentMethodCollectionDTO toPaymentMethodCollectionDTO(PaymentMethodCollection paymentMethodCollection) {
    PaymentMethodCollectionDTO dto = PaymentMethodCollectionDTO.builder().paymentMethods(Lists.newArrayList()).build();
    paymentMethodCollection.getData().forEach(p -> dto.getPaymentMethods().add(toCardDTO(p)));
    return dto;
  }
}