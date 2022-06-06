/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logserviceclient;

import static io.harness.rule.OwnerRule.VISTAAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.exception.GeneralException;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class CILogServiceUtilsTest extends CIExecutionTestBase {
  @Mock private CILogServiceClient logServiceClient;

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetLogServiceTokenSuccess() throws Exception {
    String baseUrl = "http://localhost:8079";
    String accountID = "account";
    String globalToken = "token";
    String logServiceToken = "X4fTPGJsYWhq_HI-Fb0I-n_GwvifuQFYCbPdjmqIGLiCTvTqBBi4Yg==";
    Call<String> logServiceTokenCall = mock(Call.class);
    when(logServiceTokenCall.execute()).thenReturn(Response.success(logServiceToken));
    when(logServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(logServiceTokenCall);
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    CILogServiceUtils ciLogServiceUtils = new CILogServiceUtils(logServiceClient, logServiceConfig);

    String token = ciLogServiceUtils.getLogServiceToken(accountID);
    assertThat(token).isEqualTo(logServiceToken);
    verify(logServiceTokenCall, times(1)).execute();
    verify(logServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetLogServiceTokenFailure() throws Exception {
    String baseUrl = "http://localhost:8079";
    String accountID = "account";
    String globalToken = "token";
    Call<String> logServiceTokenCall = mock(Call.class);
    when(logServiceTokenCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    when(logServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(logServiceTokenCall);
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    CILogServiceUtils ciLogServiceUtils = new CILogServiceUtils(logServiceClient, logServiceConfig);
    assertThatThrownBy(() -> ciLogServiceUtils.getLogServiceToken(accountID)).isInstanceOf(GeneralException.class);
    verify(logServiceTokenCall, times(1)).execute();
    verify(logServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testCloseLogStream() throws Exception {
    String baseUrl = "http://localhost:8079";
    String accountID = "account";
    String globalToken = "token";
    String logServiceToken = "X4fTPGJsYWhq_HI-Fb0I-n_GwvifuQFYCbPdjmqIGLiCTvTqBBi4Yg==";
    Call<String> logServiceTokenCall = mock(Call.class);
    when(logServiceTokenCall.execute()).thenReturn(Response.success(logServiceToken));
    when(logServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(logServiceTokenCall);

    Call<Void> logServiceCloseCall = mock(Call.class);
    when(logServiceCloseCall.execute()).thenReturn(Response.success(null));
    when(logServiceClient.closeLogStream(eq(accountID), eq("key"), eq(true), eq(true), eq(logServiceToken)))
        .thenReturn(logServiceCloseCall);

    LogServiceConfig logServiceConfig = LogServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    CILogServiceUtils ciLogServiceUtils = new CILogServiceUtils(logServiceClient, logServiceConfig);

    ciLogServiceUtils.closeLogStream(accountID, "key", true, true);
    verify(logServiceTokenCall, times(1)).execute();
    verify(logServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
    verify(logServiceClient, times(1))
        .closeLogStream(eq(accountID), eq("key"), eq(true), eq(true), eq(logServiceToken));
  }
}
