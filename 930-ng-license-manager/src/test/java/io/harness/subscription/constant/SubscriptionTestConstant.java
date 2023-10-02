/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.constant;

import static io.harness.subscription.entities.SubscriptionDetail.INCOMPLETE;
import static io.harness.subscription.params.UsageKey.NUMBER_OF_MAUS;
import static io.harness.subscription.params.UsageKey.NUMBER_OF_USERS;

import io.harness.ModuleType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.subscription.dto.AddressDto;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.RecommendationRequest;
import io.harness.subscription.params.StripeItemRequest;
import io.harness.subscription.params.StripeSubscriptionRequest;
import io.harness.subscription.params.SubscriptionItemRequest;
import io.harness.subscription.params.SubscriptionRequest;
import io.harness.subscription.params.UsageKey;

import com.stripe.model.Price;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.joda.time.DateTime;

public class SubscriptionTestConstant {
  public static final String DEFAULT_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  public static final String TRIAL_ACCOUNT_ID = "TRIAL_ACCOUNT_ID";
  public static final String CUSTOMER_ID = "TEST_CUSTOMER_ID";
  public static final String COMPANY_ID = "TEST_COMPANY_ID";
  public static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";
  public static final String COMPANY_NAME = "TEST_COMPANY_NAME";
  public static final String BILLING_EMAIL = "test@testing.xyz";
  public static final String YEARLY_PAYMENT_FREQUENCY = "Yearly";
  public static final String MONTHLY_PAYMENT_FREQUENCY = "Monthly";
  public static final String ENTERPRISE_EDITION = "ENTERPRISE";
  public static final String TEAM_EDITION = "TEAM";
  public static final String MAU_TYPE = "MAU";
  public static final String CF_MODULE_TYPE = "CF";
  public static final String DEVELOPERS_TYPE = "DEVELOPERS";
  public static final String DEFAULT_MAU_PRICE_ID = "DEFAULT_MAU_PRICE_ID";
  public static final String DEFAULT_DEVELOPER_PRICE_ID = "DEFAULT_DEVELOPER_PRICE_ID";

  public static final String MAX_KEY = "max";
  public static final String MAX_DEVELOPERS_VALUE = "50";
  public static final Long DEFAULT_MAU_QUANTITY = 300000L;
  public static final Long DEFAULT_DEVELOPER_QUANTITY = 50L;
  public static final Long OVER_MAX_DEVELOPERS_QUANTITY = 51L;
  public static final int DEFAULT_MAX_USERS = 10;

  public static final AccountDTO DEFAULT_ACCOUNT = AccountDTO.builder().isProductLed(true).build();

  public static ModuleLicense DEFAULT_MODULE_LICENSE =
      CFModuleLicense.builder().numberOfClientMAUs(DEFAULT_MAU_QUANTITY).numberOfUsers(DEFAULT_MAX_USERS).build();
  static {
    DEFAULT_MODULE_LICENSE.setStatus(LicenseStatus.ACTIVE);
    DEFAULT_MODULE_LICENSE.setAccountIdentifier(DEFAULT_ACCOUNT_ID);
    DEFAULT_MODULE_LICENSE.setEdition(Edition.ENTERPRISE);
    DEFAULT_MODULE_LICENSE.setLicenseType(LicenseType.PAID);
    DEFAULT_MODULE_LICENSE.setModuleType(ModuleType.CF);
    DEFAULT_MODULE_LICENSE.setStartTime(DateTime.now().getMillis());
    DEFAULT_MODULE_LICENSE.setExpiryTime(Long.MAX_VALUE);
  }
  public static ModuleLicense TRIAL_MODULE_LICENSE =
      CFModuleLicense.builder().numberOfClientMAUs(DEFAULT_MAU_QUANTITY).numberOfUsers(DEFAULT_MAX_USERS).build();
  static {
    TRIAL_MODULE_LICENSE.setStatus(LicenseStatus.ACTIVE);
    TRIAL_MODULE_LICENSE.setAccountIdentifier(TRIAL_ACCOUNT_ID);
    TRIAL_MODULE_LICENSE.setEdition(Edition.ENTERPRISE);
    TRIAL_MODULE_LICENSE.setLicenseType(LicenseType.TRIAL);
    TRIAL_MODULE_LICENSE.setModuleType(ModuleType.CF);
    TRIAL_MODULE_LICENSE.setStartTime(DateTime.now().getMillis());
    TRIAL_MODULE_LICENSE.setExpiryTime(Long.MAX_VALUE);
  }

