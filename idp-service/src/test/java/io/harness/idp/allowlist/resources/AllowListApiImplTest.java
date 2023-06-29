/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.allowlist.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.allowlist.services.AllowListService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AllowListRequest;
import io.harness.spec.server.idp.v1.model.HostInfo;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AllowListApiImplTest {
  @InjectMocks private AllowListApiImpl allowListApiImpl;
  @Mock private AllowListService allowListService;
  private static final String ACCOUNT_ID = "123";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllowList() throws Exception {
    when(allowListService.getAllowList(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Response response = allowListApiImpl.getAllowList(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllowListThrowsException() throws Exception {
    when(allowListService.getAllowList(ACCOUNT_ID)).thenThrow(InvalidRequestException.class);
    Response response = allowListApiImpl.getAllowList(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSetAllowList() throws Exception {
    AllowListRequest request = new AllowListRequest();
    List<HostInfo> hostInfoList = new ArrayList<>();
    request.setAllow(hostInfoList);
    when(allowListService.saveAllowList(request.getAllow(), ACCOUNT_ID)).thenReturn(hostInfoList);
    Response response = allowListApiImpl.saveAllowList(request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSetAllowListThrowsException() throws Exception {
    AllowListRequest request = new AllowListRequest();
    List<HostInfo> hostInfoList = new ArrayList<>();
    request.setAllow(hostInfoList);
    when(allowListService.saveAllowList(request.getAllow(), ACCOUNT_ID)).thenThrow(InvalidRequestException.class);
    Response response = allowListApiImpl.saveAllowList(request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }
}
