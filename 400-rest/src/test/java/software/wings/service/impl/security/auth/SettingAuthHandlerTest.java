/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.SettingAttribute.SettingCategory.SETTING;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SSH_AND_WINRM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SettingAttribute;
import software.wings.beans.StringValue;
import software.wings.beans.User;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
@TargetModule(HarnessModule.UNDEFINED)
public class SettingAuthHandlerTest extends WingsBaseTest {
  @Mock private AuthHandler authHandler;
  @Mock private SettingsService settingsService;
  @InjectMocks @Inject private SettingAuthHandler settingAuthHandler;

  private static final String appId = generateUuid();
  private static final String envId = generateUuid();
  private static final String entityId = generateUuid();

  private User user;
  private SettingAttribute settingAttribute;

  @Before
  public void setUp() {
    user = User.Builder.anUser().uuid(generateUuid()).build();
    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withAppId(appId)
                           .withUuid(entityId)
                           .withEnvId(envId)
                           .withName("setting-attribute")
                           .build();
  }

  private void setPermissions(PermissionType permissionType) {
    Set<PermissionType> permissionTypeSet = new HashSet<>();
    permissionTypeSet.add(permissionType);
    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder()
            .accountPermissionSummary(AccountPermissionSummary.builder().permissions(permissionTypeSet).build())
            .build();
    UserRequestContext userRequestContext =
        UserRequestContext.builder().accountId(generateUuid()).userPermissionInfo(userPermissionInfo).build();
    user.setUserRequestContext(userRequestContext);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeCloudProviderNull() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CLOUD_PROVIDERS);
      UserThreadLocal.set(user);

      settingAttribute.setCategory(CLOUD_PROVIDER);

      settingAuthHandler.authorize(new SettingAttribute(), null);
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
  public void testAuthorizeCloudProviderNotSettingCategory() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CLOUD_PROVIDERS);
      UserThreadLocal.set(user);

      settingAttribute.setCategory(SETTING);

      settingAuthHandler.authorize(new SettingAttribute(), null);
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
  public void testAuthorizeCloudProvider() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CLOUD_PROVIDERS);
      UserThreadLocal.set(user);

      settingAttribute.setCategory(CLOUD_PROVIDER);

      settingAuthHandler.authorize(settingAttribute, null);
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
  public void testAuthorizeConnectors() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CONNECTORS);

      settingAttribute.setCategory(CONNECTOR);
      UserThreadLocal.set(user);

      settingAuthHandler.authorize(settingAttribute, null);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testAuthorizeSshAndWinRM() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_SSH_AND_WINRM);

      settingAttribute.setCategory(SETTING);
      UserThreadLocal.set(user);

      settingAuthHandler.authorize(settingAttribute, null);
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
  public void testAuthorizeWithId() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CONNECTORS);

      settingAttribute.setCategory(CONNECTOR);
      UserThreadLocal.set(user);
      when(settingsService.get(eq(appId), eq(entityId))).thenReturn(settingAttribute);

      settingAuthHandler.authorize(appId, entityId);
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
  public void testAuthorizeWithIdNull() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CONNECTORS);

      settingAttribute.setCategory(CONNECTOR);
      UserThreadLocal.set(user);
      when(settingsService.get(eq(appId), eq(entityId))).thenReturn(null);

      settingAuthHandler.authorize(appId, entityId);
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
  public void testAuthorizeWithCloudProviderId() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_CLOUD_PROVIDERS);

      settingAttribute.setCategory(CLOUD_PROVIDER);
      UserThreadLocal.set(user);
      when(settingsService.get(eq(appId), eq(entityId))).thenReturn(settingAttribute);

      settingAuthHandler.authorize(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAuthorizeApplicationDefaults() {
    boolean exceptionThrown = false;
    try {
      setPermissions(ACCOUNT_MANAGEMENT);
      UserThreadLocal.set(user);

      settingAttribute.setCategory(SETTING);
      settingAttribute.setValue(StringValue.Builder.aStringValue().withValue("testValue").build());

      settingAuthHandler.authorize(settingAttribute, GLOBAL_APP_ID);
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAuthorizeAccountDefaults() {
    boolean exceptionThrown = false;
    try {
      setPermissions(MANAGE_APPLICATIONS);
      UserThreadLocal.set(user);

      settingAttribute.setCategory(SETTING);
      settingAttribute.setValue(StringValue.Builder.aStringValue().withValue("testValue").build());

      settingAuthHandler.authorize(settingAttribute, "testAppId");
    } catch (Exception e) {
      assertThat(e).isNull();
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }
}
