/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCFTemplatesType;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.cdng.common.resources.GitResourceServiceHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsIAMRolesResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenerRulesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenerRulesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListLoadBalancersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListVpcTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.AwsAutoScaleException;
import io.harness.exception.AwsCFException;
import io.harness.exception.AwsECSException;
import io.harness.exception.AwsEKSException;
import io.harness.exception.AwsIAMRolesException;
import io.harness.exception.AwsInstanceException;
import io.harness.exception.AwsLoadBalancerException;
import io.harness.exception.AwsTagException;
import io.harness.exception.AwsVPCException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsEC2Instance;
import software.wings.service.impl.aws.model.AwsVPC;

import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.ec2.model.ResourceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(CDP)
public class AwsResourceServiceImpl implements AwsResourceService {
  private Map<String, String> regionMap;
  private static final String regionNode = "awsRegionIdToName";
  private static final String yamlResourceFilePath = "aws/aws.yaml";
  @Inject private AwsResourceServiceHelper serviceHelper;
  @Inject private GitResourceServiceHelper gitResourceServiceHelper;
  private static final String EKS_GET_CLUSTERS_EXCEPTION_MESSAGE = "Failed to get AWS EKS clusters";

  @Override
  public List<String> getCapabilities() {
    return Stream.of(Capability.values()).map(Capability::toString).collect(Collectors.toList());
  }

  @Override
  public Set<String> getCFStates() {
    return EnumSet.allOf(StackStatus.class).stream().map(Enum::name).collect(Collectors.toSet());
  }

  @Override
  public Map<String, String> getRegions() {
    if (regionMap == null) {
      try {
        ClassLoader classLoader = this.getClass().getClassLoader();
        String parsedYamlFile = Resources.toString(
            Objects.requireNonNull(classLoader.getResource(yamlResourceFilePath)), StandardCharsets.UTF_8);
        getMapRegionFromYaml(parsedYamlFile);
      } catch (IOException e) {
        throw new InvalidArgumentsException("Failed to read the region yaml file:" + e);
      }
    }
    return regionMap;
  }

  private void getMapRegionFromYaml(String parsedYamlFile) {
    try {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      JsonNode node = mapper.readTree(parsedYamlFile);
      JsonNode regions = node.path(regionNode);
      mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
      regionMap = mapper.readValue(String.valueOf(regions), new TypeReference<Map<String, String>>() {});
    } catch (IOException e) {
      throw new InvalidArgumentsException("Failed to Deserialize the region yaml file:" + e);
    }
  }

  @Override
  public Map<String, String> getRolesARNs(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams params = AwsTaskParams.builder()
                               .awsTaskType(AwsTaskType.LIST_IAM_ROLES)
                               .awsConnector(awsConnector)
                               .encryptionDetails(encryptedData)
                               .region(region)
                               .build();
    AwsIAMRolesResponse response = executeSyncTask(params, access);
    return response.getRoles();
  }

  public List<AwsCFTemplateParamsData> getCFparametersKeys(String type, String region, boolean isBranch, String branch,
      String repoName, String templatePath, String commitId, IdentifierRef awsConnectorRef, String dataInput,
      String connectorDTO, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitStoreDelegateConfig gitStoreDelegateConfig = null;
    BaseNGAccess access = serviceHelper.getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);

    if (AwsCFTemplatesType.S3.getValue().equalsIgnoreCase(type)
        || AwsCFTemplatesType.BODY.getValue().equalsIgnoreCase(type)) {
      if (isEmpty(dataInput)) {
        throw new InvalidRequestException("Data is empty");
      }
    } else if (AwsCFTemplatesType.GIT.getValue().equalsIgnoreCase(type)) {
      if (isEmpty(connectorDTO) || (isEmpty(branch) && isEmpty(commitId))) {
        throw new InvalidRequestException("Missing connector ID or branch and commid ID");
      }
      FetchType fetchType = isBranch ? FetchType.BRANCH : FetchType.COMMIT;

      ConnectorInfoDTO connectorInfoDTO = gitResourceServiceHelper.getConnectorInfoDTO(connectorDTO, access);
      gitStoreDelegateConfig = gitResourceServiceHelper.getGitStoreDelegateConfig(
          connectorInfoDTO, access, fetchType, branch, commitId, templatePath, repoName);
    } else {
      throw new InvalidRequestException("Unknown source type");
    }

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    AwsCFTaskParamsRequest params = AwsCFTaskParamsRequest.builder()
                                        .awsConnector(awsConnector)
                                        .awsTaskType(AwsTaskType.CF_LIST_PARAMS)
                                        .data(dataInput)
                                        .fileStoreType(AwsCFTemplatesType.fromValue(type))
                                        .encryptionDetails(encryptedData)
                                        .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                        .region(region)
                                        .accountId(awsConnectorRef.getAccountIdentifier())
                                        .build();

