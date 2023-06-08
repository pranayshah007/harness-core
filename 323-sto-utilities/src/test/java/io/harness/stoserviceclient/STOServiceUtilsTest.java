/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stoserviceclient;

import static io.harness.rule.OwnerRule.SERGEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.rule.LifecycleRule;
import io.harness.rule.Owner;
import io.harness.sto.beans.entities.STOServiceConfig;

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

public class STOServiceUtilsTest extends CategoryTest implements MockableTestMixin {
  @Mock private STOServiceClient stoServiceClient;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void testGetSTOServiceTokenSuccess() throws Exception {
    String baseUrl = "http://localhost:4000";
    String accountID = "account";
    String globalToken = "token";
    JsonObject stoServiceTokenResponse = new JsonObject();
    stoServiceTokenResponse.addProperty("token", "sto-token");
    String stoServiceToken = "sto-token";
    Call<JsonObject> stoServiceTokenCall = mock(Call.class);
    when(stoServiceTokenCall.execute()).thenReturn(Response.success(stoServiceTokenResponse));
    when(stoServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(stoServiceTokenCall);
    STOServiceConfig stoServiceConfig =
        STOServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).internalUrl(baseUrl).build();
    STOServiceUtils stoServiceUtils = new STOServiceUtils(stoServiceClient, stoServiceConfig);

    String token = stoServiceUtils.getSTOServiceToken(accountID);
    assertThat(token).isEqualTo(stoServiceToken);
    verify(stoServiceTokenCall, times(1)).execute();
    verify(stoServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void testGetSTOServiceTokenFailure() throws Exception {
    String baseUrl = "http://localhost:4000";
    String accountID = "account";
    String globalToken = "token";
    Call<JsonObject> stoServiceTokenCall = mock(Call.class);
    when(stoServiceTokenCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    when(stoServiceClient.generateToken(eq(accountID), eq(globalToken))).thenReturn(stoServiceTokenCall);
    STOServiceConfig stoServiceConfig =
        STOServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).internalUrl(baseUrl).build();
    STOServiceUtils stoServiceUtils = new STOServiceUtils(stoServiceClient, stoServiceConfig);
    assertThatThrownBy(() -> stoServiceUtils.getSTOServiceToken(accountID)).isInstanceOf(GeneralException.class);
    verify(stoServiceTokenCall, times(1)).execute();
    verify(stoServiceClient, times(1)).generateToken(eq(accountID), eq(globalToken));
  }
}
