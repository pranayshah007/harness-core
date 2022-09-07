package io.harness.delegate.task.aws;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsECSException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsECSDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getEcsClustersList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (
        CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient = new CloseableAmazonWebServiceClient(
            awsUtils.getAmazonECSClient(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      String nextToken = null;
      List<String> result = new ArrayList<>();

      do {
        ListClustersRequest listClustersRequest = new ListClustersRequest().withNextToken(nextToken);
        tracker.trackECSCall("List Ecs Clusters");
        ListClustersResult listClustersResult = closeableAmazonECSClient.getClient().listClusters(listClustersRequest);
        result.addAll(convertToList(listClustersResult));
        nextToken = listClustersResult.getNextToken();
      } while (nextToken != null);

      return AwsListClustersTaskResponse.builder()
          .clusters(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception get aws ecs clusters", sanitizedException);
      throw new AwsECSException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
  }

  private List<String> convertToList(ListClustersResult result) {
    return CollectionUtils.emptyIfNull(result.getClusterArns())
        .stream()
        .map(arn -> arn.substring(arn.lastIndexOf('/') + 1))
        .collect(toList());
  }
}
