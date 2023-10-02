/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.tiserviceclient;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.VISTAAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.exception.GeneralException;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class TIServiceUtilsTest extends CIExecutionTestBase {
  @Mock private TIServiceClient tiServiceClient;

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetTIServiceTokenSuccess() throws Exception {
    String baseUrl = "http://localhost:8078";
    String accountID = "account";
    String globalToken = "token";
    String tiServiceToken = "X4fTPGJsYWhq_HI-Fb0I-n_GwvifuQFYCbPdjmqIGLiCTvTqBBi4Yg==";
    Call<String> tiServiceTokenCall = mock(Call.class);
    when(tiServiceTokenCall.execute()).thenReturn(Response.success(tiServiceToken));
    when(tiServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(tiServiceTokenCall);
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    TIServiceUtils tiServiceUtils = new TIServiceUtils(tiServiceClient, tiServiceConfig);

    String token = tiServiceUtils.getTIServiceToken(accountID);
    assertThat(token).isEqualTo(tiServiceToken);
    verify(tiServiceTokenCall, times(1)).execute();
    verify(tiServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetTIServiceTokenFailure() throws Exception {
    String baseUrl = "http://localhost:8078";
    String accountID = "account";
    String globalToken = "token";
    Call<String> tiServiceTokenCall = mock(Call.class);
    when(tiServiceTokenCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    when(tiServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(tiServiceTokenCall);
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    TIServiceUtils tiServiceUtils = new TIServiceUtils(tiServiceClient, tiServiceConfig);
    assertThatThrownBy(() -> tiServiceUtils.getTIServiceToken(accountID)).isInstanceOf(GeneralException.class);
    verify(tiServiceTokenCall, times(1)).execute();
    verify(tiServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testClean() throws Exception {
    String baseUrl = "http://localhost:8078";
    String accountID = "account";
    String globalToken = "token";
    Call<String> cleanupCall = mock(Call.class);
    when(cleanupCall.execute()).thenReturn(Response.success(null));
    when(tiServiceClient.clean(eq(accountID), eq(globalToken))).thenReturn(cleanupCall);
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    TIServiceUtils tiServiceUtils = new TIServiceUtils(tiServiceClient, tiServiceConfig);

    tiServiceUtils.clean(accountID);
    verify(cleanupCall, times(1)).execute();
    verify(tiServiceClient, times(1)).clean(eq(accountID), eq(globalToken));
  }
}
