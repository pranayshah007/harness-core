/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.model.ListClustersRequest;
import com.amazonaws.services.eks.model.ListClustersResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsEKSDelegateTaskHelper {
  @Inject private AwsUtils awsUtils;
  public DelegateResponseData getEKSClustersList(AwsTaskParams awsTaskParams) throws AwsEKSException {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();

    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(awsConnectorDTO, AWS_DEFAULT_REGION);
    List<String> awsRegions = awsUtils.listAwsRegionsForGivenAccount(awsInternalConfig);
    List<String> clusters = new ArrayList<>();
    awsRegions.forEach(awsRegion -> {
      List<String> eksClusters = listEKSClustersForGivenRegion(awsRegion, awsInternalConfig);
      clusters.addAll(eksClusters);
    });
    return AwsListClustersTaskResponse.builder()
        .clusters(clusters)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private List<String> listEKSClustersForGivenRegion(String region, AwsInternalConfig awsInternalConfig) {
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
      return clusterList;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(
          String.format("Exception in listing AWS EKS clusters using AWS ListClustersRequest for region : %s", region),
          sanitizedException);
      throw new AwsEKSException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
  }

  private String extractClusterNamesFromResponse(
      String region, List<String> clusterList, String nextToken, ListClustersResult listClustersResult) {
    if (listClustersResult != null) {
      List<String> clusterNames = listClustersResult.getClusters();
      if (EmptyPredicate.isNotEmpty(clusterNames)) {
        clusterNames.forEach(clusterName -> { clusterList.add(String.format("%s/%s", region, clusterName)); });
      }
      nextToken = listClustersResult.getNextToken();
    }
    return nextToken;
  }
}