  public static EnumMap<UsageKey, Long> CF_USAGE_MAP = new EnumMap<>(UsageKey.class);
  static {
    CF_USAGE_MAP.put(NUMBER_OF_MAUS, 1000l);
    CF_USAGE_MAP.put(NUMBER_OF_USERS, 5l);
  }

  public static AddressDto DEFAULT_ADDRESS = AddressDto.builder()
                                                 .city("San Francisco")
                                                 .state("CA")
                                                 .postalCode("94108")
                                                 .line1("55 Stockton St")
                                                 .country("US")
                                                 .build();

  public static CustomerDTO DEFAULT_CUSTOMER_DTO =
      CustomerDTO.builder().address(DEFAULT_ADDRESS).companyName(COMPANY_NAME).billingEmail(BILLING_EMAIL).build();
  public static StripeCustomer DEFAULT_STRIPE_CUSTOMER = StripeCustomer.builder()
                                                             .id(COMPANY_ID)
                                                             .accountIdentifier(DEFAULT_ACCOUNT_ID)
                                                             .billingEmail(BILLING_EMAIL)
                                                             .companyName(COMPANY_NAME)
                                                             .customerId(CUSTOMER_ID)
                                                             .build();
  public static CustomerDetailDTO DEFAULT_CUSTOMER_DETAIL_DTO =
      CustomerDetailDTO.builder().companyName(COMPANY_NAME).billingEmail(BILLING_EMAIL).customerId(CUSTOMER_ID).build();
  public static CustomerParams DEFAULT_CUSTOMER_PARAMS = CustomerParams.builder()
                                                             .customerId(CUSTOMER_ID)
                                                             .address(DEFAULT_ADDRESS)
                                                             .billingContactEmail(BILLING_EMAIL)
                                                             .name(COMPANY_NAME)
                                                             .build();

  public static SubscriptionItemRequest DEFAULT_MAU_ITEM = SubscriptionItemRequest.builder()
                                                               .quantityIncludedInPrice(true)
                                                               .quantity(DEFAULT_MAU_QUANTITY)
                                                               .type(MAU_TYPE)
                                                               .build();
  public static SubscriptionItemRequest DEFAULT_DEVELOPER_ITEM = SubscriptionItemRequest.builder()
                                                                     .quantityIncludedInPrice(false)
                                                                     .quantity(DEFAULT_DEVELOPER_QUANTITY)
                                                                     .type(DEVELOPERS_TYPE)
                                                                     .build();
  public static SubscriptionItemRequest OVER_MAX_DEVELOPER_ITEM = SubscriptionItemRequest.builder()
                                                                      .quantityIncludedInPrice(false)
                                                                      .quantity(OVER_MAX_DEVELOPERS_QUANTITY)
                                                                      .type(DEVELOPERS_TYPE)
                                                                      .build();

  public static List<SubscriptionItemRequest> DEFAULT_SUBSCRIPTION_ITEM_REQUEST =
      Arrays.asList(DEFAULT_MAU_ITEM, DEFAULT_DEVELOPER_ITEM);
  public static List<SubscriptionItemRequest> OVER_MAX_SUBSCRIPTION_ITEM_REQUEST =
      Arrays.asList(DEFAULT_MAU_ITEM, OVER_MAX_DEVELOPER_ITEM);

  public static StripeItemRequest DEFAULT_MAU_STRIPE_ITEM_REQUEST =
      StripeItemRequest.Builder.newInstance().withQuantity(1L).withPriceId(DEFAULT_MAU_PRICE_ID).build();
  public static StripeItemRequest DEFAULT_DEVELOPER_STRIPE_ITEM_REQUEST = StripeItemRequest.Builder.newInstance()
                                                                              .withQuantity(DEFAULT_DEVELOPER_QUANTITY)
                                                                              .withPriceId(DEFAULT_DEVELOPER_PRICE_ID)
                                                                              .build();
  public static StripeItemRequest OVER_MAX_DEVELOPER_STRIPE_ITEM_REQUEST = StripeItemRequest.Builder.newInstance()
                                                                               .withQuantity(DEFAULT_DEVELOPER_QUANTITY)
                                                                               .withPriceId(DEFAULT_DEVELOPER_PRICE_ID)
                                                                               .build();

