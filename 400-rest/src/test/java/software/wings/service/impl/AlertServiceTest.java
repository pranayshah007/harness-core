/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.alerts.AlertStatus.Closed;
import static software.wings.alerts.AlertStatus.Open;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.alert.AlertType.ARTIFACT_COLLECTION_FAILED;
import static software.wings.beans.alert.AlertType.ApprovalNeeded;
import static software.wings.beans.alert.AlertType.ManualInterventionNeeded;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.alert.AlertData;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.event.publisher.EventPublisher;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ApprovalNeededAlert;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.alert.ManualInterventionNeededAlert;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

public class AlertServiceTest extends WingsBaseTest {
  @Inject Map<AlertType, Class<? extends AlertData>> alertTypeClassMap;
  @Mock private ExecutorService executorService;
  @Mock private AppService appService;
  @Mock private SettingsService settingsService;
  @Mock private ArtifactStreamService artifactStreamService;

  @Inject @InjectMocks private AlertService alertService;

  @Mock private EventPublisher eventPublisher;

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  private final ApprovalNeededAlert approvalNeededAlert =
      ApprovalNeededAlert.builder().approvalId("approvalId").executionId("executionId").name("name").build();
  private final ManualInterventionNeededAlert manualInterventionNeededAlert =
      ManualInterventionNeededAlert.builder()
          .stateExecutionInstanceId("stateExecutionId")
          .executionId("executionId")
          .name("name")
          .build();

  @Before
  public void setUp() {
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testAlertTypeClassMap() {
    assertThat(alertTypeClassMap).hasSize(AlertType.values().length);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldListAlerts() {
    alertService.openAlert(ACCOUNT_ID, APP_ID, ApprovalNeeded, approvalNeededAlert);
    PageResponse<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(1);
    Alert alert = alerts.get(0);
    assertThat(alert.getType()).isEqualTo(ApprovalNeeded);
    assertThat(alert.getAlertData().matches(approvalNeededAlert)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldOpenAlert() {
    alertService.openAlert(ACCOUNT_ID, APP_ID, ApprovalNeeded, approvalNeededAlert);

    List<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(1);
    Alert alert = alerts.get(0);
    assertThat(alert.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(alert.getAppId()).isEqualTo(APP_ID);
    assertThat(alert.getType()).isEqualTo(ApprovalNeeded);
    assertThat(alert.getCategory()).isEqualTo(ApprovalNeeded.getCategory());
    assertThat(alert.getSeverity()).isEqualTo(ApprovalNeeded.getSeverity());
    assertThat(alert.getTitle()).isEqualTo("name needs approval");
    assertThat(alert.getTriggerCount()).isEqualTo(1);
    assertThat(alert.getStatus()).isEqualTo(Open);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotOpenMatchingAlert() {
    alertService.openAlert(ACCOUNT_ID, APP_ID, ApprovalNeeded, approvalNeededAlert);
    alertService.openAlert(ACCOUNT_ID, APP_ID, ApprovalNeeded, approvalNeededAlert);
    PageResponse<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(1);
    Alert alert = alerts.get(0);
    assertThat(alert.getType()).isEqualTo(ApprovalNeeded);
    assertThat(alert.getAlertData().matches(approvalNeededAlert)).isTrue();
    assertThat(alert.getTriggerCount()).isEqualTo(2);
    assertThat(alert.getStatus()).isEqualTo(Open);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCloseAlertsWhenDeploymentAborted() {
    alertService.openAlert(ACCOUNT_ID, APP_ID, ApprovalNeeded, approvalNeededAlert);
    alertService.openAlert(ACCOUNT_ID, APP_ID, ManualInterventionNeeded, manualInterventionNeededAlert);
    alertService.deploymentCompleted(APP_ID, "executionId");

    PageResponse<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(2);
    for (Alert alert : alerts) {
      assertThat(alert.getStatus()).isEqualTo(Closed);
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDeleteByArtifactStream() {
    AlertData alertData = ArtifactCollectionFailedAlert.builder()
                              .appId(APP_ID)
                              .serviceId(SERVICE_ID)
                              .artifactStreamId(ARTIFACT_STREAM_ID)
                              .build();

    alertService.openAlert(ACCOUNT_ID, null, ARTIFACT_COLLECTION_FAILED, alertData);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    List<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(1);

    alertService.deleteByArtifactStream(APP_ID, ARTIFACT_STREAM_ID);

    alerts = alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(0);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDeleteByArtifactStreamAtConnectorLevel() {
    AlertData alertData = ArtifactCollectionFailedAlert.builder().artifactStreamId(ARTIFACT_STREAM_ID).build();

    alertService.openAlert(ACCOUNT_ID, null, ARTIFACT_COLLECTION_FAILED, alertData);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(DockerArtifactStream.builder().settingId(SETTING_ID).build());
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build());

    List<Alert> alerts =
        alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(1);

    alertService.deleteByArtifactStream(GLOBAL_APP_ID, ARTIFACT_STREAM_ID);

    alerts = alertService.list(aPageRequest().addFilter(AlertKeys.accountId, Operator.EQ, ACCOUNT_ID).build());
    assertThat(alerts).hasSize(0);
  }
}
