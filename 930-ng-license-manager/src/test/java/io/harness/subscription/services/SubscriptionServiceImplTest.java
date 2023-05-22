/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services;

import static io.harness.rule.OwnerRule.TOMMY;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_ACCOUNT_ID;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_MODULE_LICENSE;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_RECOMMENDATION_REQUEST;
import static io.harness.subscription.constant.SubscriptionTestConstant.TRIAL_ACCOUNT_ID;
import static io.harness.subscription.constant.SubscriptionTestConstant.TRIAL_MODULE_LICENSE;
import static io.harness.subscription.params.UsageKey.NUMBER_OF_MAUS;
import static io.harness.subscription.params.UsageKey.NUMBER_OF_USERS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.repositories.SubscriptionDetailRepository;
import io.harness.rule.Owner;
import io.harness.subscription.handlers.StripeEventHandler;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.UsageKey;
import io.harness.subscription.services.impl.SubscriptionServiceImpl;
import io.harness.subscription.utils.NGFeatureFlagHelperService;
import io.harness.telemetry.TelemetryReporter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class SubscriptionServiceImplTest extends CategoryTest {
  @Mock private StripeHelper stripeHelper;
  @Mock private ModuleLicenseRepository moduleLicenseRepository;
  @Mock private StripeCustomerRepository stripeCustomerRepository;
  @Mock private SubscriptionDetailRepository subscriptionDetailRepository;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private Map<String, StripeEventHandler> eventHandlerMap;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private AccountService accountService;

  private static SubscriptionServiceImpl subscriptionService;

  @Before
  public void setUp() {
    initMocks(this);

    subscriptionService = new SubscriptionServiceImpl(stripeHelper, moduleLicenseRepository, stripeCustomerRepository,
        subscriptionDetailRepository, ngFeatureFlagHelperService, telemetryReporter, accountService, eventHandlerMap);
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testGetRecommendation() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(DEFAULT_ACCOUNT_ID, ModuleType.CF))
        .thenReturn(List.of(DEFAULT_MODULE_LICENSE));

    EnumMap<UsageKey, Long> recommendations =
        subscriptionService.getRecommendationRc(DEFAULT_ACCOUNT_ID, DEFAULT_RECOMMENDATION_REQUEST);

    assertThat(recommendations.get(NUMBER_OF_MAUS)).isEqualTo(360000L);
    assertThat(recommendations.get(NUMBER_OF_USERS)).isEqualTo(12L);
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testGetTrialRecommendation() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(TRIAL_ACCOUNT_ID, ModuleType.CF))
        .thenReturn(List.of(TRIAL_MODULE_LICENSE));

    EnumMap<UsageKey, Long> recommendations =
        subscriptionService.getRecommendationRc(TRIAL_ACCOUNT_ID, DEFAULT_RECOMMENDATION_REQUEST);

    assertThat(recommendations.get(NUMBER_OF_MAUS)).isEqualTo(1200L);
    assertThat(recommendations.get(NUMBER_OF_USERS)).isEqualTo(6L);
  }
}