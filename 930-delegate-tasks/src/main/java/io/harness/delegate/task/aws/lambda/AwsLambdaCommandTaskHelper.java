/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.eraro.ErrorCode.AWS_ACCESS_DENIED;
import static io.harness.eraro.ErrorCode.AWS_CLUSTER_NOT_FOUND;
import static io.harness.eraro.ErrorCode.AWS_SERVICE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.codedeploy.model.AmazonCodeDeployException;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ecr.model.AmazonECRException;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetAliasRequest;
import com.amazonaws.services.lambda.model.GetAliasResult;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.State;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;

import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import org.apache.logging.log4j.util.Strings;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaCommandTaskHelper {
  @Inject private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;

  public void createFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).createFunction(new CreateFunctionRequest());
  }

  public void deployFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).invoke(new InvokeRequest());
  }

  public void deleteFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).deleteFunction(new DeleteFunctionRequest());
  }

  public AWSLambdaClient getAwsLambdaClient(String region, AwsInternalConfig awsInternalConfig) {
    return awsLambdaHelperServiceDelegateNG.getAmazonLambdaClient(region, awsInternalConfig);
  }

  public GetFunctionResult getFunction(String functionName, String region, AwsInternalConfig awsInternalConfig) {
    GetFunctionResult getFunctionResult = null;
    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient = new CloseableAmazonWebServiceClient(getAwsLambdaClient(region, awsInternalConfig))) {
      try {
        GetFunctionRequest getFunctionRequest = new GetFunctionRequest();
        getFunctionRequest.setFunctionName(functionName);
        getFunctionResult = getAwsLambdaClient(region, awsInternalConfig).getFunction(getFunctionRequest);
      } catch (ResourceNotFoundException rnfe) {
        log.info("No function found with name =[{}]. Error Msg is [{}]", functionName, rnfe.getMessage());
        return null;
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return getFunctionResult;
  }

  public ListAliasesResult listAliasesRequest(String region, AwsInternalConfig awsInternalConfig, GetFunctionResult getFunctionResult) {
    ListAliasesResult listAliasesResult = null;
    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient = new CloseableAmazonWebServiceClient(getAwsLambdaClient(region, awsInternalConfig))) {
        ListAliasesRequest listAliasesRequest = new ListAliasesRequest().withFunctionName(getFunctionResult.getConfiguration().getFunctionName());
        if (Strings.isNotEmpty(getFunctionResult.getConfiguration().getVersion())) {
          listAliasesRequest.withFunctionVersion(getFunctionResult.getConfiguration().getVersion());
        }
        listAliasesResult = getAwsLambdaClient(region, awsInternalConfig).listAliases(listAliasesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return listAliasesResult;
  }

  public ListVersionsByFunctionResult listVersionsByFunction(String functionName, String region, AwsInternalConfig awsInternalConfig) {
    ListVersionsByFunctionResult listVersionsByFunctionResult = null;
    try (CloseableAmazonWebServiceClient<AWSLambdaClient> closeableAWSLambdaClient = new CloseableAmazonWebServiceClient(getAwsLambdaClient(region, awsInternalConfig))) {
      try {
        ListVersionsByFunctionRequest listVersionsByFunctionRequest = new ListVersionsByFunctionRequest();
        listVersionsByFunctionRequest.setFunctionName(functionName);
        listVersionsByFunctionResult = getAwsLambdaClient(region, awsInternalConfig).listVersionsByFunction(listVersionsByFunctionRequest);
      } catch (ResourceNotFoundException rnfe) {
        log.info("No function found with name =[{}]. Error Msg is [{}]", functionName, rnfe.getMessage());
        return null;
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return listVersionsByFunctionResult;
  }

  public AwsLambdaFunctionWithActiveVersions getAwsLambdaFunctionWithActiveVersions(String region, AwsInternalConfig awsInternalConfig, String functionName) {
    GetFunctionResult getFunctionResult = getFunction(functionName, region, awsInternalConfig);
    if(getFunctionResult == null) {
      return null;
    }
    ListVersionsByFunctionResult listVersionsByFunctionResult = listVersionsByFunction(functionName, region, awsInternalConfig);
    if(listVersionsByFunctionResult == null || listVersionsByFunctionResult.getVersions() == null) {
      return null;
    }
    List<String> activeVersions = new ArrayList<>();
      for(FunctionConfiguration functionConfiguration : listVersionsByFunctionResult.getVersions()) {
        if(State.Active.equals(State.fromValue(functionConfiguration.getState()))) {
          activeVersions.add(functionConfiguration.getVersion());
        }
      }
    ListAliasesResult listAliasesResult = listAliasesRequest(region, awsInternalConfig, getFunctionResult);
    return AwsLambdaFunctionWithActiveVersions.from(getFunctionResult, listAliasesResult, activeVersions);
  }

  public void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    log.error("AWS API call exception: {}", amazonServiceException.getMessage());
    if (amazonServiceException instanceof AmazonCodeDeployException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof AmazonEC2Exception) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof ClusterNotFoundException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_CLUSTER_NOT_FOUND, USER);
    } else if (amazonServiceException instanceof ServiceNotFoundException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_SERVICE_NOT_FOUND, USER);
    } else if (amazonServiceException instanceof AmazonAutoScalingException) {
      if (amazonServiceException.getMessage().contains(
              "Trying to remove Target Groups that are not part of the group")) {
        log.info("Target Group already not attached: [{}]", amazonServiceException.getMessage());
      } else if (amazonServiceException.getMessage().contains(
              "Trying to remove Load Balancers that are not part of the group")) {
        log.info("Classic load balancer already not attached: [{}]", amazonServiceException.getMessage());
      } else {
        log.warn(amazonServiceException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
    } else if (amazonServiceException instanceof AmazonECSException
            || amazonServiceException instanceof AmazonECRException) {
      if (amazonServiceException instanceof ClientException) {
        log.warn(amazonServiceException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof AmazonCloudFormationException) {
      if (amazonServiceException.getMessage().contains("No updates are to be performed")) {
        log.info("Nothing to update on stack" + amazonServiceException.getMessage());
      } else {
        throw new InvalidRequestException(amazonServiceException.getMessage(), amazonServiceException, USER);
      }
    } else {
      throw new InvalidRequestException(amazonServiceException.getMessage(), amazonServiceException, USER);
    }
  }

  void handleAmazonClientException(AmazonClientException amazonClientException) {
    handleAmazonClientException(amazonClientException);
  }
}
