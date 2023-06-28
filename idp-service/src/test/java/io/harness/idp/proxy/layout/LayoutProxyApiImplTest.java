/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class LayoutProxyApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "TEST_ACCOUNT_IDENTIFIER";
  private static final String LAYOUT_IDENTIFIER = "TEST_LAYOUT_IDENTIFIER";
  private Call<Object> call;
  AutoCloseable openMocks;
  @Mock BackstageResourceClient backstageResourceClient;
  @InjectMocks LayoutProxyApiImpl layoutProxyApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateLayout() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    LayoutRequest body = new LayoutRequest();
    when(backstageResourceClient.createLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.createLayout(body, ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateLayoutError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    LayoutRequest body = new LayoutRequest();
    when(backstageResourceClient.createLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.createLayout(body, ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteLayout() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    LayoutRequest body = new LayoutRequest();
    when(backstageResourceClient.deleteLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.deleteLayout(body, ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteLayoutError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    LayoutRequest body = new LayoutRequest();
    when(backstageResourceClient.deleteLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.deleteLayout(body, ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAllLayouts() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getAllLayouts(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getAllLayouts(ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAllLayoutsError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getAllLayouts(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getAllLayouts(ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetLayout() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getLayout(ACCOUNT_IDENTIFIER, LAYOUT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getLayout(LAYOUT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetLayoutError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getLayout(ACCOUNT_IDENTIFIER, LAYOUT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getLayout(LAYOUT_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetLayoutHealth() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getHealth(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getLayoutHealth(ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetLayoutHealthError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getHealth(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getLayoutHealth(ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testLayoutIngest() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    LayoutIngestRequest body = new LayoutIngestRequest();
    when(backstageResourceClient.ingestLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.layoutIngest(body, ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testLayoutIngestError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    LayoutIngestRequest body = new LayoutIngestRequest();
    when(backstageResourceClient.ingestLayout(body, ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.layoutIngest(body, ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }
}
