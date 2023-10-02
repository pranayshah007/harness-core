/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.aws.AwsEksListClustersResponseDTO.ListClustersCommandStatus.FAILURE;
import static io.harness.delegate.task.aws.AwsEksListClustersResponseDTO.ListClustersCommandStatus.SUCCESS;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsEKSException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AbortedException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.model.ListClustersRequest;
import com.amazonaws.services.eks.model.ListClustersResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsEKSDelegateTaskHelper {
  private static final String LIST_CLUSTERS_FAILURE_MESSAGE_FORMAT = "Failed to list clusters for regions [%s].";
  private static final String LIST_CLUSTERS_REGION_FAILURE_MESSAGE_FORMAT = "%nErrors: %n%s";
  private static final String LIST_CLUSTERS_INTERRUPTED_MESSAGE =
      "AWS EKS list clusters request for region [%s] was aborted.";
  private static final String LIST_CLUSTERS_GENERIC_ERROR_MESSAGE =
      "Exception in listing AWS EKS clusters using AWS ListClustersRequest for region : %s";
  @Inject private AwsUtils awsUtils;
  public DelegateResponseData getEKSClustersList(AwsTaskParams awsTaskParams) throws AwsEKSException {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();

    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(awsConnectorDTO, AWS_DEFAULT_REGION);
    List<String> awsRegions = getEligibleAwsRegions(awsTaskParams, awsInternalConfig);
    List<AwsEksListClustersResponseDTO> listClusterResponses =
        awsRegions.stream()
            .map(region -> listEKSClustersForGivenRegion(region, awsInternalConfig))
            .collect(Collectors.toList());

    long listClustersFailureCount =
        listClusterResponses.stream().filter(response -> FAILURE == response.getStatus()).count();
    if (listClustersFailureCount > 0 && awsRegions.size() == listClustersFailureCount) {
      constructErrorMessageAndThrowException(listClusterResponses);
    }

    List<String> clusters = listClusterResponses.stream()
                                .filter(response -> SUCCESS == response.getStatus())
                                .flatMap(response -> response.getClusters().stream())
                                .collect(Collectors.toList());
    return AwsListClustersTaskResponse.builder()
        .clusters(clusters)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private List<String> getEligibleAwsRegions(AwsTaskParams awsTaskParams, AwsInternalConfig awsInternalConfig) {
    if (isNotEmpty(awsTaskParams.getRegion())) {
      // User has provided AWS region, don't use all regions.
      return List.of(awsTaskParams.getRegion());
    }

    return awsUtils.listAwsRegionsForGivenAccount(awsInternalConfig);
  }

  private void constructErrorMessageAndThrowException(List<AwsEksListClustersResponseDTO> listClusterResponses) {
    String errorMessage = constructUserFriendlyErrorMessage(listClusterResponses);
    log.error(errorMessage);
    throw new AwsEKSException(errorMessage);
  }

  private String constructUserFriendlyErrorMessage(List<AwsEksListClustersResponseDTO> listClusterResponses) {
    Map<String, String> regionToErrorMessageMap =
        listClusterResponses.stream()
            .filter(response -> FAILURE == response.getStatus())
            .collect(Collectors.toMap(
                AwsEksListClustersResponseDTO::getRegion, AwsEksListClustersResponseDTO::getErrorMessage));
    String regionToErrorMessage = regionToErrorMessageMap.entrySet()
                                      .stream()
                                      .map(e -> format("[%s]: %s", e.getKey(), e.getValue()))
                                      .collect(Collectors.joining("\n"));
    String regionList =
        listClusterResponses.stream().map(AwsEksListClustersResponseDTO::getRegion).collect(Collectors.joining(", "));

    return format(LIST_CLUSTERS_FAILURE_MESSAGE_FORMAT, regionList)
        + format(LIST_CLUSTERS_REGION_FAILURE_MESSAGE_FORMAT, regionToErrorMessage);
  }

  private AwsEksListClustersResponseDTO listEKSClustersForGivenRegion(
      String region, AwsInternalConfig awsInternalConfig) {
    try (
        CloseableAmazonWebServiceClient<AmazonEKSClient> closeableAmazonEKSClient = new CloseableAmazonWebServiceClient(
            awsUtils.getAmazonEKSClient(Regions.fromName(region), awsInternalConfig))) {
      List<String> clusterList = new ArrayList<>();
      String nextToken = null;
      ListClustersRequest listClustersRequest = new ListClustersRequest();
      do {
        listClustersRequest.withNextToken(nextToken);
        ListClustersResult listClustersResult = closeableAmazonEKSClient.getClient().listClusters(listClustersRequest);
        nextToken = extractClusterNamesFromResponse(region, clusterList, nextToken, listClustersResult);
      } while (nextToken != null);
      return AwsEksListClustersResponseDTO.builder().region(region).clusters(clusterList).status(SUCCESS).build();
    } catch (AbortedException e) {
      String errorMessage = format(LIST_CLUSTERS_INTERRUPTED_MESSAGE, region);
      log.warn(errorMessage, e);
      throw new AwsEKSException(errorMessage, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      String errorMessage = format(LIST_CLUSTERS_INTERRUPTED_MESSAGE, region);
      log.warn(errorMessage, e);
      throw new AwsEKSException(errorMessage, e);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = format(LIST_CLUSTERS_GENERIC_ERROR_MESSAGE, region);
      log.warn(errorMessage, sanitizedException);

      return AwsEksListClustersResponseDTO.builder()
          .region(region)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .status(FAILURE)
          .build();
    }
  }

  private String extractClusterNamesFromResponse(
      String region, List<String> clusterList, String nextToken, ListClustersResult listClustersResult) {
    if (listClustersResult != null) {
      List<String> clusterNames = listClustersResult.getClusters();
      if (isNotEmpty(clusterNames)) {
        clusterNames.forEach(clusterName -> clusterList.add(format("%s/%s", region, clusterName)));
      }
      nextToken = listClustersResult.getNextToken();
    }
    return nextToken;
  }
}
