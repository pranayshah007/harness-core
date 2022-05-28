/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.AwsCFException;
import io.harness.exception.AwsIAMRolesException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;

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
    service.getRolesARNs(mockIdentifierRef, "foo", "bar");
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
    service.getRolesARNs(mockIdentifierRef, "foo", "bar");
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
        .getGitStoreDelegateConfig(any(), any(), any(), any(), any(), anyString());
    List<AwsCFTemplateParamsData> response = new LinkedList<>();
    response.add(AwsCFTemplateParamsData.builder().paramKey("param1").paramType("value1").build());
    response.add(AwsCFTemplateParamsData.builder().paramKey("param2").paramType("value2").build());

    AwsCFTaskResponse mockAwsCFTaskResponse = AwsCFTaskResponse.builder()
                                                  .listOfParams(response)
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .build();
    doReturn(mockAwsCFTaskResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    service.getCFparametersKeys("s3", "bar", true, "far", "cat", "baz", mockIdentifierRef, "quux", "corge");
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
        .getGitStoreDelegateConfig(any(), any(), any(), any(), any(), anyString());
    List<AwsCFTemplateParamsData> response = new LinkedList<>();
    response.add(AwsCFTemplateParamsData.builder().paramKey("param1").paramType("value1").build());
    response.add(AwsCFTemplateParamsData.builder().paramKey("param2").paramType("value2").build());

    AwsCFTaskResponse mockAwsCFTaskResponse = AwsCFTaskResponse.builder()
                                                  .listOfParams(response)
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .build();
    doReturn(mockAwsCFTaskResponse).when(serviceHelper).getResponseData(any(), any(), anyString());
    IdentifierRef mockIdentifierRef = IdentifierRef.builder().build();

    service.getCFparametersKeys("s3", "bar", true, "far", "cat", "baz", mockIdentifierRef, "quux", "corge");
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
}
