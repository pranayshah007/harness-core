/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.ANUBHAW;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.Host.Builder;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.HostService;
import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by anubhaw on 6/7/16.
 */
public class HostResourceTest extends WingsBaseTest {
  /**
   * The constant MAIN_CONFIGURATION.
   */
  public static final MainConfiguration MAIN_CONFIGURATION = mock(MainConfiguration.class);
  private static final HostService RESOURCE_SERVICE = mock(HostService.class);
  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
                                                       .instance(new HostResource(RESOURCE_SERVICE, MAIN_CONFIGURATION))
                                                       .type(WingsExceptionMapper.class)
                                                       .build();
  private static final Host host = Builder.aHost().withAppId(APP_ID).withEnvId(ENV_ID).withHostName(HOST_NAME).build();

  /**
   * Should list hosts.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldListHosts() {
    PageResponse<Host> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(host));
    pageResponse.setTotal(1l);
    when(RESOURCE_SERVICE.list(any(PageRequest.class))).thenReturn(pageResponse);
    RestResponse<PageResponse<Host>> restResponse = RESOURCES.client()
                                                        .target(format("/hosts?appId=%s&envId=%s", APP_ID, ENV_ID))
                                                        .request()
                                                        .get(new GenericType<RestResponse<PageResponse<Host>>>() {});
    PageRequest<Host> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("50");
    pageRequest.addFilter("appId", EQ, APP_ID);
    pageRequest.addFilter("envId", EQ, ENV_ID);
    verify(RESOURCE_SERVICE).list(pageRequest);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }
}
