/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsResponse;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegateBase;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Vpc;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsEc2HelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Mock private AmazonEC2Client mockClient;
  @Mock private AwsEcrApiHelperServiceDelegateBase awsEcrApiHelperServiceDelegateBase;

  @Spy @InjectMocks private AwsEc2HelperServiceDelegateImpl awsEc2HelperServiceDelegate;
  public static final String REGION = "us-east-1";
  private static final String NAME = "Name";

  @Before
  public void setup() {
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialTrue() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeRegionsResult()).when(mockClient).describeRegions();
    doNothing().when(mockTracker).trackEC2Call(anyString());
    AwsEc2ValidateCredentialsResponse validateCredentialsResponse =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(AwsConfig.builder().build(), emptyList());
    assertThat(validateCredentialsResponse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialFalseIncorrectCredentials() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    AwsConfig awsConfig = new AwsConfig("ACCESS_KEY".toCharArray(), new char[] {'s', 'e', 'c', 'r', 'e', 't'}, "", "",
        false, "", null, false, false, null, null, null, false, null);
    AmazonEC2Exception exception = new AmazonEC2Exception("Invalid Aws Credentials");
    exception.setStatusCode(401);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    when(mockClient.describeRegions()).thenThrow(exception);
    doNothing().when(mockTracker).trackEC2Call(anyString());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).attachCredentialsAndBackoffPolicy(any(), any());
    AwsEc2ValidateCredentialsResponse validateCredentialsResponse =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(awsConfig, emptyList());
    assertThat(validateCredentialsResponse.isValid()).isFalse();
    assertThat(validateCredentialsResponse.getErrorMessage()).isEqualTo(null);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialFalseAccessKeyEmpty() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    AwsConfig awsConfig = new AwsConfig("".toCharArray(), new char[] {'s', 'e', 'c', 'r', 'e', 't'}, "", "", false, "",
        null, false, false, null, null, null, false, null);
    AmazonEC2Exception exception = new AmazonEC2Exception("Invalid Aws Credentials");
    exception.setStatusCode(401);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    when(mockClient.describeRegions()).thenThrow(exception);
    doNothing().when(mockTracker).trackEC2Call(anyString());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).attachCredentialsAndBackoffPolicy(any(), any());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    AwsEc2ValidateCredentialsResponse validateCredentialsResponse =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(awsConfig, emptyList());
    assertThat(validateCredentialsResponse.isValid()).isFalse();
    assertThat(validateCredentialsResponse.getErrorMessage()).isEqualTo("Access Key should not be empty");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialFalseSecretKeyEmpty() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    AwsConfig awsConfig = new AwsConfig(
        "ACCESS_KEY".toCharArray(), null, "", "", false, "", null, false, false, null, null, null, false, null);
    AmazonEC2Exception exception = new AmazonEC2Exception("Invalid Aws Credentials");
    exception.setStatusCode(401);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    when(mockClient.describeRegions()).thenThrow(exception);
    doNothing().when(mockTracker).trackEC2Call(anyString());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).attachCredentialsAndBackoffPolicy(any(), any());
    AwsEc2ValidateCredentialsResponse validateCredentialsResponse =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(awsConfig, emptyList());
    assertThat(validateCredentialsResponse.isValid()).isFalse();
    assertThat(validateCredentialsResponse.getErrorMessage()).isEqualTo("Secret Key should not be empty");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialIamOrIrsaFail() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    AmazonEC2Exception exception = new AmazonEC2Exception("Invalid Aws Credentials");
    exception.setStatusCode(401);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doThrow(exception).when(mockTracker).trackEC2Call(anyString());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).attachCredentialsAndBackoffPolicy(any(), any());

    AwsConfig awsConfigIam = new AwsConfig(
        "ACCESS_KEY".toCharArray(), null, "", "", true, "", null, false, false, null, null, null, false, null);
    AwsEc2ValidateCredentialsResponse validateCredentialsResponseIam =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(awsConfigIam, emptyList());
    assertThat(validateCredentialsResponseIam.isValid()).isFalse();
    assertThat(validateCredentialsResponseIam.getErrorMessage()).isNull();

    AwsConfig awsConfigIrsa = new AwsConfig(
        "ACCESS_KEY".toCharArray(), null, "", "", false, "", null, true, false, null, null, null, false, null);
    AwsEc2ValidateCredentialsResponse validateCredentialsResponseIrsa =
        awsEc2HelperServiceDelegate.validateAwsAccountCredential(awsConfigIrsa, emptyList());
    assertThat(validateCredentialsResponseIrsa.isValid()).isFalse();
    assertThat(validateCredentialsResponseIrsa.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListRegions() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeRegionsResult().withRegions(
                 new Region().withRegionName("us-east-1"), new Region().withRegionName("us-east-2")))
        .when(mockClient)
        .describeRegions();
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<String> regions = awsEc2HelperServiceDelegate.listRegions(AwsConfig.builder().build(), emptyList());
    assertThat(regions).isNotNull();
    assertThat(regions.size()).isEqualTo(2);
    assertThat(regions.get(0)).isEqualTo("us-east-1");
    assertThat(regions.get(1)).isEqualTo("us-east-2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListVPCs() {
    String vpcId1 = "vpcId1";
    String vpcName1 = "vpcName1";
    String vpcId2 = "vpcId2";
    String vpcName2 = "vpcName2";
    String vpcId3 = "vpcId3";

    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeVpcsResult().withVpcs(new Vpc().withVpcId(vpcId1).withTags(new Tag(NAME, vpcName1)),
                 new Vpc().withVpcId(vpcId2).withTags(new Tag(NAME, vpcName2)), new Vpc().withVpcId(vpcId3).withTags()))
        .when(mockClient)
        .describeVpcs(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<AwsVPC> vpcs = awsEc2HelperServiceDelegate.listVPCs(AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(vpcs).isNotNull();
    assertThat(vpcs.size()).isEqualTo(3);
    assertThat(vpcs.get(0)).isEqualTo(AwsVPC.builder().id(vpcId1).name(vpcName1).build());
    assertThat(vpcs.get(1)).isEqualTo(AwsVPC.builder().id(vpcId2).name(vpcName2).build());
    assertThat(vpcs.get(2)).isEqualTo(AwsVPC.builder().id(vpcId3).name("").build());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListSubnets() {
    String subnetId1 = "snId1";
    String subnetName1 = "snName1";
    String subnetId2 = "snId2";
    String subnetName2 = "snName2";
    String subnetId3 = "snId3";

    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeSubnetsResult().withSubnets(
                 new Subnet().withSubnetId(subnetId1).withTags(new Tag(NAME, subnetName1)),
                 new Subnet().withSubnetId(subnetId2).withTags(new Tag(NAME, subnetName2)),
                 new Subnet().withSubnetId(subnetId3).withTags()))
        .when(mockClient)
        .describeSubnets(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<AwsSubnet> subnets =
        awsEc2HelperServiceDelegate.listSubnets(AwsConfig.builder().build(), emptyList(), "us-east-1", emptyList());
    assertThat(subnets).isNotNull();
    assertThat(subnets.size()).isEqualTo(3);
    assertThat(subnets.get(0)).isEqualTo(AwsSubnet.builder().id(subnetId1).name(subnetName1).build());
    assertThat(subnets.get(1)).isEqualTo(AwsSubnet.builder().id(subnetId2).name(subnetName2).build());
    assertThat(subnets.get(2)).isEqualTo(AwsSubnet.builder().id(subnetId3).name("").build());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListSGs() {
    String securityGroupId1 = "sgId1";
    String securityGroupName1 = "sgName1";
    String securityGroupId2 = "sgId2";
    String securityGroupName2 = "sgName2";
    String securityGroupId3 = "sgId3";

    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeSecurityGroupsResult().withSecurityGroups(
                 new SecurityGroup().withGroupId(securityGroupId1).withGroupName(securityGroupName1),
                 new SecurityGroup().withGroupId(securityGroupId2).withGroupName(securityGroupName2),
                 new SecurityGroup().withGroupId(securityGroupId3).withGroupName(null)))
        .when(mockClient)
        .describeSecurityGroups(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<AwsSecurityGroup> groups =
        awsEc2HelperServiceDelegate.listSGs(AwsConfig.builder().build(), emptyList(), "us-east-1", emptyList());
    assertThat(groups).isNotNull();
    assertThat(groups.size()).isEqualTo(3);
    assertThat(groups.get(0))
        .isEqualTo(AwsSecurityGroup.builder().id(securityGroupId1).name(securityGroupName1).build());
    assertThat(groups.get(1))
        .isEqualTo(AwsSecurityGroup.builder().id(securityGroupId2).name(securityGroupName2).build());
    assertThat(groups.get(2)).isEqualTo(AwsSecurityGroup.builder().id(securityGroupId3).build());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListTags() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeTagsResult().withTags(new TagDescription().withKey("k1"), new TagDescription().withKey("k2")))
        .when(mockClient)
        .describeTags(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    Set<String> tags =
        awsEc2HelperServiceDelegate.listTags(AwsConfig.builder().build(), emptyList(), "us-east-1", "instance");
    assertThat(tags).isNotNull();
    assertThat(tags.size()).isEqualTo(2);
    assertThat(tags.contains("k1")).isTrue();
    assertThat(tags.contains("k2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListEc2Instances_1() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeInstancesResult().withReservations(new Reservation().withInstances(
                 new Instance().withInstanceId("id1"), new Instance().withInstanceId("id2"))))
        .when(mockClient)
        .describeInstances(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<Instance> instances = awsEc2HelperServiceDelegate.listEc2Instances(
        AwsConfig.builder().build(), emptyList(), "us-east-1", emptyList(), false);
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(2);
    assertThat(instances.get(0).getInstanceId()).isEqualTo("id1");
    assertThat(instances.get(1).getInstanceId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListEc2Instances_2() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeInstancesResult().withReservations(new Reservation().withInstances(
                 new Instance().withInstanceId("id1"), new Instance().withInstanceId("id2"))))
        .when(mockClient)
        .describeInstances(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    List<Instance> instances = awsEc2HelperServiceDelegate.listEc2Instances(
        AwsConfig.builder().build(), emptyList(), singletonList("id1"), "us-east-1", false);
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(2);
    assertThat(instances.get(0).getInstanceId()).isEqualTo("id1");
    assertThat(instances.get(1).getInstanceId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListBlockDeviceNamesOfAmi() {
    AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
    doReturn(mockClient).when(awsEc2HelperServiceDelegate).getAmazonEc2Client(anyString(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeImagesResult().withImages(
                 new Image()
                     .withBlockDeviceMappings(new BlockDeviceMapping().withDeviceName("name0"),
                         new BlockDeviceMapping().withDeviceName("name1"))
                     .withImageId("ami")))
        .when(mockClient)
        .describeImages(any());
    doNothing().when(mockTracker).trackEC2Call(anyString());
    Set<String> names =
        awsEc2HelperServiceDelegate.listBlockDeviceNamesOfAmi(AwsConfig.builder().build(), emptyList(), REGION, "ami");
    assertThat(names).isNotNull();
    assertThat(names.size()).isEqualTo(2);
    assertThat(names.contains("name0")).isTrue();
    assertThat(names.contains("name1")).isTrue();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getLaunchTemplateVersion() {
    final LaunchTemplateVersion launchTemplateVersion1 = new LaunchTemplateVersion();
    doReturn(new DescribeLaunchTemplateVersionsResult().withLaunchTemplateVersions(launchTemplateVersion1))
        .when(mockClient)
        .describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class));
    final LaunchTemplateVersion launchTemplateVersion = awsEc2HelperServiceDelegate.getLaunchTemplateVersion(
        AwsConfig.builder().build(), emptyList(), REGION, "ltid", "3");
    assertThat(launchTemplateVersion).isEqualTo(launchTemplateVersion1);
    verify(mockClient, times(1)).describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class));
    doThrow(new AmazonClientException("exception"))
        .when(mockClient)
        .describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class));
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    assertThatThrownBy(()
                           -> awsEc2HelperServiceDelegate.getLaunchTemplateVersion(
                               AwsConfig.builder().build(), emptyList(), REGION, "ltid", "3"))
        .isInstanceOf(WingsException.class);

    doThrow(new AmazonServiceException("exception"))
        .when(mockClient)
        .describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class));
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    //    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).attachCredentialsAndBackoffPolicy(any(),any());
    assertThatThrownBy(()
                           -> awsEc2HelperServiceDelegate.getLaunchTemplateVersion(
                               AwsConfig.builder().build(), emptyList(), REGION, "ltid", "3"))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createLaunchTemplateVersion() {
    final CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest =
        new CreateLaunchTemplateVersionRequest();
    doReturn(new CreateLaunchTemplateVersionResult())
        .when(mockClient)
        .createLaunchTemplateVersion(createLaunchTemplateVersionRequest);

    final CreateLaunchTemplateVersionResult launchTemplateVersionResult =
        awsEc2HelperServiceDelegate.createLaunchTemplateVersion(
            createLaunchTemplateVersionRequest, AwsConfig.builder().build(), emptyList(), REGION);
    verify(mockClient, times(1)).createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
    doThrow(new AmazonServiceException("exception"))
        .when(mockClient)
        .createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    assertThatThrownBy(()
                           -> awsEc2HelperServiceDelegate.createLaunchTemplateVersion(
                               createLaunchTemplateVersionRequest, AwsConfig.builder().build(), emptyList(), REGION))
        .isInstanceOf(WingsException.class);

    doThrow(new AmazonClientException("exception"))
        .when(mockClient)
        .createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
    assertThatThrownBy(()
                           -> awsEc2HelperServiceDelegate.createLaunchTemplateVersion(
                               createLaunchTemplateVersionRequest, AwsConfig.builder().build(), emptyList(), REGION))
        .isInstanceOf(WingsException.class);
  }
}
