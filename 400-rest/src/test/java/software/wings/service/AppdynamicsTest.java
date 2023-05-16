/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.appdynamics.AppdynamicsTier;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import io.specto.hoverfly.junit.core.HoverflyConfig;
import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;

/**
 * Created by rsingh on 10/10/17.
 */
public class AppdynamicsTest extends WingsBaseTest {
  @Inject private AppdynamicsService appdynamicsService;
  @Inject private NewRelicService newRelicService;
  @Inject private HPersistence persistence;
  @Inject private EncryptionService encryptionService;
  @Inject private AppdynamicsDelegateService appdynamicsDelegateService;
  @Inject private ScmSecret scmSecret;
  @Mock private DelegateProxyFactory appdDelegateProxyFactory;
  @Mock private DelegateProxyFactory kmsDelegateProxyFactory;
  private SettingAttribute settingAttribute;
  private String accountId;
  private final String userEmail = "rsingh@harness.io";
  private final String userName = "raghu";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();

  @Rule public ExpectedException thrown = ExpectedException.none();
  @ClassRule public static HoverflyRule rule;
  static {
    try {
      rule = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
    } catch (Exception e) {
      // This is rarely failing in CI with port conflict exception. So retrying one more time.
      // If you still face this issue in your PR's please notify me(kamal).
      rule = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
    }
  }
  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    persistence.save(user);
    UserThreadLocal.set(user);
    FieldUtils.writeField(appdynamicsDelegateService, "encryptionService", encryptionService, true);
    when(appdDelegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(appdynamicsDelegateService);
    FieldUtils.writeField(appdynamicsService, "delegateProxyFactory", appdDelegateProxyFactory, true);
    FieldUtils.writeField(newRelicService, "delegateProxyFactory", appdDelegateProxyFactory, true);

    accountId = UUID.randomUUID().toString();

    AppDynamicsConfig appDynamicsConfig =
        AppDynamicsConfig.builder()
            .accountId(accountId)
            .controllerUrl("https://harness-test.saas.appdynamics.com/controller")
            .accountname("harness-test")
            .username("raghu@harness.io")
            .password(scmSecret.decryptToCharArray(new SecretName("appd_config_password")))
            .build();
    settingAttribute = aSettingAttribute()
                           .withName("AppD")
                           .withCategory(SettingCategory.CONNECTOR)
                           .withAccountId(accountId)
                           .withValue(appDynamicsConfig)
                           .build();
    persistence.save(settingAttribute);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void validateConfig() {
    rule.simulate(SimulationSource.file(Paths.get("400-rest/src/test/resources/hoverfly/appd-validate-test.json")));
    ((AppDynamicsConfig) settingAttribute.getValue())
        .setPassword(scmSecret.decryptToCharArray(new SecretName("appd_config_password")));
    newRelicService.validateConfig(settingAttribute, StateType.APP_DYNAMICS, Collections.emptyList());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getAllApplications() {
    rule.simulate(SimulationSource.file(Paths.get("400-rest/src/test/resources/hoverfly/appd-apps-test.json")));
    List<NewRelicApplication> applications = appdynamicsService.getApplications(settingAttribute.getUuid());
    assertThat(applications.isEmpty()).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getTiers() {
    rule.simulate(SimulationSource.file(Paths.get("400-rest/src/test/resources/hoverfly/appd-tiers-test.json")));
    List<NewRelicApplication> applications = appdynamicsService.getApplications(settingAttribute.getUuid());
    assertThat(applications.isEmpty()).isFalse();
    Optional<NewRelicApplication> app =
        applications.stream().filter(application -> application.getName().equals("cv-app")).findFirst();
    Set<AppdynamicsTier> tiers = appdynamicsService.getTiers(settingAttribute.getUuid(), app.get().getId());
    assertThat(tiers.isEmpty()).isFalse();
  }
}
