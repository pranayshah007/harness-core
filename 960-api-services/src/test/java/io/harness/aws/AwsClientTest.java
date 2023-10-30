/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.beans.EcrImageDetailConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AwsClientTest extends CategoryTest {
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private AwsCallTracker tracker;
  @InjectMocks private AwsClientImpl mockCFAWSClient;

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetListIAMRoles() throws Exception {
    AmazonIdentityManagementClient client = mock(AmazonIdentityManagementClient.class);
    AwsClientImpl service = spy(new AwsClientImpl());
    doReturn(client).when(service).getAmazonIdentityManagementClient(any());
    try (MockedConstruction<CloseableAmazonWebServiceClient> ignored = mockConstruction(
             CloseableAmazonWebServiceClient.class, (mock, context) -> when(mock.getClient()).thenReturn(client))) {
      ListRolesResult result = new ListRolesResult().withRoles(new Role().withRoleName("test"));
      doReturn(result).when(client).listRoles(any());
      AwsCallTracker mockTracker = mock(AwsCallTracker.class);
      doNothing().when(mockTracker).trackCFCall(anyString());
      on(service).set("tracker", mockTracker);
      Map<String, String> map = service.listIAMRoles(any());
      assertThat(map.size()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkServiceException() throws Exception {
    AwsClientImpl service = spy(mockCFAWSClient);
    doThrow(AmazonServiceException.class).when(service).getAmazonIdentityManagementClient(any());
    service.listIAMRoles(any());
    verify(awsApiHelperService, times(1)).handleAmazonServiceException(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkClientException() throws Exception {
    AwsClientImpl service = spy(mockCFAWSClient);
    doThrow(AmazonClientException.class).when(service).getAmazonIdentityManagementClient(any());
    service.listIAMRoles(any());
    verify(awsApiHelperService, times(1)).handleAmazonClientException(any());
  }

  @Test(expected = Exception.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGeneralException() throws Exception {
    AwsClientImpl service = spy(mockCFAWSClient);
    doThrow(Exception.class).when(service).getAmazonIdentityManagementClient(any());
    service.listIAMRoles(any());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testListEcrImageTags() {
    String registryId = "registryId";
    String region = "region";
    String imageName = "imageName";
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    int pageSize = 2;
    DescribeImagesResult describeImagesResult = new DescribeImagesResult();
    ImageDetail imageDetail = new ImageDetail();
    imageDetail.setImageTags(Arrays.asList("0.1", "0.2"));
    imageDetail.setRegistryId(registryId);
    imageDetail.setRepositoryName(imageName);
    describeImagesResult.setImageDetails(Collections.singletonList(imageDetail));
    describeImagesResult.setNextToken("nextToken");
    when(awsApiHelperService.describeEcrImages(any(), anyString(), any())).thenReturn(describeImagesResult);
    EcrImageDetailConfig ecrImageDetailConfig =
        mockCFAWSClient.listEcrImageTags(awsConfig, registryId, region, imageName, pageSize, null);
    assertThat(ecrImageDetailConfig).isNotNull();
    assertThat(ecrImageDetailConfig.getImageDetails().size()).isEqualTo(1);
    assertThat(ecrImageDetailConfig.getImageDetails().get(0)).isEqualTo(imageDetail);
    assertThat(ecrImageDetailConfig.getNextToken()).isNotNull();
    assertThat(ecrImageDetailConfig.getNextToken()).isEqualTo("nextToken");
    verify(tracker, times(1)).trackECRCall(eq("List Images"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetImageTagsThrowsException() {
    String registryId = "registryId";
    String region = "region";
    String imageName = "imageName";
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    int pageSize = 10;
    when(awsApiHelperService.describeEcrImages(any(), anyString(), any())).thenThrow(new RuntimeException());
    assertThatThrownBy(() -> mockCFAWSClient.listEcrImageTags(awsConfig, registryId, region, imageName, pageSize, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Please input a valid AWS Connector and corresponding region. Check if permissions are scoped for the authenticated user");
  }
}
