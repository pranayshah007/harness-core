package io.harness.releaseradar.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.CommitDetailsRequest;
import io.harness.releaseradar.beans.EnvDeploymentStatus;
import io.harness.releaseradar.beans.EventFilter;
import io.harness.releaseradar.beans.EventNotifyData;
import io.harness.releaseradar.beans.Service;
import io.harness.releaseradar.entities.CommitDetailsMetadata;
import io.harness.releaseradar.entities.EventEntity;
import io.harness.releaseradar.entities.UserSubscription;
import io.harness.releaseradar.helper.GitHelper;
import io.harness.releaseradar.util.SlackWebhookEncryptionUtil;
import io.harness.repositories.CommitDetailsRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@Singleton
@Slf4j
public class EventProcessor {
  @Inject private UserSubscriptionService userSubscriptionService;
  @Inject private SlackNotifier notifier;
  private HarnessEnvService harnessEnvService = new HarnessEnvServiceImpl();
  private GitService gitService = new GitServiceImpl();
  @Inject private CommitDetailsRepository commitDetailsRepository;

  public void process(EventEntity deployEvent) {
      publishCommitDetails(deployEvent);

    List<UserSubscription> allSubscriptions =
        userSubscriptionService.getAllSubscriptions(EventFilter.builder()
                                                        .eventType(deployEvent.getEventType())
                                                        .serviceName(deployEvent.getServiceName())
                                                        .buildVersion(deployEvent.getBuildVersion())
                                                        .release(deployEvent.getRelease())
                                                        .environment(deployEvent.getEnvironment())
                                                        .build());
    if (isNotEmpty(allSubscriptions)) {
      EventNotifyData notifyData = EventNotifyData.builder()
                                       .eventType(deployEvent.getEventType())
                                       .serviceName(deployEvent.getServiceName())
                                       .buildVersion(deployEvent.getBuildVersion())
                                       .release(deployEvent.getRelease())
                                       .environment(deployEvent.getEnvironment())
                                       .build();
      allSubscriptions.stream()
          .filter(userSubscription -> isNotEmpty(userSubscription.getSlackWebhookUrlEncrypted()))
          .forEach(userSubscription -> {
            try {
              notifier.notify(
                  SlackWebhookEncryptionUtil.decrypt(userSubscription.getSlackWebhookUrlEncrypted()), notifyData);
            } catch (Exception e) {
              e.printStackTrace();
            }
          });
    }
  }

  private void publishCommitDetails(EventEntity eventEntity) {
      EnvDeploymentStatus deploymentStatus = harnessEnvService.getDeploymentStatus(Service.getService(eventEntity.getServiceName()), eventEntity.getEnvironment());
      List<CommitDetails> gitCommitDetailsList = gitService.getCommitList(CommitDetailsRequest.builder()
                      .branch(deploymentStatus.getBranch())
                      .maxCommits(1000)
              .build());
      gitCommitDetailsList.forEach(gitCommitDetails -> {
          String jiraId = GitHelper.getJiraId(gitCommitDetails.getMessage());
          if (jiraId != null) {
              commitDetailsRepository.save(io.harness.releaseradar.entities.CommitDetails.builder()
                      .eventId(eventEntity.getId())
                      .sha(gitCommitDetails.getSha())
                      .jiraId(jiraId)
                      .metadata(CommitDetailsMetadata.builder()
                              .sha(gitCommitDetails.getSha())
                              .timestamp(gitCommitDetails.getDate())
                              .build())
                      .build());
          }
      });
  }
}
