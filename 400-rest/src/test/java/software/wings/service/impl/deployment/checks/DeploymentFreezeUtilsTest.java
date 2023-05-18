/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.deployment.checks;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.FREEZE_ACTIVATION_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.FREEZE_DEACTIVATION_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.PIPELINE_FREEZE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.TRIGGER_EXECUTION_REJECTED_NOTIFICATION;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.FREEZE_WINDOW_ID;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.NotificationRule;
import software.wings.beans.alert.AlertType;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class DeploymentFreezeUtilsTest extends WingsBaseTest {
  @Mock GovernanceConfigService governanceConfigService;
  @Mock NotificationService notificationService;
  @Mock AlertService alertService;
  @Inject @InjectMocks DeploymentFreezeUtils deploymentFreezeUtils;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendPipelineRejectionNotification() {
    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("key", "value");
    TimeRange range = new TimeRange(100, 100_000, "Asia/Kolkatta", false, null, null, null, false);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "FREEZE1", null, false,
        Collections.emptyList(), Collections.emptyList(), asList(USER_GROUP_ID, USER_GROUP_ID + 2), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "FREEZE2", null, true,
        Collections.emptyList(), Collections.emptyList(), asList(USER_GROUP_ID), "uuid", null);
    List<String> freezeWindowIds = Arrays.asList(FREEZE_WINDOW_ID, FREEZE_WINDOW_ID + 2);

    when(governanceConfigService.getGovernanceFreezeConfigs(ACCOUNT_ID, freezeWindowIds))
        .thenReturn(Arrays.asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2));

    deploymentFreezeUtils.sendPipelineRejectionNotification(ACCOUNT_ID, APP_ID, freezeWindowIds, placeholderValues);
    ArgumentCaptor<List> ruleCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(2)).sendNotificationAsync(notificationCaptor.capture(), ruleCaptor.capture());

    assertThat(notificationCaptor.getAllValues()).hasSize(2);
    assertThat(notificationCaptor.getValue()).isInstanceOf(InformationNotification.class);
    assertThat(notificationCaptor.getValue().getAppId()).isEqualTo(APP_ID);
    assertThat(notificationCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationCaptor.getValue().getNotificationTemplateId())
        .isEqualTo(PIPELINE_FREEZE_NOTIFICATION.name());
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables().get(
                   DeploymentFreezeUtils.BLACKOUT_WINDOW_NAME))
        .isEqualTo("FREEZE2");
    assertThat(notificationCaptor.getAllValues().get(0).getNotificationTemplateVariables().get(
                   DeploymentFreezeUtils.BLACKOUT_WINDOW_NAME))
        .isEqualTo("FREEZE1");

    NotificationRule notificationRule = (NotificationRule) ruleCaptor.getAllValues().get(0).get(0);
    assertThat(notificationRule.getUserGroupIds()).containsExactlyInAnyOrder(USER_GROUP_ID, USER_GROUP_ID + 2);
    notificationRule = (NotificationRule) ruleCaptor.getAllValues().get(1).get(0);
    assertThat(notificationRule.getUserGroupIds()).containsExactlyInAnyOrder(USER_GROUP_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendTriggerRejectedNotification() {
    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("key", "value");
    TimeRange range = new TimeRange(100, 100_000, "Asia/Kolkatta", false, null, null, null, false);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "FREEZE1", null, false,
        Collections.emptyList(), Collections.emptyList(), asList(USER_GROUP_ID, USER_GROUP_ID + 2), "uuid", null);

    List<String> freezeWindowIds = Arrays.asList(FREEZE_WINDOW_ID);

    when(governanceConfigService.getGovernanceFreezeConfigs(ACCOUNT_ID, freezeWindowIds))
        .thenReturn(Arrays.asList(timeRangeBasedFreezeConfig));

    deploymentFreezeUtils.sendTriggerRejectedNotification(ACCOUNT_ID, APP_ID, freezeWindowIds, placeholderValues);
    ArgumentCaptor<List> ruleCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(1)).sendNotificationAsync(notificationCaptor.capture(), ruleCaptor.capture());

    assertThat(notificationCaptor.getAllValues()).hasSize(1);
    assertThat(notificationCaptor.getValue()).isInstanceOf(InformationNotification.class);
    assertThat(notificationCaptor.getValue().getAppId()).isEqualTo(APP_ID);
    assertThat(notificationCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationCaptor.getValue().getNotificationTemplateId())
        .isEqualTo(TRIGGER_EXECUTION_REJECTED_NOTIFICATION.name());
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables().get(
                   DeploymentFreezeUtils.BLACKOUT_WINDOW_NAME))
        .isEqualTo("FREEZE1");
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables().get("key")).isEqualTo("value");

    NotificationRule notificationRule = (NotificationRule) ruleCaptor.getValue().get(0);
    assertThat(notificationRule.getUserGroupIds()).containsExactlyInAnyOrder(USER_GROUP_ID, USER_GROUP_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleActivationEvent() {
    TimeRange range = new TimeRange(100, 100_000, "Asia/Kolkatta", false, null, null, null, false);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "FREEZE1", null, false,
        Collections.emptyList(), Collections.emptyList(), asList(USER_GROUP_ID, USER_GROUP_ID + 2), "uuid", null);

    deploymentFreezeUtils.handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
    ArgumentCaptor<AlertData> alertCaptor = ArgumentCaptor.forClass(AlertData.class);
    verify(alertService, times(1))
        .closeExistingAlertsAndOpenNew(
            eq(ACCOUNT_ID), eq(GLOBAL_APP_ID), eq(AlertType.DEPLOYMENT_FREEZE_EVENT), alertCaptor.capture(), any());
    assertThat(alertCaptor.getValue().buildTitle()).isEqualTo("Deployment Freeze Window FREEZE1 has been activated.");

    ArgumentCaptor<List> ruleCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(1)).sendNotificationAsync(notificationCaptor.capture(), ruleCaptor.capture());

    assertThat(notificationCaptor.getAllValues()).hasSize(1);
    assertThat(notificationCaptor.getValue()).isInstanceOf(InformationNotification.class);
    assertThat(notificationCaptor.getValue().getAppId()).isEqualTo(GLOBAL_APP_ID);
    assertThat(notificationCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationCaptor.getValue().getNotificationTemplateId())
        .isEqualTo(FREEZE_ACTIVATION_NOTIFICATION.name());
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables().get(
                   DeploymentFreezeUtils.BLACKOUT_WINDOW_NAME))
        .isEqualTo("FREEZE1");
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables())
        .containsKey(DeploymentFreezeUtils.START_TIME);

    NotificationRule notificationRule = (NotificationRule) ruleCaptor.getValue().get(0);
    assertThat(notificationRule.getUserGroupIds()).containsExactlyInAnyOrder(USER_GROUP_ID, USER_GROUP_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void handleDeActivationEvent() {
    TimeRange range = new TimeRange(100, 100_000, "Asia/Kolkatta", false, null, null, null, false);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "FREEZE1", null, false,
        Collections.emptyList(), Collections.emptyList(), asList(USER_GROUP_ID, USER_GROUP_ID + 2), "uuid", null);

    deploymentFreezeUtils.handleDeActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
    ArgumentCaptor<AlertData> alertCaptor = ArgumentCaptor.forClass(AlertData.class);
    verify(alertService, times(1))
        .closeExistingAlertsAndOpenNew(
            eq(ACCOUNT_ID), eq(GLOBAL_APP_ID), eq(AlertType.DEPLOYMENT_FREEZE_EVENT), alertCaptor.capture(), any());
    assertThat(alertCaptor.getValue().buildTitle())
        .isEqualTo("Deployment Freeze Window FREEZE1 has been de-activated.");

    ArgumentCaptor<List> ruleCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService, times(1)).sendNotificationAsync(notificationCaptor.capture(), ruleCaptor.capture());

    assertThat(notificationCaptor.getAllValues()).hasSize(1);
    assertThat(notificationCaptor.getValue()).isInstanceOf(InformationNotification.class);
    assertThat(notificationCaptor.getValue().getAppId()).isEqualTo(GLOBAL_APP_ID);
    assertThat(notificationCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notificationCaptor.getValue().getNotificationTemplateId())
        .isEqualTo(FREEZE_DEACTIVATION_NOTIFICATION.name());
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables().get(
                   DeploymentFreezeUtils.BLACKOUT_WINDOW_NAME))
        .isEqualTo("FREEZE1");
    assertThat(notificationCaptor.getValue().getNotificationTemplateVariables())
        .containsKey(DeploymentFreezeUtils.END_TIME);

    NotificationRule notificationRule = (NotificationRule) ruleCaptor.getValue().get(0);
    assertThat(notificationRule.getUserGroupIds()).containsExactlyInAnyOrder(USER_GROUP_ID, USER_GROUP_ID + 2);
  }
}
