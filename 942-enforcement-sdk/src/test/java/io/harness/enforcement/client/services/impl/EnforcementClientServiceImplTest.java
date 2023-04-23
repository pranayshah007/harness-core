/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.client.services.impl;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.beans.TimeUnit;
import io.harness.enforcement.beans.metadata.AvailabilityRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.CustomRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.EnforcementClient;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.licensing.Edition;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

public class EnforcementClientServiceImplTest extends CategoryTest {
  private EnforcementClientServiceImpl enforcementClientService;
  private EnforcementClient enforcementClient;
  private EnforcementSdkRegisterService enforcementSdkRegisterService;
  private EnforcementClientConfiguration enforcementClientConfiguration;
  private FeatureRestrictionMetadataDTO dto;

  private FeatureRestrictionName featureRestrictionName = FeatureRestrictionName.TEST1;
  private String accountId = "accountId";

  @Before
  public void setup() throws IOException {
    enforcementClient = mock(EnforcementClient.class);
    enforcementSdkRegisterService = mock(EnforcementSdkRegisterService.class);
    enforcementClientConfiguration = mock(EnforcementClientConfiguration.class);
    dto = FeatureRestrictionMetadataDTO.builder()
              .name(featureRestrictionName)
              .edition(Edition.ENTERPRISE)
              .moduleType(ModuleType.CD)
              .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> metaDataCall = mock(Call.class);
    when(metaDataCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(dto)));
    when(enforcementClient.getFeatureRestrictionMetadata(any(), any())).thenReturn(metaDataCall);

    when(enforcementSdkRegisterService.getRestrictionUsageInterface(any()))
        .thenReturn((accountIdentifier, restrictionMetadataDTO) -> 10);

    when(enforcementSdkRegisterService.getCustomRestrictionInterface(any()))
        .thenReturn(customFeatureEvaluationDTO -> true);

    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);

    enforcementClientService = new EnforcementClientServiceImpl(
        enforcementClient, enforcementSdkRegisterService, enforcementClientConfiguration);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testIsAvailableWithAvailablility() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        AvailabilityRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.AVAILABILITY)
            .enabled(true)
            .build()));

    boolean result = enforcementClientService.isAvailable(featureRestrictionName, accountId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testIsEnforcementEnabled() {
    boolean result = enforcementClientService.isEnforcementEnabled();
    assertThat(result).isTrue();
    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(false);
    result = enforcementClientService.isEnforcementEnabled();
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testIsAvailableWithStaticLimit() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        RateLimitRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.RATE_LIMIT)
            .limit(Long.valueOf(10))
            .allowedIfEqual(false)
            .timeUnit(new TimeUnit(ChronoUnit.DAYS, 1))
            .build()));

    boolean result = enforcementClientService.isAvailable(featureRestrictionName, accountId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testIsAvailableWithRateLimit() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        StaticLimitRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.STATIC_LIMIT)
            .limit(Long.valueOf(11))
            .allowedIfEqual(false)
            .build()));

    boolean result = enforcementClientService.isAvailable(featureRestrictionName, accountId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testIsAvailableWithCustom() {
    dto.setRestrictionMetadata(ImmutableMap.of(
        Edition.ENTERPRISE, CustomRestrictionMetadataDTO.builder().restrictionType(RestrictionType.CUSTOM).build()));

    boolean result = enforcementClientService.isAvailable(featureRestrictionName, accountId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testIsAvailableWithDuration() {
    dto.setRestrictionMetadata(ImmutableMap.of(
        Edition.ENTERPRISE, CustomRestrictionMetadataDTO.builder().restrictionType(RestrictionType.DURATION).build()));

    boolean result = enforcementClientService.isAvailable(featureRestrictionName, accountId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void isAvailableWithIncrement() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        StaticLimitRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.STATIC_LIMIT)
            .limit(Long.valueOf(9))
            .allowedIfEqual(false)
            .build()));

    boolean result = enforcementClientService.isAvailableWithIncrement(featureRestrictionName, accountId, 2);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void checkAvailability() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        StaticLimitRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.STATIC_LIMIT)
            .limit(Long.valueOf(11))
            .allowedIfEqual(false)
            .build()));

    enforcementClientService.checkAvailability(featureRestrictionName, accountId);
  }

  @Test(expected = LimitExceededException.class)
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void checkAvailabilityWithIncrement() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        StaticLimitRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.STATIC_LIMIT)
            .limit(Long.valueOf(11))
            .allowedIfEqual(false)
            .build()));

    enforcementClientService.checkAvailabilityWithIncrement(featureRestrictionName, accountId, 2);
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void checkAvailabilityWithIncrementWithAvailabilityDisabled() {
    dto.setRestrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
        AvailabilityRestrictionMetadataDTO.builder()
            .restrictionType(RestrictionType.AVAILABILITY)
            .enabled(false)
            .build()));

    FeatureRestrictionName templateFeatureRestriction = FeatureRestrictionName.TEMPLATE_SERVICE;
    assertThatThrownBy(
        () -> enforcementClientService.checkAvailabilityWithIncrement(templateFeatureRestriction, accountId, 2))
        .hasMessage("[Template Library] Feature is not enabled. Please contact Harness Support")
        .isInstanceOf(FeatureNotSupportedException.class);
  }
}