    return executeSyncTask(params, access);
  }

  @Override
  public List<AwsEC2Instance> filterHosts(IdentifierRef awsConnectorRef, boolean winRm, String region,
      List<String> vpcIds, Map<String, String> tags, String autoScalingGroupName) {
    BaseNGAccess access = serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(),
        awsConnectorRef.getOrgIdentifier(), awsConnectorRef.getProjectIdentifier());

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    final AwsTaskParams awsTaskParams;

    if (StringUtils.isEmpty(autoScalingGroupName)) {
      awsTaskParams = AwsListEC2InstancesTaskParamsRequest.builder()
                          .awsConnector(awsConnector)
                          .awsTaskType(AwsTaskType.LIST_EC2_INSTANCES)
                          .encryptionDetails(encryptedData)
                          .region(region)
                          .vpcIds(vpcIds)
                          .tags(tags)
                          .winRm(winRm)
                          .build();
    } else {
      awsTaskParams = AwsListASGInstancesTaskParamsRequest.builder()
                          .awsConnector(awsConnector)
                          .awsTaskType(AwsTaskType.LIST_ASG_INSTANCES)
                          .encryptionDetails(encryptedData)
                          .region(region)
                          .autoScalingGroupName(autoScalingGroupName)
                          .build();
    }

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());

    return getListInstancesTaskExecutionResponse(responseData);
  }

  @Override
  public List<AwsVPC> getVPCs(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_VPC)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());

    return getListVpcTaskExecutionResponse(responseData);
  }

  @Override
  public Map<String, String> getTags(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    AwsListTagsTaskParamsRequest awsTaskParams = AwsListTagsTaskParamsRequest.builder()
                                                     .awsConnector(awsConnector)
                                                     .awsTaskType(AwsTaskType.LIST_TAGS)
                                                     .encryptionDetails(encryptedData)
                                                     .region(region)
                                                     .resourceType(ResourceType.Instance.toString())
                                                     .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());

    return getListTagsTaskExecutionResponse(responseData);
  }

  @Override
  public List<String> getLoadBalancers(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_LOAD_BALANCERS)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());

    return getListLoadBalancersTaskExecutionResponse(responseData);
  }

  @Override
  public List<String> getASGNames(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_ASG_NAMES)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());

    return getASGNamesTaskExecutionResponse(responseData);
  }

  @Override
  public List<String> getClusterNames(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_ECS_CLUSTERS)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getECSClusterNamesTaskExecutionResponse(responseData);
  }

  @Override
  public List<String> getElasticLoadBalancerNames(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier, String region) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCERS)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getListElbTaskResponse(responseData);
  }

  @Override
  public Map<String, String> getElasticLoadBalancerListenersArn(IdentifierRef awsConnectorRef, String orgIdentifier,
      String projectIdentifier, String region, String elasticLoadBalancer) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams awsTaskParams = AwsListElbListenersTaskParamsRequest.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCER_LISTENERS)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .elasticLoadBalancer(elasticLoadBalancer)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getListElbListenersTaskResponse(responseData);
  }

  @Override
  public List<String> getElasticLoadBalancerListenerRules(IdentifierRef awsConnectorRef, String orgIdentifier,
      String projectIdentifier, String region, String elasticLoadBalancer, String listenerArn) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams awsTaskParams = AwsListElbListenerRulesTaskParamsRequest.builder()
                                      .awsConnector(awsConnector)
                                      .awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCER_LISTENER_RULE)
                                      .encryptionDetails(encryptedData)
                                      .region(region)
                                      .elasticLoadBalancer(elasticLoadBalancer)
                                      .listenerArn(listenerArn)
                                      .build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getListElbListenerRulesTaskResponse(responseData);
  }

  private AwsIAMRolesResponse executeSyncTask(AwsTaskParams awsTaskParams, BaseNGAccess baseNGAccess) {
    DelegateResponseData responseData =
        serviceHelper.getResponseData(baseNGAccess, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getTaskExecutionResponse(responseData);
  }

  private List<AwsCFTemplateParamsData> executeSyncTask(
      AwsCFTaskParamsRequest awsTaskParams, BaseNGAccess baseNGAccess) {
    DelegateResponseData responseData =
        serviceHelper.getResponseData(baseNGAccess, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getCFTaskExecutionResponse(responseData);
  }

  private AwsIAMRolesResponse getTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsIAMRolesException("Failed to get IAM roles"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsIAMRolesResponse response = (AwsIAMRolesResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsIAMRolesException("Failed to get IAM roles");
    }
    return response;
  }

  private List<AwsCFTemplateParamsData> getCFTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsCFException("Failed to get CloudFormation template parameters"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsCFTaskResponse response = (AwsCFTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsCFException("Failed to get CloudFormation template parameters");
    }
    return response.getListOfParams();
  }

  private List<AwsEC2Instance> getListInstancesTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsInstanceException("Failed to get aws instances"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListEC2InstancesTaskResponse response = (AwsListEC2InstancesTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsInstanceException("Failed to get aws instances");
    }
    return response.getInstances();
  }

  private List<AwsVPC> getListVpcTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsVPCException("Failed to get aws vpc list"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListVpcTaskResponse response = (AwsListVpcTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsVPCException("Failed to get aws vpc list");
    }
    return response.getVpcs();
  }

  private Map<String, String> getListTagsTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsTagException("Failed to get aws tag list"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListTagsTaskResponse response = (AwsListTagsTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsTagException("Failed to get aws tag list");
    }
    return response.getTags();
  }

  private List<String> getListLoadBalancersTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsLoadBalancerException("Failed to get aws load balancer list"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListLoadBalancersTaskResponse response = (AwsListLoadBalancersTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsLoadBalancerException("Failed to get aws load balancer list");
    }
    return response.getLoadBalancers();
  }

  private List<String> getASGNamesTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsAutoScaleException("Failed to get aws autoscaling groups"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListASGNamesTaskResponse response = (AwsListASGNamesTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsAutoScaleException("Failed to get aws autoscaling groups");
    }
    return response.getNames();
  }

  private List<String> getECSClusterNamesTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsECSException("Failed to get aws ecs clusters"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListClustersTaskResponse response = (AwsListClustersTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsECSException("Failed to get aws ecs clusters");
    }
    return response.getClusters();
  }

  private List<String> getListElbTaskResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsLoadBalancerException("Failed to get aws elastic load balancers"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListElbTaskResponse response = (AwsListElbTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsLoadBalancerException("Failed to get elastic load balancers");
    }
    return response.getLoadBalancerNames();
  }

  private Map<String, String> getListElbListenersTaskResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsLoadBalancerException("Failed to get aws elastic load balancer listeners"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListElbListenersTaskResponse response = (AwsListElbListenersTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsLoadBalancerException("Failed to get aws elastic load balancer listeners");
    }
    return response.getListenerArnMap();
  }

  private List<String> getListElbListenerRulesTaskResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsLoadBalancerException("Failed to get aws elastic load balancer listener rules"
          + " : " + errorNotifyResponseData.getErrorMessage());
    }
    AwsListElbListenerRulesTaskResponse response = (AwsListElbListenerRulesTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsLoadBalancerException("Failed to get aws elastic load balancer listener rules");
    }
    return response.getListenerRulesArn();
  }

  public List<String> getEKSClusterNames(
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier) {
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams awsTaskParams =
        AwsTaskParams.builder().awsConnector(awsConnector).encryptionDetails(encryptedData).build();

    DelegateResponseData responseData =
        serviceHelper.getResponseData(access, awsTaskParams, TaskType.AWS_EKS_LIST_CLUSTERS_TASK.name());
    return getEKSClusterNamesFromResponse(responseData);
  }

  private List<String> getEKSClusterNamesFromResponse(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new AwsEKSException(
          String.format("%s: %s", EKS_GET_CLUSTERS_EXCEPTION_MESSAGE, errorNotifyResponseData.getErrorMessage()));
    }
    AwsListClustersTaskResponse response = (AwsListClustersTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new AwsEKSException(EKS_GET_CLUSTERS_EXCEPTION_MESSAGE);
    }
    return response.getClusters();
  }
}
