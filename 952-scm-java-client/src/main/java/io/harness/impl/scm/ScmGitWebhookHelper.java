/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.git.GitClientHelper;
import io.harness.product.ci.scm.proto.AzureWebhookEvent;
import io.harness.product.ci.scm.proto.AzureWebhookEvents;
import io.harness.product.ci.scm.proto.BitbucketCloudWebhookEvent;
import io.harness.product.ci.scm.proto.BitbucketCloudWebhookEvents;
import io.harness.product.ci.scm.proto.BitbucketServerWebhookEvent;
import io.harness.product.ci.scm.proto.BitbucketServerWebhookEvents;
import io.harness.product.ci.scm.proto.CreateWebhookRequest;
import io.harness.product.ci.scm.proto.GithubWebhookEvent;
import io.harness.product.ci.scm.proto.GithubWebhookEvents;
import io.harness.product.ci.scm.proto.GitlabWebhookEvent;
import io.harness.product.ci.scm.proto.GitlabWebhookEvents;
import io.harness.product.ci.scm.proto.HarnessWebhookEvent;
import io.harness.product.ci.scm.proto.HarnessWebhookEvents;
import io.harness.product.ci.scm.proto.NativeEvents;
import io.harness.product.ci.scm.proto.WebhookResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.NotImplementedException;

