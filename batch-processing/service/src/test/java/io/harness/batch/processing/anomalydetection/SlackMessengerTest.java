/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.SANDESH;

import static com.slack.api.webhook.WebhookPayloads.payload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.anomalydetection.alerts.SlackMessageGenerator;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.ccm.currency.Currency;
import io.harness.rule.Owner;

import com.slack.api.Slack;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.webhook.WebhookResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SlackMessengerTest extends CategoryTest {
  @Mock BatchMainConfig mainConfiguration;
  @InjectMocks SlackMessageGenerator slackMessageGenerator;

  AnomalyEntity clusterAnomaly;
  AnomalyEntity namespaceAnomaly;
  AnomalyEntity gcpProjectAnomaly;
  AnomalyEntity awsAccountAnomaly;
  Instant anomalyTime;
  Slack slack;

  @Before
  public void setup() {
    when(mainConfiguration.getBaseUrl()).thenReturn("https://app.harness.io");
    slack = Slack.getInstance();
    anomalyTime = Instant.now().minus(3, ChronoUnit.DAYS);
    clusterAnomaly = AnomalyEntity.builder()
                         .id("ANOMALY_ID1")
                         .accountId("wFHXHD0RRQWoO8tIZT5YVw")
                         .actualCost(19.1)
                         .expectedCost(13.3)
                         .anomalyTime(anomalyTime)
                         .note("K8S_Anomaly")
                         .anomalyScore(12.34)
                         .clusterId("5e279ca81e057d4bc67f8604")
                         .clusterName("pr-private")
                         .timeGranularity(TimeGranularity.DAILY)
                         .build();

    namespaceAnomaly = AnomalyEntity.builder()
                           .id("ANOMALY_ID2")
                           .accountId("wFHXHD0RRQWoO8tIZT5YVw")
                           .actualCost(17.1)
                           .expectedCost(10.3)
                           .anomalyTime(anomalyTime)
                           .note("K8S_Anomaly")
                           .anomalyScore(12.34)
                           .clusterId("5e279ca81e057d4bc67f8604")
                           .clusterName("pr-private")
                           .namespace("test-k8-activity")
                           .timeGranularity(TimeGranularity.DAILY)
                           .build();
    gcpProjectAnomaly = AnomalyEntity.builder()
                            .id("ANOMALY_ID3")
                            .accountId("wFHXHD0RRQWoO8tIZT5YVw")
                            .actualCost(15.1)
                            .expectedCost(12.3)
                            .anomalyTime(anomalyTime)
                            .note("K8S_Anomaly")
                            .anomalyScore(12.34)
                            .gcpProject("ccm-play")
                            .gcpProduct("Compute Engine")
                            .gcpSKUId("sdfjlkdsj")
                            .gcpSKUDescription("N1 Predefined Instance Core running in Americas")
                            .timeGranularity(TimeGranularity.DAILY)
                            .build();

    awsAccountAnomaly = AnomalyEntity.builder()
                            .id("ANOMALY_ID4")
                            .accountId("wFHXHD0RRQWoO8tIZT5YVw")
                            .actualCost(15.1)
                            .expectedCost(12.3)
                            .anomalyTime(anomalyTime)
                            .note("K8S_Anomaly")
                            .anomalyScore(12.34)
                            .awsAccount("896386633644")
                            .awsService("Amazon Elastic Compute Cloud")
                            .timeGranularity(TimeGranularity.DAILY)
                            .build();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  @Ignore("To be fixed ... add more details what is wrong")
  public void shouldSendAlerts() throws IOException {
    List<AnomalyEntity> anomalyList = new ArrayList<>();
    anomalyList.add(clusterAnomaly);
    anomalyList.add(namespaceAnomaly);
    anomalyList.add(gcpProjectAnomaly);
    anomalyList.add(awsAccountAnomaly);

    List<LayoutBlock> blocks = slackMessageGenerator.generateDailyReport(anomalyList, Currency.USD);

    WebhookResponse response =
        slack.send("https://hooks.slack.com/services/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",
            payload(p -> p.text("Harness CE Anomaly Alert").blocks(blocks)));
    assertThat(response.getCode()).isEqualTo(200);
  }
}
