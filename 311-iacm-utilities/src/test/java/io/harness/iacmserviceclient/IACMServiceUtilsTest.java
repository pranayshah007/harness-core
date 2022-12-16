/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.iacm.beans.entities.IACMServiceConfig;
import io.harness.rule.LifecycleRule;
import io.harness.rule.Owner;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

public class IACMServiceUtilsTest extends CategoryTest implements MockableTestMixin {
  @Mock private IACMServiceClient iacmServiceClient;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMServiceTokenSuccess() throws Exception {
    String baseUrl = "http://localhost:4000";
    String accountID = "account";
    String globalToken = "token";
    JsonObject iacmServiceTokenResponse = new JsonObject();
    iacmServiceTokenResponse.addProperty("token", "iacm-token");
    String iacmServiceToken = "iacm-token";
    Call<JsonObject> iacmServiceTokenCall = mock(Call.class);
    when(iacmServiceTokenCall.execute()).thenReturn(Response.success(iacmServiceTokenResponse));
    when(iacmServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(iacmServiceTokenCall);
    IACMServiceConfig iacmServiceConfig = IACMServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, iacmServiceConfig);

    String token = iacmServiceUtils.getIACMServiceToken(accountID);
    assertThat(token).isEqualTo(iacmServiceToken);
    verify(iacmServiceTokenCall, times(1)).execute();
    verify(iacmServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMServiceTokenFailure() throws Exception {
    String baseUrl = "http://localhost:4000";
    String accountID = "account";
    String globalToken = "token";
    Call<JsonObject> iacmServiceTokenCall = mock(Call.class);
    when(iacmServiceTokenCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    when(iacmServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(iacmServiceTokenCall);
    IACMServiceConfig iacmServiceConfig = IACMServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, iacmServiceConfig);
    assertThatThrownBy(() -> iacmServiceUtils.getIACMServiceToken(accountID)).isInstanceOf(GeneralException.class);
    verify(iacmServiceTokenCall, times(1)).execute();
    verify(iacmServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }
}
