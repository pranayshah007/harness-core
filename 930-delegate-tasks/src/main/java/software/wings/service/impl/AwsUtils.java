/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.AwsEKSException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsInstanceFilter;
import software.wings.common.InfrastructureConstants;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.AmazonEKSClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class AwsUtils {
  @Inject private ManagerExpressionEvaluator expressionEvaluator;
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private SecretDecryptionService secretDecryptionService;

  public String getHostnameFromPrivateDnsName(String dnsName) {
    return isNotEmpty(dnsName) ? dnsName.split("\\.")[0] : "";
  }

  public String getHostnameFromConvention(Map<String, Object> context, String hostNameConvention) {
    if (isEmpty(hostNameConvention)) {
      hostNameConvention = InfrastructureConstants.DEFAULT_AWS_HOST_NAME_CONVENTION;
    }
    return expressionEvaluator.substitute(hostNameConvention, context);
  }

  @NotNull
  public List<Filter> getFilters(DeploymentType deploymentType, AwsInstanceFilter instanceFilter) {
    List<Filter> filters = new ArrayList<>();
    filters.add(new Filter("instance-state-name").withValues("running"));
    if (instanceFilter != null) {
      if (isNotEmpty(instanceFilter.getVpcIds())) {
        filters.add(new Filter("vpc-id", instanceFilter.getVpcIds()));
      }
      if (isNotEmpty(instanceFilter.getTags())) {
        Multimap<String, String> tags = ArrayListMultimap.create();
        instanceFilter.getTags().forEach(tag -> {
          if (!(ExpressionEvaluator.containsVariablePattern((String) tag.getKey())
                  || ExpressionEvaluator.containsVariablePattern((String) tag.getValue()))) {
            tags.put(tag.getKey(), tag.getValue());
          }
        });
        tags.keySet().forEach(key -> filters.add(new Filter("tag:" + key, new ArrayList<>(tags.get(key)))));
      }
      if (DeploymentType.WINRM == deploymentType) {
        filters.add(new Filter("platform", asList("windows")));
      }
    }
    return filters;
  }

  public AwsInternalConfig getAwsInternalConfig(AwsConnectorDTO awsConnectorDTO, String region) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(region);
    return awsInternalConfig;
  }

  public void decryptRequestDTOs(AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
    }
  }

  public AmazonEC2Client getAmazonEc2Client(Regions region, AwsInternalConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonEC2Client) builder.build();
  }

  public AmazonElasticLoadBalancingClient getAmazonElbClient(Regions region, AwsInternalConfig awsConfig) {
    AmazonElasticLoadBalancingClientBuilder builder =
        AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonElasticLoadBalancingClient) builder.build();
  }

  public AmazonAutoScalingClient getAmazonAutoScalingClient(Regions region, AwsInternalConfig awsConfig) {
    AmazonAutoScalingClientBuilder builder = AmazonAutoScalingClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonAutoScalingClient) builder.build();
  }

  public AmazonECSClient getAmazonECSClient(Regions region, AwsInternalConfig awsConfig) {
    AmazonECSClientBuilder builder = AmazonECSClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonECSClient) builder.build();
  }

  public AmazonEKSClient getAmazonEKSClient(Regions region, AwsInternalConfig awsConfig) {
    AmazonEKSClientBuilder builder = AmazonEKSClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonEKSClient) builder.build();
  }

  public List<String> listAwsRegionsForGivenAccount(AwsInternalConfig awsInternalConfig) {
    try (
        CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEc2Client = new CloseableAmazonWebServiceClient(
            getAmazonEc2Client(Regions.fromName(AWS_DEFAULT_REGION), awsInternalConfig))) {
      return closeableAmazonEc2Client.getClient()
          .describeRegions()
          .getRegions()
          .stream()
          .map(Region::getRegionName)
          .collect(toList());
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in getting list of regions for given AWS account", sanitizedException);
      throw new AwsEKSException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
  }
}
