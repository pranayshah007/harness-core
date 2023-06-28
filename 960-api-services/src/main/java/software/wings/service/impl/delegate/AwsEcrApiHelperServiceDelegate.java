/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.delegate;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.context.MdcGlobalContextData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;

import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.Repository;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AwsEcrApiHelperServiceDelegate extends AwsEcrApiHelperServiceDelegateBase {
  public AmazonECRClient getAmazonEcrClient(AwsInternalConfig awsConfig, String region) {
    AmazonECRClientBuilder builder = AmazonECRClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonECRClient) builder.build();
  }

  private DescribeRepositoriesResult listRepositories(
      AwsInternalConfig awsConfig, DescribeRepositoriesRequest describeRepositoriesRequest, String region) {
    try (CloseableAmazonWebServiceClient<AmazonECRClient> closeableAmazonECRClient =
             new CloseableAmazonWebServiceClient(getAmazonEcrClient(awsConfig, region))) {
      tracker.trackECRCall("List Repositories");
      return closeableAmazonECRClient.getClient().describeRepositories(describeRepositoriesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      ErrorHandlingGlobalContextData globalContextData =
          GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
      if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
        Map<String, String> imageDataMap = new HashMap<>();
        imageDataMap.put(
            ExceptionMetadataKeys.IMAGE_NAME.name(), describeRepositoriesRequest.getRepositoryNames().get(0));
        imageDataMap.put(ExceptionMetadataKeys.REGION.name(), region);
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
        throw amazonServiceException;
      }
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      ErrorHandlingGlobalContextData globalContextData =
          GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
      if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
        throw amazonClientException;
      }
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listRepositories", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new DescribeRepositoriesResult();
  }

  private Repository getRepository(
      AwsInternalConfig awsConfig, String registryId, String region, String repositoryName) {
    DescribeRepositoriesRequest describeRepositoriesRequest = new DescribeRepositoriesRequest();
    if (StringUtils.isNotBlank(registryId)) {
      describeRepositoriesRequest.setRegistryId(registryId);
    }
    describeRepositoriesRequest.setRepositoryNames(Lists.newArrayList(repositoryName));
    DescribeRepositoriesResult describeRepositoriesResult =
        listRepositories(awsConfig, describeRepositoriesRequest, region);
    List<Repository> repositories = describeRepositoriesResult.getRepositories();
    if (isNotEmpty(repositories)) {
      return repositories.get(0);
    }
    return null;
  }

  public String getEcrImageUrl(AwsInternalConfig awsConfig, String registryId, String region, String imageName) {
    Repository repository = getRepository(awsConfig, registryId, region, imageName);
    return repository != null ? repository.getRepositoryUri() : null;
  }

  public String getAmazonEcrAuthToken(AwsInternalConfig awsConfig, String awsAccount, String region) {
    try (CloseableAmazonWebServiceClient<AmazonECRClient> closeableAmazonECRClient =
             new CloseableAmazonWebServiceClient(getAmazonEcrClient(awsConfig, region))) {
      tracker.trackECRCall("Get Ecr Auth Token");
      return closeableAmazonECRClient.getClient()
          .getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(singletonList(awsAccount)))
          .getAuthorizationData()
          .get(0)
          .getAuthorizationToken();
    } catch (Exception e) {
      log.error("Exception getAmazonEcrAuthToken", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }
}
