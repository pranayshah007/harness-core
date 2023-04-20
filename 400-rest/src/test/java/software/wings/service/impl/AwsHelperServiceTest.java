/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AwsAutoScaleException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AWSTemporaryCredentials;
import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.Activity;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.BatchGetImageRequest;
import com.amazonaws.services.ecr.model.BatchGetImageResult;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.Image;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@OwnedBy(CDP)
public class AwsHelperServiceTest extends WingsBaseTest {
  @Rule public WireMockRule wireMockRule = new WireMockRule(9877);
  @Mock AwsConfig awsConfig;
  @Mock private AwsCallTracker tracker;
  @Mock private EncryptionService encryptionService;
  @Mock private AwsApiHelperService awsApiHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(awsConfig.isCertValidationRequired()).thenReturn(false);
    when(awsConfig.getDefaultRegion()).thenReturn(Regions.US_EAST_1.getName());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetInstanceId() {
    AwsHelperService awsHelperService = new AwsHelperService();
    assertThat(awsHelperService.getHostnameFromPrivateDnsName("ip-172-31-18-241.ec2.internal"))
        .isEqualTo("ip-172-31-18-241");
    assertThat(awsHelperService.getHostnameFromPrivateDnsName("ip-172-31-18-241.us-west-2.compute.internal"))
        .isEqualTo("ip-172-31-18-241");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDescribeAutoScalingGroupActivities() {
    Activity incompleteActivity = new Activity()
                                      .withActivityId("TestID1")
                                      .withAutoScalingGroupName("TestAutoScalingGroup")
                                      .withCause("TestCause1")
                                      .withDescription("TestDescription1")
                                      .withDetails("TestDetails1")
                                      .withProgress(50)
                                      .withStatusCode("TestStatusCode1")
                                      .withStatusMessage("TestStatusMessage1");

    Activity completeActivity = new Activity()
                                    .withActivityId("TestID2")
                                    .withAutoScalingGroupName("TestAutoScalingGroup")
                                    .withCause("TestCause2")
                                    .withDescription("TestDescription2")
                                    .withDetails("TestDetails2")
                                    .withProgress(100)
                                    .withStatusCode("TestStatusCode2")
                                    .withStatusMessage("TestStatusMessage2");

    DescribeScalingActivitiesResult result =
        new DescribeScalingActivitiesResult().withActivities(incompleteActivity, completeActivity);

    AmazonAutoScalingClient client = mock(AmazonAutoScalingClient.class);
    when(client.describeScalingActivities(any(DescribeScalingActivitiesRequest.class))).thenReturn(result);

    LogCallback logCallback = mock(LogCallback.class);

    List<String> logResult = new ArrayList<>();

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        logResult.add((String) invocation.getArguments()[0]);
        return null;
      }
    })
        .when(logCallback)
        .saveExecutionLog(Mockito.anyString());

    Set<String> completedActivities = new HashSet<>();

    AwsHelperService awsHelperService = new AwsHelperService();
    AwsCallTracker mockTracker = mock(AwsCallTracker.class);
    doNothing().when(mockTracker).trackASGCall(anyString());
    on(awsHelperService).set("tracker", mockTracker);
    awsHelperService.describeAutoScalingGroupActivities(
        client, "TestAutoScalingGroup", completedActivities, logCallback, false);

    assertThat(logResult.size()).isEqualTo(2);

    assertThat(logResult.get(0))
        .isEqualTo(
            "AutoScalingGroup [TestAutoScalingGroup] activity [TestDescription1] progress [50 percent] , statuscode [TestStatusCode1]  details [TestDetails1]");
    assertThat(logResult.get(1))
        .isEqualTo(
            "AutoScalingGroup [TestAutoScalingGroup] activity [TestDescription2] progress [100 percent] , statuscode [TestStatusCode2]  details [TestDetails2]");

    assertThat(completedActivities.size()).isEqualTo(1);
    assertThat(completedActivities).contains("TestID2");

    logResult.clear();
    completedActivities.clear();

    awsHelperService.describeAutoScalingGroupActivities(
        client, "TestAutoScalingGroup", completedActivities, logCallback, true);

    assertThat(logResult.size()).isEqualTo(2);

    assertThat(logResult.get(0))
        .isEqualTo(
            "AutoScalingGroup [TestAutoScalingGroup] activity [TestDescription1] progress [50 percent] , statuscode [TestStatusCode1]  details [TestDetails1] cause [TestCause1]");
    assertThat(logResult.get(1))
        .isEqualTo(
            "AutoScalingGroup [TestAutoScalingGroup] activity [TestDescription2] progress [100 percent] , statuscode [TestStatusCode2]  details [TestDetails2] cause [TestCause2]");

    assertThat(completedActivities.size()).isEqualTo(1);
    assertThat(completedActivities).contains("TestID2");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleAutoScaleException() throws IllegalAccessException {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String autoScalingGroupName = "ASG Name";
    String region = "us-west-1";

    AmazonAutoScalingException awsAsException =
        new AmazonAutoScalingException("New SetDesiredCapacity value 2 is above max value 1 for the AutoScalingGroup.");
    awsAsException.setServiceName("AmazonAutoScaling");
    awsAsException.setStatusCode(400);
    awsAsException.setRequestId("730a9748-4935-485e-a525-3fb0052af1fe");
    awsAsException.setErrorCode("ValidationError");

    SetDesiredCapacityRequest request =
        new SetDesiredCapacityRequest().withAutoScalingGroupName(autoScalingGroupName).withDesiredCapacity(1);
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    DescribeScalingActivitiesResult describeScalingActivitiesResult = mock(DescribeScalingActivitiesResult.class);
    when(mockClient.describeScalingActivities(any(DescribeScalingActivitiesRequest.class)))
        .thenReturn(describeScalingActivitiesResult);
    doThrow(awsAsException).when(mockClient).setDesiredCapacity(request);

    EncryptionService mockEncryptionService = mock(EncryptionService.class);
    EncryptableSetting encryptableSetting = mock(EncryptableSetting.class);
    doReturn(encryptableSetting).when(mockEncryptionService).decrypt(any(), any(), eq(false));

    AwsHelperServiceManager awsHelperServiceManager = new AwsHelperServiceManager();
    AwsHelperService awsHelperService = spy(new AwsHelperService());
    doReturn(mockClient).when(awsHelperService).getAmazonAutoScalingClient(any(), any());
    AwsCallTracker mockTracker = mock(AwsCallTracker.class);
    doNothing().when(mockTracker).trackCFCall(anyString());
    on(awsHelperService).set("tracker", mockTracker);
    on(awsHelperService).set("awsApiHelperService", awsApiHelperService);
    FieldUtils.writeField(awsHelperService, "encryptionService", mockEncryptionService, true);
    FieldUtils.writeField(awsHelperServiceManager, "awsHelperService", awsHelperService, true);
    FieldUtils.writeField(awsHelperServiceManager, "tracker", mockTracker, true);
    FieldUtils.writeField(awsHelperServiceManager, "encryptionService", mockEncryptionService, true);
    FieldUtils.writeField(awsHelperServiceManager, "awsApiHelperService", awsApiHelperService, true);

    doCallRealMethod().when(awsApiHelperService).handleAmazonClientException(any());
    doCallRealMethod().when(awsApiHelperService).handleAmazonServiceException(any());

    try {
      awsHelperServiceManager.setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
          AwsConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Collections.emptyList(), region,
          autoScalingGroupName, Integer.valueOf(1), new ManagerExecutionLogCallback(), 30);
    } catch (AwsAutoScaleException autoScaleException) {
      assertThat(awsAsException.getMessage()).isEqualTo(autoScaleException.getMessage());
      assertThat(ErrorCode.GENERAL_ERROR).isEqualTo(autoScaleException.getCode());
      assertThat(WingsException.USER).isEqualTo(autoScaleException.getReportTargets());
    }
  }

  private String jsonToStringConverter(Object object) {
    ObjectMapper mapper = new ObjectMapper();
    String json = null;
    try {
      json = mapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return json;
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetCredentialsForIAMROleOnDelegate() {
    String url = "http://localhost:9877/";
    AWSTemporaryCredentials credentials = AWSTemporaryCredentials.builder()
                                              .code("SUCCESS")
                                              .accessKeyId(ACCESS_KEY)
                                              .secretKey(String.valueOf(SECRET_KEY))
                                              .build();

    String credentialsString = jsonToStringConverter(credentials);

    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/"))
                             .willReturn(aResponse().withBody("role").withStatus(200)));
    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/role"))
                             .willReturn(aResponse().withBody(credentialsString).withStatus(200)));

    AwsHelperService service = new AwsHelperService();

    AWSTemporaryCredentials cred = service.getCredentialsForIAMROleOnDelegate(url, awsConfig);
    assertThat(cred).isEqualTo(credentials);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetCredentialsForIAMRoleOnDelegateWithErrorResponseCodeException() {
    String url = "http://localhost:9877/";
    AWSTemporaryCredentials credentials = AWSTemporaryCredentials.builder()
                                              .code("SUCCESS")
                                              .accessKeyId(ACCESS_KEY)
                                              .secretKey(String.valueOf(SECRET_KEY))
                                              .build();

    String credentialsString = jsonToStringConverter(credentials);

    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/"))
                             .willReturn(aResponse().withBody("role").withStatus(200)));
    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/role"))
                             .willReturn(aResponse().withBody(credentialsString).withStatus(400)));

    AwsHelperService service = new AwsHelperService();

    assertThatThrownBy(() -> service.getCredentialsForIAMROleOnDelegate(url, awsConfig))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetCredentialsForIAMRoleOnDelegateWithEmptyRoleException() {
    String url = "http://localhost:9877/";
    AWSTemporaryCredentials credentials = AWSTemporaryCredentials.builder()
                                              .code("SUCCESS")
                                              .accessKeyId(ACCESS_KEY)
                                              .secretKey(String.valueOf(SECRET_KEY))
                                              .build();

    String credentialsString = jsonToStringConverter(credentials);

    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/"))
                             .willReturn(aResponse().withBody("").withStatus(200)));
    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/role"))
                             .willReturn(aResponse().withBody(credentialsString).withStatus(200)));

    AwsHelperService service = new AwsHelperService();

    assertThatThrownBy(() -> service.getCredentialsForIAMROleOnDelegate(url, awsConfig))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testGetCredentialsForIAMROleOnDelegateWithCertValidation() {
    String url = "http://localhost:9877/";
    AWSTemporaryCredentials credentials = AWSTemporaryCredentials.builder()
                                              .code("SUCCESS")
                                              .accessKeyId(ACCESS_KEY)
                                              .secretKey(String.valueOf(SECRET_KEY))
                                              .build();

    String credentialsString = jsonToStringConverter(credentials);

    when(awsConfig.isCertValidationRequired()).thenReturn(true);

    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/"))
                             .willReturn(aResponse().withBody("role").withStatus(200)));
    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/role"))
                             .willReturn(aResponse().withBody(credentialsString).withStatus(200)));

    AwsHelperService service = new AwsHelperService();

    AWSTemporaryCredentials cred = service.getCredentialsForIAMROleOnDelegate(url, awsConfig);
    assertThat(cred).isEqualTo(credentials);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFetchLabels() {
    wireMockRule.stubFor(get(urlEqualTo("/latest/meta-data/iam/security-credentials/"))
                             .willReturn(aResponse().withBody("role").withStatus(200)));
    AmazonECRClient ecrClient = mock(AmazonECRClient.class);
    AwsHelperService service = spy(new AwsHelperService());
    doReturn(ecrClient).when(service).getAmazonEcrClient(any(), any(String.class));

    Image image = new Image();
    image.setImageManifest(
        "{\"history\":[{\"v1Compatibility\": \"{\\\"config\\\":{\\\"Labels\\\":{\\\"key1\\\":\\\"val1\\\"}}}\"}]}");
    BatchGetImageResult result = new BatchGetImageResult();
    result.setImages(Collections.singletonList(image));
    when(ecrClient.batchGetImage(new BatchGetImageRequest()
                                     .withRepositoryName("imageName")
                                     .withImageIds(new ImageIdentifier().withImageTag("latest"))
                                     .withAcceptedMediaTypes("application/vnd.docker.distribution.manifest.v1+json")))
        .thenReturn(result);
    doReturn(ecrClient).when(awsApiHelperService).getAmazonEcrClient(any(), any(String.class));
    doCallRealMethod().when(awsApiHelperService).attachCredentialsAndBackoffPolicy(any(), any());
    doCallRealMethod().when(awsApiHelperService).fetchLabels(any(), any(), any(), any());
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);

    assertThat(
        service.fetchLabels(AwsConfig.builder().accessKey("qwer".toCharArray()).secretKey("qwer".toCharArray()).build(),
            ArtifactStreamAttributes.builder().region(Regions.US_EAST_1.getName()).imageName("imageName").build(),
            Lists.newArrayList("latest")))
        .isEqualTo(ImmutableMap.<String, String>builder().put("key1", "val1").build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateAwsAccountCredentialFailsInvalidCredentials() {
    when(awsApiHelperService.getAmazonEc2Client(any())).thenCallRealMethod();
    doCallRealMethod().when(awsApiHelperService).attachCredentialsAndBackoffPolicy(any(), any());
    AwsHelperService service = spy(new AwsHelperService());
    Reflect.on(service).set("tracker", tracker);
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);
    assertThatThrownBy(() -> service.validateAwsAccountCredential(ACCESS_KEY, SECRET_KEY))
        .isInstanceOf(InvalidRequestException.class);
    verify(tracker, never()).trackEC2Call("Describe Regions");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListRepositories() {
    String region = "us-west-1";
    DescribeRepositoriesRequest request = new DescribeRepositoriesRequest();
    AmazonECRClient ecrClient = mock(AmazonECRClient.class);
    AwsHelperService service = spy(new AwsHelperService());
    Reflect.on(service).set("encryptionService", encryptionService);
    doReturn(ecrClient).when(awsApiHelperService).getAmazonEcrClient(any(), any(String.class));
    Reflect.on(service).set("tracker", tracker);
    Reflect.on(awsApiHelperService).set("tracker", tracker);
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);

    DescribeRepositoriesResult result = new DescribeRepositoriesResult();
    when(ecrClient.describeRepositories(request)).thenReturn(result);
    when(awsApiHelperService.listRepositories(any(), any(), anyString())).thenCallRealMethod();
    DescribeRepositoriesResult actual = service.listRepositories(awsConfig, null, request, region);
    verify(tracker, times(1)).trackECRCall("List Repositories");
    assertThat(actual).isEqualTo(result);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListRegions() {
    AmazonEC2Client ec2Client = mock(AmazonEC2Client.class);
    AwsHelperService service = spy(new AwsHelperService());
    Reflect.on(service).set("encryptionService", encryptionService);
    doReturn(ec2Client).when(service).getAmazonEc2Client(any());
    doReturn(ec2Client).when(awsApiHelperService).getAmazonEc2Client(any());
    when(service.getAmazonEc2Client(awsConfig)).thenReturn(ec2Client);
    Reflect.on(service).set("tracker", tracker);
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);
    Reflect.on(awsApiHelperService).set("tracker", tracker);

    DescribeRegionsResult result = new DescribeRegionsResult().withRegions(
        new Region().withRegionName("us-east-1"), new Region().withRegionName("us-east-2"));
    when(ec2Client.describeRegions()).thenReturn(result);
    when(awsApiHelperService.listRegions(any())).thenCallRealMethod();
    List<String> actual = service.listRegions(awsConfig, null);
    verify(tracker, times(1)).trackEC2Call("List Regions");
    assertThat(actual).hasSize(2);
    assertThat(actual).containsExactly("us-east-1", "us-east-2");
  }

  @Test(expected = com.amazonaws.SdkClientException.class)
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testAttachCredentialsAndBackoffPolicyWithIRSA() throws Exception {
    Map<String, String> envVarsMap = Map.ofEntries(Map.entry(SDKGlobalConfiguration.AWS_ROLE_ARN_ENV_VAR, "abcd"),
        Map.entry(SDKGlobalConfiguration.AWS_WEB_IDENTITY_ENV_VAR, "/jkj"));
    new EnvironmentVariables(envVarsMap).execute(() -> {
      AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
      when(awsInternalConfig.isUseEc2IamCredentials()).thenReturn(false);
      when(awsInternalConfig.isUseIRSA()).thenReturn(true);
      when(awsInternalConfig.isAssumeCrossAccountRole()).thenReturn(false);
      AwsClientBuilder awsClientBuilder = AmazonEC2ClientBuilder.standard().withRegion("us-east-1");
      doCallRealMethod()
          .when(awsApiHelperService)
          .attachCredentialsAndBackoffPolicy(eq(awsClientBuilder), eq(awsInternalConfig));
      doCallRealMethod().when(awsApiHelperService).getAwsCredentialsProvider(eq(awsInternalConfig));

      awsApiHelperService.attachCredentialsAndBackoffPolicy(awsClientBuilder, awsInternalConfig);

      assertThat(awsClientBuilder.getCredentials()).isInstanceOf(WebIdentityTokenCredentialsProvider.class);
      awsClientBuilder.getCredentials().getCredentials().getAWSSecretKey();
    });
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetAmazonAWSSecurityTokenServiceClient() {
    AWSSecurityTokenServiceClient awsSecurityTokenServiceClient = new AWSSecurityTokenServiceClient();
    when(awsApiHelperService.getAWSSecurityTokenServiceClient(any(), any())).thenReturn(awsSecurityTokenServiceClient);

    AwsHelperService service = spy(new AwsHelperService());
    Reflect.on(service).set("tracker", tracker);
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);
    AWSSecurityTokenServiceClient result = service.getAmazonAWSSecurityTokenServiceClient(new AwsConfig(), "region");
    assertThat(result).isEqualTo(awsSecurityTokenServiceClient);
    verify(awsApiHelperService, times(1)).getAWSSecurityTokenServiceClient(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetAWSCredentialsProvider() {
    AWSCredentialsProvider awsCredentialsProvider = mock(AWSCredentialsProvider.class);
    when(awsApiHelperService.getAwsCredentialsProvider(any())).thenReturn(awsCredentialsProvider);

    AwsHelperService service = spy(new AwsHelperService());
    Reflect.on(service).set("tracker", tracker);
    Reflect.on(service).set("awsApiHelperService", awsApiHelperService);
    AWSCredentialsProvider result = service.getAWSCredentialsProvider(new AwsConfig());
    assertThat(result).isEqualTo(awsCredentialsProvider);
    verify(awsApiHelperService, times(1)).getAwsCredentialsProvider(any());
  }
}
