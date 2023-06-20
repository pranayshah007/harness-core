/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.cache.JwtTokenPublicKeysJsonData;
import io.harness.ng.core.api.cache.JwtTokenScimAccountSettingsData;
import io.harness.ng.core.api.cache.JwtTokenServiceAccountData;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javax.cache.Cache;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class JWTTokenFlowAuthFilterUtilsTest {
  private final String TEST_ACCOUNT_ID = "testAccountID";

  @Mock private SettingsService settingsService;
  @Mock private ServiceAccountService serviceAccountService;
  @Mock private Cache<String, JwtTokenPublicKeysJsonData> jwtTokenPublicKeysJsonCache;
  @Mock private Cache<String, JwtTokenServiceAccountData> jwtTokenServiceAccountCache;
  @Mock private Cache<String, JwtTokenScimAccountSettingsData> jwtTokenScimSettingCache;

  @InjectMocks JWTTokenFlowAuthFilterUtils jwtTokenAuthFilterUtils;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetTokenDTOFromServiceAccountDTO() {
    final String name = generateUuid();

    String testServiceAccountId = "testServiceAccountId";
    ServiceAccountDTO serviceAccountDTO = getTestServiceAccountDto(name, testServiceAccountId);
    TokenDTO tokenDtoResponse = jwtTokenAuthFilterUtils.getTokenDtoFromServiceAccountDto(serviceAccountDTO);

    assertNotNull(tokenDtoResponse);
    assertThat(tokenDtoResponse.getAccountIdentifier()).isEqualTo(serviceAccountDTO.getAccountIdentifier());
    assertThat(tokenDtoResponse.getEmail()).isEqualTo(serviceAccountDTO.getEmail());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetServiceAccountByIdAccountId_FromCache() {
    final String name = generateUuid();
    String testServiceAccountId = "testServiceAccountId";
    ServiceAccountDTO serviceAccountDTO = getTestServiceAccountDto(name, testServiceAccountId);
    JwtTokenServiceAccountData builtServiceAccountData = JwtTokenServiceAccountData.builder()
                                                             .serviceAccountDTO(serviceAccountDTO)
                                                             .serviceAccountId(testServiceAccountId)
                                                             .build();

    doReturn(true).when(jwtTokenServiceAccountCache).containsKey(any());
    doReturn(builtServiceAccountData).when(jwtTokenServiceAccountCache).get(any());

    ServiceAccountDTO resultDTO =
        jwtTokenAuthFilterUtils.getServiceAccountByIdAndAccountId(testServiceAccountId, TEST_ACCOUNT_ID);
    assertNotNull(resultDTO);
    assertThat(resultDTO.getAccountIdentifier()).isEqualTo(serviceAccountDTO.getAccountIdentifier());
    assertThat(resultDTO.getIdentifier()).isEqualTo(serviceAccountDTO.getIdentifier());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetServiceAccountByIdAccountId_NotFromCache() {
    final String name = generateUuid();
    String testServiceAccountId = "testServiceAccountId";
    ServiceAccountDTO serviceAccountDTO = getTestServiceAccountDto(name, testServiceAccountId);

    doReturn(false).when(jwtTokenServiceAccountCache).containsKey(any());
    doReturn(serviceAccountDTO).when(serviceAccountService).getServiceAccountDTO(anyString(), anyString());
    ServiceAccountDTO resultDTO =
        jwtTokenAuthFilterUtils.getServiceAccountByIdAndAccountId(testServiceAccountId, TEST_ACCOUNT_ID);
    assertNotNull(resultDTO);
    assertThat(resultDTO.getAccountIdentifier()).isEqualTo(serviceAccountDTO.getAccountIdentifier());
    assertThat(resultDTO.getIdentifier()).isEqualTo(serviceAccountDTO.getIdentifier());
    verify(jwtTokenServiceAccountCache, times(1)).put(anyString(), any(JwtTokenServiceAccountData.class));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetJwtConsumerFromPublicKeysUrl_FromCache() {
    final String publicKeysUrl = "http://test.dummy.io/public-keys.json";
    final String keysJson =
        "{\n  \"keys\": [\n    {\n      \"use\": \"sig\",\n      \"kty\": \"RSA\",\n      \"kid\": \"public:9b9d0b47-b9ed-4ba6-9180-52fc5b161a3a\",\n      \"alg\": \"RS256\",\n      \"n\": \"6f4qEUPMmYAyAQnGQOIx1UkIEVPPt1BnhDH70w3Gq6uYpm4hUyRFiM1oZ4_xB28gTmpR_SJZL31E_yZTLKPwKKsCDyF6YGhFtcyifhsLJc45GW4G4poX8Y34EIYlT63G9vutwNwzistWZZqBm52e-bdUQ7zjmWUGpgkq1GQJZyPz2lvA2bThRqqj94w1hqHSCXuAc90cN-Th0Ss1QhKesud7dIgaJQngjWWXdlPBqNYe1oCI04E3gcWdYRFhKey1lkO0WG4VtQxcMADgCrhFVgicpdYyNVqim7Tf31Is_bcQcbFdmumwxWewT-dC6ur3UAv1A97L567QCwlGDP5DAvH35NmL3w291tUd4q5Vlwz6gsRKqDhUSonISboWvvY2x_ndH1oE2hXYin4WL3SyCyp-De8d59C5UhC8KPTvA-3h_UfcPvz6DRDdNrKyRdKmn9vQQpTP9jMtK7Tks8qKxK4D4pesUmjiNMsVCo8AwJ-9hMd7TXamE9CErfDR7jCQONUMetLnitiM7nazCPXkO5tAhJKzQm1o0HvCVptwaa7MksfViK5YPMcCYc9bD1Uujo-782MXqAzdncu0nGKaJXnIsYB0-tFNiNXjuYFQ8KV5k5-Wnn0kga4CkCHlMU2umR19zFsFwFBdVngOYkCEG46KAgdGDqtj8t4d0GY8tcM\",\n      \"e\": \"AQAB\"\n    }\n  ]\n}";
    JwtTokenPublicKeysJsonData builtKeysJsonData =
        JwtTokenPublicKeysJsonData.builder().publicKeysUrl(publicKeysUrl).publicKeysDetailJson(keysJson).build();
    doReturn(true).when(jwtTokenPublicKeysJsonCache).containsKey(any());
    doReturn(builtKeysJsonData).when(jwtTokenPublicKeysJsonCache).get(any());
    JwtConsumer resultJwtConsumer =
        jwtTokenAuthFilterUtils.getJwtConsumerFromPublicKeysUrl(TEST_ACCOUNT_ID, publicKeysUrl);
    assertNotNull(resultJwtConsumer);
    verify(jwtTokenPublicKeysJsonCache, times(0)).put(anyString(), any(JwtTokenPublicKeysJsonData.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetJwtConsumerFromPublicKeysUrl_InvalidCacheEntry() throws IOException {
    final String publicKeysUrl1 = "http://test.dummy1.io/public-keys.json";
    final String publicKeysUrl2 = "http://test.dummy2.io/public-keys2.json";
    final String keysJson =
        "{\n  \"keys\": [\n    {\n      \"use\": \"sig\",\n      \"kty\": \"RSA\",\n      \"kid\": \"public:9b9d0b47-b9ed-4ba6-9180-52fc5b161a3a\",\n      \"alg\": \"RS256\",\n      \"n\": \"6f4qEUPMmYAyAQnGQOIx1UkIEVPPt1BnhDH70w3Gq6uYpm4hUyRFiM1oZ4_xB28gTmpR_SJZL31E_yZTLKPwKKsCDyF6YGhFtcyifhsLJc45GW4G4poX8Y34EIYlT63G9vutwNwzistWZZqBm52e-bdUQ7zjmWUGpgkq1GQJZyPz2lvA2bThRqqj94w1hqHSCXuAc90cN-Th0Ss1QhKesud7dIgaJQngjWWXdlPBqNYe1oCI04E3gcWdYRFhKey1lkO0WG4VtQxcMADgCrhFVgicpdYyNVqim7Tf31Is_bcQcbFdmumwxWewT-dC6ur3UAv1A97L567QCwlGDP5DAvH35NmL3w291tUd4q5Vlwz6gsRKqDhUSonISboWvvY2x_ndH1oE2hXYin4WL3SyCyp-De8d59C5UhC8KPTvA-3h_UfcPvz6DRDdNrKyRdKmn9vQQpTP9jMtK7Tks8qKxK4D4pesUmjiNMsVCo8AwJ-9hMd7TXamE9CErfDR7jCQONUMetLnitiM7nazCPXkO5tAhJKzQm1o0HvCVptwaa7MksfViK5YPMcCYc9bD1Uujo-782MXqAzdncu0nGKaJXnIsYB0-tFNiNXjuYFQ8KV5k5-Wnn0kga4CkCHlMU2umR19zFsFwFBdVngOYkCEG46KAgdGDqtj8t4d0GY8tcM\",\n      \"e\": \"AQAB\"\n    }\n  ]\n}";
    JwtTokenPublicKeysJsonData builtKeysJsonData =
        JwtTokenPublicKeysJsonData.builder().publicKeysUrl(publicKeysUrl2).publicKeysDetailJson(keysJson).build();
    doReturn(true).when(jwtTokenPublicKeysJsonCache).containsKey(any());
    doReturn(builtKeysJsonData).when(jwtTokenPublicKeysJsonCache).get(any());
    jwtTokenAuthFilterUtils.getJwtConsumerFromPublicKeysUrl(TEST_ACCOUNT_ID, publicKeysUrl1);
    verify(jwtTokenPublicKeysJsonCache, times(1)).remove(anyString());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetSettingListResponseByAccountForSCIMAndJWT_FromCache() {
    doReturn(true).when(jwtTokenScimSettingCache).containsKey(any());
    SettingResponseDTO keySettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER, "testKeyField");
    SettingResponseDTO valueSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER, "testValueField");
    SettingResponseDTO publicKeysUrlSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER, "testUrlField");
    SettingResponseDTO serviceAccountSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER, "testServiceAccountField");

    List<SettingResponseDTO> responseDtoList = Arrays.asList(keySettingResponseDTO, valueSettingResponseDTO,
        publicKeysUrlSettingResponseDTO, serviceAccountSettingResponseDTO);
    doReturn(JwtTokenScimAccountSettingsData.builder().scimSettingsValue(responseDtoList).build())
        .when(jwtTokenScimSettingCache)
        .get(any());

    List<SettingDTO> resultSettingList =
        jwtTokenAuthFilterUtils.getSettingListResponseByAccountForSCIMAndJWT(TEST_ACCOUNT_ID);
    assertThat(resultSettingList.size()).isEqualTo(4);
    verify(jwtTokenScimSettingCache, times(0)).put(anyString(), any(JwtTokenScimAccountSettingsData.class));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetSettingListResponseByAccountForSCIMAndJWT_FromDb() {
    doReturn(false).when(jwtTokenScimSettingCache).containsKey(any());
    SettingResponseDTO keySettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER, "testKeyField");
    SettingResponseDTO valueSettingResponseDTO =
        getSettingResponseDto(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER, "testValueField");
    SettingResponseDTO publicKeysUrlSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER, "testUrlField");
    SettingResponseDTO serviceAccountSettingResponseDTO = getSettingResponseDto(
        SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER, "testServiceAccountField");

    List<SettingResponseDTO> responseDtoList = Arrays.asList(keySettingResponseDTO, valueSettingResponseDTO,
        publicKeysUrlSettingResponseDTO, serviceAccountSettingResponseDTO);

    doReturn(responseDtoList).when(settingsService).list(any(), any(), any(), any(), any(), any());

    List<SettingDTO> resultSettingList =
        jwtTokenAuthFilterUtils.getSettingListResponseByAccountForSCIMAndJWT(TEST_ACCOUNT_ID);
    assertThat(resultSettingList.size()).isEqualTo(4);
    verify(jwtTokenScimSettingCache, times(1)).put(anyString(), any(JwtTokenScimAccountSettingsData.class));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_validJWT() {
    final String validJWTType =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdXRoVG9rZW4iOiI2MmU3NTUxMzJjNThkZDAxM2ZlNDEzOWIiLCJpc3MiOiJIYXJuZXNzIEluYyIsImV4cCI6MTY1OTQxNDE2MywiZW52IjoiZ2F0ZXdheSIsImlhdCI6MTY1OTMyNzcwM30.ud35uShhaOGMXgsdDAYbMl8bZX40muRdwqBByxQUqhA";
    assertTrue(jwtTokenAuthFilterUtils.isJWTTokenType(validJWTType, TEST_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_invalidJWT() {
    final String inValidJWTType =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6InB1YmxpYzozOTk5MzIxMy0zZWQ3LTQ1ODItODZjNS03NDVlODFkOGZkYTkifQ.eyJhY3IiOiIwIiwiYXRfaGFzaCI6ImVjRGVtSnZKRFBaUXJkTDg0b1RSTVEiLCJhdWQiOlsiaHJuLXN0LWJhY2t1cCJdLCJhdXRoX3RpbWUiOjE2Njk5Mzc2MTAsImV4cCI6MTY2OTk0MTIxOSwiaWF0IjoxNjY5OTM3NjE5LCJpc3MiOiJodHRwczovL2xvY2FsaG9zdDo1NDQ0LyIsImp0aSI6IjcwMzRmNzU1LTMwYjYtNDEyYy1iZjk0LTNjYjEzOWRjNTEwZCIsIm5vbmNlIjoiaWR5c2tqd3ViYXZoZ3ppaGttbmpobXR1IiwicmF0IjoxNjY5OTM3NTk4LCJzaWQiOiJjMTQxNWI2Ni1mZGVjLTQ5MjMtOGIzZi04YjQ0NmVjYjI1YmEiLCJzdWIiOiJmb29AYmFyLmNvbSJ9.tV76sxqVM4xgy8auofarB2zFItZrWgW6R025j_V8jKtgXB11t_m8FMiESGxqbPCJV6hY_2fowSURC0MPSpz284K2a7eAmZCHR6f_jLjALITdQrExmkcE1wSMQmbGy9wqYBiHVGCjCeUFUxyR6kEFRti_jR6SO8NMmutoMhqi5f9183QBSnuiFNWpJvTdDiGEQ-gJ0B50WSREBEVrnEMCQKl5-GczIAJI-kWQTvHzbIFL-VuCWFJtozJ1P3_eYKH_xqFTFaagXd89NpanXPDB7GRypn5EUBla-D8uAzuRJXDH__IpsekEMTWRgXpTnJkOJf69m5h7VVxmnS_0gkSe3PE7b5E9clsGZN3MPhawUfBY08O089XG3R_qcr98eriQSTJhUxon0hm6FJr2rNEbmVN90OWpSijaPwCqtgy-kKR21kDI30RAmkj9AepZZPFVmHWZM_XIrJeN9zJV3YUM9-mZFPhHVFhvUL-pJJ-FSuxX-U-5EjdE1Rt5hWGeqJ2HbUtFRqf-nEgHeKhbUOdsKCKiVCV-DD4hjHnOr2wqMgaddch6QnMnBqC8Z1P6mpkBQ0OwtXR0IaKLCzH4th9u3Rbx5js0dCbznhDF1zNj9yH_23ekmD8DqrKgJTA1A9JmzDLJp5l1GZoRJ8Ad_S8SbMXv_Tc2XpxFYLD3ZsT6tWI";
    assertThatThrownBy(() -> jwtTokenAuthFilterUtils.isJWTTokenType(inValidJWTType, TEST_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  private ServiceAccountDTO getTestServiceAccountDto(String name, String id) {
    return ServiceAccountDTO.builder()
        .identifier(id)
        .name(name)
        .email(name + "@harness.io")
        .tags(new HashMap<>())
        .accountIdentifier(TEST_ACCOUNT_ID)
        .orgIdentifier(null)
        .projectIdentifier(null)
        .build();
  }

  private SettingResponseDTO getSettingResponseDto(String settingIdentifier, String value) {
    SettingDTO settingDTO =
        SettingDTO.builder().identifier(settingIdentifier).valueType(SettingValueType.STRING).value(value).build();
    return SettingResponseDTO.builder().setting(settingDTO).build();
  }
}
