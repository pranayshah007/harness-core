/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.api.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.subscription.dto.AddressDto;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.ItemDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.PriceDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.params.StripeItemRequest;
import io.harness.subscription.params.SubscriptionItemRequest;
import io.harness.subscription.params.SubscriptionRequest;
import io.harness.subscription.params.UsageKey;
import io.harness.subscription.resource.SubscriptionResource;
import io.harness.subscription.services.SubscriptionService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SubscriptionResourceTest extends CategoryTest {
  @Mock SubscriptionService subscriptionService;
  @InjectMocks SubscriptionResource subscriptionResource;
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final ModuleType DEFAULT_MODULE_TYPE = ModuleType.CF;
  private static final Long DEFAULT_NUMBER_MAUS = 300000L; // 300k
  private static final Long DEFAULT_NUMBER_USERS = 10L;
  private static final EnumMap<UsageKey, Long> DEFAULT_RECOMMENDATION =
      new EnumMap<>(Map.of(UsageKey.NUMBER_OF_USERS, 10L, UsageKey.NUMBER_OF_MAUS, 300000L));
  private static final long DEFAULT_MAU_PRICE = 1000L;
  private static final long DEFAULT_DEVELOPER_PRICE = 100L;
  private static final String TEAM_EDITION = "team";
  private static final String MAU_TYPE = "MAU";
  private static final String DEVELOPERS_TYPE = "DEVELOPERS";
  private static final String MAU_PRICE_ID = "mau_price_id";
  private static final String DEVELOPER_PRICE_ID = "developer_price_id";
  private static final String MAU_PRODUCT_ID = "mau_product_id";
  private static final String DEVELOPER_PRODUCT_ID = "developer_product_id";
  private static final String YEARLY_PAYMENT_FREQUENCY = "Yearly";
  private static final String INVOICE_ID = "invoice_id";
  private static final String DEFAULT_MAU_DESCRIPTION = "Feature Flags - Team - Up to 500k MAUs - Monthly";
  private static final String DEFAULT_DEVELOPER_DESCRIPTION = "Feature Flags - Team - Users - Monthly";

  private static final List<SubscriptionItemRequest> DEFAULT_ITEM_LIST = List.of(SubscriptionItemRequest.builder()
                                                                                     .type(MAU_TYPE)
                                                                                     .quantity(DEFAULT_NUMBER_MAUS)
                                                                                     .quantityIncludedInPrice(true)
                                                                                     .build(),
      SubscriptionItemRequest.builder()
          .type(DEVELOPERS_TYPE)
          .quantity(DEFAULT_NUMBER_USERS)
          .quantityIncludedInPrice(false)
          .build());

  private static final List<StripeItemRequest> DEFAULT_STRIPE_ITEM_LIST =
      List.of(StripeItemRequest.Builder.newInstance()
                  .withQuantity(DEFAULT_NUMBER_MAUS)
                  .withQuantityIncludedInPrice(true)
                  .withPriceId(MAU_PRICE_ID)
                  .build(),
          StripeItemRequest.Builder.newInstance()
              .withQuantity(DEFAULT_NUMBER_USERS)
              .withQuantityIncludedInPrice(false)
              .withPriceId(DEVELOPER_PRICE_ID)
              .build());

  private static final PriceDTO DEFAULT_MAU_PRICE_DTO =
      PriceDTO.builder().priceId(MAU_PRICE_ID).productId(MAU_PRODUCT_ID).unitAmount(DEFAULT_MAU_PRICE).build();

  private static final PriceDTO DEFAULT_DEVELOPER_PRICE_DTO = PriceDTO.builder()
                                                                  .priceId(DEVELOPER_PRICE_ID)
                                                                  .productId(DEVELOPER_PRODUCT_ID)
                                                                  .unitAmount(DEFAULT_DEVELOPER_PRICE)
                                                                  .build();

  private static final PriceCollectionDTO DEFAULT_PRICE_COLLECTION_DTO =
      PriceCollectionDTO.builder().prices(List.of(DEFAULT_MAU_PRICE_DTO, DEFAULT_DEVELOPER_PRICE_DTO)).build();

  private static final CustomerDTO DEFAULT_CUSTOMER_DTO = CustomerDTO.builder()
                                                              .address(AddressDto.builder()
                                                                           .line1("Address Line 1")
                                                                           .postalCode("90210")
                                                                           .state("CA")
                                                                           .city("Beverly Hills")
                                                                           .build())
                                                              .build();

  private static final SubscriptionRequest DEFAULT_SUBSCRIPTION_REQUEST =
      SubscriptionRequest.builder()
          .accountIdentifier(ACCOUNT_IDENTIFIER)
          .edition(TEAM_EDITION)
          .moduleType(DEFAULT_MODULE_TYPE)
          .paymentFrequency(YEARLY_PAYMENT_FREQUENCY)
          .premiumSupport(false)
          .customer(DEFAULT_CUSTOMER_DTO)
          .items(DEFAULT_ITEM_LIST)
          .build();

  private static final SubscriptionDetailDTO DEFAULT_SUBSCRIPTION_DETAIL_DTO =
      SubscriptionDetailDTO.builder()
          .accountIdentifier(ACCOUNT_IDENTIFIER)
          .latestInvoice(INVOICE_ID)
          .moduletype(DEFAULT_MODULE_TYPE)
          .subscriptionId(SUBSCRIPTION_ID)
          .build();

  private static final SubscriptionDTO DEFAULT_SUBSCRIPTION_DTO = SubscriptionDTO.builder()
                                                                      .moduleType(DEFAULT_MODULE_TYPE)
                                                                      .customer(DEFAULT_CUSTOMER_DTO)
                                                                      .items(DEFAULT_STRIPE_ITEM_LIST)
                                                                      .build();

  private static final List<ItemDTO> DEFAULT_ITEM_DTO_LIST = List.of(ItemDTO.builder()
                                                                         .description(DEFAULT_MAU_DESCRIPTION)
                                                                         .price(DEFAULT_MAU_PRICE_DTO)
                                                                         .quantity(DEFAULT_NUMBER_MAUS)
                                                                         .build(),
      ItemDTO.builder()
          .description(DEFAULT_DEVELOPER_DESCRIPTION)
          .price(DEFAULT_DEVELOPER_PRICE_DTO)
          .quantity(DEFAULT_NUMBER_USERS)
          .build());

  private static final InvoiceDetailDTO DEFAULT_INVOICE_DETAIL_DTO = InvoiceDetailDTO.builder()
                                                                         .invoiceId(INVOICE_ID)
                                                                         .subscriptionId(SUBSCRIPTION_ID)
                                                                         .items(DEFAULT_ITEM_DTO_LIST)
                                                                         .build();

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testGetRecommendation() {
    doReturn(DEFAULT_RECOMMENDATION)
        .when(subscriptionService)
        .getRecommendation(ACCOUNT_IDENTIFIER, DEFAULT_NUMBER_MAUS, DEFAULT_NUMBER_USERS);

    ResponseDTO<EnumMap<UsageKey, Long>> responseDTO =
        subscriptionResource.retrieveRecommendedUsage(ACCOUNT_IDENTIFIER, DEFAULT_NUMBER_MAUS, DEFAULT_NUMBER_USERS);

    Mockito.verify(subscriptionService, times(1))
        .getRecommendation(ACCOUNT_IDENTIFIER, DEFAULT_NUMBER_MAUS, DEFAULT_NUMBER_USERS);
    assertThat(responseDTO.getData()).isNotNull();
    assertThat(responseDTO.getData().values().stream().findFirst()).isPresent();
    assertThat(responseDTO.getData().get(UsageKey.NUMBER_OF_MAUS).longValue()).isEqualTo(DEFAULT_NUMBER_MAUS);
    assertThat(responseDTO.getData().get(UsageKey.NUMBER_OF_USERS).longValue()).isEqualTo(DEFAULT_NUMBER_USERS);
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testGetPrices() {
    doReturn(DEFAULT_PRICE_COLLECTION_DTO)
        .when(subscriptionService)
        .listPrices(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    ResponseDTO<PriceCollectionDTO> responseDTO =
        subscriptionResource.retrieveProductPrices(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    Mockito.verify(subscriptionService, times(1)).listPrices(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    assertThat(responseDTO.getData()).isNotNull();
    assertThat(responseDTO.getData().getPrices().get(0).getUnitAmount()).isEqualTo(DEFAULT_MAU_PRICE);
    assertThat(responseDTO.getData().getPrices().get(1).getUnitAmount()).isEqualTo(DEFAULT_DEVELOPER_PRICE);
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testCreateSubscription() {
    doReturn(DEFAULT_SUBSCRIPTION_DETAIL_DTO)
        .when(subscriptionService)
        .createSubscription(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_REQUEST);

    ResponseDTO<SubscriptionDetailDTO> responseDTO =
        subscriptionResource.createSubscription(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_REQUEST);

    Mockito.verify(subscriptionService, times(1)).createSubscription(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_REQUEST);

    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testUpdateSubscription() {
    doReturn(DEFAULT_SUBSCRIPTION_DETAIL_DTO)
        .when(subscriptionService)
        .updateSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID, DEFAULT_SUBSCRIPTION_DTO);

    ResponseDTO<SubscriptionDetailDTO> responseDTO =
        subscriptionResource.updateSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID, DEFAULT_SUBSCRIPTION_DTO);

    Mockito.verify(subscriptionService, times(1))
        .updateSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID, DEFAULT_SUBSCRIPTION_DTO);

    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testCancelSubscription() {
    ResponseDTO responseDTO = subscriptionResource.cancelSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID);

    Mockito.verify(subscriptionService, times(1)).cancelSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID);

    assertThat(responseDTO).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testGetSubscription() {
    doReturn(DEFAULT_SUBSCRIPTION_DETAIL_DTO)
        .when(subscriptionService)
        .getSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID);

    ResponseDTO<SubscriptionDetailDTO> responseDTO =
        subscriptionResource.retrieveSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID);

    Mockito.verify(subscriptionService, times(1)).getSubscription(ACCOUNT_IDENTIFIER, SUBSCRIPTION_ID);
    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testListSubscription() {
    doReturn(List.of(DEFAULT_SUBSCRIPTION_DETAIL_DTO))
        .when(subscriptionService)
        .listSubscriptions(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    ResponseDTO<List<SubscriptionDetailDTO>> responseDTO =
        subscriptionResource.listSubscriptions(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    Mockito.verify(subscriptionService, times(1)).listSubscriptions(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);
    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testRetrieveUpcomingInvoice() {
    doReturn(DEFAULT_INVOICE_DETAIL_DTO)
        .when(subscriptionService)
        .previewInvoice(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_DTO);

    ResponseDTO<InvoiceDetailDTO> responseDTO =
        subscriptionResource.retrieveUpcomingInvoice(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_DTO);

    Mockito.verify(subscriptionService, times(1)).previewInvoice(ACCOUNT_IDENTIFIER, DEFAULT_SUBSCRIPTION_DTO);
    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.TOMMY)
  @Category(UnitTests.class)
  public void testPayInvoice() {
    RestResponse responseDTO = subscriptionResource.payInvoice(ACCOUNT_IDENTIFIER, INVOICE_ID);

    Mockito.verify(subscriptionService, times(1)).payInvoice(INVOICE_ID);
    assertThat(responseDTO).isNotNull();
  }
}
