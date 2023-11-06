/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.delegate.beans.DelegateTokenStatus.ACTIVE;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VLAD;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.service.impl.DelegateNgTokenServiceImpl;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.util.List;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.DEL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateNgTokenServiceTest extends WingsBaseTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";

  @Inject private HPersistence persistence;
  @Inject private OutboxService outboxService;
  @InjectMocks @Inject private DelegateNgTokenServiceImpl delegateNgTokenService;
  @Mock private FeatureFlagService featureFlagService;
  @Inject private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;

  @Before
  public void setUp() {
    initMocks(this);
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldCreateToken() {
    String tokenName = "token1";
    DelegateTokenDetails token = delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName, null);
    assertThat(token.getName()).isEqualTo(tokenName);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldRevokeToken() {
    String tokenName = "token1";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName, null);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(1);
    DelegateTokenDetails revokedToken = delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName);
    assertThat(revokedToken.getStatus()).isEqualTo(DelegateTokenStatus.REVOKED);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .isEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldNotRevokeAlreadyRevokedToken() {
    String tokenName = "token1";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName, null);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(1);
    delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .isEmpty();
    delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldListTokens() {
    String tokenName1 = "token1";
    String tokenName2 = "token12";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName2, null);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(2);
    delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName1);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(1);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.REVOKED, false))
        .hasSize(1);
    delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName2);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(0);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.REVOKED, false))
        .hasSize(2);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldGetToken() {
    String tokenName1 = "token1";
    String tokenName2 = "token12";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName2, null);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(2);
    DelegateTokenDetails result = delegateNgTokenService.getDelegateToken(TEST_ACCOUNT_ID, tokenName1);
    assertThat(result.getName()).isEqualTo(tokenName1);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldGetTokenValue() {
    String tokenName1 = "token1";
    String tokenName2 = "token12";
    DelegateTokenDetails delegateTokenDetails =
        delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName2, null);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    String result = delegateNgTokenService.getDelegateTokenValue(TEST_ACCOUNT_ID, tokenName1);
    assertThat(result).isEqualTo(decodeBase64ToString(delegateTokenDetails.getValue()));
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldUpsertDefaultToken() {
    String tokenName1 = delegateNgTokenService.getDefaultTokenName(null);
    DelegateTokenDetails delegateTokenDetails =
        delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    DelegateTokenDetails result = delegateNgTokenService.upsertDefaultToken(TEST_ACCOUNT_ID, null, false);
    assertThat(result.getValue()).isNotEqualTo(delegateTokenDetails.getValue());
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldUpsertDefaultTokenSkipIfExists() {
    String tokenName1 = delegateNgTokenService.getDefaultTokenName(null);
    DelegateTokenDetails delegateTokenDetails =
        delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    DelegateTokenDetails result = delegateNgTokenService.upsertDefaultToken(TEST_ACCOUNT_ID, null, true);
    assertThat(result.getValue()).isEqualTo(delegateTokenDetails.getValue());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegateTokensEncryptedTokenId() {
    String tokenName1 = delegateNgTokenService.getDefaultTokenName(null);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName1, null);
    assertThat(delegateNgTokenService.getDelegateTokenValue(TEST_ACCOUNT_ID, tokenName1)).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void UpsertDefaultToken() {
    DelegateTokenDetails result = delegateNgTokenService.upsertDefaultToken(
        TEST_ACCOUNT_ID, DelegateEntityOwner.builder().identifier("default_project_default/p4").build(), false);
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void selfDestructOnRevokeToken() {
    String tokenName = "token1";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, tokenName, null);
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("localhost")
                            .accountId(TEST_ACCOUNT_ID)
                            .delegateName("testDelegateName")
                            .status(DelegateInstanceStatus.ENABLED)
                            .delegateTokenName(tokenName)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .hasSize(1);
    DelegateTokenDetails revokedToken = delegateNgTokenService.revokeDelegateToken(TEST_ACCOUNT_ID, tokenName);
    assertThat(revokedToken.getStatus()).isEqualTo(DelegateTokenStatus.REVOKED);
    assertThat(delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, null, DelegateTokenStatus.ACTIVE, false))
        .isEmpty();
    verify(broadcasterFactory).lookup("/stream/delegate/testAccountId", true);
    verify(broadcaster, times(1)).broadcast(SELF_DESTRUCT + delegate.getUuid());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetDelegateTokensShouldReturnDelegateTokenValue() {
    String tokenName1 = "token1";
    String tokenName2 = "token2";
    String ownerIdentifier = "default_project_default/p4";
    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier(ownerIdentifier).build();
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, owner, tokenName1, null);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, owner, tokenName2, null);
    List<DelegateTokenDetails> result =
        delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, owner, DelegateTokenStatus.ACTIVE, true);
    assertThat(result).hasSize(2);
    result.forEach(tokenDetail -> assertThat(tokenDetail.getValue()).isNotEmpty());
    result.forEach(tokenDetail -> assertThat(tokenDetail.getValue()).contains("="));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetDelegateTokensShouldNotReturnDelegateTokenValue() {
    String tokenName1 = "token1";
    String tokenName2 = "token2";
    String ownerIdentifier = "default_project_default/p4";
    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier(ownerIdentifier).build();
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, owner, tokenName1, null);
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, owner, tokenName2, null);
    List<DelegateTokenDetails> result =
        delegateNgTokenService.getDelegateTokens(TEST_ACCOUNT_ID, owner, DelegateTokenStatus.ACTIVE, false);
    assertThat(result).hasSize(2);
    result.forEach(tokenDetail -> assertThat(tokenDetail.getValue()).isNull());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void
  testGetDefaultTokenOrOldestActiveDelegateToken_shouldReturnOnlyNgOldestActiveTokenInCaseDefaultTokenDoestNotExist() {
    String randomTokenValue = "randomCgTokenValue";
    DelegateToken cgToken = DelegateToken.builder()
                                .accountId(TEST_ACCOUNT_ID)
                                .owner(null)
                                .name("cg_token")
                                .status(ACTIVE)
                                .value(randomTokenValue)
                                .createdByNgUser(SourcePrincipalContextBuilder.getSourcePrincipal())
                                .revokeAfter(null)
                                .build();
    persistence.save(cgToken);
    String ngTokenName = "ngToken";
    delegateNgTokenService.createToken(TEST_ACCOUNT_ID, null, ngTokenName, null);
    DelegateTokenDetails delegateTokenDetails =
        delegateNgTokenService.getDefaultTokenOrOldestActiveDelegateToken(TEST_ACCOUNT_ID, null);
    assertNotNull(delegateTokenDetails);
    assertThat(delegateTokenDetails.getName()).isEqualTo(ngTokenName);
  }
}
