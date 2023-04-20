/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.AWS_OVERRIDE_REGION;
import static io.harness.beans.FeatureName.NEW_KUBECTL_VERSION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SAINATH;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SETTING_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.ccm.setup.service.support.intfc.AWSCEConfigValidationService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.shell.AccessType;

import software.wings.WingsBaseTest;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.ElkConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostConnectionAttributes.ConnectionType;
import software.wings.beans.InstanaConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.ScalyrConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.ValidationResult;
import software.wings.beans.config.LogzConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfigValidationResponse;
import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.gcp.GcpHelperServiceManager;
import software.wings.service.impl.newrelic.NewRelicApplicationsResponse;
import software.wings.service.intfc.AwsHelperResourceService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.elk.ElkAnalysisService;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.splunk.SplunkDelegateService;
import software.wings.service.intfc.sumo.SumoDelegateService;
import software.wings.settings.validation.ConnectivityValidationDelegateResponse;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.splunk.Job;
import com.splunk.JobArgs;
import com.splunk.JobCollection;
import dev.morphia.query.Query;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;

/**
 * Created by Pranjal on 09/14/2018
 */
@OwnedBy(CDC)
public class SettingValidationServiceTest extends WingsBaseTest {
  @Inject private SettingValidationService settingValidationService;
  @Inject private AnalysisService analysisService;
  @Inject private NewRelicService newRelicService;
  @Inject private SplunkDelegateService splunkDelegateService;
  @Inject private ElkDelegateService elkDelegateService;
  @Inject private LogzDelegateService logzDelegateService;
  @Inject private SumoDelegateService sumoDelegateService;
  @Inject private InstanaDelegateService instanaDelegateService;
  @Inject private NewRelicDelegateService newRelicDelegateService;
  @Inject private AppdynamicsDelegateService appdynamicsDelegateService;
  @Inject private DynaTraceDelegateService dynaTraceDelegateService;
  @Inject private APMDelegateService apmDelegateService;
  @Inject private RequestExecutor requestExecutor;
  @Mock private DelegateService delegateService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private ElkAnalysisService elkAnalysisService;
  @Mock private AWSCEConfigValidationService awsceConfigValidationService;
  @Mock private AwsEc2HelperServiceManager awsEc2HelperServiceManager;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AwsHelperResourceService awsHelperResourceService;
  @Mock private SecretManager secretManager;

