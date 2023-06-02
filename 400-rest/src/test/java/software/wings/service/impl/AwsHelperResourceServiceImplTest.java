/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRAKHAR;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.aws.manager.AwsEc2HelperServiceManagerImpl;
import software.wings.service.impl.aws.manager.AwsS3HelperServiceManagerImpl;
import software.wings.service.intfc.AwsHelperResourceService;
import software.wings.service.intfc.SettingsService;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.ec2.model.ResourceType;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by sgurubelli on 7/20/18.
 */
@OwnedBy(CDP)
public class AwsHelperResourceServiceImplTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;
  @Mock private AwsS3HelperServiceManagerImpl awsS3HelperServiceManagerImpl;
  @Mock private AwsEc2HelperServiceManagerImpl awsEc2HelperServiceManagerImpl;
  @Inject @InjectMocks private AwsHelperResourceService awsHelperResourceService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRegions() {
    List<NameValuePair> regions = awsHelperResourceService.getAwsRegions();
    assertThat(regions).isNotEmpty().extracting(NameValuePair::getName).contains(Regions.US_EAST_1.getName());
    assertThat(regions).extracting(NameValuePair::getName).contains(Regions.GovCloud.getName());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testgetRegions() throws InterruptedException {
    Map<String, String> regions = awsHelperResourceService.getRegions();
    assertThat(regions).isNotEmpty().containsKeys(Regions.US_EAST_1.getName());
    assertThat(regions).doesNotContainKeys(Regions.GovCloud.getName());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListTagsWithAppId() throws InterruptedException {
    AwsConfig awsConfig = AwsConfig.builder().accountId("accountId").build();
    SettingAttribute computeProviderSetting = SettingAttribute.Builder.aSettingAttribute()
                                                  .withName("name")
                                                  .withAccountId("accountId")
                                                  .withAppId("appId")
                                                  .withValue(awsConfig)
                                                  .build();
    Set<String> tags = new HashSet<>(asList("tag1", "tag2"));
    doReturn(computeProviderSetting).when(settingsService).get("computeProviderId");
    doReturn(tags)
        .when(awsEc2HelperServiceManagerImpl)
        .listTags(any(), any(), anyString(), anyString(), any(ResourceType.class));

    assertThat(awsHelperResourceService.listTags("appId", "computeProviderId", "region", null)).isEqualTo(tags);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListTagsWithNoAppId() throws InterruptedException {
    AwsConfig awsConfig = AwsConfig.builder().accountId("accountId").build();
    SettingAttribute computeProviderSetting = SettingAttribute.Builder.aSettingAttribute()
                                                  .withName("name")
                                                  .withAccountId("accountId")
                                                  .withAppId("appId")
                                                  .withValue(awsConfig)
                                                  .build();
    Set<String> tags = new HashSet<>(asList("tag1", "tag2"));

    doReturn(computeProviderSetting).when(settingsService).get("computeProviderId");
    doReturn(tags).when(awsEc2HelperServiceManagerImpl).listTags(any(), any(), anyString(), any(ResourceType.class));

    assertThat(awsHelperResourceService.listTags("computeProviderId", "region", null)).isEqualTo(tags);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListTagsException() throws InterruptedException {
    SettingAttribute computeProviderSetting = SettingAttribute.Builder.aSettingAttribute()
                                                  .withName("name")
                                                  .withAccountId("accountId")
                                                  .withAppId("appId")
                                                  .build();
    doReturn(computeProviderSetting).when(settingsService).get("computeProviderId");
    assertThatThrownBy(() -> awsHelperResourceService.listTags("appId", "computeProviderId", "region", null))
        .isInstanceOf(WingsException.class);
    assertThatThrownBy(() -> awsHelperResourceService.listTags("appId", "computeProviderId", "region", null))
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testlistBuckets() throws InterruptedException {
    AwsConfig awsConfig = AwsConfig.builder().accountId("accountId").tag("tags").build();
    SettingAttribute computeProviderSetting = SettingAttribute.Builder.aSettingAttribute()
                                                  .withName("name")
                                                  .withAccountId("accountId")
                                                  .withAppId("appId")
                                                  .withValue(awsConfig)
                                                  .build();
    List<String> buckets = asList("bucket1", "bucket2");

    doReturn(buckets).when(awsS3HelperServiceManagerImpl).listBucketNames(any(), any());
    doReturn(computeProviderSetting).when(settingsService).get("awsSettingId");

    assertThat(awsHelperResourceService.listBuckets("awsSettingId")).isEqualTo(buckets);
  }

  @Test
  @Owner(developers = PRAKHAR)
  @Category(UnitTests.class)
  public void testListCloudformationCapabilities() {
    List<String> expectedCapabilities =
        Stream.of(Capability.values()).map(Capability::toString).collect(Collectors.toList());
    assertThat(awsHelperResourceService.listCloudformationCapabilities()).isEqualTo(expectedCapabilities);
  }
}