  public static List<StripeItemRequest> DEFAULT_STRIPE_ITEM_REQUEST =
      Arrays.asList(DEFAULT_MAU_STRIPE_ITEM_REQUEST, DEFAULT_DEVELOPER_STRIPE_ITEM_REQUEST);
  public static List<StripeItemRequest> OVER_MAX_STRIPE_ITEM_REQUEST =
      Arrays.asList(DEFAULT_MAU_STRIPE_ITEM_REQUEST, OVER_MAX_DEVELOPER_STRIPE_ITEM_REQUEST);

  public static Optional<Price> DEFAULT_MAU_PRICE = Optional.of(new Price());
  static {
    DEFAULT_MAU_PRICE.get().setId(DEFAULT_MAU_PRICE_ID);
    DEFAULT_MAU_PRICE.get().setMetadata(Collections.emptyMap());
  }
  public static Optional<Price> DEFAULT_DEVELOPER_PRICE = Optional.of(new Price());
  static {
    DEFAULT_DEVELOPER_PRICE.get().setId(DEFAULT_DEVELOPER_PRICE_ID);
    DEFAULT_DEVELOPER_PRICE.get().setMetadata(Collections.singletonMap(MAX_KEY, MAX_DEVELOPERS_VALUE));
  }

  public static SubscriptionRequest DEFAULT_SUBSCRIPTION_REQUEST = SubscriptionRequest.builder()
                                                                       .customer(DEFAULT_CUSTOMER_DTO)
                                                                       .premiumSupport(false)
                                                                       .paymentFrequency(YEARLY_PAYMENT_FREQUENCY)
                                                                       .edition(TEAM_EDITION)
                                                                       .items(DEFAULT_SUBSCRIPTION_ITEM_REQUEST)
                                                                       .moduleType(ModuleType.CF)
                                                                       .build();

  public static SubscriptionRequest OVER_MAX_SUBSCRIPTION_REQUEST = SubscriptionRequest.builder()
                                                                        .customer(DEFAULT_CUSTOMER_DTO)
                                                                        .premiumSupport(false)
                                                                        .paymentFrequency(YEARLY_PAYMENT_FREQUENCY)
                                                                        .edition(TEAM_EDITION)
                                                                        .items(OVER_MAX_SUBSCRIPTION_ITEM_REQUEST)
                                                                        .moduleType(ModuleType.CF)
                                                                        .build();

  public static SubscriptionDetailDTO DEFAULT_SUBSCRIPTION_DETAIL_DTO = SubscriptionDetailDTO.builder()
                                                                            .accountIdentifier(DEFAULT_ACCOUNT_ID)
                                                                            .customerId(CUSTOMER_ID)
                                                                            .subscriptionId(SUBSCRIPTION_ID)
                                                                            .status(INCOMPLETE)
                                                                            .build();

  public static StripeSubscriptionRequest DEFAULT_STRIPE_SUBSCRIPTION_REQUEST =
      StripeSubscriptionRequest.builder()
          .customerId(CUSTOMER_ID)
          .moduleType(CF_MODULE_TYPE)
          .paymentFrequency(YEARLY_PAYMENT_FREQUENCY)
          .items(DEFAULT_STRIPE_ITEM_REQUEST)
          .build();
  public static StripeSubscriptionRequest OVER_MAX_STRIPE_SUBSCRIPTION_REQUEST =
      StripeSubscriptionRequest.builder()
          .customerId(CUSTOMER_ID)
          .moduleType(CF_MODULE_TYPE)
          .paymentFrequency(YEARLY_PAYMENT_FREQUENCY)
          .items(OVER_MAX_STRIPE_ITEM_REQUEST)
          .build();

  public static RecommendationRequest DEFAULT_RECOMMENDATION_REQUEST =
      RecommendationRequest.builder().moduleType(ModuleType.CF).usageMap(CF_USAGE_MAP).build();
}
