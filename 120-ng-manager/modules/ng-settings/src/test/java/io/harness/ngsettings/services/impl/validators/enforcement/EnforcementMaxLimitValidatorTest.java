/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators.enforcement;

import static io.harness.rule.OwnerRule.TEJAS;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.EnforcementClient;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import javax.ws.rs.InternalServerErrorException;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class EnforcementMaxLimitValidatorTest extends CategoryTest {
  @Mock private EnforcementClient enforcementClient;
  private EnforcementMaxLimitValidator enforcementMaxLimitValidator;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    enforcementMaxLimitValidator = new EnforcementMaxLimitValidator(enforcementClient);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    String value = "1";
    String accountIdentifier = randomAlphabetic(10);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingDTO settingDTO = SettingDTO.builder().name(randomAlphabetic(10)).value(value).build();
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO =
        FeatureRestrictionMetadataDTO.builder()
            .edition(Edition.FREE)
            .restrictionMetadata(ImmutableMap.of(Edition.FREE,
                StaticLimitRestrictionMetadataDTO.builder()
                    .restrictionType(RestrictionType.STATIC_LIMIT)
                    .limit(10L)
                    .build()))
            .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> request = mock(Call.class);

    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier))
        .thenReturn(request);

    enforcementMaxLimitValidator.validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);

    verify(enforcementClient, times(1)).getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidationFailure() throws IOException {
    String value = "100";
    Long limit = 10L;
    String accountIdentifier = randomAlphabetic(10);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingDTO settingDTO = SettingDTO.builder().name(randomAlphabetic(10)).value(value).build();
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO =
        FeatureRestrictionMetadataDTO.builder()
            .edition(Edition.FREE)
            .restrictionMetadata(ImmutableMap.of(Edition.FREE,
                StaticLimitRestrictionMetadataDTO.builder()
                    .restrictionType(RestrictionType.STATIC_LIMIT)
                    .limit(limit)
                    .build()))
            .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> request = mock(Call.class);

    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier))
        .thenReturn(request);

    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(
        String.format("%s cannot be greater than %s for given account plan", settingDTO.getName(), limit));
    enforcementMaxLimitValidator.validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidationEnforcementFailure() throws IOException {
    String value = "100";
    String accountIdentifier = randomAlphabetic(10);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingDTO settingDTO = SettingDTO.builder().name(randomAlphabetic(10)).value(value).build();

    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> request = mock(Call.class);
    ResponseBody body = mock(ResponseBody.class);

    when(request.execute()).thenReturn(Response.error(400, body));
    when(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier))
        .thenReturn(request);

    exceptionRule.expect(InternalServerErrorException.class);
    exceptionRule.expectMessage("Failed to fetch enforcement limits for the given account plan.");
    enforcementMaxLimitValidator.validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidationEnforcementNullResponseFailure() throws IOException {
    String value = "100";
    String accountIdentifier = randomAlphabetic(10);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingDTO settingDTO = SettingDTO.builder().name(randomAlphabetic(10)).value(value).build();

    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> request = mock(Call.class);

    when(request.execute()).thenReturn(Response.success(null));
    when(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier))
        .thenReturn(request);

    exceptionRule.expect(InternalServerErrorException.class);
    exceptionRule.expectMessage("Failed to fetch enforcement limits for the given account plan.");
    enforcementMaxLimitValidator.validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidationEnforcementEditionMetadataMissingFailure() throws IOException {
    String value = "100";
    Long limit = 10L;
    String accountIdentifier = randomAlphabetic(10);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingDTO settingDTO = SettingDTO.builder().name(randomAlphabetic(10)).value(value).build();

    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO =
        FeatureRestrictionMetadataDTO.builder()
            .edition(Edition.FREE)
            .restrictionMetadata(ImmutableMap.of(Edition.ENTERPRISE,
                StaticLimitRestrictionMetadataDTO.builder()
                    .restrictionType(RestrictionType.STATIC_LIMIT)
                    .limit(limit)
                    .build()))
            .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> request = mock(Call.class);

    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier))
        .thenReturn(request);

    exceptionRule.expect(InternalServerErrorException.class);
    exceptionRule.expectMessage("Failed to fetch enforcement limits for the given account plan.");
    enforcementMaxLimitValidator.validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);
  }
}
