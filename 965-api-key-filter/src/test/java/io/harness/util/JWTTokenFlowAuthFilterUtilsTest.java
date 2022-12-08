/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.remote.TokenClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class JWTTokenFlowAuthFilterUtilsTest {
  private final String TEST_ACCOUNT_ID = "testAccountID";

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_validJWT() {
    final String validJWTType =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdXRoVG9rZW4iOiI2MmU3NTUxMzJjNThkZDAxM2ZlNDEzOWIiLCJpc3MiOiJIYXJuZXNzIEluYyIsImV4cCI6MTY1OTQxNDE2MywiZW52IjoiZ2F0ZXdheSIsImlhdCI6MTY1OTMyNzcwM30.ud35uShhaOGMXgsdDAYbMl8bZX40muRdwqBByxQUqhA";
    assertTrue(JWTTokenFlowAuthFilterUtils.isJWTTokenType(validJWTType.split("\\."), TEST_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_invalidJWT() {
    final String inValidJWTType =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6InB1YmxpYzozOTk5MzIxMy0zZWQ3LTQ1ODItODZjNS03NDVlODFkOGZkYTkifQ.eyJhY3IiOiIwIiwiYXRfaGFzaCI6ImVjRGVtSnZKRFBaUXJkTDg0b1RSTVEiLCJhdWQiOlsiaHJuLXN0LWJhY2t1cCJdLCJhdXRoX3RpbWUiOjE2Njk5Mzc2MTAsImV4cCI6MTY2OTk0MTIxOSwiaWF0IjoxNjY5OTM3NjE5LCJpc3MiOiJodHRwczovL2xvY2FsaG9zdDo1NDQ0LyIsImp0aSI6IjcwMzRmNzU1LTMwYjYtNDEyYy1iZjk0LTNjYjEzOWRjNTEwZCIsIm5vbmNlIjoiaWR5c2tqd3ViYXZoZ3ppaGttbmpobXR1IiwicmF0IjoxNjY5OTM3NTk4LCJzaWQiOiJjMTQxNWI2Ni1mZGVjLTQ5MjMtOGIzZi04YjQ0NmVjYjI1YmEiLCJzdWIiOiJmb29AYmFyLmNvbSJ9.tV76sxqVM4xgy8auofarB2zFItZrWgW6R025j_V8jKtgXB11t_m8FMiESGxqbPCJV6hY_2fowSURC0MPSpz284K2a7eAmZCHR6f_jLjALITdQrExmkcE1wSMQmbGy9wqYBiHVGCjCeUFUxyR6kEFRti_jR6SO8NMmutoMhqi5f9183QBSnuiFNWpJvTdDiGEQ-gJ0B50WSREBEVrnEMCQKl5-GczIAJI-kWQTvHzbIFL-VuCWFJtozJ1P3_eYKH_xqFTFaagXd89NpanXPDB7GRypn5EUBla-D8uAzuRJXDH__IpsekEMTWRgXpTnJkOJf69m5h7VVxmnS_0gkSe3PE7b5E9clsGZN3MPhawUfBY08O089XG3R_qcr98eriQSTJhUxon0hm6FJr2rNEbmVN90OWpSijaPwCqtgy-kKR21kDI30RAmkj9AepZZPFVmHWZM_XIrJeN9zJV3YUM9-mZFPhHVFhvUL-pJJ-FSuxX-U-5EjdE1Rt5hWGeqJ2HbUtFRqf-nEgHeKhbUOdsKCKiVCV-DD4hjHnOr2wqMgaddch6QnMnBqC8Z1P6mpkBQ0OwtXR0IaKLCzH4th9u3Rbx5js0dCbznhDF1zNj9yH_23ekmD8DqrKgJTA1A9JmzDLJp5l1GZoRJ8Ad_S8SbMXv_Tc2XpxFYLD3ZsT6tWI";
    assertThatThrownBy(() -> JWTTokenFlowAuthFilterUtils.isJWTTokenType(inValidJWTType.split("\\."), TEST_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void getScimJwtTokenSettingConfigurationValuesFromDTOList_Valid() {
    final String testKeyField = "testKeyField";
    final String testValueField = "testValueField";
    final String testUrlField = "http://testUrlField/keys.json";
    final String testServiceAccountField = "testServiceAccountId";
    SettingResponseDTO keySettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER, testKeyField);
    SettingResponseDTO valueSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER, testValueField);
    SettingResponseDTO publicKeysUrlSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER, testUrlField);
    SettingResponseDTO serviceAccountSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER, testServiceAccountField);

    List<SettingResponseDTO> responseDtoList = Arrays.asList(keySettingResponseDTO, valueSettingResponseDTO,
        publicKeysUrlSettingResponseDTO, serviceAccountSettingResponseDTO);

    // Act
    Map<String, String> configValuesMap =
        JWTTokenFlowAuthFilterUtils.getScimJwtTokenSettingConfigurationValuesFromDTOList(
            responseDtoList, TEST_ACCOUNT_ID);

    // Assert
    assertNotNull(configValuesMap);
    assertTrue(configValuesMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER));
    assertTrue(configValuesMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER));
    assertTrue(configValuesMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER));
    assertTrue(
        configValuesMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER));

    assertThat(configValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER))
        .isEqualTo(testKeyField);
    assertThat(configValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER))
        .isEqualTo(testValueField);
    assertThat(configValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER))
        .isEqualTo(testUrlField);
    assertThat(configValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER))
        .isEqualTo(testServiceAccountField);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void getScimJwtTokenSettingConfigurationValuesFromDTOList_Invalid() {
    SettingResponseDTO keySettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER, "testKeyField");
    SettingResponseDTO valueSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER, "testValueField");
    SettingResponseDTO publicKeysUrlSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER, "http://testUrlField/keys.json");

    List<SettingResponseDTO> responseDtoList =
        Arrays.asList(keySettingResponseDTO, valueSettingResponseDTO, publicKeysUrlSettingResponseDTO);

    assertThatThrownBy(()
                           -> JWTTokenFlowAuthFilterUtils.getScimJwtTokenSettingConfigurationValuesFromDTOList(
                               responseDtoList, TEST_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "NG_SCIM_JWT: Some or all values for SCIM JWT token configuration at NG account settings are not populated in account ["
            + TEST_ACCOUNT_ID + "]");
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetPublicKeysJSONFromPublicKeysUrl() throws IOException {
    final String testPublicKeysUrl = "http://testUrlField/keys.json";
    OkHttpClient okHttpClient = mock(OkHttpClient.class);
    Request aMockRequest = new Request.Builder().url(testPublicKeysUrl).method("GET", null).build();

    Response responseSuccess = new Response.Builder()
                                   .code(404)
                                   .message("error")
                                   .request(aMockRequest)
                                   .protocol(Protocol.HTTP_1_1)
                                   .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                                   .build();

    Call aMockCall = mock(Call.class);
    when(okHttpClient.newCall(any())).thenReturn(aMockCall);
    when(aMockCall.execute()).thenReturn(responseSuccess);

    assertThatThrownBy(
        () -> JWTTokenFlowAuthFilterUtils.getPublicKeysJsonStringFromUrl(TEST_ACCOUNT_ID, testPublicKeysUrl))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetSettingListResponseByAccountForSCIMAndJWT() throws IOException {
    TokenClient aMockClient = mock(TokenClient.class, RETURNS_DEEP_STUBS);

    final String testKeyField = "testKeyField";
    final String testValueField = "testValueField";
    final String testUrlField = "http://testUrlField/keys.json";
    final String testServiceAccountField = "testServiceAccountId";
    SettingResponseDTO keySettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER, testKeyField);
    SettingResponseDTO valueSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER, testValueField);
    SettingResponseDTO publicKeysUrlSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER, testUrlField);
    SettingResponseDTO serviceAccountSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER, testServiceAccountField);

    List<SettingResponseDTO> responseDtoList = Arrays.asList(keySettingResponseDTO, valueSettingResponseDTO,
        publicKeysUrlSettingResponseDTO, serviceAccountSettingResponseDTO);

    retrofit2.Call<RestResponse<List<SettingResponseDTO>>> request = mock(retrofit2.Call.class);
    doReturn(request).when(aMockClient).listSettings(anyString(), any(), any(), any(), anyString());
    doReturn(retrofit2.Response.success(ResponseDTO.newResponse(responseDtoList))).when(request).execute();

    // Act
    List<SettingResponseDTO> settingListResponseResult =
        JWTTokenFlowAuthFilterUtils.getSettingListResponseByAccountForSCIMAndJWT(TEST_ACCOUNT_ID, aMockClient);

    // Assert
    assertNotNull(settingListResponseResult);
    assertThat(settingListResponseResult.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetServiceAccountByIdAndAccountId() throws IOException {
    final String name = generateUuid();

    TokenClient aMockClient = mock(TokenClient.class, RETURNS_DEEP_STUBS);

    String testServiceAccountId = "testServiceAccountId";
    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTO.builder()
                                              .identifier(testServiceAccountId)
                                              .name(name)
                                              .email(name + "@harness.io")
                                              .tags(new HashMap<>())
                                              .accountIdentifier(TEST_ACCOUNT_ID)
                                              .orgIdentifier(null)
                                              .projectIdentifier(null)
                                              .build();

    retrofit2.Call<RestResponse<ServiceAccountDTO>> request = mock(retrofit2.Call.class);
    doReturn(request).when(aMockClient).getServiceAccount(anyString(), anyString());
    doReturn(retrofit2.Response.success(ResponseDTO.newResponse(serviceAccountDTO))).when(request).execute();

    ServiceAccountDTO dtoResponseResult =
        JWTTokenFlowAuthFilterUtils.getServiceAccountByIdAndAccountId(TEST_ACCOUNT_ID, name, aMockClient);
    // Assert
    assertNotNull(dtoResponseResult);
    assertThat(dtoResponseResult.getIdentifier()).isEqualTo(testServiceAccountId);
    assertThat(dtoResponseResult.getName()).isEqualTo(name);
  }

  private SettingResponseDTO getSettingResponseDto(String settingIdentifier, String value) {
    SettingDTO settingDTO =
        SettingDTO.builder().identifier(settingIdentifier).valueType(SettingValueType.STRING).value(value).build();
    return SettingResponseDTO.builder().setting(settingDTO).build();
  }
}
