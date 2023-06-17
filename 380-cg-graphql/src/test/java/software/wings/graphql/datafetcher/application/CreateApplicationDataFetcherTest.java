/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LALIT;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.graphql.datafetcher.AuthRuleGraphQL;
import software.wings.graphql.datafetcher.BaseDataFetcher;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLCreateApplicationInput;
import software.wings.graphql.schema.mutation.application.payload.QLCreateApplicationPayload;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CreateApplicationDataFetcherTest extends CategoryTest {
  @Mock AuthRuleGraphQL authRuleInstrumentation;
  @Mock DataFetcherUtils utils;
  @Mock AppService appService;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks
  @Spy
  CreateApplicationDataFetcher createApplicationDataFetcher =
      new CreateApplicationDataFetcher(appService, featureFlagService);

  @Before

  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_get() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("clientMutationId", "req1", "name", "appname", "description", "app description"))
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid1").when(utils).getAccountId(dataFetchingEnvironment);
    final Application savedApplication = Application.Builder.anApplication()
                                             .name("appname")
                                             .description("app description")
                                             .appId("appid")
                                             .accountId("accountid1")
                                             .build();
    doReturn(savedApplication).when(appService).save(any(Application.class));

    final QLCreateApplicationPayload qlCreateApplicationPayload =
        createApplicationDataFetcher.get(dataFetchingEnvironment);
    final QLApplication qlApplication = qlCreateApplicationPayload.getApplication();
    assertThat(qlCreateApplicationPayload.getClientMutationId()).isEqualTo("req1");
    ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
    verify(appService, times(1)).save(applicationArgumentCaptor.capture());
    verify(authRuleInstrumentation, times(1))
        .instrumentDataFetcher(
            any(BaseDataFetcher.class), eq(dataFetchingEnvironment), eq(QLCreateApplicationPayload.class));

    verify(authRuleInstrumentation, times(1))
        .handlePostMutation(
            any(MutationContext.class), any(QLCreateApplicationInput.class), any(QLCreateApplicationPayload.class));

    final Application applicationArgument = applicationArgumentCaptor.getValue();
    assertThat(applicationArgument.getName()).isEqualTo("appname");
    assertThat(applicationArgument.getDescription()).isEqualTo("app description");
    assertThat(applicationArgument.getAccountId()).isEqualTo("accountid1");

    assertThat(qlApplication.getId()).isEqualTo(savedApplication.getAppId());
    assertThat(qlApplication.getName()).isEqualTo(savedApplication.getName());
    assertThat(qlApplication.getDescription()).isEqualTo(savedApplication.getDescription());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = CreateApplicationDataFetcher.class.getDeclaredMethod(
        "mutateAndFetch", QLCreateApplicationInput.class, MutationContext.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_APPLICATIONS);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateApplicationWithManualTriggerAuthorized() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("isManualTriggerAuthorized", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();

    doReturn("accountid1").when(utils).getAccountId(dataFetchingEnvironment);
    final Application savedApplication = Application.Builder.anApplication()
                                             .name("appname")
                                             .description("app description")
                                             .appId("appid")
                                             .accountId("accountid1")
                                             .isManualTriggerAuthorized(true)
                                             .build();
    doReturn(savedApplication).when(appService).save(any(Application.class));
    doReturn(true).when(featureFlagService).isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), anyString());

    final QLCreateApplicationPayload qlCreateApplicationPayload =
        createApplicationDataFetcher.get(dataFetchingEnvironment);
    final QLApplication qlApplication = qlCreateApplicationPayload.getApplication();
    assertThat(qlCreateApplicationPayload.getClientMutationId()).isEqualTo("req1");
    ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
    verify(appService, times(1)).save(applicationArgumentCaptor.capture());
    verify(authRuleInstrumentation, times(1))
        .instrumentDataFetcher(
            any(BaseDataFetcher.class), eq(dataFetchingEnvironment), eq(QLCreateApplicationPayload.class));

    verify(authRuleInstrumentation, times(1))
        .handlePostMutation(
            any(MutationContext.class), any(QLCreateApplicationInput.class), any(QLCreateApplicationPayload.class));

    final Application applicationArgument = applicationArgumentCaptor.getValue();
    assertThat(applicationArgument.getName()).isEqualTo("appname");
    assertThat(applicationArgument.getDescription()).isEqualTo("app description");
    assertThat(applicationArgument.getAccountId()).isEqualTo("accountid1");
    assertThat(qlApplication.getId()).isEqualTo(savedApplication.getAppId());
    assertThat(qlApplication.getName()).isEqualTo(savedApplication.getName());
    assertThat(qlApplication.getDescription()).isEqualTo(savedApplication.getDescription());
    assertThat(qlApplication.getIsManualTriggerAuthorized()).isEqualTo(savedApplication.getIsManualTriggerAuthorized());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateApplicationWithManualTriggerAuthorized_FfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("isManualTriggerAuthorized", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn(false).when(featureFlagService).isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), anyString());

    assertThatThrownBy(() -> createApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void testCreateApplicationWithWebHookSecretsMandated() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("areWebHookSecretsMandated", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();

    doReturn("accountid1").when(utils).getAccountId(dataFetchingEnvironment);
    final Application savedApplication = Application.Builder.anApplication()
                                             .name("appname")
                                             .description("app description")
                                             .appId("appid")
                                             .accountId("accountid1")
                                             .areWebHookSecretsMandated(true)
                                             .build();
    doReturn(savedApplication).when(appService).save(any(Application.class));
    doReturn(true).when(featureFlagService).isEnabled(eq(GITHUB_WEBHOOK_AUTHENTICATION), anyString());

    final QLCreateApplicationPayload qlCreateApplicationPayload =
        createApplicationDataFetcher.get(dataFetchingEnvironment);
    final QLApplication qlApplication = qlCreateApplicationPayload.getApplication();
    assertThat(qlCreateApplicationPayload.getClientMutationId()).isEqualTo("req1");
    ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
    verify(appService, times(1)).save(applicationArgumentCaptor.capture());
    verify(authRuleInstrumentation, times(1))
        .instrumentDataFetcher(
            any(BaseDataFetcher.class), eq(dataFetchingEnvironment), eq(QLCreateApplicationPayload.class));

    verify(authRuleInstrumentation, times(1))
        .handlePostMutation(
            any(MutationContext.class), any(QLCreateApplicationInput.class), any(QLCreateApplicationPayload.class));

    final Application applicationArgument = applicationArgumentCaptor.getValue();
    assertThat(applicationArgument.getName()).isEqualTo("appname");
    assertThat(applicationArgument.getDescription()).isEqualTo("app description");
    assertThat(applicationArgument.getAccountId()).isEqualTo("accountid1");
    assertThat(qlApplication.getId()).isEqualTo(savedApplication.getAppId());
    assertThat(qlApplication.getName()).isEqualTo(savedApplication.getName());
    assertThat(qlApplication.getDescription()).isEqualTo(savedApplication.getDescription());
    assertThat(qlApplication.getAreWebHookSecretsMandated()).isEqualTo(savedApplication.getAreWebHookSecretsMandated());
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void testCreateApplicationWithWebHookSecretsMandated_FfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("areWebHookSecretsMandated", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn(false).when(featureFlagService).isEnabled(eq(GITHUB_WEBHOOK_AUTHENTICATION), anyString());

    assertThatThrownBy(() -> createApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateApplicationWithDisableTriggers() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("disableTriggers", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();

    doReturn("accountid1").when(utils).getAccountId(dataFetchingEnvironment);
    final Application savedApplication = Application.Builder.anApplication()
                                             .name("appname")
                                             .description("app description")
                                             .appId("appid")
                                             .accountId("accountid1")
                                             .disableTriggers(true)
                                             .build();
    doReturn(savedApplication).when(appService).save(any(Application.class));
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_ALLOW_DISABLE_TRIGGERS), anyString());

    final QLCreateApplicationPayload qlCreateApplicationPayload =
        createApplicationDataFetcher.get(dataFetchingEnvironment);
    final QLApplication qlApplication = qlCreateApplicationPayload.getApplication();
    assertThat(qlCreateApplicationPayload.getClientMutationId()).isEqualTo("req1");
    ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
    verify(appService, times(1)).save(applicationArgumentCaptor.capture());
    verify(authRuleInstrumentation, times(1))
        .instrumentDataFetcher(
            any(BaseDataFetcher.class), eq(dataFetchingEnvironment), eq(QLCreateApplicationPayload.class));

    verify(authRuleInstrumentation, times(1))
        .handlePostMutation(
            any(MutationContext.class), any(QLCreateApplicationInput.class), any(QLCreateApplicationPayload.class));

    final Application applicationArgument = applicationArgumentCaptor.getValue();
    assertThat(applicationArgument.getName()).isEqualTo("appname");
    assertThat(applicationArgument.getDescription()).isEqualTo("app description");
    assertThat(applicationArgument.getAccountId()).isEqualTo("accountid1");
    assertThat(qlApplication.getId()).isEqualTo(savedApplication.getAppId());
    assertThat(qlApplication.getName()).isEqualTo(savedApplication.getName());
    assertThat(qlApplication.getDescription()).isEqualTo(savedApplication.getDescription());
    assertThat(qlApplication.getDisableTriggers()).isEqualTo(savedApplication.getDisableTriggers());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateApplicationWithDisableTriggers_FfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "appname")
                 .put("description", "app description")
                 .put("disableTriggers", "false")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn(false).when(featureFlagService).isEnabled(eq(SPG_ALLOW_DISABLE_TRIGGERS), anyString());

    assertThatThrownBy(() -> createApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }
}