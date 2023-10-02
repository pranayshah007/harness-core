/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.resources;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datapointsdata.resource.KubernetesDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.service.KubernetesDataPointsService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.KubernetesRequest;

import java.util.Collections;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesDataPointsApiImplTest extends CategoryTest {
  public static final String TEST_ACCOUNT = "testAccount";
  AutoCloseable openMocks;
  @Mock private KubernetesDataPointsService kubernetesDataPointsService;
  @InjectMocks private KubernetesDataPointsApiImpl kubernetesDataPointsApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetK8sDataPointValues() {
    KubernetesRequest validRequest = new KubernetesRequest();
    Map<String, Object> data = Collections.singletonMap("key", "value");

    when(kubernetesDataPointsService.getDataPointDataValues(eq(TEST_ACCOUNT), any())).thenReturn(data);

    Response response = kubernetesDataPointsApi.getK8sDataPointValues(validRequest, TEST_ACCOUNT);

    verify(kubernetesDataPointsService).getDataPointDataValues(TEST_ACCOUNT, validRequest.getRequest());
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(data, response.getEntity());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetK8sDataPointValuesException() {
    KubernetesRequest validRequest = new KubernetesRequest();
    String errorMessage = "Error message";

    when(kubernetesDataPointsService.getDataPointDataValues(eq(TEST_ACCOUNT), any()))
        .thenThrow(new RuntimeException(errorMessage));

    Response response = kubernetesDataPointsApi.getK8sDataPointValues(validRequest, TEST_ACCOUNT);

    verify(kubernetesDataPointsService).getDataPointDataValues(TEST_ACCOUNT, validRequest.getRequest());
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    ResponseMessage responseMessage = (ResponseMessage) response.getEntity();
    assertEquals(errorMessage, responseMessage.getMessage());
  }
}
