/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.GetTemplateResult;
import com.amazonaws.services.cloudformation.model.GetTemplateSummaryResult;
import com.amazonaws.services.cloudformation.model.ParameterDeclaration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsCFHelperServiceDelegateImplTest extends CategoryTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsCFHelperServiceDelegateImpl awsCFHelperServiceDelegate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetParamsData() {
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(mockClient).when(awsCFHelperServiceDelegate).getAmazonCloudFormationClient(any(), any());
    doReturn(new GetTemplateSummaryResult().withParameters(
                 new ParameterDeclaration().withParameterKey("k1").withParameterType("t1").withDefaultValue("d1"),
                 new ParameterDeclaration().withParameterKey("k2").withParameterType("t2").withDefaultValue("d2")))
        .when(mockClient)
        .getTemplateSummary(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<AwsCFTemplateParamsData> paramsData = awsCFHelperServiceDelegate.getParamsData(
        AwsInternalConfig.builder().build(), "us-east-1", "url", "body", null, null, null);
    assertThat(paramsData).isNotNull();
    assertThat(paramsData.size()).isEqualTo(2);
    verifyParamsData(paramsData.get(0), "k1", "t1", "d1");
    verifyParamsData(paramsData.get(1), "k2", "t2", "d2");
  }

  private void verifyParamsData(AwsCFTemplateParamsData data, String key, String type, String defaultVal) {
    assertThat(data).isNotNull();
    assertThat(data.getParamKey()).isEqualTo(key);
    assertThat(data.getParamType()).isEqualTo(type);
    assertThat(data.getDefaultValue()).isEqualTo(defaultVal);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetStackBody() {
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(awsCFHelperServiceDelegate).getAmazonCloudFormationClient(any(), any());
    doReturn(new GetTemplateResult().withTemplateBody("body")).when(mockClient).getTemplate(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    String body = awsCFHelperServiceDelegate.getStackBody(AwsInternalConfig.builder().build(), "us-east-1", "stackId");
    assertThat(body).isEqualTo("body");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetCapabilities() {
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(awsCFHelperServiceDelegate).getAmazonCloudFormationClient(any(), any());
    doReturn(new GetTemplateSummaryResult().withCapabilities("c1", "c2")).when(mockClient).getTemplateSummary(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<String> capabilities =
        awsCFHelperServiceDelegate.getCapabilities(AwsInternalConfig.builder().build(), "us-east-1", "foo", "body");
    assertThat(capabilities).isNotNull();
    assertThat(capabilities.size()).isEqualTo(2);
    assertThat(capabilities.get(0)).isEqualTo("c1");
    assertThat(capabilities.get(1)).isEqualTo("c2");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testS3TemplatePath() {
    String s3Path = "https://anil-harness-test.s3.amazonaws.com/anilTest/basicCf.yaml";
    String s3PathWithPlus = "https://anil-harness-test.s3.amazonaws.com/anil%2Btest/basicCf.yaml";
    String s3PathWithSpace = "https://anil-harness-test.s3.amazonaws.com/anil+test/basicCf.yaml";
    String s3PathWithMultipleSpace = "https://anil-harness-test.s3.amazonaws.com/anil+multiple+space+test/basicCf.yaml";
    String s3SpaceInFileName = "https://anil-harness-test.s3.amazonaws.com/anilSpaceInFileName/basic+test.yaml";
    String s3PlusInFileName = "https://anil-harness-test.s3.amazonaws.com/anilPlusInFileName/basic%2Btest.yaml";
    String s3SpaceInFolderAndFileName =
        "https://anil-harness-test.s3.amazonaws.com/anil+test/folder/folder%2Btest/basic+test.yaml";

    String normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3Path);
    assertThat(normalizedPath).isEqualTo(s3Path);

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3PathWithPlus);
    assertThat(normalizedPath).isEqualTo(s3PathWithPlus);

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3PathWithSpace);
    assertThat(normalizedPath).isEqualTo("https://anil-harness-test.s3.amazonaws.com/anil%20test/basicCf.yaml");

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3PathWithMultipleSpace);
    assertThat(normalizedPath)
        .isEqualTo("https://anil-harness-test.s3.amazonaws.com/anil%20multiple%20space%20test/basicCf.yaml");

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3SpaceInFileName);
    assertThat(normalizedPath)
        .isEqualTo("https://anil-harness-test.s3.amazonaws.com/anilSpaceInFileName/basic%20test.yaml");

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3PlusInFileName);
    assertThat(normalizedPath)
        .isEqualTo("https://anil-harness-test.s3.amazonaws.com/anilPlusInFileName/basic%2Btest.yaml");

    normalizedPath = awsCFHelperServiceDelegate.normalizeS3TemplatePath(s3SpaceInFolderAndFileName);
    assertThat(normalizedPath)
        .isEqualTo("https://anil-harness-test.s3.amazonaws.com/anil%20test/folder/folder%2Btest/basic%20test.yaml");
  }
}
