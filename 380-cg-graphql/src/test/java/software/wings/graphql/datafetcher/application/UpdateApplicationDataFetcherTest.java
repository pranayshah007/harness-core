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
import io.harness.utils.RequestField;

import software.wings.beans.Application;
import software.wings.graphql.datafetcher.AuthRuleGraphQL;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationInput;
import software.wings.graphql.schema.mutation.application.payload.QLUpdateApplicationPayload;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class UpdateApplicationDataFetcherTest extends CategoryTest {
  @Mock AppService appService;
  @Mock AuthRuleGraphQL authRuleInstrumentation;
  @Mock DataFetcherUtils utils;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks
  @Spy
  UpdateApplicationDataFetcher updateApplicationDataFetcher =
      new UpdateApplicationDataFetcher(appService, featureFlagService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    configureAppService();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_mutateAndFetch() {
    final QLUpdateApplicationInput applicationParameters =
        QLUpdateApplicationInput.builder()
            .clientMutationId("req1")
            .applicationId("appid")
            .name(RequestField.ofNullable("   new app name   "))
            .description(RequestField.ofNullable("new app description"))
            .build();
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountid")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    final QLUpdateApplicationPayload qlUpdateApplicationPayload =
        updateApplicationDataFetcher.mutateAndFetch(applicationParameters, mutationContext);
    assertThat(qlUpdateApplicationPayload.getClientMutationId()).isEqualTo("req1");

    verify(appService, times(1)).get("appid");
    final ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
    verify(appService, times(1)).update(applicationArgumentCaptor.capture());

    final Application applicationArgument = applicationArgumentCaptor.getValue();
    assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
    assertThat(applicationArgument.getName()).isEqualTo("new app name");
    assertThat(applicationArgument.getDescription()).isEqualTo("new app description");
    assertThat(applicationArgument.getUuid()).isEqualTo("appid");
    assertThat(applicationArgument.getAppId()).isEqualTo("appid");
  }

  private void configureAppService() {
    doReturn(Application.Builder.anApplication()
                 .appId("appid")
                 .accountId("accountid")
                 .uuid("appid")
                 .name("old name")
                 .description("old description")
                 .build())
        .when(appService)
        .get("appid");
    doReturn(Application.Builder.anApplication().build()).when(appService).update(any(Application.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_1() {
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountid")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();
    final QLUpdateApplicationPayload qlUpdateApplicationPayload =
        updateApplicationDataFetcher.mutateAndFetch(QLUpdateApplicationInput.builder()
                                                        .applicationId("appid")
                                                        .name(RequestField.ofNull())
                                                        .description(RequestField.absent())
                                                        .build(),
            mutationContext);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_2() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(
        ImmutableMap.of("clientMutationId", "req1", "applicationId", "appid", "description", "new app description"))
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);

    {
      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(1)).update(applicationArgumentCaptor.capture());
      final Application applicationArgument = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument.getName()).isEqualTo("old name");
      assertThat(applicationArgument.getDescription()).isEqualTo("new app description");
      assertThat(applicationArgument.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
    }
    {
      doReturn(new HashMap<String, String>() {
        {
          put("clientMutationId", "req1");
          put("applicationId", "appid");
          put("name", "new_app_name");
          put("description", null);
        }
      })
          .when(dataFetchingEnvironment)
          .getArguments();
      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(2)).update(applicationArgumentCaptor.capture());
      final Application applicationArgument = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument.getName()).isEqualTo("new_app_name");
      assertThat(applicationArgument.getDescription()).isNull();
      assertThat(applicationArgument.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = UpdateApplicationDataFetcher.class.getDeclaredMethod(
        "mutateAndFetch", QLUpdateApplicationInput.class, MutationContext.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_APPLICATIONS);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithIsManualTriggerAuthorized() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("isManualTriggerAuthorized", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(true).when(featureFlagService).isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), anyString());

    final Application applicationArgument;
    {
      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(1)).update(applicationArgumentCaptor.capture());
      applicationArgument = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument.getName()).isEqualTo("new_app_name");
      assertThat(applicationArgument.getDescription()).isEqualTo("new description");
      assertThat(applicationArgument.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument.getIsManualTriggerAuthorized()).isTrue();
    }
    {
      doReturn(new HashMap<String, String>() {
        {
          put("clientMutationId", "req1");
          put("applicationId", "appid");
          put("name", "new_app_name_2");
          put("description", null);
          put("isManualTriggerAuthorized", null);
        }
      })
          .when(dataFetchingEnvironment)
          .getArguments();
      doReturn(applicationArgument).when(appService).get("appid");

      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(2)).update(applicationArgumentCaptor.capture());
      final Application applicationArgument2 = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument2.getName()).isEqualTo("new_app_name_2");
      assertThat(applicationArgument2.getDescription()).isNull();
      assertThat(applicationArgument2.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument2.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument2.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument2.getIsManualTriggerAuthorized()).isFalse();
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithIsManualTriggerAuthorizedAndFfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("isManualTriggerAuthorized", true)
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(false).when(featureFlagService).isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), anyString());

    assertThatThrownBy(() -> updateApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithWebHookSecretsMandated() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("areWebHookSecretsMandated", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(true).when(featureFlagService).isEnabled(eq(GITHUB_WEBHOOK_AUTHENTICATION), anyString());

    final Application applicationArgument;
    {
      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(1)).update(applicationArgumentCaptor.capture());
      applicationArgument = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument.getName()).isEqualTo("new_app_name");
      assertThat(applicationArgument.getDescription()).isEqualTo("new description");
      assertThat(applicationArgument.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument.getAreWebHookSecretsMandated()).isTrue();
    }
    {
      doReturn(new HashMap<String, String>() {
        {
          put("clientMutationId", "req1");
          put("applicationId", "appid");
          put("name", "new_app_name_2");
          put("description", null);
          put("areWebHookSecretsMandated", null);
        }
      })
          .when(dataFetchingEnvironment)
          .getArguments();
      doReturn(applicationArgument).when(appService).get("appid");

      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(2)).update(applicationArgumentCaptor.capture());
      final Application applicationArgument2 = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument2.getName()).isEqualTo("new_app_name_2");
      assertThat(applicationArgument2.getDescription()).isNull();
      assertThat(applicationArgument2.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument2.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument2.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument2.getAreWebHookSecretsMandated()).isFalse();
    }
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithWebHookSecretsMandatedAndFfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("areWebHookSecretsMandated", true)
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(false).when(featureFlagService).isEnabled(eq(GITHUB_WEBHOOK_AUTHENTICATION), anyString());

    assertThatThrownBy(() -> updateApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithDisableTriggers() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("disableTriggers", "true")
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_ALLOW_DISABLE_TRIGGERS), anyString());

    final Application applicationArgument;
    {
      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(1)).update(applicationArgumentCaptor.capture());
      applicationArgument = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument.getName()).isEqualTo("new_app_name");
      assertThat(applicationArgument.getDescription()).isEqualTo("new description");
      assertThat(applicationArgument.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument.getDisableTriggers()).isTrue();
    }
    {
      doReturn(new HashMap<String, String>() {
        {
          put("clientMutationId", "req1");
          put("applicationId", "appid");
          put("name", "new_app_name_2");
          put("description", null);
          put("disableTriggers", null);
        }
      })
          .when(dataFetchingEnvironment)
          .getArguments();
      doReturn(applicationArgument).when(appService).get("appid");

      final QLUpdateApplicationPayload qlUpdateApplicationPayload =
          updateApplicationDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<Application> applicationArgumentCaptor = ArgumentCaptor.forClass(Application.class);
      verify(appService, times(2)).update(applicationArgumentCaptor.capture());
      final Application applicationArgument2 = applicationArgumentCaptor.getValue();
      assertThat(applicationArgument2.getName()).isEqualTo("new_app_name_2");
      assertThat(applicationArgument2.getDescription()).isNull();
      assertThat(applicationArgument2.getAppId()).isEqualTo("appid");
      assertThat(applicationArgument2.getUuid()).isEqualTo("appid");
      assertThat(applicationArgument2.getAccountId()).isEqualTo("accountid");
      assertThat(applicationArgument2.getDisableTriggers()).isFalse();
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_WithDisableTriggersAndFfOff() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.builder()
                 .put("clientMutationId", "req1")
                 .put("applicationId", "appid")
                 .put("name", "new_app_name")
                 .put("description", "new description")
                 .put("disableTriggers", true)
                 .build())
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(false).when(featureFlagService).isEnabled(eq(SPG_ALLOW_DISABLE_TRIGGERS), anyString());

    assertThatThrownBy(() -> updateApplicationDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class);
  }
}
