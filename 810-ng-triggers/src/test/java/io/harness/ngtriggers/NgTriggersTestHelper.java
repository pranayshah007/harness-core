/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CommitDetails;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.WebhookBaseAttributes;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;

import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.AzureRepoSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoTriggerEvent;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class NgTriggersTestHelper {
  public TriggerDetails getAwsRepoTriggerDetails() {
    return TriggerDetails.builder()
        .ngTriggerEntity(
            NGTriggerEntity.builder()
                .accountId("acc")
                .orgIdentifier("org")
                .projectIdentifier("proj")
                .metadata(NGTriggerMetadata.builder()
                              .webhook(WebhookMetadata.builder()
                                           .type("AWS_CODECOMMIT")
                                           .git(GitMetadata.builder().connectorIdentifier("conRepo").build())
                                           .build())
                              .build())
                .build())
        .ngTriggerConfigV2(
            NGTriggerConfigV2.builder()
                .source(NGTriggerSourceV2.builder()
                            .type(NGTriggerType.WEBHOOK)
                            .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.AWS_CODECOMMIT).build())
                            .build())
                .build())
        .build();
  }
  public TriggerDetails getAwsRegionTriggerDetails() {
    return TriggerDetails.builder()
        .ngTriggerEntity(
            NGTriggerEntity.builder()
                .accountId("acc")
                .orgIdentifier("org")
                .projectIdentifier("proj")
                .metadata(
                    NGTriggerMetadata.builder()
                        .webhook(
                            WebhookMetadata.builder()
                                .type("AWS_CODECOMMIT")
                                .git(GitMetadata.builder().repoName("test").connectorIdentifier("conRegion").build())
                                .build())
                        .build())
                .build())
        .ngTriggerConfigV2(
            NGTriggerConfigV2.builder()
                .source(NGTriggerSourceV2.builder()
                            .type(NGTriggerType.WEBHOOK)
                            .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.AWS_CODECOMMIT).build())
                            .build())
                .build())
        .build();
  }

  public TriggerDetails getAwsRepoTriggerDetails2() {
    return TriggerDetails.builder()
        .ngTriggerEntity(
            NGTriggerEntity.builder()
                .accountId("acc")
                .orgIdentifier("org")
                .projectIdentifier("proj")
                .metadata(NGTriggerMetadata.builder()
                              .webhook(WebhookMetadata.builder()
                                           .type("AWS_CODECOMMIT")
                                           .git(GitMetadata.builder().connectorIdentifier("conRepo2").build())
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
  }

  public ConnectorResponseDTO getAwsCodeCommitRepoConnectorResponsesDTO() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(AwsCodeCommitConnectorDTO.builder()
                                            .urlType(AwsCodeCommitUrlType.REPO)
                                            .url("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                                            .build())
                       .orgIdentifier("org")
                       .projectIdentifier("proj")
                       .identifier("conRepo")
                       .build())
        .build();
  }

  public ConnectorResponseDTO getAwsCodeCommitRegionConnectorResponsesDTO() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(AwsCodeCommitConnectorDTO.builder()
                                            .urlType(AwsCodeCommitUrlType.REGION)
                                            .url("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos")
                                            .build())
                       .orgIdentifier("org")
                       .projectIdentifier("proj")
                       .identifier("conRegion")
                       .build())
        .build();
  }

  public ConnectorResponseDTO getAwsCodeCommitRepoConnectorResponsesDTO2() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(AwsCodeCommitConnectorDTO.builder()
                                            .urlType(AwsCodeCommitUrlType.REPO)
                                            .url("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test2")
                                            .build())
                       .orgIdentifier("org")
                       .projectIdentifier("proj")
                       .identifier("conRepo2")
                       .build())
        .build();
  }

  public PushWebhookEvent getAwsCodecommitPushWebhookEvent() {
    return PushWebhookEvent.builder()
        .branchName("main")
        .commitDetailsList(singletonList(
            CommitDetails.builder()
                .commitId("f70e8226cac251f6116315984b6e9ed7098ce586")
                .ownerId("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                .timeStamp(1616318557000L)
                .build()))
        .repository(Repository.builder().id("arn:aws:codecommit:eu-central-1:44864EXAMPLE:test").build())
        .baseAttributes(
            WebhookBaseAttributes.builder()
                .ref("refs/heads/main")
                .target("main")
                .authorLogin("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                .sender("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                .build())
        .build();
  }

  public TriggerWebhookEvent getTriggerWehookEventForAwsCodeCommitPush() {
    return TriggerWebhookEvent.builder()
        .payload("{\n"
            + "  \"Type\" : \"Notification\",\n"
            + "  \"MessageId\" : \"2d8bef2e-9233-543b-90c0-b7ff80aec631\",\n"
            + "  \"TopicArn\" : \"arn:aws:sns:eu-central-1:44864EXAMPLE:aws_cc_push_trigger\",\n"
            + "  \"Subject\" : \"UPDATE: AWS CodeCommit eu-central-1 push: test\",\n"
            + "  \"Message\" : \"{\\\"Records\\\":[{\\\"awsRegion\\\":\\\"eu-central-1\\\",\\\"codecommit\\\":{\\\"references\\\":[{\\\"commit\\\":\\\"f70e8226cac251f6116315984b6e9ed7098ce586\\\",\\\"ref\\\":\\\"refs/heads/main\\\"}]},\\\"customData\\\":null,\\\"eventId\\\":\\\"6363f269-6070-4999-9b55-52c4e40d74b0\\\",\\\"eventName\\\":\\\"ReferenceChanges\\\",\\\"eventPartNumber\\\":1,\\\"eventSource\\\":\\\"aws:codecommit\\\",\\\"eventSourceARN\\\":\\\"arn:aws:codecommit:eu-central-1:44864EXAMPLE:test\\\",\\\"eventTime\\\":\\\"2021-03-21T09:22:37.156+0000\\\",\\\"eventTotalParts\\\":1,\\\"eventTriggerConfigId\\\":\\\"e15f4b05-bec2-47f8-9505-c10df79f4c42\\\",\\\"eventTriggerName\\\":\\\"push\\\",\\\"eventVersion\\\":\\\"1.0\\\",\\\"userIdentityARN\\\":\\\"arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029\\\"}]}\",\n"
            + "  \"Timestamp\" : \"2021-03-21T09:22:37.198Z\",\n"
            + "  \"SignatureVersion\" : \"1\",\n"
            + "  \"Signature\" : \"DnmP7IBvzHPEqm8054phY6aQztpvTktsBNTLqOeX1j48t65vFKxpyrhFvOKDh6vispOFZO+RgJjTvG7vq9LEEMtns56sLoNcZUYtyydRQC3KQdQjzFhPx+M/oZX81WxWc3gIUVNVteIF4izObmi9NUrU1ioiW+D/IBFVVbzW7Yw3TJT8SeF6verQdImDBuQ7izPVEPzz1RsZH/9DN8iQg5F5rMnQwADZzjNLLQ+hpbh0Addy40x89TMzjSYXYLSOS24hxxpnftWP9LTrZVlekwMz+NqFMhX6mzKWXI80mYnRqaCwDdy7ZaEskJvW+9gByl05MH++b6ZJGdIhmgyVMw==\",\n"
            + "  \"SigningCertURL\" : \"https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem\",\n"
            + "  \"UnsubscribeURL\" : \"https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:44864EXAMPLE:aws_cc_push_trigger:05b595dd-06fb-4002-b409-80a7a7bbfda6\"\n"
            + "}")
        .headers(
            asList(HeaderConfig.builder().key("X-Amz-Sns-Message-Type").values(singletonList("Notification")).build(),
                HeaderConfig.builder()
                    .key("X-Amz-Sns-Message-Id")
                    .values(singletonList("2d8bef2e-9233-543b-90c0-b7ff80aec631"))
                    .build(),
                HeaderConfig.builder()
                    .key("X-Amz-Sns-Topic-Arn")
                    .values(singletonList("arn:aws:sns:eu-central-1:44864EXAMPLE:aws_cc_push_trigger"))
                    .build()))
        .accountId("acc")
        .orgIdentifier("org")
        .projectIdentifier("proj")
        .sourceRepoType("AWS_CODECOMMIT")
        .createdAt(1L)
        .build();
  }

  public TriggerDetails getAzureRepoTriggerDetails() {
    return TriggerDetails.builder()
        .ngTriggerEntity(
            NGTriggerEntity.builder()
                .accountId("acc")
                .orgIdentifier("org")
                .projectIdentifier("proj")
                .metadata(
                    NGTriggerMetadata.builder()
                        .webhook(WebhookMetadata.builder()
                                     .type("AZURE_REPO")
                                     .git(GitMetadata.builder().repoName("test").connectorIdentifier("conRepo").build())
                                     .build())
                        .build())
                .build())
        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
             .source(NGTriggerSourceV2.builder()
                         .type(NGTriggerType.WEBHOOK)
                         .spec(WebhookTriggerConfigV2.builder()
                                   .type(WebhookTriggerType.AZURE)
                                   .spec(AzureRepoSpec.builder()
                                             .type(AzureRepoTriggerEvent.PUSH)
                                             .spec(AzureRepoPushSpec.builder().projectName("test").build())
                                             .build())
                                   .build())
                         .build())
             .build())
        .build();
  }

  public ConnectorResponseDTO getAzureRepoAccountConnectorResponsesDTO() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(AzureRepoConnectorDTO.builder()
                                            .connectionType(GitConnectionType.ACCOUNT)
                                            .url("https://dev.azure.com/org/")
                                            .build())
                       .orgIdentifier("org")
                       .projectIdentifier("proj")
                       .identifier("conRepo")
                       .build())
        .build();
  }

  public ConnectorResponseDTO getAzureRepoSSHAccountConnectorResponsesDTO() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(AzureRepoConnectorDTO.builder()
                                            .connectionType(GitConnectionType.ACCOUNT)
                                            .url("git@ssh.dev.azure.com:v3/org/")
                                            .build())
                       .orgIdentifier("org")
                       .projectIdentifier("proj")
                       .identifier("conRepo")
                       .build())
        .build();
  }
}
