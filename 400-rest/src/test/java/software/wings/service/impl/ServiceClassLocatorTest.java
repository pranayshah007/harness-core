/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.limits.configuration.LimitConfigurationServiceMongo;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AlertNotificationRuleService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WhitelistService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByActivity;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class ServiceClassLocatorTest extends WingsBaseTest {
  @Inject ServiceClassLocator serviceClassLocator;

  @Inject private ApiKeyService apiKeyService;
  @Inject private ActivityService activityService;
  @Inject private AlertNotificationRuleService notificationRuleService;
  @Inject private AlertService alertService;
  @Inject private AppContainerService appContainerService;
  @Inject private AppService appService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private DelegateProfileService profileService;
  @Inject private DelegateService delegateService;
  @Inject private GovernanceConfigService governanceConfigService;
  @Inject private InstanceService instanceService;
  @Inject private LimitConfigurationServiceMongo limitConfigurationServiceMongo;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private LogService logService;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private ResourceConstraintService resourceConstraintService;
  @Inject private RoleService roleService;
  @Inject private SecretManager secretManager;
  @Inject private EncryptedSettingAttributes encryptedSettingAttributes;
  @Inject private SettingsService settingsService;
  @Inject private SSOSettingServiceImpl ssoSettingService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private TemplateService templateService;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private WhitelistService whitelistService;
  @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @Inject private DelegateNgTokenService delegateNgTokenService;
  @Inject private YamlGitConfigService yamlGitConfigService;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testActivityDescendingServices() {
    List<OwnedByActivity> ownedByActivities =
        ServiceClassLocator.descendingServices(activityService, ActivityServiceImpl.class, OwnedByActivity.class);
    assertThat(ownedByActivities).containsExactly(logService);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAccountDescendingServices() {
    for (int i = 0; i < 3; ++i) {
      List<OwnedByAccount> ownedByAccounts = serviceClassLocator.descendingServicesForInterface(OwnedByAccount.class);
      assertThat(ownedByAccounts)
          .containsExactlyInAnyOrder(alertService, apiKeyService, appContainerService, appService,
              cvConfigurationService, delegateNgTokenService, delegateService, governanceConfigService, instanceService,
              limitConfigurationServiceMongo, loginSettingsService, notificationRuleService, notificationSetupService,
              profileService, resourceConstraintService, roleService, secretManager, encryptedSettingAttributes,
              settingsService, ssoSettingService, templateGalleryService, templateService, userGroupService,
              userService, whitelistService, delegateTaskServiceClassic, yamlGitConfigService);
    }
  }
}
