/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.LUCAS;
import static io.harness.rule.OwnerRule.NICOLAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.impl.DelegateTokenServiceImpl;
import io.harness.service.intfc.DelegateTokenService;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTokenServiceTest extends WingsBaseTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String TEST_ACCOUNT_ID_2 = "testAccountId2";
  private static final String TEST_TOKEN_NAME = "testTokenName";
  private static final String TEST_TOKEN_NAME2 = "testTokenName2";
  private static final String TEST_TOKEN_VALUE = "tokenValue";
  private static final String TEST_TOKEN_DEFAULT_NAME = "default";

  @Inject private HPersistence persistence;
  @Inject private DelegateTokenService delegateTokenService;
  @Inject private DelegateTokenServiceImpl delegateTokenServiceImpl;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setUp() {
    persistence.ensureIndexForTesting(DelegateToken.class);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testDelegateTokenServiceCreateToken() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertCreatedToken(createdToken);

    DelegateTokenDetails retrievedToken = retrieveTokenFromDB(TEST_TOKEN_NAME);
    assertCreatedToken(retrievedToken);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testTokenDeleteByAccountId() {
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    DelegateTokenDetails retrievedToken = retrieveTokenFromDB(TEST_TOKEN_NAME);
    assertCreatedToken(retrievedToken);
    delegateTokenServiceImpl.deleteByAccountId(TEST_ACCOUNT_ID);
    DelegateTokenDetails retrievedTokenAfterDelete = retrieveTokenFromDB(TEST_TOKEN_NAME);
    assertThat(retrievedTokenAfterDelete).isNull();
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void testUpsertDelegateTokenService() {
    DelegateTokenDetails upsertToken = delegateTokenService.upsertDefaultToken(TEST_ACCOUNT_ID, TEST_TOKEN_VALUE);

    assertThat(upsertToken).isNotNull();
    assertThat(upsertToken.getUuid()).isNotEmpty();
    assertThat(upsertToken.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(upsertToken.getName()).isEqualTo(TEST_TOKEN_DEFAULT_NAME);
    assertThat(upsertToken.getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);
    assertThat(upsertToken.getValue()).isNullOrEmpty();

    DelegateTokenDetails retrievedToken = retrieveTokenFromDB(TEST_TOKEN_DEFAULT_NAME);

    String storedTokenValue = delegateTokenService.getTokenValue(TEST_ACCOUNT_ID, TEST_TOKEN_DEFAULT_NAME);

    assertThat(retrievedToken).isNotNull();
    assertThat(retrievedToken.getUuid()).isNotEmpty();
    assertThat(retrievedToken.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(retrievedToken.getName()).isEqualTo(TEST_TOKEN_DEFAULT_NAME);
    assertThat(retrievedToken.getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);

    assertThat(storedTokenValue).isEqualTo(TEST_TOKEN_VALUE);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testDelegateTokenServiceCreateTokenInvalidDuplicate() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertCreatedToken(createdToken);

    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testDelegateTokenServiceCreateTokenValidDuplicate() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertCreatedToken(createdToken);

    DelegateTokenDetails tokenForOtherAccount =
        delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID_2, TEST_TOKEN_NAME);
    assertThat(tokenForOtherAccount).isNotNull();
    assertThat(tokenForOtherAccount.getAccountId()).isEqualTo(TEST_ACCOUNT_ID_2);
    assertThat(tokenForOtherAccount.getName()).isEqualTo(TEST_TOKEN_NAME);
    assertThat(tokenForOtherAccount.getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);
    assertThat(tokenForOtherAccount.getUuid()).isNotEmpty();
    assertThat(tokenForOtherAccount.getUuid()).isNotEqualTo(createdToken.getUuid());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testDelegateTokenServiceRevokeToken() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertCreatedToken(createdToken);

    delegateTokenService.revokeDelegateToken(createdToken.getAccountId(), createdToken.getName());

    DelegateTokenDetails retrievedToken = retrieveTokenFromDB(createdToken.getName());
    assertThat(retrievedToken).isNotNull();
    assertThat(retrievedToken.getUuid()).isEqualTo(createdToken.getUuid());
    assertThat(retrievedToken.getStatus()).isEqualTo(DelegateTokenStatus.REVOKED);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testDelegateTokenServiceDeleteToken() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertCreatedToken(createdToken);

    delegateTokenService.deleteDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);

    DelegateTokenDetails delegateToken = retrieveTokenFromDB(TEST_TOKEN_NAME);
    assertThat(delegateToken).isNull();
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void testGetAllDelegateTokens() {
    DelegateTokenDetails createdToken = delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    List<DelegateTokenDetails> delegateTokens =
        delegateTokenService.getDelegateTokens(createdToken.getAccountId(), null, null);

    assertThat(delegateTokens).isNotNull();
    assertThat(delegateTokens.get(0).getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateTokens.get(0).getName()).isEqualTo(TEST_TOKEN_NAME);
    assertThat(delegateTokens.get(0).getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void testGetDelegateTokensByStatus() {
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME2);

    delegateTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME2);

    List<DelegateTokenDetails> delegateTokens =
        delegateTokenService.getDelegateTokens(TEST_ACCOUNT_ID, DelegateTokenStatus.REVOKED, null);

    assertThat(delegateTokens).isNotNull();
    assertThat(delegateTokens.size()).isEqualTo(1);
    assertThat(delegateTokens.get(0).getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateTokens.get(0).getName()).isEqualTo(TEST_TOKEN_NAME2);
    assertThat(delegateTokens.get(0).getStatus()).isEqualTo(DelegateTokenStatus.REVOKED);
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void testGetDelegateTokensByName() {
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME2);

    List<DelegateTokenDetails> delegateTokens =
        delegateTokenService.getDelegateTokens(TEST_ACCOUNT_ID, DelegateTokenStatus.ACTIVE, TEST_TOKEN_NAME2);

    assertThat(delegateTokens).isNotNull();
    assertThat(delegateTokens.size()).isEqualTo(1);
    assertThat(delegateTokens.get(0).getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateTokens.get(0).getName()).startsWith(TEST_TOKEN_NAME);
    assertThat(delegateTokens.get(0).getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegateTokensEncryptedTokenId() {
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    delegateTokenService.createDelegateToken(TEST_ACCOUNT_ID, TEST_TOKEN_NAME);
    assertThat(delegateTokenService.getTokenValue(TEST_ACCOUNT_ID, TEST_TOKEN_NAME)).isNotNull();
  }

  private DelegateTokenDetails retrieveTokenFromDB(String tokenName) {
    DelegateToken delegateToken =
        persistence.createQuery(DelegateToken.class).field(DelegateTokenKeys.name).equal(tokenName).get();

    return delegateToken != null ? getDelegateTokenDetails(delegateToken) : null;
  }

  private void assertCreatedToken(DelegateTokenDetails tokenToAssert) {
    assertThat(tokenToAssert).isNotNull();
    assertThat(tokenToAssert.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(tokenToAssert.getName()).isEqualTo(TEST_TOKEN_NAME);
    assertThat(tokenToAssert.getStatus()).isEqualTo(DelegateTokenStatus.ACTIVE);
    assertThat(tokenToAssert.getUuid()).isNotEmpty();
  }

  private DelegateTokenDetails getDelegateTokenDetails(DelegateToken delegateToken) {
    return DelegateTokenDetails.builder()
        .uuid(delegateToken.getUuid())
        .accountId(delegateToken.getAccountId())
        .name(delegateToken.getName())
        .createdAt(delegateToken.getCreatedAt())
        .createdBy(delegateToken.getCreatedBy())
        .status(delegateToken.getStatus())
        .build();
  }
}
