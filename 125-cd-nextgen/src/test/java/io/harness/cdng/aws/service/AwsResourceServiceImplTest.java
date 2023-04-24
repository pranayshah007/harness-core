/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.cdng.common.resources.GitResourceServiceHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsIAMRolesResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListLoadBalancersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListVpcTaskResponse;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.AwsCFException;
import io.harness.exception.AwsIAMRolesException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsEC2Instance;
import software.wings.service.impl.aws.model.AwsVPC;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsResourceServiceImplTest extends CategoryTest {
  @Mock private AwsResourceServiceHelper serviceHelper;
  @Mock private GitResourceServiceHelper gitHelper;

  @InjectMocks private AwsResourceServiceImpl service;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetRolesArn() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    Map<String, String> rolesMap = new HashMap<>();
    rolesMap.put("role1", "arn:aws:iam::123456789012:role/role1");
    rolesMap.put("role2", "arn:aws:iam::123456789012:role/role2");
    AwsIAMRolesResponse mockAwsIAMRolesResponse =
        AwsIAMRolesResponse.builder().roles(rolesMap).commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    doReturn(mockAwsIAMRolesResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();
    service.getRolesARNs(mockIdentifierRef, "foo", "bar", "us-east-1");
    assertThat(mockAwsIAMRolesResponse.getRoles().size()).isEqualTo(2);
  }

  @Test(expected = AwsIAMRolesException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetRolesArnWithErrorNotifiedResponse() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    Map<String, String> rolesMap = new HashMap<>();
    rolesMap.put("role1", "arn:aws:iam::123456789012:role/role1");
    rolesMap.put("role2", "arn:aws:iam::123456789012:role/role2");
    ErrorNotifyResponseData mockAwsIAMRolesResponse = ErrorNotifyResponseData.builder().build();
    doReturn(mockAwsIAMRolesResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();
    service.getRolesARNs(mockIdentifierRef, "foo", "bar", "us-east-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAwsCFParameterKeys() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());
    GitStoreDelegateConfig mockGitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(mockGitStoreDelegateConfig)
        .when(gitHelper)
        .getGitStoreDelegateConfig(any(), any(), any(), any(), any(), any(), anyString());
    List<AwsCFTemplateParamsData> response = new LinkedList<>();
    response.add(AwsCFTemplateParamsData.builder().paramKey("param1").paramType("value1").build());
    response.add(AwsCFTemplateParamsData.builder().paramKey("param2").paramType("value2").build());

    AwsCFTaskResponse mockAwsCFTaskResponse = AwsCFTaskResponse.builder()
                                                  .listOfParams(response)
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .build();
    doReturn(mockAwsCFTaskResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    service.getCFparametersKeys(
        "s3", "bar", true, "far", null, "cat", "baz", mockIdentifierRef, "quux", "corge", "abc", "efg", "hij");
    assertThat(mockAwsCFTaskResponse.getListOfParams().size()).isEqualTo(2);
  }

  @Test(expected = AwsCFException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAwsCFParameterKeysWithErrorNotifiedResponse() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());
    GitStoreDelegateConfig mockGitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(mockGitStoreDelegateConfig)
        .when(gitHelper)
        .getGitStoreDelegateConfig(any(), any(), any(), any(), any(), any(), anyString());
    List<AwsCFTemplateParamsData> response = new LinkedList<>();
    response.add(AwsCFTemplateParamsData.builder().paramKey("param1").paramType("value1").build());
    response.add(AwsCFTemplateParamsData.builder().paramKey("param2").paramType("value2").build());

    AwsCFTaskResponse mockAwsCFTaskResponse = AwsCFTaskResponse.builder()
                                                  .listOfParams(response)
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .build();
    doReturn(mockAwsCFTaskResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    service.getCFparametersKeys(
        "s3", "bar", true, "far", null, "zar", "baz", mockIdentifierRef, "quux", "corge", "abc", "efg", "hij");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testAwsFilterHosts() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    List<AwsEC2Instance> instances = Arrays.asList(AwsEC2Instance.builder().build());

    AwsListEC2InstancesTaskResponse mockResponse = AwsListEC2InstancesTaskResponse.builder()
                                                       .instances(instances)
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> vpcIds = Collections.emptyList();
    Map<String, String> tags = Collections.emptyMap();

    List<AwsEC2Instance> result = service.filterHosts(mockIdentifierRef, true, "region", vpcIds, tags, null);
    assertThat(result.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testAwsFilterHostsASG() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    List<AwsEC2Instance> instances = Arrays.asList(AwsEC2Instance.builder().build());

    AwsListEC2InstancesTaskResponse mockResponse = AwsListEC2InstancesTaskResponse.builder()
                                                       .instances(instances)
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<AwsEC2Instance> result =
        service.filterHosts(mockIdentifierRef, true, "region", null, null, "autoScalingGroupName");
    assertThat(result.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetVPCs() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    List<AwsVPC> vpcs = Arrays.asList(AwsVPC.builder().id("id").build());
    AwsListVpcTaskResponse mockResponse =
        AwsListVpcTaskResponse.builder().vpcs(vpcs).commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<AwsVPC> result = service.getVPCs(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get(0).getId().equals("id"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetTags() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListTagsTaskResponse mockResponse = AwsListTagsTaskResponse.builder()
                                               .tags(Collections.singletonMap("tag", "value"))
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    Map<String, String> result = service.getTags(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get("tag").equals("value"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetLoadBalancers() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListLoadBalancersTaskResponse mockResponse = AwsListLoadBalancersTaskResponse.builder()
                                                        .loadBalancers(Arrays.asList("lb1"))
                                                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                        .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> result = service.getLoadBalancers(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get(0).equals("lb1"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetASGNames() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListASGNamesTaskResponse mockResponse = AwsListASGNamesTaskResponse.builder()
                                                   .names(Arrays.asList("asg1"))
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> result = service.getASGNames(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get(0).equals("asg1"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetClusterNames() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListClustersTaskResponse mockResponse = AwsListClustersTaskResponse.builder()
                                                   .clusters(Arrays.asList("cluster"))
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> result = service.getClusterNames(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get(0).equals("cluster"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetElasticLoadBalancerNames() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListElbTaskResponse mockResponse = AwsListElbTaskResponse.builder()
                                              .loadBalancerNames(Arrays.asList("elb"))
                                              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                              .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> result = service.getElasticLoadBalancerNames(mockIdentifierRef, "org", "project", "region");
    assertThat(result.get(0).equals("elb"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetElasticLoadBalancerListenersArn() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListElbListenersTaskResponse mockResponse = AwsListElbListenersTaskResponse.builder()
                                                       .listenerArnMap(Collections.singletonMap("tag", "value"))
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    Map<String, String> result = service.getElasticLoadBalancerListenersArn(
        mockIdentifierRef, "org", "project", "region", "elasticLoadBalancer");
    assertThat(result.get("tag").equals("value"));
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getCapabilities() {
    List<String> capabilities = service.getCapabilities();
    assertThat(capabilities).size().isEqualTo(3);
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getCFStates() {
    Set<String> states = service.getCFStates();
    assertThat(states).size().isEqualTo(23);
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getRegions() {
    Map<String, String> regions = service.getRegions();
    assertThat(regions).size().isEqualTo(19);
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAwsCFParameterKeysThowExceptions() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());
    GitStoreDelegateConfig mockGitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(mockGitStoreDelegateConfig)
        .when(gitHelper)
        .getGitStoreDelegateConfig(any(), any(), any(), any(), any(), any(), anyString());
    List<AwsCFTemplateParamsData> response = new LinkedList<>();
    response.add(AwsCFTemplateParamsData.builder().paramKey("param1").paramType("value1").build());
    response.add(AwsCFTemplateParamsData.builder().paramKey("param2").paramType("value2").build());

    AwsCFTaskResponse mockAwsCFTaskResponse = AwsCFTaskResponse.builder()
                                                  .listOfParams(response)
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .build();
    doReturn(mockAwsCFTaskResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    assertThatThrownBy(()
                           -> service.getCFparametersKeys("s3", "bar", true, "far", null, "cat", "baz",
                               mockIdentifierRef, "", "corge", "abc", "efg", "hij"))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.getCFparametersKeys("git", "bar", true, "far", null, "cat", "baz",
                               mockIdentifierRef, "", "", "abc", "efg", "hij"))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.getCFparametersKeys(
                               "", "", true, "", null, "", "", mockIdentifierRef, "", "", "abc", "efg", "hij"))
        .isInstanceOf(InvalidRequestException.class);
    AwsCFTaskResponse mockAwsCFTaskResponseFailed = AwsCFTaskResponse.builder()
                                                        .listOfParams(response)
                                                        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                        .build();
    doReturn(mockAwsCFTaskResponseFailed).when(serviceHelper).getResponseData(any(), any(), anyString());
    assertThatThrownBy(()
                           -> service.getCFparametersKeys("s3", "bar", true, "far", null, "cat", "baz",
                               mockIdentifierRef, "data", "corge", "abc", "efg", "hij"))
        .isInstanceOf(AwsCFException.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetEKSClusterNames() {
    AwsConnectorDTO awsMockConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(awsMockConnectorDTO).when(serviceHelper).getAwsConnector(any());
    BaseNGAccess mockAccess = BaseNGAccess.builder().build();
    doReturn(mockAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(serviceHelper).getAwsEncryptionDetails(any(), any());
    ConnectorInfoDTO mockConnectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(mockConnectorInfoDTO).when(gitHelper).getConnectorInfoDTO(any(), any());

    AwsListClustersTaskResponse mockResponse = AwsListClustersTaskResponse.builder()
                                                   .clusters(Arrays.asList("cluster"))
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(mockResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    List<String> result = service.getEKSClusterNames(mockIdentifierRef, "org", "project");
    assertThat(result.get(0).equals("cluster"));
  }
}
