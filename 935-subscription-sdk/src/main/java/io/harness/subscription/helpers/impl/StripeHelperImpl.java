/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.helpers.impl;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Calendar.getInstance;

import io.harness.ModuleType;
import io.harness.exception.InvalidArgumentsException;
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
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.ItemParams;
import io.harness.subscription.params.SubscriptionParams;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
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
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerRetrieveParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoiceUpcomingParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class StripeHelperImpl implements StripeHelper {
  private StripeHandlerImpl stripeHandler;
  private List<String> subscriptionExpandList = Arrays.asList("latest_invoice.payment_intent");
  private static final String ACCOUNT_IDENTIFIER_KEY = "accountIdentifier";
  private static final String MODULE_TYPE_KEY = "moduleType";

  public StripeHelperImpl() {
    this.stripeHandler = new StripeHandlerImpl();
  }

  @Override
  public CustomerDetailDTO createCustomer(CustomerParams customerParams) {
    CustomerCreateParams params =
        CustomerCreateParams.builder()
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

    Customer customer = stripeHandler.updateCustomer(customerParams.getCustomerId(), paramsBuilder.build());
    return toCustomerDetailDTO(customer);
  }

  @Override
  public CustomerDetailDTO getCustomer(String customerId) {
    CustomerRetrieveParams params =
        CustomerRetrieveParams.builder().addAllExpand(Lists.newArrayList("sources")).build();
    return toCustomerDetailDTO(stripeHandler.retrieveCustomer(customerId, params));
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
  public SubscriptionDetailDTO createSubscription(SubscriptionParams subscriptionParams) {
    Calendar next = getInstance();

    next.set(DAY_OF_MONTH, 1);
    if (subscriptionParams.getPaymentFrequency().equals("MONTHLY")) {
      next.set(YEAR, next.get(YEAR));
      if (next.get(MONTH) == 11) {
        next.set(YEAR, next.get(YEAR) + 1);
        next.set(MONTH, 0);
      } else {
        next.set(MONTH, next.get(MONTH) + 1);
      }
    } else {
      next.set(YEAR, next.get(YEAR) + 1);
      next.set(MONTH, 0);
    }

    SubscriptionCreateParams.Builder creationParamsBuilder = SubscriptionCreateParams.builder();
    creationParamsBuilder.setCustomer(subscriptionParams.getCustomerId())
        .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
        .addAllExpand(subscriptionExpandList)
        .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.ALWAYS_INVOICE)
        .setBillingCycleAnchor(next.getTimeInMillis() / 1000L);

    // Register subscription items
    subscriptionParams.getItems().forEach(item
        -> creationParamsBuilder.addItem(SubscriptionCreateParams.Item.builder()
                                             .setPrice(item.getPriceId())
                                             .setQuantity(item.getQuantity())
                                             .build()));

    // Add metadata
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ACCOUNT_IDENTIFIER_KEY, subscriptionParams.getAccountIdentifier());
    metadata.put(MODULE_TYPE_KEY, subscriptionParams.getModuleType());
    creationParamsBuilder.setMetadata(metadata);

    // Set payment method
    if (!Strings.isNullOrEmpty(subscriptionParams.getPaymentMethodId())) {
      creationParamsBuilder.setDefaultPaymentMethod(subscriptionParams.getPaymentMethodId());
    }

    Subscription subscription = stripeHandler.createSubscription(creationParamsBuilder.build());
    return toSubscriptionDetailDTO(subscription);
  }

  @Override
  public SubscriptionDetailDTO updateSubscription(SubscriptionParams subscriptionParams) {
    Subscription subscription = stripeHandler.retrieveSubscription(subscriptionParams.getSubscriptionId());

    // Collect item information in new subscription
    Map<String, ItemParams> newItems = new HashMap<>();
    subscriptionParams.getItems().forEach(item -> newItems.put(item.getPriceId(), item));

    // Go through current subscription and update.
    SubscriptionUpdateParams.Builder updateParamBuilder = SubscriptionUpdateParams.builder();
    updateParamBuilder.setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
        .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.PENDING_IF_INCOMPLETE)
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
      for (ItemParams newItem : newItems.values()) {
        updateParamBuilder.addItem(SubscriptionUpdateParams.Item.builder()
                                       .setPrice(newItem.getPriceId())
                                       .setQuantity(newItem.getQuantity())
                                       .build());
      }
    }

    return toSubscriptionDetailDTO(
        stripeHandler.updateSubscription(subscriptionParams.getSubscriptionId(), updateParamBuilder.build()));
  }

  @Override
  public SubscriptionDetailDTO updateSubscriptionDefaultPayment(SubscriptionParams subscriptionParams) {
    SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                                                .setDefaultPaymentMethod(subscriptionParams.getPaymentMethodId())
                                                .addAllExpand(subscriptionExpandList)
                                                .build();
    return toSubscriptionDetailDTO(
        stripeHandler.updateSubscription(subscriptionParams.getSubscriptionId(), updateParams));
  }

  @Override
  public void cancelSubscription(SubscriptionParams subscriptionParams) {
    stripeHandler.cancelSubscription(subscriptionParams.getSubscriptionId());
  }

  @Override
  public SubscriptionDetailDTO retrieveSubscription(SubscriptionParams subscriptionParams) {
    return toSubscriptionDetailDTO(
        stripeHandler.retrieveSubscription(subscriptionParams.getSubscriptionId(), subscriptionExpandList));
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
  public InvoiceDetailDTO previewInvoice(SubscriptionParams subscriptionParams) {
    InvoiceUpcomingParams.Builder upcomingParamBuilder = InvoiceUpcomingParams.builder();

    if (!Strings.isNullOrEmpty(subscriptionParams.getCustomerId())) {
      upcomingParamBuilder.setCustomer(subscriptionParams.getCustomerId());
    }

    if (!Strings.isNullOrEmpty(subscriptionParams.getSubscriptionId())) {
      Subscription subscription = stripeHandler.retrieveSubscription(subscriptionParams.getSubscriptionId());

      // Delete existed items in subscription
      List<SubscriptionItem> data = subscription.getItems().getData();
      data.forEach(oldItem
          -> upcomingParamBuilder.addSubscriptionItem(
              InvoiceUpcomingParams.SubscriptionItem.builder().setId(oldItem.getId()).setDeleted(true).build()));

      // set subscription id and proration behavior
      upcomingParamBuilder.setSubscription(subscriptionParams.getSubscriptionId())
          .setSubscriptionProrationBehavior(InvoiceUpcomingParams.SubscriptionProrationBehavior.CREATE_PRORATIONS);
    }

    // Add new items
    subscriptionParams.getItems().forEach(newItem
        -> upcomingParamBuilder.addSubscriptionItem(InvoiceUpcomingParams.SubscriptionItem.builder()
                                                        .setPrice(newItem.getPriceId())
                                                        .setQuantity(newItem.getQuantity())
                                                        .build()));

    return toInvoiceDetailDTO(stripeHandler.previewInvoice(
        subscriptionParams.getCustomerId(), subscriptionParams.getSubscriptionId(), upcomingParamBuilder.build()));
  }

  @Override
  public PaymentMethodCollectionDTO listPaymentMethods(String customerId) {
    PaymentMethodCollection paymentMethodCollection = stripeHandler.retrievePaymentMethodsUnderCustomer(customerId);
    return toPaymentMethodCollectionDTO(paymentMethodCollection);
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

  private CustomerDetailDTO toCustomerDetailDTO(Customer customer) {
    CustomerDetailDTOBuilder builder = CustomerDetailDTO.builder();
    builder.customerId(customer.getId()).billingEmail(customer.getEmail()).companyName(customer.getName());
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

  private SubscriptionDetailDTO toSubscriptionDetailDTO(Subscription subscription) {
    SubscriptionDetailDTO dto = SubscriptionDetailDTO.builder()
                                    .subscriptionId(subscription.getId())
                                    .accountIdentifier(subscription.getMetadata().get(ACCOUNT_IDENTIFIER_KEY))
                                    .moduletype(ModuleType.valueOf(subscription.getMetadata().get(MODULE_TYPE_KEY)))
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