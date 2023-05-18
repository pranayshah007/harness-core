/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RAJENDRA_BAVISKAR;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.ngtriggers.NgTriggersTestHelper;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.eventmapper.TriggerGitConnectorWrapper;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.GitProviderDataObtainmentManager;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

@OwnedBy(PIPELINE)
public class GitWebhookTriggerRepoFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private GitProviderDataObtainmentManager dataObtainmentManager;
  @Inject @InjectMocks private GitWebhookTriggerRepoFilter filter;
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  private static List<TriggerDetails> triggerDetailsList;
  private static List<TriggerDetails> triggerDetailsList1;
  private static List<ConnectorResponseDTO> connectors;
  private static List<ConnectorResponseDTO> connectors1;
  private static Repository repository1 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo1.git")
                                              .sshURL("git@github.com:owner1/repo1.git")
                                              .link("https://github.com/owner1/repo1/b")
                                              .build();

  private static Repository repository2 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo2.git")
                                              .sshURL("git@github.com:owner1/repo2")
                                              .link("https://github.com/owner1/repo2")
                                              .build();

  private static Repository repository3 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo3.git")
                                              .sshURL("git@github.com:owner1/repo3")
                                              .link("https://github.com/owner1/repo3")
                                              .build();

  private static Repository repository4 = Repository.builder()
                                              .httpURL("https://dev.azure.com/org/test/_git/test")
                                              .sshURL("git@ssh.dev.azure.com:v3/org/test/test")
                                              .link("https://dev.azure.com/org/test/_git/test")
                                              .build();

  private static Repository repository5 = Repository.builder()
                                              .httpURL("https://dev.azure.com/org/test/_git/test")
                                              .sshURL("")
                                              .link("https://dev.azure.com/org/test/_git/test")
                                              .build();

  private static Repository repository6 = Repository.builder()
                                              .httpURL("https://org.visualstudio.com/test/_git/test")
                                              .sshURL("git@ssh.org.visualstudio.com:v3/test/test")
                                              .link("https://org.visualstudio.com/test/_git/test")
                                              .build();

  private static Repository repository7 = Repository.builder()
                                              .httpURL("http://gitlab.gitlab/venkat/sample.git")
                                              .sshURL("git@gitlab.gitlab:venkat/sample.git")
                                              .link("http://gitlab.gitlab/venkat/sample.git")
                                              .build();

  private static Repository repository8 =
      Repository.builder()
          .httpURL("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest.git")
          .sshURL("ssh://git@bitbucket.dev.harness.io:7999/har/deepakgitsynctest.git")
          .link("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest.git")
          .build();

  private static Repository repository9 =
      Repository.builder()
          .httpURL("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest1.git")
          .sshURL("ssh://git@bitbucket.dev.harness.io:7999/har/deepakgitsynctest1.git")
          .link("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest1.git")
          .build();

  private static Repository repository10 =
      Repository.builder()
          .httpURL("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest.git")
          .sshURL("git@bitbucket.dev.harness.io:7999/har/deepakgitsynctest.git")
          .link("https://bitbucket.dev.harness.io/scm/har/deepakgitsynctest.git")
          .build();

  static {
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .build();
    ConnectorResponseDTO connectorResponseDTO1 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.REPO)
                                                .url("https://github.com/owner1/repo1")
                                                .build())
                           .identifier("con1")
                           .build())
            .build();

    TriggerDetails details2 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(
                                WebhookMetadata.builder()
                                    .type("GITHUB")
                                    .git(
                                        GitMetadata.builder().repoName("repo2").connectorIdentifier("org.con1").build())
                                    .build())
                            .build())
                    .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .build();
    ConnectorResponseDTO connectorResponseDTO2 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("https://github.com/owner1")
                                                .build())
                           .orgIdentifier("org")
                           .identifier("con1")
                           .build())
            .build();

    TriggerDetails details3 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(
                                WebhookMetadata.builder()
                                    .type("GITHUB")
                                    .git(GitMetadata.builder().repoName("repo3").connectorIdentifier("con1").build())
                                    .build())
                            .build())
                    .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .build();
    ConnectorResponseDTO connectorResponseDTO3 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("http://github.com/owner1")
                                                .build())
                           .orgIdentifier("org")
                           .projectIdentifier("proj")
                           .identifier("con1")
                           .build())
            .build();
    ConnectorResponseDTO connectorResponseDTO4 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("https://www.github.com/owner1")
                                                .build())
                           .orgIdentifier("org")
                           .identifier("con1")
                           .build())
            .build();
    ConnectorResponseDTO connectorResponseDTO5 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("http://www.github.com/owner1")
                                                .build())
                           .orgIdentifier("org")
                           .projectIdentifier("proj")
                           .identifier("con1")
                           .build())
            .build();

    ConnectorResponseDTO connectorResponseDTO6 =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(BitbucketConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("ssh://git@bitbucket.dev.harness.io:7999")
                                                .build())
                           .orgIdentifier("org")
                           .projectIdentifier("proj")
                           .identifier("con6")
                           .build())
            .build();

    TriggerDetails details4 =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder()
                                 .identifier("details4")
                                 .accountId("acc")
                                 .orgIdentifier("org")
                                 .projectIdentifier("proj")
                                 .metadata(NGTriggerMetadata.builder()
                                               .webhook(WebhookMetadata.builder()
                                                            .type("BITBUCKET")
                                                            .git(GitMetadata.builder()
                                                                     .repoName("har/deepakgitsynctest.git")
                                                                     .connectorIdentifier("con6")
                                                                     .build())
                                                            .build())
                                               .build())
                                 .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.BITBUCKET).build())
                                .build())
                    .build())
            .build();

    ConnectorResponseDTO connectorResponseDTO7 =
        ConnectorResponseDTO.builder()
            .connector(
                ConnectorInfoDTO.builder()
                    .connectorConfig(BitbucketConnectorDTO.builder()
                                         .connectionType(GitConnectionType.REPO)
                                         .url("ssh://git@bitbucket.dev.harness.io:7999/har/deepakgitsynctest.git")
                                         .build())
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .identifier("con7")
                    .build())
            .build();

    TriggerDetails details5 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .identifier("details5")
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("BITBUCKET")
                                               .git(GitMetadata.builder().connectorIdentifier("con7").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.BITBUCKET).build())
                                .build())
                    .build())
            .build();

    triggerDetailsList = asList(details1, details2, details3);
    triggerDetailsList1 = asList(details2, details3, details4, details5);
    connectors = asList(connectorResponseDTO1, connectorResponseDTO2, connectorResponseDTO3);
    connectors1 = asList(connectorResponseDTO4, connectorResponseDTO5, connectorResponseDTO6, connectorResponseDTO7);
  }

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    initMocks(this);
    logger = (Logger) LoggerFactory.getLogger(GitWebhookTriggerRepoFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void applyRepoUrlFilterTest() {
    doReturn(connectors).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .repository(repository1)
                    .build())
            .details(triggerDetailsList)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    List<TriggerDetails> triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList.get(0));

    filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository2).build())
                    .repository(repository2)
                    .build())
            .details(triggerDetailsList)
            .build();
    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList.get(1));

    filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository3).build())
                    .repository(repository3)
                    .build())
            .details(triggerDetailsList)
            .build();
    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList.get(2));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void applyRepoUrlFilterTest2() {
    doReturn(connectors1).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("BITBUCKET").build())
                    .webhookEvent(PushWebhookEvent.builder().repository(repository8).build())
                    .repository(repository8)
                    .build())
            .details(triggerDetailsList1)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    List<TriggerDetails> triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(2);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList1.get(2));
    assertThat(triggerDetails.get(1)).isEqualTo(triggerDetailsList1.get(3));

    filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId("acc")
                                                       .createdAt(10L)
                                                       .sourceRepoType("BITBUCKET")
                                                       .build())
                                    .webhookEvent(PushWebhookEvent.builder().repository(repository9).build())
                                    .repository(repository9)
                                    .build())
            .details(triggerDetailsList1)
            .build();

    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(0);

    filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId("acc")
                                                       .createdAt(10L)
                                                       .sourceRepoType("BITBUCKET")
                                                       .build())
                                    .webhookEvent(PushWebhookEvent.builder().repository(repository10).build())
                                    .repository(repository10)
                                    .build())
            .details(triggerDetailsList1)
            .build();

    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(2);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList1.get(2));
    assertThat(triggerDetails.get(1)).isEqualTo(triggerDetailsList1.get(3));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void applyRepoUrlFilterTest1() {
    doReturn(connectors1).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository2).build())
                    .repository(repository2)
                    .build())
            .details(triggerDetailsList1)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    List<TriggerDetails> triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList1.get(0));

    filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository3).build())
                    .repository(repository3)
                    .build())
            .details(triggerDetailsList1)
            .build();
    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails.get(0)).isEqualTo(triggerDetailsList1.get(1));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void prepareTriggerConnectorWrapperListTest() {
    doReturn(connectors).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());
    List<TriggerGitConnectorWrapper> connectorWrappers =
        filter.prepareTriggerConnectorWrapperList("acc", triggerDetailsList);

    assertThat(connectorWrappers.size()).isEqualTo(3);

    TriggerGitConnectorWrapper connector = getConnectorWrapperFromList(connectorWrappers, "acc/con1");
    assertThat(connector.getTriggers().size()).isEqualTo(1);
    assertThat(connector.getTriggers().get(0)).isEqualTo(triggerDetailsList.get(0));
    assertThat(connector.getConnectorConfigDTO()).isEqualTo(connectors.get(0).getConnector().getConnectorConfig());

    connector = getConnectorWrapperFromList(connectorWrappers, "acc/org/con1");
    assertThat(connector.getTriggers().size()).isEqualTo(1);
    assertThat(connector.getTriggers().get(0)).isEqualTo(triggerDetailsList.get(1));
    assertThat(connector.getConnectorConfigDTO()).isEqualTo(connectors.get(1).getConnector().getConnectorConfig());

    connector = getConnectorWrapperFromList(connectorWrappers, "acc/org/proj/con1");
    assertThat(connector.getTriggers().size()).isEqualTo(1);
    assertThat(connector.getTriggers().get(0)).isEqualTo(triggerDetailsList.get(2));
    assertThat(connector.getConnectorConfigDTO()).isEqualTo(connectors.get(2).getConnector().getConnectorConfig());
  }

  private TriggerGitConnectorWrapper getConnectorWrapperFromList(
      List<TriggerGitConnectorWrapper> connectorWrappers, String fqn) {
    return connectorWrappers.stream()
        .filter(connector -> connector.getConnectorFQN().equals(fqn))
        .findFirst()
        .orElse(null);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldFilterAWSCodecommitTrigger() {
    Repository repository = Repository.builder().id("arn:aws:codecommit:eu-central-1:44864EXAMPLE:test").build();
    List<ConnectorResponseDTO> connectors = asList(NgTriggersTestHelper.getAwsCodeCommitRegionConnectorResponsesDTO(),
        NgTriggersTestHelper.getAwsCodeCommitRepoConnectorResponsesDTO(),
        NgTriggersTestHelper.getAwsCodeCommitRepoConnectorResponsesDTO2());

    doReturn(connectors).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    List<TriggerDetails> triggerDetails = asList(NgTriggersTestHelper.getAwsRepoTriggerDetails(),
        NgTriggersTestHelper.getAwsRegionTriggerDetails(), NgTriggersTestHelper.getAwsRepoTriggerDetails2());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId("acc")
                                                       .orgIdentifier("org")
                                                       .projectIdentifier("proj")
                                                       .sourceRepoType("AWS_CODECOMMIT")
                                                       .build())
                                    .webhookEvent(PRWebhookEvent.builder().repository(repository).build())
                                    .repository(repository)
                                    .build())
            .details(triggerDetails)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(2);
    assertThat(triggerDetails)
        .containsOnly(
            NgTriggersTestHelper.getAwsRepoTriggerDetails(), NgTriggersTestHelper.getAwsRegionTriggerDetails());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldFilterAzureRepoTriggerForHTTPConnector() {
    List<ConnectorResponseDTO> connectors = asList(NgTriggersTestHelper.getAzureRepoProjectConnectorResponsesDTO());

    doReturn(connectors).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    List<TriggerDetails> triggerDetails = asList(NgTriggersTestHelper.getAzureRepoTriggerDetails());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId("acc")
                                                       .orgIdentifier("org")
                                                       .projectIdentifier("proj")
                                                       .sourceRepoType("AZURE_REPO")
                                                       .build())
                                    .webhookEvent(PRWebhookEvent.builder().repository(repository4).build())
                                    .repository(repository4)
                                    .build())
            .details(triggerDetails)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails).containsOnly(NgTriggersTestHelper.getAzureRepoTriggerDetails());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldFilterAzureRepoTriggerForSSHConnector() {
    List<ConnectorResponseDTO> connectors = asList(NgTriggersTestHelper.getAzureRepoSSHProjectConnectorResponsesDTO());

    doReturn(connectors).when(ngTriggerService).fetchConnectorsByFQN(eq("acc"), anyList());

    List<TriggerDetails> triggerDetails = asList(NgTriggersTestHelper.getAzureRepoTriggerDetails());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .originalEvent(TriggerWebhookEvent.builder()
                                                       .accountId("acc")
                                                       .orgIdentifier("org")
                                                       .projectIdentifier("proj")
                                                       .sourceRepoType("AZURE_REPO")
                                                       .build())
                                    .webhookEvent(PRWebhookEvent.builder().repository(repository5).build())
                                    .repository(repository5)
                                    .build())
            .details(triggerDetails)
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(1);
    assertThat(triggerDetails).containsOnly(NgTriggersTestHelper.getAzureRepoTriggerDetails());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetUrls() {
    HashSet<String> urls = filter.getUrls(repository1, "Github");
    assertThat(urls).containsExactlyInAnyOrder("https://github.com/owner1/repo1.git", "https://github.com/owner1/repo1",
        "git@github.com:owner1/repo1.git", "git@github.com:owner1/repo1", "https://github.com/owner1/repo1/b");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetUrlsForHttp() {
    HashSet<String> urls = filter.getUrls(repository7, "Gitlab");
    assertThat(urls).containsExactlyInAnyOrder("https://gitlab.gitlab/venkat/sample.git",
        "https://gitlab.gitlab/venkat/sample", "git@gitlab.gitlab:venkat/sample.git", "git@gitlab.gitlab:venkat/sample",
        "http://gitlab.gitlab/venkat/sample.git");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetUrlsForAzure() {
    HashSet<String> urls = filter.getUrls(repository4, "AZURE_REPO");
    assertThat(urls).containsExactlyInAnyOrder(
        "https://dev.azure.com/org/test/_git/test", "git@ssh.dev.azure.com:v3/org/test/test");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGetUrlsForOldAzure() {
    HashSet<String> urls = filter.getUrls(repository6, "AZURE_REPO");
    assertThat(urls).containsExactlyInAnyOrder(
        "https://dev.azure.com/org/test/_git/test", "git@ssh.dev.azure.com:v3/org/test/test");
  }
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetUrlsForAzureWithEmptySSHUrl() {
    HashSet<String> urls = filter.getUrls(repository5, "AZURE_REPO");
    assertThat(urls).containsExactlyInAnyOrder(
        "https://dev.azure.com/org/test/_git/test", "git@ssh.dev.azure.com:v3/org/test/test");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGenerateConnectorFQNFromTriggerConfig() {
    Map<String, List<TriggerDetails>> triggerToConnectorMap = null;

    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(
                                WebhookMetadata.builder().type("GITHUB").git(GitMetadata.builder().build()).build())
                            .build())
                    .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.WEBHOOK)
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .build();

    filter.generateConnectorFQNFromTriggerConfig(triggerDetails, triggerToConnectorMap);
    ILoggingEvent log = listAppender.list.get(0);
    assertThat(log).isNotNull();
    assertThat(log.getFormattedMessage())
        .isEqualTo(
            "TRIGGER_ERROR_LOG: Exception while evaluating Trigger: acc:org:proj:null:null, Filter: GitWebhookTriggerRepoFilter, Skipping this one.");
    assertThat(log.getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  @Owner(developers = RAJENDRA_BAVISKAR)
  @Category(UnitTests.class)
  public void testSanitizeUrl() {
    // GitLab
    String sanitizedUrlGitLab1 = filter.sanitizeUrl("ssh://gitlab.com/username/repo");
    assertThat(sanitizedUrlGitLab1).isEqualTo("git@gitlab.com:username/repo");

    String sanitizedUrlGitLab2 = filter.sanitizeUrl("ssh://git@gitlab.com/username/repo");
    assertThat(sanitizedUrlGitLab2).isEqualTo("git@gitlab.com:username/repo");

    String sanitizedUrlGitLab3 = filter.sanitizeUrl("git@gitlab.com:username");
    assertThat(sanitizedUrlGitLab3).isEqualTo("git@gitlab.com:username");

    String sanitizedUrlGitLab4 = filter.sanitizeUrl("http://gitlab.com/username/repo");
    assertThat(sanitizedUrlGitLab4).isEqualTo("https://gitlab.com/username/repo");

    String sanitizedUrlGitLab5 = filter.sanitizeUrl("http://www.gitlab.com/username/repo");
    assertThat(sanitizedUrlGitLab5).isEqualTo("https://gitlab.com/username/repo");

    // Github
    String sanitizedUrlGithub1 = filter.sanitizeUrl("ssh://github.com/username/repo");
    assertThat(sanitizedUrlGithub1).isEqualTo("git@github.com:username/repo");

    String sanitizedUrlGithub2 = filter.sanitizeUrl("ssh://git@github.com/username/repo");
    assertThat(sanitizedUrlGithub2).isEqualTo("git@github.com:username/repo");

    String sanitizedUrlGithub3 = filter.sanitizeUrl("git@github.com:username");
    assertThat(sanitizedUrlGithub3).isEqualTo("git@github.com:username");

    String sanitizedUrlGithub4 = filter.sanitizeUrl("http://github.com/username/repo");
    assertThat(sanitizedUrlGithub4).isEqualTo("https://github.com/username/repo");

    String sanitizedUrlGithub5 = filter.sanitizeUrl("http://www.github.com/username/repo");
    assertThat(sanitizedUrlGithub5).isEqualTo("https://github.com/username/repo");

    // Bitbucket
    String sanitizedUrlBitbucket1 = filter.sanitizeUrl("ssh://bitbucket.org/username/repo");
    assertThat(sanitizedUrlBitbucket1).isEqualTo("git@bitbucket.org:username/repo");

    String sanitizedUrlBitbucket2 = filter.sanitizeUrl("ssh://bitbucket.org/username/repo");
    assertThat(sanitizedUrlBitbucket2).isEqualTo("git@bitbucket.org:username/repo");

    String sanitizedUrlBitbucket3 = filter.sanitizeUrl("git@bitbucket.org:username");
    assertThat(sanitizedUrlBitbucket3).isEqualTo("git@bitbucket.org:username");

    String sanitizedUrlBitbucket4 = filter.sanitizeUrl("http://bitbucket.org/username/repo");
    assertThat(sanitizedUrlBitbucket4).isEqualTo("https://bitbucket.org/username/repo");

    String sanitizedUrlBitbucket6 = filter.sanitizeUrl("ssh://git@bitbucket.dev.harness.io:7999/username/");
    assertThat(sanitizedUrlBitbucket6).isEqualTo("git@bitbucket.dev.harness.io:7999/username");

    String sanitizedUrlBitbucket5 = filter.sanitizeUrl("http://www.bitbucket.org/username/repo");
    assertThat(sanitizedUrlBitbucket5).isEqualTo("https://bitbucket.org/username/repo");

    // Azure
    String sanitizedUrlAzure1 = filter.sanitizeUrl("ssh://dev.azure.com/username/repo");
    assertThat(sanitizedUrlAzure1).isEqualTo("git@dev.azure.com:username/repo");

    String sanitizedUrlAzure2 = filter.sanitizeUrl("ssh://dev.azure.com/username/repo");
    assertThat(sanitizedUrlAzure2).isEqualTo("git@dev.azure.com:username/repo");

    String sanitizedUrlAzure3 = filter.sanitizeUrl("git@dev.azure.com:username");
    assertThat(sanitizedUrlAzure3).isEqualTo("git@dev.azure.com:username");

    String sanitizedUrlAzure4 = filter.sanitizeUrl("http://dev.azure.com/username/repo");
    assertThat(sanitizedUrlAzure4).isEqualTo("https://dev.azure.com/username/repo");

    String sanitizedUrlAzure5 = filter.sanitizeUrl("http://www.dev.azure.com/username/repo");
    assertThat(sanitizedUrlAzure5).isEqualTo("https://dev.azure.com/username/repo");
  }
}
