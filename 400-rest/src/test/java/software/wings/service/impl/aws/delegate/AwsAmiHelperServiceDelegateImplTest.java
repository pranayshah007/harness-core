/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.BG_BLUE;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.BG_GREEN;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.BG_VERSION;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.HARNESS_AUTOSCALING_GROUP_TAG;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.NAME_TAG;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_DESIRED_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MAX_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiResizeData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse.AwsAmiServiceSetupResponseBuilder;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.InstanceMonitoring;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.LaunchTemplate;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsAmiHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private ExecutorService mockExecutorService;
  @Mock private DelegateLogService delegateLogService;
  @Mock private AwsAsgHelperServiceDelegate mockAwsAsgHelperServiceDelegate;
  @Mock private AwsElbHelperServiceDelegate mockAwsElbHelperServiceDelegate;
  @Mock private AwsEc2HelperServiceDelegate mockAwsEc2HelperServiceDelegate;
  @Mock private EncryptionService mockEncryptionService;
  @Mock ExecutionLogCallback mockCallback;

  @InjectMocks @Spy private AwsAmiHelperServiceDelegateImpl awsAmiHelperServiceDelegate;
  public static final String REGION = "us-east-1";
  public static final String USER_DATA_NON_EMPTY = "// echo \"test\"";
  public static final String USER_DATA_EMPTY = "";

  @Before
  public void setup() {
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
  }
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSwitchAmiRoutes() {
    String newAsgName = "Old_Asg";
    String oldAsgName = "New_Asg";
    AwsAmiSwitchRoutesRequest request = AwsAmiSwitchRoutesRequest.builder()
                                            .awsConfig(AwsConfig.builder().build())
                                            .encryptionDetails(emptyList())
                                            .region("us-east-1")
                                            .primaryClassicLBs(singletonList("primaryClassicLB"))
                                            .primaryTargetGroupARNs(singletonList("primaryTargetGpArn"))
                                            .stageClassicLBs(singletonList("stageClassicLB"))
                                            .stageTargetGroupARNs(singletonList("stageTargetGroupArn"))
                                            .registrationTimeout(10)
                                            .oldAsgName(oldAsgName)
                                            .newAsgName(newAsgName)
                                            .downscaleOldAsg(true)
                                            .build();
    AwsAmiSwitchRoutesResponse response = awsAmiHelperServiceDelegate.switchAmiRoutes(request, mockCallback);
    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .addUpdateTagAutoScalingGroup(
            any(), anyList(), eq(oldAsgName), anyString(), eq(BG_VERSION), eq(BG_GREEN), any());
    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .addUpdateTagAutoScalingGroup(
            any(), anyList(), eq(newAsgName), anyString(), eq(BG_VERSION), eq(BG_BLUE), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .registerAsgWithTargetGroups(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsElbHelperServiceDelegate)
        .waitForAsgInstancesToRegisterWithTargetGroup(
            any(), anyList(), anyString(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .registerAsgWithClassicLBs(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsElbHelperServiceDelegate)
        .waitForAsgInstancesToRegisterWithClassicLB(
            any(), anyList(), anyString(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate, times(2))
        .deRegisterAsgWithTargetGroups(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsAsgHelperServiceDelegate, times(2))
        .deRegisterAsgWithClassicLBs(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .setAutoScalingGroupLimits(any(), anyList(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
            any(), anyList(), anyString(), anyString(), anyInt(), any(), anyInt(), anyBoolean());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRollbackSwitchAmiRoutes() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    String newAsgName = "Old_Asg";
    String oldAsgName = "New_Asg";
    AwsAmiPreDeploymentData preDeploymentData =
        AwsAmiPreDeploymentData.builder().oldAsgName("Old_Asg").desiredCapacity(1).minCapacity(0).build();

    AwsAmiSwitchRoutesRequest request = AwsAmiSwitchRoutesRequest.builder()
                                            .awsConfig(AwsConfig.builder().build())
                                            .encryptionDetails(emptyList())
                                            .region("us-east-1")
                                            .primaryClassicLBs(singletonList("primaryClassicLB"))
                                            .primaryTargetGroupARNs(singletonList("primaryTargetGpArn"))
                                            .stageClassicLBs(singletonList("stageClassicLB"))
                                            .stageTargetGroupARNs(singletonList("stageTargetGroupArn"))
                                            .registrationTimeout(10)
                                            .oldAsgName(oldAsgName)
                                            .newAsgName(newAsgName)
                                            .downscaleOldAsg(true)
                                            .preDeploymentData(preDeploymentData)
                                            .build();
    AwsAmiSwitchRoutesResponse response = awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutes(request, mockCallback);
    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .addUpdateTagAutoScalingGroup(
            any(), anyList(), eq(newAsgName), anyString(), eq(BG_VERSION), eq(BG_GREEN), any());
    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .addUpdateTagAutoScalingGroup(
            any(), anyList(), eq(oldAsgName), anyString(), eq(BG_VERSION), eq(BG_BLUE), any());
    verify(mockAwsAsgHelperServiceDelegate, times(2))
        .setAutoScalingGroupLimits(any(), anyList(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate, times(2))
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
            any(), anyList(), anyString(), anyString(), anyInt(), any(), anyInt(), anyBoolean());
    verify(mockAwsAsgHelperServiceDelegate)
        .setMinInstancesForAsg(any(), anyList(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .registerAsgWithTargetGroups(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsElbHelperServiceDelegate)
        .waitForAsgInstancesToRegisterWithTargetGroup(
            any(), anyList(), anyString(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .registerAsgWithClassicLBs(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsElbHelperServiceDelegate)
        .waitForAsgInstancesToRegisterWithClassicLB(
            any(), anyList(), anyString(), anyString(), anyString(), anyInt(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .deRegisterAsgWithTargetGroups(any(), anyList(), anyString(), anyString(), anyList(), any());
    verify(mockAwsAsgHelperServiceDelegate)
        .deRegisterAsgWithClassicLBs(any(), anyList(), anyString(), anyString(), anyList(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testResizeAsgs() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing()
        .when(mockAwsAsgHelperServiceDelegate)
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
            any(), anyList(), anyString(), anyString(), anyInt(), any(), anyInt(), anyBoolean());
    awsAmiHelperServiceDelegate.resizeAsgs("us-east-1", AwsConfig.builder().build(), emptyList(), "newName", 2,
        singletonList(AwsAmiResizeData.builder().asgName("oldName").desiredCount(0).build()), mockCallback, true, 10, 2,
        0, AwsAmiPreDeploymentData.builder().build(), emptyList(), emptyList(), false, emptyList(), 1, false,
        emptyList(), false);
    verify(mockAwsAsgHelperServiceDelegate, times(2))
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
            any(), anyList(), anyString(), anyString(), anyInt(), any(), anyInt(), anyBoolean());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testCreateNewAutoScalingGroupRequest_LT() {
    AutoScalingGroup baseAutoScalingGroup = getAutoScalingGroup();
    CreateAutoScalingGroupRequest request = awsAmiHelperServiceDelegate.createNewAutoScalingGroupRequest("id",
        asList("lb1", "lb2"), asList("a1", "a2"), "newName", baseAutoScalingGroup, 2, 10,
        new LaunchTemplateVersion().withLaunchTemplateId("ltid").withLaunchTemplateName("ltname").withVersionNumber(3L),
        false);
    validateCreateAutoScalingGroupRequest(request, 4);
    assertThat(request.getLaunchConfigurationName()).isNull();
    assertThat(request.getLaunchTemplate().getVersion()).isEqualTo("3");
    assertThat(request.getLaunchTemplate().getLaunchTemplateId()).isEqualTo("ltid");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testCreateNewAutoScalingGroupRequestWithNonBGDeployment() {
    AutoScalingGroup baseAutoScalingGroup = getAutoScalingGroup();
    CreateAutoScalingGroupRequest request = awsAmiHelperServiceDelegate.createNewAutoScalingGroupRequest(
        "id", asList("lb1", "lb2"), asList("a1", "a2"), "newName", baseAutoScalingGroup, 2, 10, null, false);
    validateCreateAutoScalingGroupRequest(request, 4);
    assertThat(request.getLaunchConfigurationName()).isEqualTo("newName");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testCreateNewAutoScalingGroupRequestWithBGDeployment() {
    AutoScalingGroup baseAutoScalingGroup = getAutoScalingGroup();
    CreateAutoScalingGroupRequest request = awsAmiHelperServiceDelegate.createNewAutoScalingGroupRequest(
        "id", asList("lb1", "lb2"), asList("a1", "a2"), "newName", baseAutoScalingGroup, 2, 10, null, true);
    validateCreateAutoScalingGroupRequest(request, 5);
    assertThat(checkIfContainsTag(request.getTags(), BG_VERSION, BG_GREEN)).isTrue();
    assertThat(request.getLaunchConfigurationName()).isEqualTo("newName");
  }

  private boolean checkIfContainsTag(List<Tag> tags, String key, String value) {
    List<Tag> filteredTags =
        tags.stream()
            .filter(tag -> tag.getKey().equalsIgnoreCase(key) && tag.getValue().equalsIgnoreCase(value))
            .collect(Collectors.toList());
    return EmptyPredicate.isNotEmpty(filteredTags);
  }

  private void validateCreateAutoScalingGroupRequest(CreateAutoScalingGroupRequest request, int numTagsExpected) {
    assertThat(request).isNotNull();
    assertThat(request.getTags().size()).isEqualTo(numTagsExpected);
    assertThat(request.getAutoScalingGroupName()).isEqualTo("newName");
    assertThat(request.getDesiredCapacity()).isEqualTo(0);
    assertThat(request.getMinSize()).isEqualTo(0);
    assertThat(request.getMaxSize()).isEqualTo(10);
    assertThat(request.getDefaultCooldown()).isEqualTo(12);
    assertThat(request.getAvailabilityZones()).isEqualTo(asList("z1", "z2"));
    assertThat(request.getTerminationPolicies()).isEqualTo(asList("p1", "p2"));
    assertThat(request.getNewInstancesProtectedFromScaleIn()).isEqualTo(true);
    assertThat(request.getLoadBalancerNames()).isEqualTo(asList("lb1", "lb2"));
    assertThat(request.getTargetGroupARNs()).isEqualTo(asList("a1", "a2"));
    assertThat(request.getHealthCheckType()).isEqualTo("hcType");
    assertThat(request.getHealthCheckGracePeriod()).isEqualTo(13);
    assertThat(request.getPlacementGroup()).isEqualTo("pGroup");
    assertThat(request.getVPCZoneIdentifier()).isEqualTo("vpcI");
  }

  private AutoScalingGroup getAutoScalingGroup() {
    return new AutoScalingGroup()
        .withTags(new TagDescription().withKey("k1").withValue("v1"),
            new TagDescription().withKey("k2").withValue("v2"),
            new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("id__1"),
            new TagDescription().withKey(NAME_TAG).withValue("oldName"))
        .withMaxSize(10)
        .withDefaultCooldown(11)
        .withAvailabilityZones("z1", "z2")
        .withTerminationPolicies("p1", "p2")
        .withNewInstancesProtectedFromScaleIn(true)
        .withDefaultCooldown(12)
        .withHealthCheckType("hcType")
        .withHealthCheckGracePeriod(13)
        .withPlacementGroup("pGroup")
        .withVPCZoneIdentifier("vpcI");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetBlockDeviceMappings() {
    LaunchConfiguration baseLC = new LaunchConfiguration().withBlockDeviceMappings(
        new BlockDeviceMapping().withDeviceName("name0"), new BlockDeviceMapping().withDeviceName("name1"));
    doReturn(ImmutableSet.of("name1"))
        .when(mockAwsEc2HelperServiceDelegate)
        .listBlockDeviceNamesOfAmi(any(), any(), any(), any());
    List<BlockDeviceMapping> result = awsAmiHelperServiceDelegate.getBlockDeviceMappings(
        AwsConfig.builder().build(), emptyList(), "us-east-1", baseLC);
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getDeviceName()).isEqualTo("name0");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateNewLaunchConfigurationRequest() {
    LaunchConfiguration cloneBaseLaunchConfiguration =
        new LaunchConfiguration()
            .withSecurityGroups("sg1", "sg2")
            .withClassicLinkVPCId("cLVI")
            .withEbsOptimized(true)
            .withAssociatePublicIpAddress(true)
            .withInstanceType("iType")
            .withBlockDeviceMappings(new BlockDeviceMapping().withDeviceName("name0"))
            .withKernelId("kId")
            .withRamdiskId("rId")
            .withInstanceMonitoring(new InstanceMonitoring().withEnabled(true))
            .withSpotPrice("sPrice")
            .withIamInstanceProfile("iAmProfile")
            .withPlacementTenancy("pTency")
            .withKeyName("key");
    CreateLaunchConfigurationRequest request =
        awsAmiHelperServiceDelegate.createNewLaunchConfigurationRequest(AwsConfig.builder().build(), emptyList(),
            "us-east-1", "aRev", cloneBaseLaunchConfiguration, "newName", "userData");
    assertThat(request).isNotNull();
    assertThat(request.getLaunchConfigurationName()).isEqualTo("newName");
    assertThat(request.getImageId()).isEqualTo("aRev");
    assertThat(request.getSecurityGroups()).isEqualTo(asList("sg1", "sg2"));
    assertThat(request.getClassicLinkVPCId()).isEqualTo("cLVI");
    assertThat(request.getEbsOptimized()).isEqualTo(true);
    assertThat(request.getAssociatePublicIpAddress()).isEqualTo(true);
    assertThat(request.getUserData()).isEqualTo("userData");
    assertThat(request.getInstanceType()).isEqualTo("iType");
    assertThat(request.getKernelId()).isEqualTo("kId");
    assertThat(request.getRamdiskId()).isEqualTo("rId");
    assertThat(request.getInstanceMonitoring().getEnabled()).isEqualTo(true);
    assertThat(request.getSpotPrice()).isEqualTo("sPrice");
    assertThat(request.getIamInstanceProfile()).isEqualTo("iAmProfile");
    assertThat(request.getPlacementTenancy()).isEqualTo("pTency");
    assertThat(request.getKeyName()).isEqualTo("key");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetNewHarnessVersion() {
    List<AutoScalingGroup> groups = asList(
        new AutoScalingGroup().withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("id__1")),
        new AutoScalingGroup().withTags(
            new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("id__2")));
    Integer nextRev = awsAmiHelperServiceDelegate.getNewHarnessVersion(groups);
    assertThat(nextRev).isNotNull();
    assertThat(nextRev).isEqualTo(3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetMostRecentActiveAsgWhenNotBlueGreenRequest() {
    List<AutoScalingGroup> groups =
        asList(new AutoScalingGroup().withDesiredCapacity(1).withAutoScalingGroupName("name1"),
            new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name0"));

    List<AutoScalingGroup> groups1 =
        asList(new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name2"),
            new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name3"),
            new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name4"));

    AwsAmiServiceSetupRequest request = mock(AwsAmiServiceSetupRequest.class);
    when(request.isBlueGreen()).thenReturn(false);
    AutoScalingGroup mostRecentActiveAsg =
        awsAmiHelperServiceDelegate.getMostRecentOrBlueActiveAsg(groups1, groups, request);
    assertThat(mostRecentActiveAsg).isNotNull();
    assertThat(mostRecentActiveAsg.getAutoScalingGroupName()).isEqualTo("name1");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testGetMostRecentActiveAsgWhenBlueGreenRequest() {
    TagDescription tagDescription =
        new TagDescription().withKey(BG_VERSION).withValue(BG_GREEN).withPropagateAtLaunch(true);
    TagDescription blueTagDescription =
        new TagDescription().withKey(BG_VERSION).withValue(BG_BLUE).withPropagateAtLaunch(true);
    List<AutoScalingGroup> groups = asList(
        new AutoScalingGroup().withDesiredCapacity(1).withAutoScalingGroupName("name1").withTags(tagDescription),
        new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name0").withTags(tagDescription),
        new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name3").withTags(blueTagDescription),
        new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name4").withTags(tagDescription));

    List<AutoScalingGroup> groups1 =
        asList(new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name2"),
            new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name3"),
            new AutoScalingGroup().withDesiredCapacity(0).withAutoScalingGroupName("name4"));

    AwsAmiServiceSetupRequest request = mock(AwsAmiServiceSetupRequest.class);
    when(request.isBlueGreen()).thenReturn(true);
    AutoScalingGroup mostRecentActiveAsg =
        awsAmiHelperServiceDelegate.getMostRecentOrBlueActiveAsg(groups, groups1, request);
    assertThat(mostRecentActiveAsg).isNotNull();
    assertThat(mostRecentActiveAsg.getAutoScalingGroupName()).isEqualTo("name3");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAllHarnessManagedAsgs() {
    List<AutoScalingGroup> groups = asList(new AutoScalingGroup(),
        new AutoScalingGroup()
            .withAutoScalingGroupName("foo")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("idFoo"))
            .withCreatedTime(new Date(10)),
        new AutoScalingGroup()
            .withAutoScalingGroupName("bar")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("idBar"))
            .withCreatedTime(new Date(20)));
    doReturn(groups).when(mockAwsAsgHelperServiceDelegate).listAllAsgs(any(), anyList(), anyString());
    List<AutoScalingGroup> result = awsAmiHelperServiceDelegate.listAllHarnessManagedAsgs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "id");
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).getAutoScalingGroupName()).isEqualTo("bar");
    assertThat(result.get(1).getAutoScalingGroupName()).isEqualTo("foo");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testEnsureAndGetBaseLaunchConfiguration() {
    ExecutionLogCallback mockLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doReturn(null)
        .when(mockAwsAsgHelperServiceDelegate)
        .getLaunchConfiguration(any(), anyList(), anyString(), anyString());

    Throwable thrown = catchThrowable(() -> {
      awsAmiHelperServiceDelegate.ensureAndGetBaseLaunchConfiguration(
          AwsConfig.builder().build(), emptyList(), "us-east-1", "asgName", new AutoScalingGroup(), mockLogCallback);
    });
    assertThat(thrown).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testEnsureAndGetBaseAutoScalingGroup() {
    ExecutionLogCallback mockLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doReturn(null)
        .when(mockAwsAsgHelperServiceDelegate)
        .getAutoScalingGroup(any(), anyList(), anyString(), anyString());
    try {
      awsAmiHelperServiceDelegate.ensureAndGetBaseAutoScalingGroup(
          AwsConfig.builder().build(), emptyList(), "us-east-1", "asgName", mockLogCallback);
      fail("Exception should have been thrown");
    } catch (InvalidRequestException ex) {
      // Expected
    } catch (Exception ex) {
      fail(format("Exception: [%s]", ex.getMessage()));
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testPopulatePreDeploymentData() {
    ExecutionLogCallback mockLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    AutoScalingGroup autoScalingGroup =
        new AutoScalingGroup().withAutoScalingGroupName("name_2").withDesiredCapacity(3).withMinSize(2);

    AwsAmiServiceSetupResponseBuilder builder = AwsAmiServiceSetupResponse.builder();
    awsAmiHelperServiceDelegate.populatePreDeploymentData(
        AwsConfig.builder().build(), emptyList(), "us-east-1", autoScalingGroup, builder, mockLogCallback, false);
    AwsAmiServiceSetupResponse response = builder.build();
    AwsAmiPreDeploymentData preDeploymentData = response.getPreDeploymentData();
    assertThat(preDeploymentData.getOldAsgName()).isEqualTo("name_2");
    assertThat(preDeploymentData.getMinCapacity()).isEqualTo(2);
    assertThat(preDeploymentData.getDesiredCapacity()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createAndGetNewLaunchTemplateVersion() {
    final LaunchTemplateVersion baseLaunchTemplateVersion =
        new LaunchTemplateVersion().withLaunchTemplateName("LtName").withLaunchTemplateId("ltid").withVersionNumber(3L);
    final String artifactVersion = "artifact_version";
    final AwsAmiServiceSetupRequest request =
        AwsAmiServiceSetupRequest.builder().artifactRevision(artifactVersion).userData("user-data").build();
    final AwsConfig awsConfig = AwsConfig.builder().build();
    final CreateLaunchTemplateVersionResult result =
        new CreateLaunchTemplateVersionResult().withLaunchTemplateVersion(new LaunchTemplateVersion());
    doReturn(result)
        .when(mockAwsEc2HelperServiceDelegate)
        .createLaunchTemplateVersion(
            any(CreateLaunchTemplateVersionRequest.class), eq(awsConfig), eq(emptyList()), eq(REGION));

    final LaunchTemplateVersion newLaunchTemplate = awsAmiHelperServiceDelegate.createAndGetNewLaunchTemplateVersion(
        baseLaunchTemplateVersion, request, mockCallback, awsConfig, emptyList(), REGION);
    assertThat(newLaunchTemplate).isEqualTo(result.getLaunchTemplateVersion());
    ArgumentCaptor<CreateLaunchTemplateVersionRequest> requestArgumentCaptor =
        ArgumentCaptor.forClass(CreateLaunchTemplateVersionRequest.class);
    verify(mockAwsEc2HelperServiceDelegate)
        .createLaunchTemplateVersion(requestArgumentCaptor.capture(), eq(awsConfig), eq(emptyList()), eq(REGION));
    final CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest = requestArgumentCaptor.getValue();
    assertThat(createLaunchTemplateVersionRequest.getLaunchTemplateData().getImageId()).isEqualTo(artifactVersion);
    assertThat(createLaunchTemplateVersionRequest.getLaunchTemplateId()).isEqualTo("ltid");
    assertThat(createLaunchTemplateVersionRequest.getSourceVersion()).isEqualTo("3");
    assertThat(createLaunchTemplateVersionRequest.getLaunchTemplateData().getUserData()).isEqualTo("user-data");

    doReturn(new CreateLaunchTemplateVersionResult())
        .when(mockAwsEc2HelperServiceDelegate)
        .createLaunchTemplateVersion(
            any(CreateLaunchTemplateVersionRequest.class), eq(awsConfig), eq(emptyList()), eq(REGION));

    assertThatThrownBy(()
                           -> awsAmiHelperServiceDelegate.createAndGetNewLaunchTemplateVersion(
                               baseLaunchTemplateVersion, request, mockCallback, awsConfig, emptyList(), REGION))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_ensureAndGetLaunchTemplateVersion() {
    final LaunchTemplateSpecification launchTemplateSpecification = new LaunchTemplateSpecification();
    final AutoScalingGroup autoScalingGroup = new AutoScalingGroup();
    final AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(mock(LaunchTemplateVersion.class))
        .when(mockAwsEc2HelperServiceDelegate)
        .getLaunchTemplateVersion(awsConfig, emptyList(), REGION, launchTemplateSpecification.getLaunchTemplateId(),
            launchTemplateSpecification.getVersion());
    final LaunchTemplateVersion launchTemplateVersion = awsAmiHelperServiceDelegate.ensureAndGetLaunchTemplateVersion(
        launchTemplateSpecification, autoScalingGroup, awsConfig, emptyList(), REGION, mockCallback);

    assertThat(launchTemplateVersion).isNotNull();
    verify(mockAwsEc2HelperServiceDelegate, times(1))
        .getLaunchTemplateVersion(awsConfig, emptyList(), REGION, launchTemplateSpecification.getLaunchTemplateId(),
            launchTemplateSpecification.getVersion());

    //  check error

    doReturn(null)
        .when(mockAwsEc2HelperServiceDelegate)
        .getLaunchTemplateVersion(awsConfig, emptyList(), REGION, launchTemplateSpecification.getLaunchTemplateId(),
            launchTemplateSpecification.getVersion());

    assertThatThrownBy(()
                           -> awsAmiHelperServiceDelegate.ensureAndGetLaunchTemplateVersion(launchTemplateSpecification,
                               autoScalingGroup, awsConfig, emptyList(), REGION, mockCallback))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_extractLaunchTemplateSpecFrom() {
    final LaunchTemplateSpecification launchTemplate = new LaunchTemplateSpecification();
    final AutoScalingGroup autoScalingGroup = new AutoScalingGroup().withLaunchTemplate(launchTemplate);
    assertThat(awsAmiHelperServiceDelegate.extractLaunchTemplateSpecFrom(autoScalingGroup)).isEqualTo(launchTemplate);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_extractLaunchTemplateSpecFrom_1() {
    final LaunchTemplateSpecification launchTemplateSpecification = new LaunchTemplateSpecification();
    final AutoScalingGroup autoScalingGroup =
        new AutoScalingGroup().withMixedInstancesPolicy(new MixedInstancesPolicy().withLaunchTemplate(
            new LaunchTemplate().withLaunchTemplateSpecification(launchTemplateSpecification)));
    assertThat(awsAmiHelperServiceDelegate.extractLaunchTemplateSpecFrom(autoScalingGroup))
        .isEqualTo(launchTemplateSpecification);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_setUpAmiService_LT() {
    final AwsConfig awsConfig = AwsConfig.builder().build();
    final AwsAmiServiceSetupRequest awsAmiServiceSetupRequest = AwsAmiServiceSetupRequest.builder()
                                                                    .region(REGION)
                                                                    .infraMappingAsgName("asg_name")
                                                                    .awsConfig(awsConfig)
                                                                    .encryptionDetails(emptyList())
                                                                    .infraMappingId("infra_id")
                                                                    .newAsgNamePrefix("newsasgprefix_")
                                                                    .useCurrentRunningCount(false)
                                                                    .minInstances(1)
                                                                    .maxInstances(3)
                                                                    .desiredInstances(1)
                                                                    .infraMappingClassisLbs(emptyList())
                                                                    .infraMappingTargetGroupArns(emptyList())
                                                                    .blueGreen(false)
                                                                    .build();
    final LaunchTemplateSpecification baseLaunchTemplateSpecification =
        new LaunchTemplateSpecification().withLaunchTemplateId("ltid").withVersion("1");
    final AutoScalingGroup baseAutoScalingGroup =
        new AutoScalingGroup().withLaunchTemplate(baseLaunchTemplateSpecification);

    doReturn(baseAutoScalingGroup)
        .when(awsAmiHelperServiceDelegate)
        .ensureAndGetBaseAutoScalingGroup(eq(awsConfig), eq(emptyList()), anyString(), anyString(), eq(mockCallback));

    final LaunchTemplateVersion baseLaunchTemplateVersion =
        new LaunchTemplateVersion().withVersionNumber(1L).withLaunchTemplateId("ltid").withLaunchTemplateName(
            "base_lt_name");

    doReturn(baseLaunchTemplateVersion)
        .when(awsAmiHelperServiceDelegate)
        .ensureAndGetLaunchTemplateVersion(eq(baseLaunchTemplateSpecification), eq(baseAutoScalingGroup), eq(awsConfig),
            eq(emptyList()), anyString(), eq(mockCallback));
    List<AutoScalingGroup> groups = asList(new AutoScalingGroup(),
        new AutoScalingGroup()
            .withAutoScalingGroupName("foo")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue("idFoo"))
            .withCreatedTime(new Date(10)));

    doReturn(groups).when(mockAwsAsgHelperServiceDelegate).listAllAsgs(any(), anyList(), anyString());

    final LaunchTemplateVersion newLaunchTemplateVersion =
        new LaunchTemplateVersion().withVersionNumber(2L).withLaunchTemplateId("ltid").withLaunchTemplateName(
            "base_lt_name");
    doReturn(newLaunchTemplateVersion)
        .when(awsAmiHelperServiceDelegate)
        .createAndGetNewLaunchTemplateVersion(eq(baseLaunchTemplateVersion), eq(awsAmiServiceSetupRequest),
            eq(mockCallback), eq(awsConfig), eq(emptyList()), eq(REGION));

    doReturn(new CreateAutoScalingGroupResult())
        .when(mockAwsAsgHelperServiceDelegate)
        .createAutoScalingGroup(
            eq(awsConfig), eq(emptyList()), eq(REGION), any(CreateAutoScalingGroupRequest.class), eq(mockCallback));

    final AwsAmiServiceSetupResponse awsAmiServiceSetupResponse =
        awsAmiHelperServiceDelegate.setUpAmiService(awsAmiServiceSetupRequest, mockCallback);

    verify(awsAmiHelperServiceDelegate, times(1))
        .ensureAndGetBaseAutoScalingGroup(eq(awsConfig), eq(emptyList()), anyString(), anyString(), eq(mockCallback));
    verify(awsAmiHelperServiceDelegate, times(1))
        .ensureAndGetLaunchTemplateVersion(eq(baseLaunchTemplateSpecification), eq(baseAutoScalingGroup), eq(awsConfig),
            eq(emptyList()), anyString(), eq(mockCallback));

    verify(mockAwsAsgHelperServiceDelegate, times(1)).listAllAsgs(any(), anyList(), anyString());
    verify(awsAmiHelperServiceDelegate, times(1))
        .createAndGetNewLaunchTemplateVersion(eq(baseLaunchTemplateVersion), eq(awsAmiServiceSetupRequest),
            eq(mockCallback), eq(awsConfig), eq(emptyList()), eq(REGION));

    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .createAutoScalingGroup(
            eq(awsConfig), eq(emptyList()), eq(REGION), any(CreateAutoScalingGroupRequest.class), eq(mockCallback));

    verify(awsAmiHelperServiceDelegate)
        .createNewAutoScalingGroupRequest("infra_id", emptyList(), emptyList(),
            awsAmiServiceSetupResponse.getNewAsgName(), baseAutoScalingGroup,
            awsAmiServiceSetupResponse.getHarnessRevision(), 3, newLaunchTemplateVersion, false);

    assertThat(awsAmiServiceSetupResponse.getNewLaunchTemplateName())
        .isEqualTo(newLaunchTemplateVersion.getLaunchTemplateName());
    assertThat(awsAmiServiceSetupResponse.getNewLaunchTemplateVersion())
        .isEqualTo(String.valueOf(newLaunchTemplateVersion.getVersionNumber()));

    assertThat(awsAmiServiceSetupResponse.getBaseLaunchTemplateName())
        .isEqualTo(baseLaunchTemplateVersion.getLaunchTemplateName());
    assertThat(awsAmiServiceSetupResponse.getBaseLaunchTemplateVersion())
        .isEqualTo(String.valueOf(baseLaunchTemplateVersion.getVersionNumber()));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateAwsAmiSetupRequest() {
    LbDetailsForAlbTrafficShift lbDetails = LbDetailsForAlbTrafficShift.builder()
                                                .loadBalancerName("lbName")
                                                .loadBalancerArn("lbArn")
                                                .listenerPort("port")
                                                .listenerArn("listArn")
                                                .useSpecificRule(true)
                                                .ruleArn("ruleArn")
                                                .prodTargetGroupName("prodTarget")
                                                .prodTargetGroupArn("prodTargetArn")
                                                .stageTargetGroupName("stageTarget")
                                                .stageTargetGroupArn("stageTargetArn")
                                                .build();

    AwsConfig awsConfig = AwsConfig.builder().build();
    AwsAmiServiceTrafficShiftAlbSetupRequest trafficShiftAlbSetupRequest =
        AwsAmiServiceTrafficShiftAlbSetupRequest.builder()
            .awsConfig(awsConfig)
            .region(REGION)
            .encryptionDetails(emptyList())
            .lbDetails(singletonList(LbDetailsForAlbTrafficShift.builder().build()))
            .newAsgNamePrefix("new_Asg")
            .minInstances(1)
            .maxInstances(4)
            .desiredInstances(2)
            .userData(USER_DATA_NON_EMPTY)
            .build();

    AwsAmiServiceSetupRequest request =
        awsAmiHelperServiceDelegate.createAwsAmiSetupRequest(trafficShiftAlbSetupRequest, singletonList(lbDetails));
    assertThat(request.getUserData()).isEqualTo(trafficShiftAlbSetupRequest.getUserData());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSetUpAmiServiceTrafficShift() {
    LbDetailsForAlbTrafficShift lbDetails = LbDetailsForAlbTrafficShift.builder()
                                                .loadBalancerName("lbName")
                                                .loadBalancerArn("lbArn")
                                                .listenerPort("port")
                                                .listenerArn("listArn")
                                                .useSpecificRule(true)
                                                .ruleArn("ruleArn")
                                                .prodTargetGroupName("prodTarget")
                                                .prodTargetGroupArn("prodTargetArn")
                                                .stageTargetGroupName("stageTarget")
                                                .stageTargetGroupArn("stageTargetArn")
                                                .build();

    AwsConfig awsConfig = AwsConfig.builder().build();
    AwsAmiServiceTrafficShiftAlbSetupRequest trafficShiftAlbSetupRequest =
        AwsAmiServiceTrafficShiftAlbSetupRequest.builder()
            .awsConfig(awsConfig)
            .region(REGION)
            .encryptionDetails(emptyList())
            .lbDetails(singletonList(LbDetailsForAlbTrafficShift.builder().build()))
            .newAsgNamePrefix("new_Asg")
            .minInstances(1)
            .maxInstances(4)
            .desiredInstances(2)
            .build();

    AwsAmiServiceSetupResponse setupSuccessResponse = AwsAmiServiceSetupResponse.builder()
                                                          .executionStatus(ExecutionStatus.SUCCESS)
                                                          .lastDeployedAsgName("old_Asg")
                                                          .harnessRevision(2)
                                                          .build();
    AwsAmiServiceSetupResponse setupFailureResponse =
        AwsAmiServiceSetupResponse.builder().executionStatus(ExecutionStatus.FAILED).build();

    doReturn(lbDetails)
        .when(mockAwsElbHelperServiceDelegate)
        .loadTrafficShiftTargetGroupData(
            eq(awsConfig), eq(REGION), eq(emptyList()), any(LbDetailsForAlbTrafficShift.class), any());

    doReturn(setupSuccessResponse)
        .when(awsAmiHelperServiceDelegate)
        .setUpAmiService(any(AwsAmiServiceSetupRequest.class), any());
    AwsAmiServiceTrafficShiftAlbSetupResponse trafficShiftSetupResponse =
        awsAmiHelperServiceDelegate.setUpAmiServiceTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(trafficShiftSetupResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    doReturn(setupFailureResponse)
        .when(awsAmiHelperServiceDelegate)
        .setUpAmiService(any(AwsAmiServiceSetupRequest.class), any());
    trafficShiftSetupResponse = awsAmiHelperServiceDelegate.setUpAmiServiceTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(trafficShiftSetupResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);

    trafficShiftAlbSetupRequest.setLbDetails(emptyList());
    trafficShiftSetupResponse = awsAmiHelperServiceDelegate.setUpAmiServiceTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(trafficShiftSetupResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeployAmiServiceTrafficShift() {
    AwsAmiServiceTrafficShiftAlbDeployRequest trafficShiftAlbDeployRequest =
        AwsAmiServiceTrafficShiftAlbDeployRequest.builder()
            .awsConfig(AwsConfig.builder().build())
            .region(REGION)
            .encryptionDetails(emptyList())
            .minInstances(1)
            .maxInstances(4)
            .desiredInstances(2)
            .oldAutoScalingGroupName("oldAsg")
            .newAutoScalingGroupName("newAsg")
            .infraMappingTargetGroupArns(emptyList())
            .build();

    doReturn(AwsAmiServiceDeployResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build())
        .when(awsAmiHelperServiceDelegate)
        .deployAmiService(any(), any());
    AwsAmiServiceDeployResponse awsAmiServiceDeployResponse =
        awsAmiHelperServiceDelegate.deployAmiServiceTrafficShift(trafficShiftAlbDeployRequest);
    assertThat(awsAmiServiceDeployResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeployAmiServiceTrafficShiftFailure() {
    AwsAmiServiceTrafficShiftAlbDeployRequest trafficShiftAlbDeployRequest =
        AwsAmiServiceTrafficShiftAlbDeployRequest.builder()
            .awsConfig(AwsConfig.builder().build())
            .region(REGION)
            .encryptionDetails(emptyList())
            .minInstances(1)
            .maxInstances(4)
            .desiredInstances(2)
            .oldAutoScalingGroupName("oldAsg")
            .newAutoScalingGroupName("newAsg")
            .infraMappingTargetGroupArns(emptyList())
            .build();
    doAnswer(invocation -> { throw new Exception(); })
        .when(awsAmiHelperServiceDelegate)
        .resizeAsgs(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt(), any(),
            any(), any(), anyBoolean(), any(), anyInt(), anyBoolean(), any(), anyBoolean());
    AwsAmiServiceDeployResponse awsAmiServiceDeployResponse =
        awsAmiHelperServiceDelegate.deployAmiServiceTrafficShift(trafficShiftAlbDeployRequest);
    assertThat(awsAmiServiceDeployResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAmiRollbackSwitchRoutesTrafficShift() {
    LbDetailsForAlbTrafficShift lbDetails = LbDetailsForAlbTrafficShift.builder()
                                                .loadBalancerName("lbName")
                                                .loadBalancerArn("lbArn")
                                                .listenerPort("port")
                                                .listenerArn("listArn")
                                                .useSpecificRule(true)
                                                .ruleArn("ruleArn")
                                                .prodTargetGroupName("prodTarget")
                                                .prodTargetGroupArn("prodTargetArn")
                                                .stageTargetGroupName("stageTarget")
                                                .stageTargetGroupArn("stageTargetArn")
                                                .build();

    AwsConfig awsConfig = AwsConfig.builder().build();
    AwsAmiTrafficShiftAlbSwitchRouteRequest trafficShiftAlbSetupRequest =
        AwsAmiTrafficShiftAlbSwitchRouteRequest.builder()
            .awsConfig(awsConfig)
            .region(REGION)
            .encryptionDetails(emptyList())
            .lbDetails(singletonList(lbDetails))
            .newAsgName("newAsg")
            .newAutoscalingGroupWeight(DEFAULT_TRAFFIC_SHIFT_WEIGHT)
            .timeoutIntervalInMin(10)
            .encryptionDetails(Collections.emptyList())
            .build();

    doReturn(AwsAmiSwitchRoutesResponse.builder().executionStatus(SUCCESS).build())
        .when(awsAmiHelperServiceDelegate)
        .rollbackSwitchAmiRoutes(any(), eq(mockCallback));

    AwsAmiSwitchRoutesResponse awsAmiSwitchRoutesResponse =
        awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    doAnswer(invocation -> { throw new Exception(); })
        .when(mockAwsAsgHelperServiceDelegate)
        .clearAllScalingPoliciesForAsg(eq(awsConfig), eq(emptyList()), eq(REGION), anyString(), any());
    awsAmiSwitchRoutesResponse =
        awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(FAILED);

    doAnswer(invocation -> { throw new Exception(); })
        .when(awsAmiHelperServiceDelegate)
        .rollbackSwitchAmiRoutes(any(), any());
    awsAmiSwitchRoutesResponse =
        awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(FAILED);

    trafficShiftAlbSetupRequest.setLbDetails(emptyList());
    awsAmiSwitchRoutesResponse =
        awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAmiSwitchRoutesTrafficShift() {
    LbDetailsForAlbTrafficShift lbDetails = LbDetailsForAlbTrafficShift.builder()
                                                .loadBalancerName("lbName")
                                                .loadBalancerArn("lbArn")
                                                .listenerPort("port")
                                                .listenerArn("listArn")
                                                .useSpecificRule(true)
                                                .ruleArn("ruleArn")
                                                .prodTargetGroupName("prodTarget")
                                                .prodTargetGroupArn("prodTargetArn")
                                                .stageTargetGroupName("stageTarget")
                                                .stageTargetGroupArn("stageTargetArn")
                                                .build();

    AwsConfig awsConfig = AwsConfig.builder().build();
    AwsAmiTrafficShiftAlbSwitchRouteRequest trafficShiftAlbSetupRequest =
        AwsAmiTrafficShiftAlbSwitchRouteRequest.builder()
            .awsConfig(awsConfig)
            .region(REGION)
            .encryptionDetails(emptyList())
            .lbDetails(singletonList(lbDetails))
            .newAsgName("newAsg")
            .oldAsgName("oldAsg")
            .newAutoscalingGroupWeight(DEFAULT_TRAFFIC_SHIFT_WEIGHT)
            .timeoutIntervalInMin(10)
            .encryptionDetails(Collections.emptyList())
            .downscaleOldAsg(true)
            .build();

    AwsAmiSwitchRoutesResponse awsAmiSwitchRoutesResponse =
        awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    trafficShiftAlbSetupRequest.setNewAutoscalingGroupWeight(MAX_TRAFFIC_SHIFT_WEIGHT);
    awsAmiSwitchRoutesResponse = awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    trafficShiftAlbSetupRequest.setNewAsgName("");
    awsAmiSwitchRoutesResponse = awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    trafficShiftAlbSetupRequest.setNewAutoscalingGroupWeight(MIN_TRAFFIC_SHIFT_WEIGHT);
    awsAmiSwitchRoutesResponse = awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    trafficShiftAlbSetupRequest.setNewAsgName("newAsg");
    trafficShiftAlbSetupRequest.setNewAutoscalingGroupWeight(DEFAULT_TRAFFIC_SHIFT_WEIGHT);
    doAnswer(invocation -> { throw new Exception(); })
        .when(mockAwsElbHelperServiceDelegate)
        .updateRulesForAlbTrafficShift(eq(awsConfig), eq(REGION), eq(emptyList()), anyList(), any(), anyInt(), any());
    awsAmiSwitchRoutesResponse = awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(trafficShiftAlbSetupRequest);
    assertThat(awsAmiSwitchRoutesResponse.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void test_setUpAmiService_LT_BG_Deployment() {
    final AwsConfig awsConfig = AwsConfig.builder().build();
    String newAsgPrefix = "newsasgprefix_";
    String infraMappingId = "infra_id";
    final AwsAmiServiceSetupRequest awsAmiServiceSetupRequest = AwsAmiServiceSetupRequest.builder()
                                                                    .region(REGION)
                                                                    .infraMappingAsgName("asg_name")
                                                                    .awsConfig(awsConfig)
                                                                    .encryptionDetails(emptyList())
                                                                    .infraMappingId(infraMappingId)
                                                                    .newAsgNamePrefix(newAsgPrefix)
                                                                    .useCurrentRunningCount(false)
                                                                    .minInstances(1)
                                                                    .maxInstances(3)
                                                                    .desiredInstances(1)
                                                                    .infraMappingClassisLbs(emptyList())
                                                                    .infraMappingTargetGroupArns(emptyList())
                                                                    .blueGreen(true)
                                                                    .build();
    final LaunchTemplateSpecification baseLaunchTemplateSpecification =
        new LaunchTemplateSpecification().withLaunchTemplateId("ltid").withVersion("1");
    final AutoScalingGroup baseAutoScalingGroup =
        new AutoScalingGroup().withLaunchTemplate(baseLaunchTemplateSpecification);

    doReturn(baseAutoScalingGroup)
        .when(awsAmiHelperServiceDelegate)
        .ensureAndGetBaseAutoScalingGroup(eq(awsConfig), eq(emptyList()), anyString(), anyString(), eq(mockCallback));

    final LaunchTemplateVersion baseLaunchTemplateVersion =
        new LaunchTemplateVersion().withVersionNumber(1L).withLaunchTemplateId("ltid").withLaunchTemplateName(
            "base_lt_name");

    doReturn(baseLaunchTemplateVersion)
        .when(awsAmiHelperServiceDelegate)
        .ensureAndGetLaunchTemplateVersion(eq(baseLaunchTemplateSpecification), eq(baseAutoScalingGroup), eq(awsConfig),
            eq(emptyList()), anyString(), eq(mockCallback));
    List<AutoScalingGroup> groups = asList(new AutoScalingGroup(),
        new AutoScalingGroup()
            .withAutoScalingGroupName("foo")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue(infraMappingId),
                new TagDescription().withKey(BG_VERSION).withValue(BG_GREEN))
            .withDesiredCapacity(1)
            .withMinSize(0)
            .withCreatedTime(new Date(10)),
        new AutoScalingGroup()
            .withAutoScalingGroupName("blueGroup")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue(infraMappingId),
                new TagDescription().withKey(BG_VERSION).withValue(BG_BLUE))
            .withDesiredCapacity(1)
            .withMinSize(0)
            .withCreatedTime(new Date(9)),
        new AutoScalingGroup()
            .withAutoScalingGroupName("greenGroup")
            .withTags(new TagDescription().withKey(HARNESS_AUTOSCALING_GROUP_TAG).withValue(infraMappingId),
                new TagDescription().withKey(BG_VERSION).withValue(BG_GREEN))
            .withDesiredCapacity(1)
            .withMinSize(0)
            .withCreatedTime(new Date(12)));

    doReturn(groups).when(mockAwsAsgHelperServiceDelegate).listAllAsgs(any(), anyList(), anyString());

    final LaunchTemplateVersion newLaunchTemplateVersion =
        new LaunchTemplateVersion().withVersionNumber(2L).withLaunchTemplateId("ltid").withLaunchTemplateName(
            "base_lt_name");
    doReturn(newLaunchTemplateVersion)
        .when(awsAmiHelperServiceDelegate)
        .createAndGetNewLaunchTemplateVersion(eq(baseLaunchTemplateVersion), eq(awsAmiServiceSetupRequest),
            eq(mockCallback), eq(awsConfig), eq(emptyList()), eq(REGION));

    doReturn(new CreateAutoScalingGroupResult())
        .when(mockAwsAsgHelperServiceDelegate)
        .createAutoScalingGroup(
            eq(awsConfig), eq(emptyList()), eq(REGION), any(CreateAutoScalingGroupRequest.class), eq(mockCallback));

    final AwsAmiServiceSetupResponse awsAmiServiceSetupResponse =
        awsAmiHelperServiceDelegate.setUpAmiService(awsAmiServiceSetupRequest, mockCallback);

    verify(awsAmiHelperServiceDelegate, times(1))
        .ensureAndGetBaseAutoScalingGroup(eq(awsConfig), eq(emptyList()), anyString(), anyString(), eq(mockCallback));
    verify(awsAmiHelperServiceDelegate, times(1))
        .ensureAndGetLaunchTemplateVersion(eq(baseLaunchTemplateSpecification), eq(baseAutoScalingGroup), eq(awsConfig),
            eq(emptyList()), anyString(), eq(mockCallback));

    verify(mockAwsAsgHelperServiceDelegate, times(1)).listAllAsgs(any(), anyList(), anyString());
    verify(awsAmiHelperServiceDelegate, times(1))
        .createAndGetNewLaunchTemplateVersion(eq(baseLaunchTemplateVersion), eq(awsAmiServiceSetupRequest),
            eq(mockCallback), eq(awsConfig), eq(emptyList()), eq(REGION));

    verify(mockAwsAsgHelperServiceDelegate, times(1))
        .createAutoScalingGroup(
            eq(awsConfig), eq(emptyList()), eq(REGION), any(CreateAutoScalingGroupRequest.class), eq(mockCallback));

    verify(awsAmiHelperServiceDelegate)
        .createNewAutoScalingGroupRequest(infraMappingId, emptyList(), emptyList(),
            awsAmiServiceSetupResponse.getNewAsgName(), baseAutoScalingGroup,
            awsAmiServiceSetupResponse.getHarnessRevision(), 3, newLaunchTemplateVersion, true);

    assertThat(awsAmiServiceSetupResponse.getNewLaunchTemplateName())
        .isEqualTo(newLaunchTemplateVersion.getLaunchTemplateName());
    assertThat(awsAmiServiceSetupResponse.getNewAsgName()).isEqualTo(newAsgPrefix + "__1");
    assertThat(awsAmiServiceSetupResponse.getLastDeployedAsgName()).isEqualTo("blueGroup");
    assertThat(awsAmiServiceSetupResponse.getNewLaunchTemplateVersion())
        .isEqualTo(String.valueOf(newLaunchTemplateVersion.getVersionNumber()));

    assertThat(awsAmiServiceSetupResponse.getBaseLaunchTemplateName())
        .isEqualTo(baseLaunchTemplateVersion.getLaunchTemplateName());
    assertThat(awsAmiServiceSetupResponse.getBaseLaunchTemplateVersion())
        .isEqualTo(String.valueOf(baseLaunchTemplateVersion.getVersionNumber()));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchExistingInstancesForOlderASG() {
    Instance instance = new Instance().withInstanceId("1");
    doReturn(Arrays.asList(instance))
        .when(mockAwsAsgHelperServiceDelegate)
        .listAutoScalingGroupInstances(any(), anyList(), any(), any(), anyBoolean());

    List<AwsAmiResizeData> awsAmiResizeData =
        Arrays.asList(AwsAmiResizeData.builder().asgName("1").desiredCount(1).build());
    AwsAmiServiceDeployRequest awsAmiServiceDeployRequest =
        AwsAmiServiceDeployRequest.builder().asgDesiredCounts(awsAmiResizeData).build();
    List<Instance> existingInstancesForOlderASG = awsAmiHelperServiceDelegate.fetchExistingInstancesForOlderASG(
        AwsConfig.builder().build(), emptyList(), awsAmiServiceDeployRequest, mockCallback);
    assertThat(existingInstancesForOlderASG.size()).isEqualTo(1);
    assertThat(existingInstancesForOlderASG.get(0).equals(instance)).isTrue();

    awsAmiServiceDeployRequest.setAsgDesiredCounts(null);
    awsAmiServiceDeployRequest.setOldAutoScalingGroupName("myname");
    existingInstancesForOlderASG = awsAmiHelperServiceDelegate.fetchExistingInstancesForOlderASG(
        AwsConfig.builder().build(), emptyList(), awsAmiServiceDeployRequest, mockCallback);
    assertThat(existingInstancesForOlderASG.size()).isEqualTo(1);
    assertThat(existingInstancesForOlderASG.get(0).equals(instance)).isTrue();

    // Make sure, it doesnt throw an exception
    awsAmiServiceDeployRequest.setOldAutoScalingGroupName(null);
    awsAmiServiceDeployRequest.setAsgDesiredCounts(awsAmiResizeData);
    doAnswer(invocation -> { throw new InvalidRequestException(""); })
        .when(mockAwsAsgHelperServiceDelegate)
        .listAutoScalingGroupInstances(any(), anyList(), any(), any(), anyBoolean());
    existingInstancesForOlderASG = awsAmiHelperServiceDelegate.fetchExistingInstancesForOlderASG(
        AwsConfig.builder().build(), emptyList(), awsAmiServiceDeployRequest, mockCallback);
    assertThat(existingInstancesForOlderASG.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetAsgRunningCountDataWithNullAutoscalingGroup() {
    AutoScalingGroup autoScalingGroup = null;
    AwsAsgGetRunningCountData asgRunningCountData =
        awsAmiHelperServiceDelegate.getAsgRunningCountData(autoScalingGroup);
    assertThat(asgRunningCountData.getAsgDesired()).isEqualTo(DEFAULT_AMI_ASG_DESIRED_INSTANCES);
    assertThat(asgRunningCountData.getAsgMax()).isEqualTo(DEFAULT_AMI_ASG_MAX_INSTANCES);
    assertThat(asgRunningCountData.getAsgName()).isEqualTo(DEFAULT_AMI_ASG_NAME);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetAsgRunningCountDataNotNullAutoscalingGroup() {
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup()
                                            .withAutoScalingGroupName("autoScalingGroup")
                                            .withMaxSize(5)
                                            .withMinSize(2)
                                            .withDesiredCapacity(3);
    AwsAsgGetRunningCountData asgRunningCountData =
        awsAmiHelperServiceDelegate.getAsgRunningCountData(autoScalingGroup);
    assertThat(asgRunningCountData.getAsgDesired()).isEqualTo(3);
    assertThat(asgRunningCountData.getAsgMax()).isEqualTo(5);
    assertThat(asgRunningCountData.getAsgName()).isEqualTo("autoScalingGroup");
  }
}