@UtilityClass
@OwnedBy(HarnessTeam.DX)
public class ScmGitWebhookHelper {
  public static boolean isIdenticalEvents(WebhookResponse webhookResponse, HookEventType hookEventType,
      ScmConnector scmConnector, List<NativeEvents> allNativeEventsList) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return compareEvents(
          webhookResponse.getNativeEvents().getGithub().getEventsList(), hookEventType.githubWebhookEvents);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return compareEvents(
          webhookResponse.getNativeEvents().getGitlab().getEventsList(), hookEventType.gitlabWebhookEvents);
    } else if (scmConnector instanceof BitbucketConnectorDTO
        && GitClientHelper.isBitBucketSAAS(scmConnector.getUrl())) {
      return compareEvents(webhookResponse.getNativeEvents().getBitbucketCloud().getEventsList(),
          hookEventType.bitbucketCloudWebhookEvents);
    } else if (scmConnector instanceof BitbucketConnectorDTO
        && !GitClientHelper.isBitBucketSAAS(scmConnector.getUrl())) {
      return compareEvents(webhookResponse.getNativeEvents().getBitbucketServer().getEventsList(),
          hookEventType.bitbucketServerWebhookEvents);
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      // create list of events
      List<AzureWebhookEvent> azureWebhookEvents = new ArrayList<>();
      allNativeEventsList.forEach(nativeEvents -> azureWebhookEvents.addAll(nativeEvents.getAzure().getEventsList()));
      return compareEvents(azureWebhookEvents, hookEventType.azureWebhookEvents);
    } else if (scmConnector instanceof HarnessConnectorDTO) {
      return compareEvents(
          webhookResponse.getNativeEvents().getHarness().getEventsList(), hookEventType.harnessScmWebhookEvents);
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  private static <T> boolean compareEvents(List<T> webhookResponseEventsList, List<T> hookEventTypeEventsList) {
    return webhookResponseEventsList.containsAll(hookEventTypeEventsList);
  }

  public static CreateWebhookRequest getCreateWebhookRequest(CreateWebhookRequest.Builder createWebhookRequestBuilder,
      GitWebhookDetails gitWebhookDetails, ScmConnector scmConnector, WebhookResponse existingWebhook,
      List<NativeEvents> existingNativeEventsList) {
    if (scmConnector instanceof GithubConnectorDTO) {
      final List<GithubWebhookEvent> githubWebhookEvents = (existingWebhook != null)
          ? existingWebhook.getNativeEvents().getGithub().getEventsList()
          : Collections.emptyList();
      return createWebhookRequestBuilder
          .setNativeEvents(
              NativeEvents.newBuilder()
                  .setGithub(GithubWebhookEvents.newBuilder()
                                 .addAllEvents(ListUtils.union(
                                     gitWebhookDetails.getHookEventType().githubWebhookEvents, githubWebhookEvents))
                                 .build())
                  .build())
          .build();
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      final List<GitlabWebhookEvent> gitlabWebhookEvents = (existingWebhook != null)
          ? existingWebhook.getNativeEvents().getGitlab().getEventsList()
          : Collections.emptyList();
      return createWebhookRequestBuilder
          .setNativeEvents(
              NativeEvents.newBuilder()
                  .setGitlab(GitlabWebhookEvents.newBuilder()
                                 .addAllEvents(ListUtils.union(
                                     gitWebhookDetails.getHookEventType().gitlabWebhookEvents, gitlabWebhookEvents))
                                 .build())
                  .build())
          .build();
    } else if (scmConnector instanceof BitbucketConnectorDTO
        && GitClientHelper.isBitBucketSAAS(scmConnector.getUrl())) {
      Set<BitbucketCloudWebhookEvent> bitbucketCloudWebhookEvents = new HashSet<>();
      if (existingNativeEventsList != null) {
        existingNativeEventsList.forEach(
            nativeEvents -> bitbucketCloudWebhookEvents.addAll(nativeEvents.getBitbucketCloud().getEventsList()));
      }
      bitbucketCloudWebhookEvents.addAll(gitWebhookDetails.getHookEventType().bitbucketCloudWebhookEvents);
      return createWebhookRequestBuilder.setName("HarnessWebhook")
          .setNativeEvents(
              NativeEvents.newBuilder()
                  .setBitbucketCloud(
                      BitbucketCloudWebhookEvents.newBuilder().addAllEvents(bitbucketCloudWebhookEvents).build())
                  .build())
          .build();
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      Set<AzureWebhookEvent> azureWebhookEvent = new HashSet<>();
      if (existingNativeEventsList != null) {
        existingNativeEventsList.forEach(
            nativeEvents -> azureWebhookEvent.addAll(nativeEvents.getAzure().getEventsList()));
      }
      azureWebhookEvent.addAll(gitWebhookDetails.getHookEventType().azureWebhookEvents);
      return createWebhookRequestBuilder.setName("HarnessWebhook")
          .setNativeEvents(NativeEvents.newBuilder()
                               .setAzure(AzureWebhookEvents.newBuilder().addAllEvents(azureWebhookEvent).build())
                               .build())
          .build();
    } else if (scmConnector instanceof BitbucketConnectorDTO
        && !GitClientHelper.isBitBucketSAAS(scmConnector.getUrl())) {
      Set<BitbucketServerWebhookEvent> bitbucketServerWebhookEvents = new HashSet<>();
      if (existingNativeEventsList != null) {
        existingNativeEventsList.forEach(
            nativeEvents -> bitbucketServerWebhookEvents.addAll(nativeEvents.getBitbucketServer().getEventsList()));
      }
      bitbucketServerWebhookEvents.addAll(gitWebhookDetails.getHookEventType().bitbucketServerWebhookEvents);
      return createWebhookRequestBuilder
          .setNativeEvents(
              NativeEvents.newBuilder()
                  .setBitbucketServer(
                      BitbucketServerWebhookEvents.newBuilder().addAllEvents(bitbucketServerWebhookEvents).build())
                  .build())
          .build();
    } else if (scmConnector instanceof HarnessConnectorDTO) {
      final List<HarnessWebhookEvent> harnessWebhookEvents = (existingWebhook != null)
          ? existingWebhook.getNativeEvents().getHarness().getEventsList()
          : Collections.emptyList();
      return createWebhookRequestBuilder.setName("HarnessWebhook")
          .setNativeEvents(NativeEvents.newBuilder()
                               .setHarness(HarnessWebhookEvents.newBuilder()
                                               .addAllEvents(ListUtils.union(
                                                   gitWebhookDetails.getHookEventType().harnessScmWebhookEvents,
                                                   harnessWebhookEvents))
                                               .build())
                               .build())
          .build();
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }
}
