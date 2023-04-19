/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.resources;

import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.idp.plugin.services.PluginInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Exports;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginInfoResponse;
import io.harness.spec.server.idp.v1.model.PluginRequestResponseList;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoApiImplTest {
  @Mock private IdpCommonService idpCommonService;
  @InjectMocks private PluginInfoApiImpl pluginInfoApiImpl;
  @Mock private PluginInfoService pluginInfoService;
  private static final String ACCOUNT_ID = "123";
  private static final String PAGER_DUTY_NAME = "PagerDuty";
  private static final String PAGER_DUTY_ID = "pager-duty";
  private static final String GITHUB_INSIGHTS_ID = "github-insights";
  private static final String PLUGIN_REQUEST_NAME = "pluginName";
  private static final String PLUGIN_REQUEST_CREATOR = "foo";
  private static final String PLUGIN_REQUEST_PACKAGE_LINK = "https://www.harness.io";
  private static final String PLUGIN_REQUEST_DOC_LINK = "https://www.harness.io";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetPlugins() {
    List<PluginInfo> pluginInfoList = new ArrayList<>();
    pluginInfoList.add(getPagerDutyInfo());
    when(pluginInfoService.getAllPluginsInfo(ACCOUNT_ID)).thenReturn(pluginInfoList);
    Response response = pluginInfoApiImpl.getPlugins(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<PluginInfoResponse> pluginInfoResponses = (List<PluginInfoResponse>) response.getEntity();
    assertThat(pluginInfoResponses.get(0).getPlugin().getId()).isEqualTo(PAGER_DUTY_ID);
    assertThat(pluginInfoResponses.get(0).getPlugin().getName()).isEqualTo(PAGER_DUTY_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPluginsInfoPluginId() {
    when(pluginInfoService.getPluginDetailedInfo(PAGER_DUTY_ID, ACCOUNT_ID)).thenReturn(getPagerDutyDetailedInfo());
    Response response = pluginInfoApiImpl.getPluginsInfoPluginId(PAGER_DUTY_ID, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertNotNull(response.getEntity());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPluginsInfoPluginIdThrowsException() {
    when(pluginInfoService.getPluginDetailedInfo(GITHUB_INSIGHTS_ID, ACCOUNT_ID))
        .thenThrow(InvalidRequestException.class);
    Response response = pluginInfoApiImpl.getPluginsInfoPluginId(GITHUB_INSIGHTS_ID, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPostPluginRequest() {
    RequestPlugin request = new RequestPlugin();
    request.setName(PLUGIN_REQUEST_NAME);
    request.setCreator(PLUGIN_REQUEST_CREATOR);
    request.setPackageLink(PLUGIN_REQUEST_PACKAGE_LINK);
    request.setDocLink(PLUGIN_REQUEST_DOC_LINK);
    when(pluginInfoService.savePluginRequest(ACCOUNT_ID, request)).thenReturn(getRequestPlugin());
    Response response = pluginInfoApiImpl.postPluginRequest(request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetPluginRequest() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(pluginInfoService.getPluginRequests(ACCOUNT_ID, 0, 10)).thenReturn(getPagePluginRequestEntity());
    when(idpCommonService.buildPageResponse(0, 10, 1, getPluginRequestResponseList()))
        .thenReturn(Response.ok().entity(getPluginRequestResponseList()).build());
    Response response = pluginInfoApiImpl.getPluginRequest(ACCOUNT_ID, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    PluginRequestResponseList pluginRequestResponseList = (PluginRequestResponseList) response.getEntity();
    assertThat(pluginRequestResponseList.getPluginRequests().get(0).getName()).isEqualTo(PLUGIN_REQUEST_NAME);
    assertThat(pluginRequestResponseList.getPluginRequests().get(0).getCreator()).isEqualTo(PLUGIN_REQUEST_CREATOR);
    assertThat(pluginRequestResponseList.getPluginRequests().get(0).getPackageLink())
        .isEqualTo(PLUGIN_REQUEST_PACKAGE_LINK);
    assertThat(pluginRequestResponseList.getPluginRequests().get(0).getDocLink()).isEqualTo(PLUGIN_REQUEST_DOC_LINK);
  }

  private PluginDetailedInfo getPagerDutyDetailedInfo() {
    PluginDetailedInfo pluginDetailedInfo = new PluginDetailedInfo();
    pluginDetailedInfo.setPluginDetails(getPagerDutyInfo());
    pluginDetailedInfo.setExports(new Exports().cards(1).pages(0).tabContents(0));
    return pluginDetailedInfo;
  }

  private PluginInfo getPagerDutyInfo() {
    PluginInfo pluginInfo = new PluginInfo();
    pluginInfo.setName(PAGER_DUTY_NAME);
    pluginInfo.setId(PAGER_DUTY_ID);
    pluginInfo.setEnabled(true);
    return pluginInfo;
  }

  private RequestPlugin getRequestPlugin() {
    RequestPlugin requestPlugin = new RequestPlugin();
    requestPlugin.setName(PLUGIN_REQUEST_NAME);
    requestPlugin.setCreator(PLUGIN_REQUEST_CREATOR);
    requestPlugin.setPackageLink(PLUGIN_REQUEST_PACKAGE_LINK);
    requestPlugin.setDocLink(PLUGIN_REQUEST_DOC_LINK);
    return requestPlugin;
  }

  private Page<PluginRequestEntity> getPagePluginRequestEntity() {
    PluginRequestEntity pluginRequestEntity = PluginRequestEntity.builder()
                                                  .name(PLUGIN_REQUEST_NAME)
                                                  .creator(PLUGIN_REQUEST_CREATOR)
                                                  .packageLink(PLUGIN_REQUEST_PACKAGE_LINK)
                                                  .docLink(PLUGIN_REQUEST_DOC_LINK)
                                                  .build();
    List<PluginRequestEntity> pluginRequestEntityList = new ArrayList<>();
    pluginRequestEntityList.add(pluginRequestEntity);
    return new PageImpl<>(pluginRequestEntityList);
  }

  private PluginRequestResponseList getPluginRequestResponseList() {
    PluginRequestResponseList pluginRequestResponseList = new PluginRequestResponseList();
    pluginRequestResponseList.setPluginRequests(Collections.singletonList(getRequestPlugin()));
    return pluginRequestResponseList;
  }
}
