/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.beans.KeyValuePair;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.HttpConnectionParameters;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.network.Http;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StringUtils.class, Http.class})
@PowerMockIgnore({"javax.xml.*", "org.xml.*", "javax.net.ssl.*"})
public class HttpConnectionExecutionCapabilityCheckTest {
  @InjectMocks HttpConnectionExecutionCapabilityCheck httpConnectionExecutionCapabilityCheck;
  @Mock CapabilityParameters parameters;

  HttpConnectionExecutionCapability httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectTrue;
  HttpConnectionExecutionCapability httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectFalse;
  HttpConnectionExecutionCapability httpConnectionExecutionCapability_Header;

  @Before
  public void setup() {
    httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectTrue =
        HttpConnectionExecutionCapability.builder().url("abc").ignoreRedirect(true).build();
    httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectFalse =
        HttpConnectionExecutionCapability.builder().url("abc").ignoreRedirect(false).build();
    List<KeyValuePair> temp = new ArrayList<>();
    httpConnectionExecutionCapability_Header =
        HttpConnectionExecutionCapability.builder().url("abc").headers(temp).build();
  }

  // Mocking StringUtils in below methods for NEXT_GEN variable to reduce dependency on System method call

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheck_NG_False_Headers_Null_IgnoreRedirect_Valid() {
    PowerMockito.mockStatic(StringUtils.class);
    PowerMockito.mockStatic(Http.class);
    when(StringUtils.isNotBlank(anyString())).thenAnswer(mockBool -> false);

    when(Http.connectableHttpUrlWithoutFollowingRedirect(
             httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectTrue.fetchConnectableUrl()))
        .thenAnswer(mockBool -> true);
    CapabilityResponse response = httpConnectionExecutionCapabilityCheck.performCapabilityCheck(
        httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectTrue);

    assertThat(response.isValidated()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheck_NG_False_Headers_Null_Redirect_Valid() {
    PowerMockito.mockStatic(StringUtils.class);
    PowerMockito.mockStatic(Http.class);

    when(StringUtils.isNotBlank(anyString())).thenAnswer(mockBool -> false);
    when(
        Http.connectableHttpUrl(httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectFalse.fetchConnectableUrl()))
        .thenAnswer(mockBool -> true);

    CapabilityResponse response = httpConnectionExecutionCapabilityCheck.performCapabilityCheck(
        httpConnectionExecutionCapability_HeaderNull_IgnoreRedirectFalse);

    assertThat(response.isValidated()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheck_NG_False_Headers_Valid() {
    PowerMockito.mockStatic(StringUtils.class);
    PowerMockito.mockStatic(Http.class);

    when(StringUtils.isNotBlank(anyString())).thenAnswer(mockBool -> false);

    when(Http.connectableHttpUrlWithHeaders(httpConnectionExecutionCapability_Header.fetchConnectableUrl(),
             httpConnectionExecutionCapability_Header.getHeaders()))
        .thenAnswer(mockBool -> true);

    CapabilityResponse response =
        httpConnectionExecutionCapabilityCheck.performCapabilityCheck(httpConnectionExecutionCapability_Header);

    assertThat(response.isValidated()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheck_NG_Headers_Valid() {
    PowerMockito.mockStatic(Http.class);
    when(Http.connectableHttpUrlWithHeaders(httpConnectionExecutionCapability_Header.fetchConnectableUrl(),
             httpConnectionExecutionCapability_Header.getHeaders()))
        .thenAnswer(mockBool -> true);
    when(
        Http.connectableHttpUrlWithoutFollowingRedirect(httpConnectionExecutionCapability_Header.fetchConnectableUrl()))
        .thenAnswer(mockBool -> true);

    CapabilityResponse response =
        httpConnectionExecutionCapabilityCheck.performCapabilityCheck(httpConnectionExecutionCapability_Header);

    assertThat(response.isValidated()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheckWithProto_CapabilityCase_Null() {
    when(parameters.getCapabilityCase()).thenReturn(null);

    CapabilitySubjectPermission result =
        httpConnectionExecutionCapabilityCheck.performCapabilityCheckWithProto(parameters);

    assertThat(result.getPermissionResult()).isEqualTo(CapabilitySubjectPermission.PermissionResult.DENIED);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheckWithProto_HeaderList_Null_Invalid() {
    HttpConnectionParameters temp = HttpConnectionParameters.newBuilder().getDefaultInstanceForType();
    when(parameters.getCapabilityCase()).thenReturn(CapabilityParameters.CapabilityCase.HTTP_CONNECTION_PARAMETERS);
    when(parameters.getHttpConnectionParameters()).thenReturn(temp);

    PowerMockito.mockStatic(Http.class);
    when(Http.connectableHttpUrl(temp.getUrl())).thenAnswer(mockBool -> false);

    CapabilitySubjectPermission result =
        httpConnectionExecutionCapabilityCheck.performCapabilityCheckWithProto(parameters);

    assertThat(result.getPermissionResult()).isEqualTo(CapabilitySubjectPermission.PermissionResult.DENIED);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void performCapabilityCheckWithProto_HeaderList_Invalid() {
    HttpConnectionParameters.Header header =
        HttpConnectionParameters.Header.newBuilder().setKey("first").setValue("firstValue").build();

    HttpConnectionParameters temp = HttpConnectionParameters.newBuilder().addHeaders(header).build();

    when(parameters.getCapabilityCase()).thenReturn(CapabilityParameters.CapabilityCase.HTTP_CONNECTION_PARAMETERS);
    when(parameters.getHttpConnectionParameters()).thenReturn(temp);

    PowerMockito.mockStatic(Http.class);
    when(Http.connectableHttpUrlWithHeaders(temp.getUrl(),
             temp.getHeadersList()
                 .stream()
                 .map(entry -> KeyValuePair.builder().key(entry.getKey()).value(entry.getValue()).build())
                 .collect(Collectors.toList())))
        .thenAnswer(mockBool -> false);

    CapabilitySubjectPermission result =
        httpConnectionExecutionCapabilityCheck.performCapabilityCheckWithProto(parameters);

    assertThat(result.getPermissionResult()).isEqualTo(CapabilitySubjectPermission.PermissionResult.DENIED);
  }
}
