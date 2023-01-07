/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDP)

public class AsgTaskHelperTest extends CategoryTest {
  private AsgTaskHelper asgTaskHelper = new AsgTaskHelper();
  @Mock AwsUtils awsUtils;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetLogCallback() {
    ILogStreamingTaskClient logStreamingTaskClient = mock(ILogStreamingTaskClient.class);
    String commandUnitName = "commandUnitName";
    boolean shouldOpenStream = true;
    CommandUnitsProgress commandUnitsProgress = mock(CommandUnitsProgress.class);

    LogCallback result =
        asgTaskHelper.getLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
    assertNotNull(result);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgLaunchTemplateContent() {
    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put(AsgLaunchTemplate, Arrays.asList("launch template content"));

    String result = asgTaskHelper.getAsgLaunchTemplateContent(asgStoreManifestsContent);
    assertEquals("launch template content", result);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgConfigurationContent() {
    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put(AsgConfiguration, Arrays.asList("configuration content"));

    String result = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);
    assertEquals("configuration content", result);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgScalingPolicyContent() {
    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put(AsgScalingPolicy, Arrays.asList("scaling policy content"));

    List<String> result = asgTaskHelper.getAsgScalingPolicyContent(asgStoreManifestsContent);
    assertEquals(1, result.size());
    assertEquals("scaling policy content", result.get(0));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgSdkManager() {
    AsgCommandRequest asgCommandRequest = mock(AsgCommandRequest.class);
    when(asgCommandRequest.getTimeoutIntervalInMin()).thenReturn(10);
    when(asgCommandRequest.getAsgInfraConfig()).thenReturn(mock(AsgInfraConfig.class));
    when(asgCommandRequest.getAsgInfraConfig().getRegion()).thenReturn("us-west-2");
    when(asgCommandRequest.getAsgInfraConfig().getAwsConnectorDTO()).thenReturn(mock(AwsConnectorDTO.class));

    LogCallback logCallback = mock(LogCallback.class);

    AsgSdkManager result = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback);
    assertEquals(10, result.getSteadyStateTimeOutInMinutes().intValue());
    verify(awsUtils).getAmazonEc2Client(any(Regions.class), any(AwsInternalConfig.class));
    verify(awsUtils).getAmazonAutoScalingClient(any(Regions.class), any(AwsInternalConfig.class));
    verify(awsUtils).getAwsInternalConfig(any(AwsConnectorDTO.class), any(String.class));
    assertSame(logCallback, result.getLogCallback());
  }
}
