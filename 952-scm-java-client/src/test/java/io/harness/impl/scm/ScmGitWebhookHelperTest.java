/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.product.ci.scm.proto.AzureWebhookEvent;
import io.harness.product.ci.scm.proto.AzureWebhookEvents;
import io.harness.product.ci.scm.proto.HarnessWebhookEvent;
import io.harness.product.ci.scm.proto.HarnessWebhookEvents;
import io.harness.product.ci.scm.proto.NativeEvents;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class ScmGitWebhookHelperTest extends CategoryTest {
  private ScmConnector scmConnector;
  private ScmConnector harnessConnector;
  private final String repoUrl = "https://dev.azure.com/org/project/_git/repo";

  @Before
  public void setup() {
    AzureRepoConnectorDTO githubConnector = AzureRepoConnectorDTO.builder()
                                                .apiAccess(AzureRepoApiAccessDTO.builder().build())
                                                .url(repoUrl)
                                                .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                .build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    HarnessConnectorDTO harnessConnectorDTO = HarnessConnectorDTO.builder().url(repoUrl).build();
    ConnectorInfoDTO connectorInfo1 = ConnectorInfoDTO.builder().connectorConfig(harnessConnectorDTO).build();
    harnessConnector = (ScmConnector) connectorInfo1.getConnectorConfig();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testIsIdenticalEventsForAzureTrue() {
    List<NativeEvents> allNativeEvents = Arrays.asList(
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PUSH).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULLREQUEST_CREATED).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULLREQUEST_UPDATED).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULLREQUEST_MERGED).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(
                AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULL_REQUEST_ISSUE_COMMENT).build())
            .build());
    boolean result =
        ScmGitWebhookHelper.isIdenticalEvents(null, HookEventType.TRIGGER_EVENTS, scmConnector, allNativeEvents);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testIsIdenticalEventsForAzureFalse() {
    List<NativeEvents> allNativeEvents = Arrays.asList(
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PUSH).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULLREQUEST_CREATED).build())
            .build(),
        NativeEvents.newBuilder()
            .setAzure(AzureWebhookEvents.newBuilder().addEvents(AzureWebhookEvent.AZURE_PULLREQUEST_UPDATED).build())
            .build());
    boolean result =
        ScmGitWebhookHelper.isIdenticalEvents(null, HookEventType.TRIGGER_EVENTS, scmConnector, allNativeEvents);
    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testIsIdenticalEventsForHarnessTrue() {
    List<HarnessWebhookEvent> allNativeEvents = Arrays.asList(HarnessWebhookEvent.HARNESS_PULLREQ_CREATED,
        HarnessWebhookEvent.HARNESS_PULLREQ_REOPENED, HarnessWebhookEvent.HARNESS_PULLREQ_BRANCH_UPDATED,
        HarnessWebhookEvent.HARNESS_BRANCH_CREATED, HarnessWebhookEvent.HARNESS_BRANCH_UPDATED,
        HarnessWebhookEvent.HARNESS_BRANCH_DELETED, HarnessWebhookEvent.HARNESS_TAG_CREATED,
        HarnessWebhookEvent.HARNESS_TAG_DELETED, HarnessWebhookEvent.HARNESS_TAG_UPDATED);
    WebhookResponse webhookResponse =
        WebhookResponse.newBuilder()
            .setNativeEvents(NativeEvents.newBuilder()
                                 .setHarness(HarnessWebhookEvents.newBuilder().addAllEvents(allNativeEvents).build())
                                 .build())
            .build();
    boolean result =
        ScmGitWebhookHelper.isIdenticalEvents(webhookResponse, HookEventType.TRIGGER_EVENTS, harnessConnector, null);
    assertThat(result).isEqualTo(true);
  }
}
