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
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.AwsCFException;
import io.harness.exception.AwsIAMRolesException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;

import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.StackStatus;
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

@Singleton
@OwnedBy(CDP)
public class AwsResourceServiceImpl implements AwsResourceService {
  private Map<String, String> regionMap;
  private static final String regionNode = "awsRegionIdToName";
  private static final String yamlResourceFilePath = "aws/aws.yaml";
  @Inject private AwsResourceServiceHelper serviceHelper;
  @Inject private GitResourceServiceHelper gitResourceServiceHelper;

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
      IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier) {
    AwsConnectorDTO awsConnector = serviceHelper.getAwsConnector(awsConnectorRef);
    BaseNGAccess access =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptedData = serviceHelper.getAwsEncryptionDetails(awsConnector, access);
    AwsTaskParams params = AwsTaskParams.builder()
                               .awsTaskType(AwsTaskType.LIST_IAM_ROLES)
                               .awsConnector(awsConnector)
                               .encryptionDetails(encryptedData)
                               .build();
    AwsIAMRolesResponse response = executeSyncTask(params, access);
    return response.getRoles();
  }

  public List<AwsCFTemplateParamsData> getCFparametersKeys(String type, String region, boolean isBranch, String branch,
      String templatePath, String commitId, IdentifierRef awsConnectorRef, String dataInput, String connectorDTO) {
    GitStoreDelegateConfig gitStoreDelegateConfig = null;
    BaseNGAccess access = serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(),
        awsConnectorRef.getOrgIdentifier(), awsConnectorRef.getProjectIdentifier());

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
          connectorInfoDTO, access, fetchType, branch, commitId, templatePath);
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
}
