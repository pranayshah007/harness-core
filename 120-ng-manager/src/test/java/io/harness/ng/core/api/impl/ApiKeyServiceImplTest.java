/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.GAURAV_NANDA;
import static io.harness.rule.OwnerRule.SOWMYA;

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
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.google.common.collect.ImmutableList;
import io.fabric8.utils.Lists;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
  private ApiKey apiKey;
  private AccountOrgProjectValidator accountOrgProjectValidator;
  private AccountService accountService;
  private TransactionTemplate transactionTemplate;
  private NgUserService ngUserService;

  private static final String TEST_PRINCIPAL = "TEST_PRINCIPAL";
  private static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  private static final String TEST_USER_EMAIL = "test.user@harness.io";

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
    ngUserService = mock(NgUserService.class);
    transactionTemplate = mock(TransactionTemplate.class);

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
    when(accountOrgProjectValidator.isPresent(any(), any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any())).thenReturn(apiKeyDTO);
    FieldUtils.writeField(apiKeyService, "apiKeyRepository", apiKeyRepository, true);
    FieldUtils.writeField(apiKeyService, "accountOrgProjectValidator", accountOrgProjectValidator, true);
    FieldUtils.writeField(apiKeyService, "accountService", accountService, true);
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(apiKeyService, "ngUserService", ngUserService, true);

    Principal principal = new UserPrincipal(TEST_PRINCIPAL, TEST_USER_EMAIL, "", TEST_ACCOUNT_ID);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateApiKey_duplicateIdentifier() {
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
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateApiKey_noDescription() {
    ApiKeyDTO dto = ApiKeyDTO.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .identifier("createApiKey_noDescription")
                        .parentIdentifier(parentIdentifier)
                        .apiKeyType(SERVICE_ACCOUNT)
                        .name(randomAlphabetic(10))
                        .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                        .tags(new HashMap<>())
                        .build();
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    when(transactionTemplate.execute(any())).thenReturn(dto);
    ApiKeyDTO apiKey = apiKeyService.createApiKey(dto);
    assertThat(apiKey.getDescription()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateApiKey_noAccountExists() {
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
  public void listServiceAccountDTO() {
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
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void validateParentIdentifier_userBelongToAccount_noExceptionThrown() {
    // Arrange
    doReturn(
        Optional.of(UserInfo.builder()
                        .email(TEST_USER_EMAIL)
                        .uuid(TEST_PRINCIPAL)
                        .accounts(ImmutableList.of(GatewayAccountRequestDTO.builder().uuid(TEST_ACCOUNT_ID).build()))
                        .build()))
        .when(ngUserService)
        .getUserById(any());

    // Act
    apiKeyService.validateParentIdentifier(TEST_ACCOUNT_ID, null, null, ApiKeyType.USER, TEST_PRINCIPAL);
  }

  @Test(expected = NotAuthorizedException.class)
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void validateParentIdentifier_userDoesNotBelongToAccount_notAuthorizedExceptionThrown() {
    // Arrange
    String randomAccountId = "34353";
    doReturn(
        Optional.of(UserInfo.builder()
                        .email(TEST_USER_EMAIL)
                        .uuid(TEST_PRINCIPAL)
                        .accounts(ImmutableList.of(GatewayAccountRequestDTO.builder().uuid(randomAccountId).build()))
                        .build()))
        .when(ngUserService)
        .getUserById(any());

    // Act
    apiKeyService.validateParentIdentifier(TEST_ACCOUNT_ID, null, null, ApiKeyType.USER, TEST_PRINCIPAL);
  }
}
