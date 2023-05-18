/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ConfigFile;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.User;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ServiceTemplateService;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigFileAuthHandlerTest extends WingsBaseTest {
  @Mock private AuthService authService;
  @Mock private ServiceTemplateService serviceTemplateService;

  @InjectMocks @Inject private ConfigFileAuthHandler configFileAuthHandler;

  private static final String entityId = generateUuid();
  private static final String appId = generateUuid();

  ConfigFile configFile;
  User user;

  @Before
  public void setUp() {
    configFile = ConfigFile.builder().entityId(entityId).build();
    configFile.setAppId(appId);

    UserRequestContext userRequestContext = UserRequestContext.builder().accountId(generateUuid()).build();
    user = User.Builder.anUser().uuid(generateUuid()).userRequestContext(userRequestContext).build();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(SERVICE);
      doNothing().when(authService).authorize(anyString(), anyList(), any(), any(), anyList());

      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(SERVICE_TEMPLATE);
      doNothing().when(authService).authorize(anyString(), anyList(), any(), any(), anyList());
      ServiceTemplate serviceTemplate =
          ServiceTemplate.Builder.aServiceTemplate().withServiceId(generateUuid()).withEnvId(generateUuid()).build();
      when(serviceTemplateService.get(eq(appId), anyString())).thenReturn(serviceTemplate);
      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(ENVIRONMENT);
      doNothing().when(authService).authorize(anyString(), anyList(), any(), any(), anyList());

      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC3_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(ENVIRONMENT);
      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), any(), any(), anyList());
      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(SERVICE);
      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), any(), any(), anyList());
      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testAuthorize() {
    boolean exceptionThrown = false;
    try {
      UserThreadLocal.set(user);
      configFile.setEntityType(SERVICE_TEMPLATE);
      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), any(), any(), anyList());
      ServiceTemplate serviceTemplate =
          ServiceTemplate.Builder.aServiceTemplate().withServiceId(generateUuid()).withEnvId(generateUuid()).build();
      when(serviceTemplateService.get(eq(appId), anyString())).thenReturn(serviceTemplate);
      configFileAuthHandler.authorize(configFile);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }
}
