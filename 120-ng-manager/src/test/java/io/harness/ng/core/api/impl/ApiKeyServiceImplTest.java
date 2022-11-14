/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.SOWMYA;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.JwtVerificationApiKeyDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.rule.Owner;

import io.fabric8.utils.Lists;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ApiKeyServiceImplTest extends NgManagerTestBase {
  private ApiKeyService apiKeyService;
  private ApiKeyRepository apiKeyRepository;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String identifier;
  private String parentIdentifier;
  private ApiKeyDTO apiKeyDTO;
  private JwtVerificationApiKeyDTO jwtApiKeyDTO;
  private ApiKey apiKey;
  private ApiKey jwtApiKey;
  private AccountOrgProjectValidator accountOrgProjectValidator;
  private AccountService accountService;
  private TransactionTemplate transactionTemplate;
  private TransactionTemplate transactionTemplateJwt;

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    projectIdentifier = randomAlphabetic(10);
    identifier = randomAlphabetic(10);
    parentIdentifier = randomAlphabetic(10);
    apiKeyRepository = mock(ApiKeyRepository.class);
    apiKeyService = new ApiKeyServiceImpl();
    accountOrgProjectValidator = mock(AccountOrgProjectValidator.class);
    accountService = mock(AccountService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    transactionTemplateJwt = mock(TransactionTemplate.class);

    apiKeyDTO = ApiKeyDTO.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .identifier(identifier)
                    .parentIdentifier(parentIdentifier)
                    .apiKeyType(SERVICE_ACCOUNT)
                    .name(randomAlphabetic(10))
                    .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                    .description("")
                    .tags(new HashMap<>())
                    .build();

    jwtApiKeyDTO = JwtVerificationApiKeyDTO.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .identifier(identifier)
                       .parentIdentifier(parentIdentifier)
                       .apiKeyType(SERVICE_ACCOUNT_JWT_VERIFICATION)
                       .name(randomAlphabetic(10))
                       .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                       .description("jwt_desc")
                       .tags(new HashMap<>())
                       .keyField("jwt_key_field")
                       .valueField("jwt_value_field")
                       .certificateUrl("http://test.certificate.url")
                       .build();

    apiKey = ApiKey.builder()
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .identifier(identifier)
                 .parentIdentifier(parentIdentifier)
                 .apiKeyType(SERVICE_ACCOUNT)
                 .name(randomAlphabetic(10))
                 .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                 .description("")
                 .tags(new ArrayList<>())
                 .build();

    jwtApiKey = ApiKey.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .identifier(identifier)
                    .parentIdentifier(parentIdentifier)
                    .apiKeyType(SERVICE_ACCOUNT_JWT_VERIFICATION)
                    .name(randomAlphabetic(10))
                    .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                    .description("jwt_desc")
                    .certificateUrl("http://test.certificate.url")
                    .keyField("jwt_key_field")
                    .valueField("jwt_value_field")
                    .tags(new ArrayList<>())
                    .build();

    when(accountOrgProjectValidator.isPresent(any(), any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any())).thenReturn(apiKeyDTO);
    when(transactionTemplateJwt.execute(any())).thenReturn(jwtApiKeyDTO);
    FieldUtils.writeField(apiKeyService, "apiKeyRepository", apiKeyRepository, true);
    FieldUtils.writeField(apiKeyService, "accountOrgProjectValidator", accountOrgProjectValidator, true);
    FieldUtils.writeField(apiKeyService, "accountService", accountService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateApiKey_duplicateIdentifier() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplate, true);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    apiKeyService.createApiKey(apiKeyDTO);
    doThrow(new DuplicateFieldException(String.format("Try using different Key name, [%s] already exists", identifier)))
        .when(transactionTemplate)
        .execute(any());
    assertThatThrownBy(() -> apiKeyService.createApiKey(apiKeyDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format("Try using different Key name, [%s] already exists", identifier));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateApiKey_noAccountExists() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplate, true);
    doReturn(Optional.empty())
        .when(apiKeyRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, SERVICE_ACCOUNT, parentIdentifier, identifier);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());

    assertThatThrownBy(() -> apiKeyService.updateApiKey(apiKeyDTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Api key not present in scope for identifier: " + identifier);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void listServiceAccountDTO() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplate, true);
    doReturn(Lists.newArrayList(ApiKey.builder()
                                    .identifier(identifier)
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build()))
        .when(apiKeyRepository)
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, SERVICE_ACCOUNT, parentIdentifier);
    List<ApiKeyDTO> apiKeys = apiKeyService.listApiKeys(
        accountIdentifier, orgIdentifier, projectIdentifier, SERVICE_ACCOUNT, parentIdentifier, new ArrayList<>());
    assertThat(apiKeys.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreateJwtVerificationApiKey_duplicateIdentifier() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplateJwt, true);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(
                     ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).jwtVerificationApiKeyLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    apiKeyService.createJwtVerificationApiKey(jwtApiKeyDTO, jwtApiKeyDTO.getAccountIdentifier(), null);
    doThrow(new DuplicateFieldException(
                String.format("Try using different key name or identifier, [%s] already exists", identifier)))
        .when(transactionTemplateJwt)
        .execute(any());
    assertThatThrownBy(
        () -> apiKeyService.createJwtVerificationApiKey(jwtApiKeyDTO, jwtApiKeyDTO.getAccountIdentifier(), null))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format("Try using different key name or identifier, [%s] already exists", identifier));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreateJwtVerificationApiKey_Success() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplateJwt, true);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(
                     ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).jwtVerificationApiKeyLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    doReturn(1L)
        .when(apiKeyRepository)
        .countByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndApiKeyType(
            any(), any(), any(), any(), any());
    doReturn(Optional.empty())
        .when(apiKeyRepository)
        .findByAccountIdentifierAndKeyFieldAndValueFieldAndApiKeyType(any(), any(), any(), any());

    JwtVerificationApiKeyDTO resultDTO =
        apiKeyService.createJwtVerificationApiKey(jwtApiKeyDTO, jwtApiKeyDTO.getAccountIdentifier(), null);
    assertNotNull(resultDTO);
    assertThat(resultDTO.getKeyField()).isEqualTo(jwtApiKeyDTO.getKeyField());
    assertThat(resultDTO.getValueField()).isEqualTo(jwtApiKeyDTO.getValueField());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateJwtVerificationApiKey_Success() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplateJwt, true);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(
                     ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).jwtVerificationApiKeyLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    doReturn(Optional.of(jwtApiKey))
        .when(apiKeyRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());

    JwtVerificationApiKeyDTO resultDTO =
        apiKeyService.updateJwtVerificationApiKey(jwtApiKeyDTO, jwtApiKeyDTO.getAccountIdentifier(), null);
    assertNotNull(resultDTO);
    assertThat(resultDTO.getKeyField()).isEqualTo(jwtApiKeyDTO.getKeyField());
    assertThat(resultDTO.getValueField()).isEqualTo(jwtApiKeyDTO.getValueField());
    assertThat(resultDTO.getName()).isEqualTo(jwtApiKeyDTO.getName());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testDeleteJwtVerificationApiKey_Success() throws IllegalAccessException {
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplateJwt, true);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(
                     ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).jwtVerificationApiKeyLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    doReturn(Optional.of(jwtApiKey))
        .when(apiKeyRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());

    boolean resultDTO =
        apiKeyService.deleteJwtVerificationApiKey(jwtApiKeyDTO.getAccountIdentifier(), jwtApiKeyDTO.getOrgIdentifier(),
            jwtApiKeyDTO.getProjectIdentifier(), jwtApiKeyDTO.getParentIdentifier(), jwtApiKeyDTO.getIdentifier());
    assertTrue(resultDTO);
  }
}
