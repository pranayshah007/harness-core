/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.services;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.client.NgConnectorManagerClient;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class NgConnectorManagerClientServiceImplTest extends CategoryTest {
  @Mock private NgConnectorManagerClient ngConnectorManagerClient;
  private NgConnectorManagerClientServiceImpl ngConnectorManagerClientService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ngConnectorManagerClientService = new NgConnectorManagerClientServiceImpl(ngConnectorManagerClient);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category({UnitTests.class})
  public void testIsHarnessSupportUser() throws IOException {
    String userId = UUIDGenerator.generateUuid();
    RestResponse<Boolean> restResponse = new RestResponse<>(RandomUtils.nextBoolean());
    Call<RestResponse<Boolean>> restResponseCall = mock(Call.class);
    when(ngConnectorManagerClient.isHarnessSupportUser(anyString())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    ngConnectorManagerClientService.isHarnessSupportUser(userId);
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(userId);
  }
}
