/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.utils.ResourceTestRule;
import software.wings.utils.WingsTestConstants;

import java.util.UUID;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalAnswers;

/**
 * Created by peeyushaggarwal on 9/27/16.
 */
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ServiceVariableResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = UUID.randomUUID().toString();
  private static final ServiceVariableService VARIABLE_SERVICE = mock(ServiceVariableService.class);
  private static final AuthHandler AUTH_HANDLER = mock(AuthHandler.class);
  private static final AppService APP_SERVICE = mock(AppService.class);
  private static final ServiceVariableResource VARIABLE_RESOURCE = new ServiceVariableResource();

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(VARIABLE_RESOURCE).type(WingsExceptionMapper.class).build();

  private static final ServiceVariable SERVICE_VARIABLE = ServiceVariable.builder()
                                                              .envId(GLOBAL_ENV_ID)
                                                              .entityType(EntityType.ENVIRONMENT)
                                                              .entityId(TEMPLATE_ID)
                                                              .templateId(TEMPLATE_ID)
                                                              .type(ServiceVariableType.TEXT)
                                                              .value("8080".toCharArray())
                                                              .accountId(ACCOUNT_ID)
                                                              .build();
  static {
    when(VARIABLE_SERVICE.save(any())).then(AdditionalAnswers.returnsFirstArg());
    when(VARIABLE_SERVICE.saveWithChecks(anyString(), any())).then(AdditionalAnswers.returnsSecondArg());
    when(APP_SERVICE.get(anyString())).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());
    try {
      FieldUtils.writeField(VARIABLE_RESOURCE, "serviceVariablesService", VARIABLE_SERVICE, true);
      FieldUtils.writeField(VARIABLE_RESOURCE, "appService", APP_SERVICE, true);
      FieldUtils.writeField(VARIABLE_RESOURCE, "authHandler", AUTH_HANDLER, true);
    } catch (IllegalAccessException e) {
      log.error("", e);
    }
    SERVICE_VARIABLE.setUuid(WingsTestConstants.SERVICE_VARIABLE_ID);
    SERVICE_VARIABLE.setAppId(APP_ID);
    when(AUTH_HANDLER.authorize(any(), any(), anyString())).thenReturn(true);
  }

  /**
   * Should list variables.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldListVariables() throws Exception {
    PageResponse<ServiceVariable> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(SERVICE_VARIABLE));
    pageResponse.setTotal(1l);
    when(VARIABLE_SERVICE.list(any(PageRequest.class), any())).thenReturn(pageResponse);
    RestResponse<PageResponse<ServiceVariable>> restResponse =
        RESOURCES.client()
            .target("/service-variables/?appId=" + APP_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<ServiceVariable>>>() {});
    PageRequest<ServiceVariable> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    verify(VARIABLE_SERVICE).list(pageRequest, MASKED);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }

  /**
   * Should save service variable.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldSaveServiceVariable() throws Exception {
    when(VARIABLE_SERVICE.saveWithChecks(anyString(), any(ServiceVariable.class))).thenReturn(SERVICE_VARIABLE);
    RestResponse<ServiceVariable> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/?appId=%s", APP_ID))
            .request()
            .post(entity(SERVICE_VARIABLE, APPLICATION_JSON), new GenericType<RestResponse<ServiceVariable>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(ServiceVariable.class);
    verify(VARIABLE_SERVICE).saveWithChecks(APP_ID, SERVICE_VARIABLE);
  }

  /**
   * Should get variable.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetVariable() throws Exception {
    when(VARIABLE_SERVICE.get(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID, MASKED)).thenReturn(SERVICE_VARIABLE);
    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, APP_ID))
            .request()
            .get(new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(VARIABLE_SERVICE).get(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID, MASKED);
  }

  /**
   * Should update service variable.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateServiceVariable() throws Exception {
    when(VARIABLE_SERVICE.updateWithChecks(anyString(), anyString(), any(ServiceVariable.class)))
        .thenReturn(SERVICE_VARIABLE);
    RestResponse<ServiceVariable> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, APP_ID))
            .request()
            .put(entity(SERVICE_VARIABLE, APPLICATION_JSON), new GenericType<RestResponse<ServiceVariable>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(ServiceVariable.class);
    verify(VARIABLE_SERVICE).updateWithChecks(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID, SERVICE_VARIABLE);
  }

  /**
   * Should delete service variable.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldDeleteServiceVariable() throws Exception {
    Response restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, "APP_ID_1"))
            .request()
            .delete();
    assertThat(restResponse.getStatus()).isEqualTo(200);
    verify(VARIABLE_SERVICE).deleteWithChecks("APP_ID_1", WingsTestConstants.SERVICE_VARIABLE_ID);
  }

  /**
   * Shoudl delete by entity.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shoudlDeleteByEntity() throws Exception {
    Response restResponse = RESOURCES.client()
                                .target(format("/service-variables/entity/%s?appId=%s", TEMPLATE_ID, APP_ID))
                                .request()
                                .delete();
    assertThat(restResponse.getStatus()).isEqualTo(200);
    verify(VARIABLE_SERVICE).pruneByService(APP_ID, TEMPLATE_ID);
  }
}