  private String accountId;
  private SplunkDelegateService spySplunkDelegateService;
  private RequestExecutor spyRequestExecutor;
  private Query<SettingAttribute> spyQuery;
  private static final String NG_SMTP_SETTINGS_PREFIX = "ngSmtpConfig-";

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setupTests() throws Exception {
    accountId = generateUuid();
    spyRequestExecutor = spy(requestExecutor);

    FieldUtils.writeField(analysisService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(newRelicService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(settingValidationService, "analysisService", analysisService, true);
    FieldUtils.writeField(settingValidationService, "newRelicService", newRelicService, true);
    FieldUtils.writeField(elkDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(logzDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(instanaDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(newRelicDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(appdynamicsDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(dynaTraceDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(apmDelegateService, "requestExecutor", spyRequestExecutor, true);
    FieldUtils.writeField(settingValidationService, "awsEc2HelperServiceManager", awsEc2HelperServiceManager, true);
    FieldUtils.writeField(settingValidationService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(settingValidationService, "awsHelperResourceService", awsHelperResourceService, true);
    FieldUtils.writeField(settingValidationService, "secretManager", secretManager, true);

    spySplunkDelegateService = spy(splunkDelegateService);
    when(delegateProxyFactory.getV2(eq(SplunkDelegateService.class), any())).thenReturn(spySplunkDelegateService);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    when(delegateProxyFactory.getV2(eq(LogzDelegateService.class), any())).thenReturn(logzDelegateService);
    when(delegateProxyFactory.getV2(eq(SumoDelegateService.class), any())).thenReturn(sumoDelegateService);
    when(delegateProxyFactory.getV2(eq(InstanaDelegateService.class), any())).thenReturn(instanaDelegateService);
    when(delegateProxyFactory.getV2(eq(NewRelicDelegateService.class), any())).thenReturn(newRelicDelegateService);
    when(delegateProxyFactory.getV2(eq(AppdynamicsDelegateService.class), any()))
        .thenReturn(appdynamicsDelegateService);
    when(delegateProxyFactory.getV2(eq(DynaTraceDelegateService.class), any())).thenReturn(dynaTraceDelegateService);
    when(delegateProxyFactory.getV2(eq(APMDelegateService.class), any())).thenReturn(apmDelegateService);
    when(featureFlagService.isEnabled(NEW_KUBECTL_VERSION, ACCOUNT_ID)).thenReturn(false);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidCredentialsSplunkConnector() {
    com.splunk.Service splunkService = Mockito.mock(com.splunk.Service.class);
    when(splunkService.getJobs()).thenThrow(new RuntimeException("invalid credentials"));
    doReturn(splunkService).when(spySplunkDelegateService).initSplunkService(any(SplunkConfig.class), any());
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(createSplunkConfig()).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: invalid credentials");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongUrlSplunkConnector() {
    final SplunkConfig splunkConfig = SplunkConfig.builder()
                                          .splunkUrl("https://google.com")
                                          .username(generateUuid())
                                          .password(generateUuid().toCharArray())
                                          .build();
    splunkConfig.setDecrypted(true);
    com.splunk.Service splunkService = Mockito.mock(com.splunk.Service.class);
    doThrow(new DataCollectionException("HttpException: HTTP 404")).when(splunkService).getJobs();
    doReturn(splunkService).when(spySplunkDelegateService).initSplunkService(any(SplunkConfig.class), any());
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(splunkConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("HttpException: HTTP 404");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlSplunkConnector() {
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(createSplunkConfig()).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("MalformedURLException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidSplunkConnector() {
    JobCollection jobCollection = Mockito.mock(JobCollection.class);
    when(jobCollection.create(any(), any(JobArgs.class))).thenReturn(Mockito.mock(Job.class));
    com.splunk.Service splunkService = Mockito.mock(com.splunk.Service.class);
    when(splunkService.getJobs()).thenReturn(jobCollection);
    doReturn(splunkService).when(spySplunkDelegateService).initSplunkService(any(SplunkConfig.class), any());
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(createSplunkConfig()).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  private SplunkConfig createSplunkConfig() {
    final SplunkConfig splunkConfig = SplunkConfig.builder()
                                          .splunkUrl(generateUuid())
                                          .accountId(accountId)
                                          .username(generateUuid())
                                          .username(generateUuid())
                                          .password(generateUuid().toCharArray())
                                          .build();
    splunkConfig.setDecrypted(true);
    return splunkConfig;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenUsernameAndNoPasswordElkConnector() {
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute()
            .withAccountId(accountId)
            .withName(generateUuid())
            .withValue(ElkConfig.builder().elkUrl(generateUuid()).username(generateUuid()).build())
            .build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("IllegalArgumentException: User name is given but password is empty");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenPasswordAndNoUsernameElkConnector() {
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute()
            .withAccountId(accountId)
            .withName(generateUuid())
            .withValue(ElkConfig.builder().elkUrl(generateUuid()).password(generateUuid().toCharArray()).build())
            .build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("IllegalArgumentException: User name is empty but password is given");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlElkConnector() {
    final ElkConfig elkConfig = ElkConfig.builder().elkUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(elkConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlElkConnector() {
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute()
            .withAccountId(accountId)
            .withName(generateUuid())
            .withValue(ElkConfig.builder().elkUrl("https://elk-example.com/").build())
            .build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: elk-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidElkConnector() {
    doReturn(new Object()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute()
            .withAccountId(accountId)
            .withName(generateUuid())
            .withValue(ElkConfig.builder().elkUrl("https://elk-example.com/").build())
            .build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlLogzConnector() {
    final LogzConfig logzConfig = LogzConfig.builder().logzUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(logzConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlLogzConnector() {
    final LogzConfig logzConfig =
        LogzConfig.builder().logzUrl("https://logz-example.com").token(generateUuid().toCharArray()).build();
    logzConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(logzConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: logz-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenLogzConnector() {
    final LogzConfig logzConfig =
        LogzConfig.builder().logzUrl("https://api.logz.io").token(generateUuid().toCharArray()).build();
    logzConfig.setDecrypted(true);
    doThrow(
        new DataCollectionException(
            "Response code: 401, Message: Unauthorized, Error: {\"code\":401,\"message\":\"HTTP 401 Unauthorized\""))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(logzConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .contains(
            "Error: Response code: 401, Message: Unauthorized, Error: {\"code\":401,\"message\":\"HTTP 401 Unauthorized\"");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidLogzConnector() {
    final LogzConfig logzConfig =
        LogzConfig.builder().logzUrl("https://logz-example.com").token(generateUuid().toCharArray()).build();
    logzConfig.setDecrypted(true);
    doReturn(new Object()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(logzConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlSumoConnector() {
    final SumoConfig sumoConfig = SumoConfig.builder()
                                      .sumoUrl("ranom-invalid-url")
                                      .accessId(generateUuid().toCharArray())
                                      .accessKey(generateUuid().toCharArray())
                                      .build();
    sumoConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(sumoConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: no protocol: " + sumoConfig.getSumoUrl());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlSumoConnector() {
    final SumoConfig sumoConfig = SumoConfig.builder()
                                      .sumoUrl("https://sumo-example.com")
                                      .accessId(generateUuid().toCharArray())
                                      .accessKey(generateUuid().toCharArray())
                                      .build();
    sumoConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(sumoConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("SumoClientException: Error reading server response; "
            + "Cause: UnknownHostException: sumo-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWongTokenSumoConnector() {
    final SumoConfig sumoConfig = SumoConfig.builder()
                                      .sumoUrl("https://api.us2.sumologic.com/api/v1/")
                                      .accessId(generateUuid().toCharArray())
                                      .accessKey(generateUuid().toCharArray())
                                      .build();
    sumoConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(sumoConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("SumoServerException: ");
    assertThat(validationResult.getErrorMessage()).contains("\"status\" : 401");
    assertThat(validationResult.getErrorMessage()).contains("\"message\" : \"Credential could not be verified.\"\n");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlInstanaConnector() {
    final InstanaConfig instanaConfig = InstanaConfig.builder().instanaUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(instanaConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlInstanaConnector() {
    final InstanaConfig instanaConfig = InstanaConfig.builder()
                                            .instanaUrl("https://instana-example.com")
                                            .apiToken(generateUuid().toCharArray())
                                            .build();
    instanaConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(instanaConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: instana-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenInstanaConnector() {
    final InstanaConfig instanaConfig = InstanaConfig.builder()
                                            .instanaUrl("https://integration-harness.instana.io/")
                                            .apiToken(generateUuid().toCharArray())
                                            .build();
    instanaConfig.setDecrypted(true);
    doThrow(new DataCollectionException(
                "Response code: 401, Message: Unauthorized, Error: {\"errors\":[\"Unauthorized request\"]}"))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(instanaConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Error: Response code: 401, Message: Unauthorized, Error: {\"errors\":[\"Unauthorized request\"]}");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidInstanaConnector() {
    final InstanaConfig instanaConfig = InstanaConfig.builder()
                                            .instanaUrl("https://integration-harness.instana.io/")
                                            .apiToken(generateUuid().toCharArray())
                                            .build();
    instanaConfig.setDecrypted(true);
    doReturn(new Object()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(instanaConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlNewRelicConnector() {
    final NewRelicConfig newRelicConfig = NewRelicConfig.builder().newRelicUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(newRelicConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlNewRelicConnector() {
    final NewRelicConfig newRelicConfig = NewRelicConfig.builder()
                                              .newRelicUrl("https://newrelic-example.com")
                                              .apiKey(generateUuid().toCharArray())
                                              .build();
    newRelicConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(newRelicConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: newrelic-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenNewRelicConnector() {
    final NewRelicConfig newRelicConfig =
        NewRelicConfig.builder().newRelicUrl("https://api.newrelic.com").apiKey(generateUuid().toCharArray()).build();
    newRelicConfig.setDecrypted(true);
    doThrow(
        new DataCollectionException(
            "Response code: 401, Message: Unauthorized, Error: {\"error\":{\"title\":\"The API key provided is invalid\"}}"))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(newRelicConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo(
            "Error: Response code: 401, Message: Unauthorized, Error: {\"error\":{\"title\":\"The API key provided is invalid\"}}");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidNewRelicConnector() {
    final NewRelicConfig newRelicConfig =
        NewRelicConfig.builder().newRelicUrl("https://api.newrelic.com").apiKey(generateUuid().toCharArray()).build();
    newRelicConfig.setDecrypted(true);
    doReturn(NewRelicApplicationsResponse.builder().applications(Lists.newArrayList()).build())
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(newRelicConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlAppdConnector() {
    final AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder().controllerUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(appDynamicsConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlAppdConnector() {
    final AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                                    .controllerUrl("https://appd-example.com")
                                                    .accountname(generateUuid())
                                                    .username(generateUuid())
                                                    .password(generateUuid().toCharArray())
                                                    .build();
    appDynamicsConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(appDynamicsConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: appd-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenAppdConnector() {
    final AppDynamicsConfig appDynamicsConfig =
        AppDynamicsConfig.builder()
            .controllerUrl("https://harness-test.saas.appdynamics.com/controller/")
            .accountname("harness-test")
            .username(generateUuid())
            .password(generateUuid().toCharArray())
            .build();
    appDynamicsConfig.setDecrypted(true);
    doThrow(new DataCollectionException("Response code: 401, Message: Unauthorized, Error: "))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(appDynamicsConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Error: Response code: 401, Message: Unauthorized, Error: ");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidAppdConnector() {
    final AppDynamicsConfig appDynamicsConfig =
        AppDynamicsConfig.builder()
            .controllerUrl("https://harness-test.saas.appdynamics.com/controller/")
            .accountname(generateUuid())
            .username(generateUuid())
            .password(generateUuid().toCharArray())
            .build();
    appDynamicsConfig.setDecrypted(true);
    doReturn(new ArrayList<>()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(appDynamicsConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlDynatraceConnector() {
    final DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder().dynaTraceUrl(generateUuid()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(dynaTraceConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlDynatraceConnector() {
    final DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder()
                                                .dynaTraceUrl("https://dynatrace-example.com")
                                                .apiToken(generateUuid().toCharArray())
                                                .build();
    dynaTraceConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(dynaTraceConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).isEqualTo("Error: dynatrace-example.com: Name or service not known");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenDynatraceConnector() {
    final DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder()
                                                .dynaTraceUrl("https://lvd51754.live.dynatrace.com/")
                                                .apiToken(generateUuid().toCharArray())
                                                .build();
    dynaTraceConfig.setDecrypted(true);
    doThrow(
        new DataCollectionException(
            "Response code: 401, Message: Unauthorized, Error: {\"error\":{\"code\":401,\"message\":\"Token Authentication failed\"}}"))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(dynaTraceConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo(
            "Error: Response code: 401, Message: Unauthorized, Error: {\"error\":{\"code\":401,\"message\":\"Token Authentication failed\"}}");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidDynatraceConnector() {
    final DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder()
                                                .dynaTraceUrl("https://lvd51754.live.dynatrace.com/")
                                                .apiToken(generateUuid().toCharArray())
                                                .build();
    dynaTraceConfig.setDecrypted(true);
    doReturn(new ArrayList<>()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(dynaTraceConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlPrometheusConnector() {
    final PrometheusConfig prometheusConfig = PrometheusConfig.builder().url(generateUuid() + "/").build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(prometheusConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlPrometheusConnector() {
    final PrometheusConfig prometheusConfig = PrometheusConfig.builder().url("https://prometheus-example.com").build();
    prometheusConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(prometheusConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Error while saving configuration. The Base URL must end with a / (forward slash)");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidPrometheusConnector() {
    final PrometheusConfig prometheusConfig = PrometheusConfig.builder().url("https://prometheus-example.com/").build();
    prometheusConfig.setDecrypted(true);
    doReturn(new ArrayList<>()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(prometheusConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenIllegalUrlScalyrConnector() {
    final ScalyrConfig scalyrConfig =
        ScalyrConfig.builder().url(generateUuid() + "/").apiToken(generateUuid().toCharArray()).build();
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(scalyrConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage()).contains("IllegalArgumentException");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenInvalidUrlScalyrConnector() {
    final ScalyrConfig scalyrConfig =
        ScalyrConfig.builder().url("https://scalyr-example.com").apiToken(generateUuid().toCharArray()).build();
    scalyrConfig.setDecrypted(true);
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(scalyrConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo("Error while saving configuration. The Base URL must end with a / (forward slash)");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenWrongTokenScalyrConnector() {
    final ScalyrConfig scalyrConfig =
        ScalyrConfig.builder().url("https://scalyr-example.com/").apiToken(generateUuid().toCharArray()).build();
    scalyrConfig.setDecrypted(true);
    doThrow(
        new DataCollectionException(
            "Response code: 401, Message: , Error: {\"message\": \"Couldn't decode API token ...\",\"status\": \"error/client/badParam\"}"))
        .when(spyRequestExecutor)
        .executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(scalyrConfig).build());
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getErrorMessage())
        .isEqualTo(
            "Error: Response code: 401, Message: , Error: {\"message\": \"Couldn't decode API token ...\",\"status\": \"error/client/badParam\"}");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateConnectivity_whenValidScalyrConnector() {
    final ScalyrConfig scalyrConfig =
        ScalyrConfig.builder().url("https://scalyr-example.com/").apiToken(generateUuid().toCharArray()).build();
    scalyrConfig.setDecrypted(true);
    doReturn(new ArrayList<>()).when(spyRequestExecutor).executeRequest(any(Call.class));
    final ValidationResult validationResult = settingValidationService.validateConnectivity(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(scalyrConfig).build());
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.getErrorMessage()).isEmpty();
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testElkValidate() throws IOException {
    final String url = "https://ec2-34-207-78-53.compute-1.amazonaws.com:9200/";
    final String userName = "username";
    final String password = "password";

    when(wingsPersistence.createQuery(eq(SettingAttribute.class))).thenReturn(spyQuery);
    when(elkAnalysisService.getVersion(any(), any(ElkConfig.class), any())).thenThrow(IOException.class);

    ElkConfig elkConfig = new ElkConfig();
    elkConfig.setAccountId(ACCOUNT_ID);
    elkConfig.setElkConnector(ElkConnector.KIBANA_SERVER);
    elkConfig.setElkUrl(url);
    elkConfig.setUsername(userName);
    elkConfig.setPassword(password.toCharArray());

    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(elkConfig);
    thrown.expect(WingsException.class);
    settingValidationService.validate(attribute);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPcfValidate() throws IllegalAccessException {
    final String url = "https://test.com";
    final String userName = "username";
    final String password = "password";
    PcfConfig pcfConfig = new PcfConfig();
    pcfConfig.setAccountId(ACCOUNT_ID);
    pcfConfig.setEndpointUrl(url);
    pcfConfig.setUsername(userName.toCharArray());
    pcfConfig.setPassword(password.toCharArray());
    pcfConfig.setSkipValidation(true);
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(pcfConfig);
    PcfHelperService pcfHelperService = mock(PcfHelperService.class);
    FieldUtils.writeField(settingValidationService, "pcfHelperService", pcfHelperService, true);

    settingValidationService.validate(attribute);
    verify(pcfHelperService, times(0)).validate(any(), any());

    pcfConfig.setSkipValidation(false);
    settingValidationService.validate(attribute);
    verify(pcfHelperService, times(1)).validate(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateAwsConfig() throws IllegalAccessException {
    SettingAttribute attribute = aSettingAttribute()
                                     .withName(SETTING_NAME)
                                     .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                     .withAccountId(ACCOUNT_ID)
                                     .withValue(AwsConfig.builder()
                                                    .useEc2IamCredentials(false)
                                                    .useEncryptedAccessKey(true)
                                                    .accessKey(ACCESS_KEY.toCharArray())
                                                    .secretKey(SECRET_KEY)
                                                    .build())
                                     .build();

    when(wingsPersistence.createQuery(eq(SettingAttribute.class))).thenReturn(spyQuery);

    doNothing()
        .when(awsEc2HelperServiceManager)
        .validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());

    settingValidationService.validate(attribute);
    verify(awsEc2HelperServiceManager).validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateAwsConfigWithRegion() throws IllegalAccessException {
    SettingAttribute attribute = aSettingAttribute()
                                     .withName(SETTING_NAME)
                                     .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                     .withAccountId(ACCOUNT_ID)
                                     .withValue(AwsConfig.builder()
                                                    .useEc2IamCredentials(false)
                                                    .useEncryptedAccessKey(true)
                                                    .accessKey(ACCESS_KEY.toCharArray())
                                                    .secretKey(SECRET_KEY)
                                                    .defaultRegion("us-east-1")
                                                    .build())
                                     .build();
    when(wingsPersistence.createQuery(eq(SettingAttribute.class))).thenReturn(spyQuery);
    doNothing()
        .when(awsEc2HelperServiceManager)
        .validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());

    when(featureFlagService.isNotEnabled(AWS_OVERRIDE_REGION, ACCOUNT_ID)).thenReturn(true);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> settingValidationService.validate(attribute))
        .withMessageContaining("AWS Override region support is not enabled");
    verify(awsEc2HelperServiceManager, times(0))
        .validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());

    when(featureFlagService.isNotEnabled(AWS_OVERRIDE_REGION, ACCOUNT_ID)).thenReturn(false);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> settingValidationService.validate(attribute))
        .withMessageContaining("Invalid AWS region provided: ");
    verify(awsEc2HelperServiceManager, times(0))
        .validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());

    when(awsHelperResourceService.getAwsRegions())
        .thenReturn(Collections.singletonList(NameValuePair.builder().value("us-east-1").build()));
    settingValidationService.validate(attribute);
    verify(awsEc2HelperServiceManager).validateAwsAccountCredential(eq((AwsConfig) attribute.getValue()), any());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testHostConnectionValidationForPrivateKeyField() {
    HostConnectionAttributes.Builder hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes()
            .withAccessType(AccessType.KEY)
            .withAccountId(UUIDGenerator.generateUuid())
            .withConnectionType(ConnectionType.SSH)
            .withKeyless(false)
            .withUserName("TestUser")
            .withAuthenticationScheme(SSH_KEY);

    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(hostConnectionAttributes.build());

    thrown.expect(InvalidRequestException.class);
    settingValidationService.validate(attribute);

    hostConnectionAttributes.withKey("Test Private Key".toCharArray());
    attribute.setValue(hostConnectionAttributes.build());

    thrown = ExpectedException.none();
    settingValidationService.validate(attribute);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testHostConnectionValidationForUsernameField() {
    HostConnectionAttributes.Builder hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes()
            .withAccessType(AccessType.KEY)
            .withAuthenticationScheme(SSH_KEY)
            .withConnectionType(ConnectionType.SSH)
            .withAccountId(UUIDGenerator.generateUuid())
            .withKey("Test Private Key".toCharArray())
            .withKeyless(false);

    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(hostConnectionAttributes.build());

    thrown.expect(InvalidRequestException.class);
    settingValidationService.validate(attribute);

    hostConnectionAttributes.withUserName("TestUser");
    attribute.setValue(hostConnectionAttributes.build());

    thrown = ExpectedException.none();
    settingValidationService.validate(attribute);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHostConnectionValidationForWrongSshPassword() {
    HostConnectionAttributes.Builder hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes()
            .withAccessType(AccessType.USER_PASSWORD)
            .withAuthenticationScheme(SSH_KEY)
            .withConnectionType(ConnectionType.SSH)
            .withAccountId(UUIDGenerator.generateUuid())
            .withKeyless(false)
            .withSshPassword("testPassword".toCharArray())
            .withEncryptedSshPassword("encryptedTestPassword")
            .withUserName("TestUser");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(hostConnectionAttributes.build());

    when(secretManager.getSecretById(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .hasMessageContaining("Specified password field doesn't exist")
        .isInstanceOf(InvalidRequestException.class);

    when(secretManager.getSecretById(any(), any())).thenReturn(EncryptedData.builder().build());
    assertThatCode(() -> settingValidationService.validate(attribute)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHostConnectionValidationForNonExistingSecrets() {
    HostConnectionAttributes.Builder hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes()
            .withAccessType(AccessType.KEY)
            .withAuthenticationScheme(SSH_KEY)
            .withConnectionType(ConnectionType.SSH)
            .withAccountId(UUIDGenerator.generateUuid())
            .withKey("Test Private Key".toCharArray())
            .withEncryptedPassphrase("Encrypted Passphrase")
            .withEncryptedKey("Encrypted Key")
            .withKeyless(false)
            .withUserName("TestUser");

    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(hostConnectionAttributes.build());

    when(secretManager.getSecretById(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .hasMessageContaining("Specified Encrypted SSH key File doesn't exist")
        .isInstanceOf(InvalidRequestException.class);

    when(secretManager.getSecretById(any(), any())).thenReturn(EncryptedData.builder().build()).thenReturn(null);
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .hasMessageContaining("Specified Encrypted Passphrase field doesn't exist")
        .isInstanceOf(InvalidRequestException.class);

    when(secretManager.getSecretById(any(), any())).thenReturn(EncryptedData.builder().build());
    assertThatCode(() -> settingValidationService.validate(attribute)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGcpConfigSkipValidate() throws IllegalAccessException {
    GcpConfig gcpConfig = GcpConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(gcpConfig);

    GcpHelperServiceManager gcpHelperServiceManager = mock(GcpHelperServiceManager.class);

    FieldUtils.writeField(settingValidationService, "gcpHelperServiceManager", gcpHelperServiceManager, true);

    gcpConfig.setDelegateSelectors(Collections.singletonList("delegate1"));

    // useDelegate = true, skipValidation = true
    gcpConfig.setUseDelegateSelectors(true);
    gcpConfig.setSkipValidation(true);
    settingValidationService.validate(attribute);
    verify(gcpHelperServiceManager, times(0)).validateCredential(any(), any());

    // useDelegate = true, skipValidation = false
    gcpConfig.setUseDelegateSelectors(true);
    gcpConfig.setSkipValidation(false);
    settingValidationService.validate(attribute);
    verify(gcpHelperServiceManager, times(1)).validateCredential(any(), any());

    // useDelegate = false, skipValidation = true
    gcpConfig.setUseDelegateSelectors(false);
    gcpConfig.setSkipValidation(true);
    assertThatExceptionOfType(InvalidArgumentsException.class).isThrownBy(() -> {
      settingValidationService.validate(attribute);
    });

    // useDelegate = false, skipValidation = false
    gcpConfig.setUseDelegateSelectors(false);
    gcpConfig.setSkipValidation(false);
    settingValidationService.validate(attribute);
    verify(gcpHelperServiceManager, times(2)).validateCredential(any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGcpConfigDelegateSelector() throws IllegalAccessException {
    GcpConfig gcpConfig = GcpConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(gcpConfig);

    GcpHelperServiceManager gcpHelperServiceManager = mock(GcpHelperServiceManager.class);

    FieldUtils.writeField(settingValidationService, "gcpHelperServiceManager", gcpHelperServiceManager, true);

    // useDelegate = true, delegateSelector Provided
    gcpConfig.setUseDelegateSelectors(true);
    gcpConfig.setDelegateSelectors(Collections.singletonList("delegate1"));
    settingValidationService.validate(attribute);
    verify(gcpHelperServiceManager, times(1)).validateCredential(any(), any());

    // useDelegate = true, no delegateSelector Provided
    gcpConfig.setUseDelegateSelectors(true);
    gcpConfig.setDelegateSelectors(null);
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Delegate Selector must be provided if inherit from delegate option is selected.");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testK8sDelegateSelectorValidation() throws IllegalAccessException {
    KubernetesClusterConfig kubernetesClusterConfig =
        KubernetesClusterConfig.builder().useKubernetesDelegate(true).skipValidation(true).build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(kubernetesClusterConfig);

    GcpHelperServiceManager gcpHelperServiceManager = mock(GcpHelperServiceManager.class);

    FieldUtils.writeField(settingValidationService, "gcpHelperServiceManager", gcpHelperServiceManager, true);

    // useDelegate = true, delegateSelector Provided
    kubernetesClusterConfig.setDelegateSelectors(Collections.singleton("delegate1"));
    assertThat(settingValidationService.validate(attribute)).isTrue();

    // useDelegate = true, no delegateSelector Provided
    kubernetesClusterConfig.setDelegateSelectors(null);
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No Delegate Selector Provided");

    // useDelegate = true, empty delegateSelector Provided
    kubernetesClusterConfig.setDelegateSelectors(Collections.singleton(""));
    assertThatThrownBy(() -> settingValidationService.validate(attribute))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No or Empty Delegate Selector Provided");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidateChartMuseumVersionFlagInHelmRepoConfig() throws InterruptedException, IllegalAccessException {
    FieldUtils.writeField(settingValidationService, "delegateService", delegateService, true);

    HelmRepoConfigValidationResponse validationResponse = mock(HelmRepoConfigValidationResponse.class);
    GCSHelmRepoConfig repoConfig = GCSHelmRepoConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(repoConfig);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn(validationResponse).when(delegateService).executeTaskV2(delegateTaskArgumentCaptor.capture());
    doReturn(true).when(featureFlagService).isEnabled(any(), any());

    settingValidationService.validate(attribute);

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    assertThat(((HelmRepoConfigValidationTaskParams) task.getData().getParameters()[0]).isUseLatestChartMuseumVersion())
        .isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateHelmRepoConfigForDelegateSelector() throws IllegalAccessException, InterruptedException {
    AmazonS3HelmRepoConfig amazonS3HelmRepoConfig = AmazonS3HelmRepoConfig.builder()
                                                        .connectorId("CONNECTOR_ID")
                                                        .bucketName("aws-s3-bucket")
                                                        .region("us-east-1")
                                                        .build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(amazonS3HelmRepoConfig);

    SettingAttribute connectorAttribute = new SettingAttribute();
    connectorAttribute.setValue(AwsConfig.builder().tag("aws-delegate").build());
    SettingsService settingsService = mock(SettingsService.class);
    DelegateService delegateService = mock(DelegateService.class);
    FieldUtils.writeField(settingValidationService, "settingsService", settingsService, true);
    FieldUtils.writeField(settingValidationService, "delegateService", delegateService, true);

    when(settingsService.get(any(), any())).thenReturn(connectorAttribute);
    when(delegateService.executeTaskV2(any()))
        .thenReturn(
            HelmRepoConfigValidationResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());

    settingValidationService.validate(attribute);
    connectorAttribute.setValue(
        GcpConfig.builder().delegateSelectors(Collections.singletonList("gcp-delegate")).build());
    settingValidationService.validate(attribute);

    ArgumentCaptor<DelegateTask> taskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).executeTaskV2(taskArgumentCaptor.capture());
    List<DelegateTask> delegateTaskList = taskArgumentCaptor.getAllValues();

    List<String> delegateSelectors =
        delegateTaskList.stream()
            .map(delegateTask -> (HelmRepoConfigValidationTaskParams) delegateTask.getData().getParameters()[0])
            .map(taskParams -> taskParams.getDelegateSelectors())
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    assertThat(delegateSelectors).containsExactlyInAnyOrder("aws-delegate", "gcp-delegate");
  }

  private SmtpConfig createSmtpConfig() {
    return SmtpConfig.builder()
        .host("HOST")
        .port(0)
        .fromAddress("fromAddress@harness.io")
        .username("userName")
        .accountId("AccountId")
        .build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testValidateConnectivitySmtpNg_withNgDelegateAvailable()
      throws InterruptedException, IllegalAccessException {
    DelegateService delegateService = mock(DelegateService.class);
    FieldUtils.writeField(settingValidationService, "delegateService", delegateService, true);
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(createSmtpConfig());
    settingAttribute.setName(NG_SMTP_SETTINGS_PREFIX + "dummy");
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(null);
    ConnectivityValidationDelegateResponse response =
        ConnectivityValidationDelegateResponse.builder().valid(true).build();
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(response);
    assertThat(settingValidationService.validateConnectivity(settingAttribute).isValid()).isEqualTo(true);
    ArgumentCaptor<DelegateTask> taskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).executeTaskV2(taskArgumentCaptor.capture());
    DelegateTask delegateTask = taskArgumentCaptor.getValue();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("ng", "true");
    assertThat(delegateTask.getSetupAbstractions()).isEqualTo(setupAbstractions);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testValidateConnectivitySmtpNg_withNgDelegateUnAvailable()
      throws InterruptedException, IllegalAccessException, InvalidRequestException {
    DelegateService delegateService = mock(DelegateService.class);
    FieldUtils.writeField(settingValidationService, "delegateService", delegateService, true);
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(createSmtpConfig());
    settingAttribute.setName(NG_SMTP_SETTINGS_PREFIX + "dummy");
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(null);
    when(delegateService.executeTaskV2(any(DelegateTask.class)))
        .thenThrow(new NoEligibleDelegatesInAccountException(NO_ELIGIBLE_DELEGATES));
    try {
      settingValidationService.validateConnectivity(settingAttribute);
      fail("The delegate task executed should have thrown error.");
    } catch (NoEligibleDelegatesInAccountException e) {
      assertThat(e.getMessage()).isEqualTo("No eligible delegate(s) in account to execute task. ");
    }
    ArgumentCaptor<DelegateTask> taskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).executeTaskV2(taskArgumentCaptor.capture());
    DelegateTask delegateTask = taskArgumentCaptor.getValue();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("ng", "true");
    assertThat(delegateTask.getSetupAbstractions()).isEqualTo(setupAbstractions);
  }
}
