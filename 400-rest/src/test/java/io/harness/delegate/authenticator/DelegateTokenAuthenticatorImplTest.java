/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.authenticator;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.JOHANNES;
import static io.harness.rule.OwnerRule.LUCAS;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VLAD;

import static software.wings.utils.WingsTestConstants.ACCOUNT1_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.agent.utils.AgentMtlsVerifier;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateNgToken;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTokenException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.TokenGenerator;

import software.wings.WingsBaseTest;
import software.wings.beans.Service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTokenAuthenticatorImplTest extends WingsBaseTest {
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  public static final String GLOBAL_DELEGATE_ACCOUNT_ID = "__GLOBAL_DELEGATE_ACCOUNT_ID__";

  private static final String FQDN = "agent.some-fqdn.harness.io";

  @Mock LoadingCache<String, String> keyCache;
  @Mock private HPersistence persistence;
  @Mock private AgentMtlsVerifier agentMtlsVerifier;
  @Inject @InjectMocks private DelegateTokenAuthenticatorImpl delegateTokenAuthenticator;

  private String accountKey = "2f6b0988b6fb3370073c3d0505baee59";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    FieldUtils.writeField(delegateTokenAuthenticator, "keyCache", keyCache, true);
    when(keyCache.get(ACCOUNT_ID)).thenReturn(accountKey);

    when(agentMtlsVerifier.isValidRequest(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldValidateDelegateToken() {
    createPersistenceMocksForDelegateToken(null);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false);
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldValidateDelegateToken_Active() {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name("default")
                                      .value(accountKey)
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .build();

    createPersistenceMocksForDelegateToken(delegateToken);

    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void shouldValidateMtlsAuthority_validFqdn() {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name("default")
                                      .value(accountKey)
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .build();

    createPersistenceMocksForDelegateToken(delegateToken);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, FQDN, false);

    verify(this.agentMtlsVerifier, times(1)).isValidRequest(anyString(), anyString());
    verify(this.agentMtlsVerifier, times(1)).isValidRequest(ACCOUNT_ID, FQDN);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void shouldValidateMtlsAuthority_invalidFqdn() {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name("default")
                                      .value(accountKey)
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .build();

    createPersistenceMocksForDelegateToken(delegateToken);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    when(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID, FQDN)).thenReturn(false);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, FQDN, false);
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldValidateDelegateTokenThrowsInvalidTokenException() {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name("default")
                                      .value(accountKey)
                                      .status(DelegateTokenStatus.REVOKED)
                                      .build();
    when(keyCache.get(ACCOUNT1_ID)).thenReturn("2f6b0987b6fb3370073c3d0505baee59");

    createPersistenceMocksForDelegateToken(null);

    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    assertThatThrownBy(
        ()
            -> delegateTokenAuthenticator.validateDelegateToken(
                ACCOUNT1_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldValidateDelegateToken_Revoked() {
    DelegateToken delegateTokenRevoked = DelegateToken.builder()
                                             .accountId(ACCOUNT_ID)
                                             .name("TokenName")
                                             .value(accountKey)
                                             .status(DelegateTokenStatus.REVOKED)
                                             .build();
    Query mockQuery = mock(Query.class);
    FieldEnd<Service> fieldEnd = mock(FieldEnd.class);

    MorphiaIterator<DelegateToken, DelegateToken> morphiaIterator = mock(MorphiaIterator.class);

    doReturn(mockQuery).when(persistence).createQuery(DelegateToken.class);
    doReturn(fieldEnd).when(mockQuery).field(anyString());
    doReturn(mockQuery).when(fieldEnd).equal(any());

    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(delegateTokenRevoked);
    when(mockQuery.fetch()).thenReturn(morphiaIterator);

    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldValidateDelegateToken_validateNgToken() {
    DelegateToken delegateTokenActive = DelegateToken.builder()
                                            .accountId(ACCOUNT_ID)
                                            .name("TokenName")
                                            .value("InvalidTokenValue")
                                            .status(DelegateTokenStatus.ACTIVE)
                                            .build();
    createPersistenceMocksForDelegateToken(delegateTokenActive);

    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    Query mockQueryNg = mock(Query.class);
    FieldEnd<Service> fieldEndNg = mock(FieldEnd.class);

    MorphiaIterator<DelegateNgToken, DelegateNgToken> morphiaIteratorNg = mock(MorphiaIterator.class);

    doReturn(mockQueryNg).when(persistence).createQuery(DelegateNgToken.class);
    doReturn(fieldEndNg).when(mockQueryNg).field(anyString());
    doReturn(mockQueryNg).when(fieldEndNg).equal(any());

    when(morphiaIteratorNg.hasNext()).thenReturn(true).thenReturn(false);

    DelegateNgToken delegateTokenNg = DelegateNgToken.builder()
                                          .accountId(ACCOUNT_ID)
                                          .name("TokenName")
                                          .value(accountKey)
                                          .status(DelegateTokenStatus.ACTIVE)
                                          .build();

    when(morphiaIteratorNg.next()).thenReturn(delegateTokenNg);
    when(mockQueryNg.fetch()).thenReturn(morphiaIteratorNg);

    TokenGenerator tokenGeneratorNg = new TokenGenerator(ACCOUNT_ID, accountKey);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGeneratorNg.getToken("https", "localhost", 9090, "hostname"), null, null, null, false);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldNotValidateDelegateToken() {
    createPersistenceMocksForDelegateToken(null);
    TokenGenerator tokenGenerator = new TokenGenerator(GLOBAL_ACCOUNT_ID, accountKey);
    assertThatThrownBy(
        ()
            -> delegateTokenAuthenticator.validateDelegateToken(GLOBAL_ACCOUNT_ID,
                tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotValidateExpiredDelegateToken() {
    createPersistenceMocksForDelegateToken(null);

    String expiredToken =
        "eyJlbmMiOiJBMTI4R0NNIiwiYWxnIjoiZGlyIn0..SFvYSml0znPxoa7K.JcsFw5GiYevubqqzjy-nQyDMzjtA64YhxZjnQz6VH7lRCAGP5JML9Ov86rSRV1V7Kb-a12UvTNzqEqdJ4PCLv4R7GA5SzCwxLEYrlTLtUWX40r0GKuRGoiJVJqax2bBy3gOqDftETZCm_90lD3NxDeJ__RICl4osp9IxCKmlfGyoqriAswoEvkVtu0wjRlvBS-FtY42AeyCf9XIH5rppw-AsXoHH40M6_8FN-mFkilfqv3QKPaGL6Zph.1ipAjbMS834AKSotvHy4sg";
    assertThatThrownBy(
        () -> delegateTokenAuthenticator.validateDelegateToken(ACCOUNT_ID, expiredToken, null, null, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unauthorized");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldThrowDenyAccessWhenAccountIdNullForDelegate() {
    createPersistenceMocksForDelegateToken(null);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    assertThatThrownBy(
        ()
            -> delegateTokenAuthenticator.validateDelegateToken(
                null, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldThrowDenyAccessWhenAccountIdNotFoundForDelegate() {
    createPersistenceMocksForDelegateToken(null);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    assertThatThrownBy(
        ()
            -> delegateTokenAuthenticator.validateDelegateToken(ACCOUNT_ID + "1",
                tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowThrowInavlidTokenForDelegate() {
    assertThatThrownBy(
        () -> delegateTokenAuthenticator.validateDelegateToken(ACCOUNT_ID, "Dummy", null, null, null, false))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenUnableToDecryptToken() {
    createPersistenceMocksForDelegateToken(null);

    assertThatThrownBy(
        () -> delegateTokenAuthenticator.validateDelegateToken(ACCOUNT_ID, getDelegateToken(), null, null, null, false))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldValidateTokenForGlobalDelegate() {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(GLOBAL_DELEGATE_ACCOUNT_ID)
                                      .name("default")
                                      .value(accountKey)
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .build();

    createPersistenceMocksForDelegateToken(delegateToken);
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);

    delegateTokenAuthenticator.validateDelegateToken(
        ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"), null, null, FQDN, false);

    verify(this.agentMtlsVerifier, times(1)).isValidRequest(anyString(), anyString());
    verify(this.agentMtlsVerifier, times(1)).isValidRequest(ACCOUNT_ID, FQDN);
  }

  private void createPersistenceMocksForDelegateToken(DelegateToken delegateToken) {
    Query mockQuery = mock(Query.class);
    FieldEnd<Service> fieldEnd = mock(FieldEnd.class);

    MorphiaIterator<DelegateToken, DelegateToken> morphiaIterator = mock(MorphiaIterator.class);

    doReturn(mockQuery).when(persistence).createQuery(DelegateToken.class);
    doReturn(mockQuery).when(persistence).createQuery(DelegateNgToken.class);
    doReturn(fieldEnd).when(mockQuery).field(anyString());
    doReturn(mockQuery).when(fieldEnd).equal(any());

    when(morphiaIterator.hasNext()).thenReturn(delegateToken != null).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(delegateToken);
    when(mockQuery.fetch()).thenReturn(morphiaIterator);
  }

  private String getDelegateToken() {
    KeyGenerator keyGen = null;
    try {
      keyGen = KeyGenerator.getInstance("AES");
    } catch (NoSuchAlgorithmException e) {
      throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE);
    }
    keyGen.init(128);
    SecretKey secretKey = keyGen.generateKey();
    byte[] encoded = secretKey.getEncoded();
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, Hex.encodeHexString(encoded));
    return tokenGenerator.getToken("https", "localhost", 9090, "hostname");
  }
}
