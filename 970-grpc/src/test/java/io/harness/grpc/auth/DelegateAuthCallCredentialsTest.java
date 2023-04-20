/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.auth;

import static io.harness.grpc.auth.DelegateAuthCallCredentials.ACCOUNT_ID_METADATA_KEY;
import static io.harness.grpc.auth.DelegateAuthCallCredentials.TOKEN_METADATA_KEY;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.TokenGenerator;

import io.grpc.CallCredentials.MetadataApplier;
import io.grpc.CallCredentials.RequestInfo;
import io.grpc.Metadata;
import io.grpc.SecurityLevel;
import lombok.val;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class DelegateAuthCallCredentialsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String TOKEN = "TOKEN";

  private DelegateAuthCallCredentials delegateAuthCallCredentials;
  private TokenGenerator tokenGenerator;
  private RequestInfo requestInfo;
  private MetadataApplier metadataApplier;

  @Before
  public void setUp() throws Exception {
    tokenGenerator = mock(TokenGenerator.class);
    when(tokenGenerator.getToken(anyString(), anyString())).thenReturn(TOKEN);
    requestInfo = mock(RequestInfo.class);
    metadataApplier = mock(MetadataApplier.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFailCallIfRequirePrivacyAndChannelIsNotSecure() throws Exception {
    delegateAuthCallCredentials = new DelegateAuthCallCredentials(tokenGenerator, ACCOUNT_ID, true);
    when(requestInfo.getSecurityLevel()).thenReturn(SecurityLevel.NONE);
    delegateAuthCallCredentials.applyRequestMetadata(requestInfo, directExecutor(), metadataApplier);
    verify(metadataApplier).fail(any());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldAddCredentialsIfNotRequirePrivacyAndChannelIsNotSecure() throws Exception {
    delegateAuthCallCredentials = new DelegateAuthCallCredentials(tokenGenerator, ACCOUNT_ID, false);
    when(requestInfo.getSecurityLevel()).thenReturn(SecurityLevel.NONE);
    delegateAuthCallCredentials.applyRequestMetadata(requestInfo, directExecutor(), metadataApplier);
    val captor = ArgumentCaptor.forClass(Metadata.class);
    verify(metadataApplier).apply(captor.capture());
    assertThat(captor.getValue()).satisfies(metadata -> {
      assertThat(metadata.containsKey(ACCOUNT_ID_METADATA_KEY)).isTrue();
      assertThat(metadata.containsKey(TOKEN_METADATA_KEY)).isTrue();
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldAddCredentialsIfRequirePrivacyAndChannelIsSecure() throws Exception {
    delegateAuthCallCredentials = new DelegateAuthCallCredentials(tokenGenerator, ACCOUNT_ID, true);
    when(requestInfo.getSecurityLevel()).thenReturn(SecurityLevel.PRIVACY_AND_INTEGRITY);
    delegateAuthCallCredentials.applyRequestMetadata(requestInfo, directExecutor(), metadataApplier);
    val captor = ArgumentCaptor.forClass(Metadata.class);
    verify(metadataApplier).apply(captor.capture());
    assertThat(captor.getValue()).satisfies(metadata -> {
      assertThat(metadata.containsKey(ACCOUNT_ID_METADATA_KEY)).isTrue();
      assertThat(metadata.containsKey(TOKEN_METADATA_KEY)).isTrue();
    });
  }
}
