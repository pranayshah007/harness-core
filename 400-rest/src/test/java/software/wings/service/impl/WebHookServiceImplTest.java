/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;
import static software.wings.service.impl.WebHookServiceImpl.X_BIT_BUCKET_EVENT;
import static software.wings.service.impl.WebHookServiceImpl.X_GIT_HUB_EVENT;
import static software.wings.service.impl.trigger.WebhookEventUtils.X_HUB_SIGNATURE_256;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PORTAL_URL;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static com.google.common.collect.ImmutableMap.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SecretChangeLog.SecretChangeLogKeys;
import io.harness.beans.SecretUsageLog;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.WebHookRequest;
import software.wings.beans.WebHookResponse;
import software.wings.beans.WebHookToken;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.trigger.GithubAction;
import software.wings.beans.trigger.ReleaseAction;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebHookTriggerResponseData;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WebhookSource.BitBucketEventType;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WebHookService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.trigger.TriggerExecutionService;
import software.wings.utils.CryptoUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WebHookServiceImplTest extends WingsBaseTest {
  private static final String API_KEY = "API_KEY";
  private static final String WEBHOOK_SECRET = "WEBHOOK_SECRET";
  @Mock private TriggerService triggerService;
  @Mock private AppService appService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MainConfiguration configuration;
  @Mock HttpHeaders httpHeaders;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SettingsService settingsService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Inject ManagerExpressionEvaluator expressionEvaluator;
  @Inject @InjectMocks TriggerExecutionService triggerExecutionService;
  @Mock private SecretManager secretManager;
  @Mock private DelegateServiceImpl delegateService;

  @Inject @InjectMocks private WebHookService webHookService;
  @Inject WingsPersistence wingsPersistence;
  @Inject @InjectMocks private WebHookServiceImpl webHookServiceImpl;

  final String token = CryptoUtils.secureRandAlphaNumString(40);

  WorkflowExecution execution = WorkflowExecution.builder()
                                    .appId(APP_ID)
                                    .envId(ENV_ID)
                                    .uuid(WORKFLOW_EXECUTION_ID)
                                    .status(RUNNING)
                                    .workflowId(WORKFLOW_ID)
                                    .build();

  Trigger trigger = Trigger.builder()
                        .workflowId(PIPELINE_ID)
                        .uuid(TRIGGER_ID)
                        .appId(APP_ID)
                        .name(TRIGGER_NAME)
                        .webHookToken(token)
                        .condition(WebHookTriggerCondition.builder()
                                       .webhookSource(WebhookSource.BITBUCKET)
                                       .parameters(of("PullRequestId", "${pullrequest.id}"))
                                       .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                       .build())
                        .build();

  Trigger triggerWithBranchNameRegex =
      Trigger.builder()
          .workflowId(PIPELINE_ID)
          .uuid(TRIGGER_ID)
          .appId(APP_ID)
          .name(TRIGGER_NAME)
          .webHookToken(token)
          .condition(WebHookTriggerCondition.builder()
                         .webhookSource(WebhookSource.BITBUCKET)
                         .parameters(of("PullRequestId", "${pullrequest.id}"))
                         .webHookToken(WebHookToken.builder().webHookToken(token).build())
                         .branchRegex("harshBranch(.*)")
                         .build())
          .build();

  WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
  WebHookTriggerCondition webHookTriggerConditionWithBranch =
      (WebHookTriggerCondition) triggerWithBranchNameRegex.getCondition();

  @Before
  public void setUp() {
    final Application application = anApplication().uuid(APP_ID).appId(APP_ID).accountId(ACCOUNT_ID).build();
    doReturn(application).when(appService).get(APP_ID);
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), any(), any(), any(), any(), any());

    doReturn(trigger).when(triggerService).getTriggerByWebhookToken(anyString());
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configuration.getApiUrl()).thenReturn(PORTAL_URL);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteNoService() {
    List<Map<String, Object>> artifacts =
        Collections.singletonList(of("service", SERVICE_NAME, "buildNumber", BUILD_NO));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, null).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteWithService() {
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).build());
    List<Map<String, Object>> artifacts =
        Collections.singletonList(of("service", SERVICE_NAME, "buildNumber", BUILD_NO));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotExecuteDisabledTrigger() {
    trigger.setDisabled(true);

    Response response =
        webHookService.execute(token, WebHookRequest.builder().application(APP_ID).build(), httpHeaders);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_SERVICE_UNAVAILABLE);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getError().contains("Trigger rejected")).isTrue();
    assertThat(webHookResponse.getStatus()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotExecuteTriggerWithFilesChangedManually() {
    WebHookTriggerCondition triggerCondition = (WebHookTriggerCondition) trigger.getCondition();
    triggerCondition.setCheckFileContentChanged(true);

    Response response =
        webHookService.execute(token, WebHookRequest.builder().application(APP_ID).build(), httpHeaders);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getError())
        .isEqualTo("Trigger configured with deploy only if files changed option cannot be executed by manual trigger");
    assertThat(webHookResponse.getStatus()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteByEventNoTrigger() {
    String payLoad = "Some payload";
    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, null).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteByEventTriggerInvalidJson() {
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);
    String payLoad = "Some payload";
    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, null).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteByEventTriggerBitBucket() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_APPROVED));
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pullrequest:approved").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());
    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonParsing() throws IOException {
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    // convert JSON string to Map
    Map<String, Object> map = JsonUtils.asObject(payLoad, new TypeReference<Map<String, Object>>() {});

    final String value = expressionEvaluator.substitute("${pullrequest.id} - MyVal", map);
    assertThat(value).isNotEmpty().isEqualTo("23 - MyVal");
    assertThat(expressionEvaluator.substitute("${pullrequest.id} - MyVal - ${pullrequest.id}", map))
        .isNotEmpty()
        .isEqualTo("23 - MyVal - 23");
    assertThat(expressionEvaluator.substitute("${pullrequest.id} - MyVal - ${app.name}", map))
        .isNotEmpty()
        .isEqualTo("23 - MyVal - ${app.name}");
  }
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonGitHubPushParsing() throws IOException {
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_push_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    // convert JSON string to Map
    Map<String, Object> map = JsonUtils.asObject(payLoad, new TypeReference<Map<String, Object>>() {
    }); // mapper.readValue(payLoad, new TypeReference<Map<String, Object>>(){});
    final String value = expressionEvaluator.substitute("${commits[0].id}", map);
    assertThat(value).isNotEmpty().isEqualTo("4ebc6e9e489979a29ca17b8da0c29d9f6803a5b9");
    final String ref = expressionEvaluator.substitute("${ref.substring(${ref.indexOf('/')+1})}", map);
    assertThat(ref).isNotEmpty().isEqualTo("heads/master");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketPushParsing() throws IOException {
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_push_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    // convert JSON string to Map
    Map<String, Object> map = JsonUtils.asObject(payLoad, new TypeReference<Map<String, Object>>() {
    }); // mapper.readValue(payLoad, new TypeReference<Map<String, Object>>(){});
    final String value = expressionEvaluator.substitute("${push.changes[0].'new'.name}", map);
    assertThat(value).isNotEmpty().isEqualTo("master");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketPull() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_APPROVED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pullrequest:approved").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketPullWithBranchName() throws IOException {
    webHookTriggerConditionWithBranch.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerConditionWithBranch.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_CREATED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(triggerWithBranchNameRegex);

    ClassLoader classLoader = getClass().getClassLoader();
    File file =
        new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_pull_request_created.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pullrequest:created").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketRefChange() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.REPO));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.REFS_CHANGED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file =
        new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_ref_changes_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("repo:refs_changed").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketFork() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.REPO));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.FORK));
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_fork_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("repo:fork").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketForkWithPullRequest() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.ALL));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_fork_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("repo:fork").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldThrowErrorJsonBitBucketFork() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.REPO));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.COMMIT_COMMENT_CREATED));
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_fork_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("repo:fork").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPRWithoutActions() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPRWithClosedAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                                                .actions(Collections.singletonList(GithubAction.CLOSED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS, intermittent = true)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPRWithDifferentEvent() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PUSH))
                                                .actions(Collections.singletonList(GithubAction.OPENED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS, intermittent = true)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPRWithDifferentAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                                                .actions(Collections.singletonList(GithubAction.OPENED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubReleaseWithPublishedAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.RELEASE))
                                                .releaseActions(Collections.singletonList(ReleaseAction.PUBLISHED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_release.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("release").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotTriggerGitHubReleaseWithDifferentAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.RELEASE))
                                                .releaseActions(Collections.singletonList(ReleaseAction.CREATED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_release.json");

    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("release").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubReleaseWithoutActions() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.RELEASE))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_release.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("release").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPackageWithPublishedAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PACKAGE))
                                                .actions(Collections.singletonList(GithubAction.PACKAGE_PUBLISHED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_package.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("package").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPackageWithoutActions() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PACKAGE))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_package.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("package").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotTriggerGitHubRPackageWithDifferentAction() throws IOException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PACKAGE))
                                                .actions(Collections.singletonList(GithubAction.PACKAGE_PUBLISHED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_package_updated.json");

    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("package").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getError()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS, intermittent = true)
  @Category(UnitTests.class)
  public void shouldTriggerGitHubPushRequest() throws IOException {
    Trigger webhookTrigger = constructWebhookPushTrigger();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_push_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("push").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotExecuteDisabledGitTrigger() {
    Trigger trigger = constructWebhookPushTrigger();
    trigger.setDisabled(true);

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);
    doReturn("push").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);

    Response response = webHookService.executeByEvent(token, "some payload", httpHeaders);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_SERVICE_UNAVAILABLE);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getError().contains("Trigger rejected")).isTrue();
    assertThat(webHookResponse.getStatus()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketForkWithPullRequestOnPremMerge() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_MERGED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(
        "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_deployment_trigger_pull_request_onprem.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pr:merged").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketForkWithPullRequestOnPremPRApproved() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_APPROVED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(
        "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_deployment_trigger_pull_request_onprem.json");

    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pr:reviewer:approved").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketForkWithPullRequestOnPremCommentCreated() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_COMMENT_CREATED));
    trigger.setCondition(webHookTriggerCondition);
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(
        "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_deployment_trigger_pull_request_onprem.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pr:comment:added").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTestJsonBitBucketForkWithPullRequestOnPremCommentDeleted() throws IOException {
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));
    webHookTriggerCondition.setBitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_COMMENT_DELETED));

    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(trigger);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(
        "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_deployment_trigger_pull_request_onprem.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn("pullrequest:comment_deleted").when(httpHeaders).getHeaderString(X_BIT_BUCKET_EVENT);
    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testPopulateUrlFieldsWhenTriggering() {
    final Application application = anApplication().uuid(APP_ID).appId(APP_ID).accountId(ACCOUNT_ID).build();
    doReturn(application).when(appService).get(APP_ID);
    final WorkflowExecution execution =
        WorkflowExecution.builder().appId(APP_ID).envId(ENV_ID).uuid(WORKFLOW_EXECUTION_ID).status(RUNNING).build();
    final String token = CryptoUtils.secureRandAlphaNumString(40);
    doReturn(execution)
        .when(triggerService)
        .triggerExecutionByWebHook(eq(APP_ID), eq(token), anyMap(), anyMap(), any(), anyMap());

    WebHookRequest request = WebHookRequest.builder().application(APP_ID).build();
    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getRequestId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
    assertThat(response.getApiUrl())
        .isEqualTo(String.format("%s/api/external/v1/executions/%s/status?accountId=%s&appId=%s", PORTAL_URL,
            WORKFLOW_EXECUTION_ID, ACCOUNT_ID, APP_ID));
    assertThat(response.getUiUrl())
        .isEqualTo(String.format("%s/#/account/%s/app/%s/env/%s/executions/%s/details", PORTAL_URL, ACCOUNT_ID, APP_ID,
            ENV_ID, WORKFLOW_EXECUTION_ID));
    assertThat(response.getUiSetupUrl())
        .isEqualTo(
            String.format("%s/#/account/%s/app/%s/workflows/%s/details", PORTAL_URL, ACCOUNT_ID, APP_ID, WORKFLOW_ID));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateGitHubWebhookEventMissing() {
    Trigger webhookTrigger = constructWebhookPushTrigger();
    WebHookServiceImpl webHookServiceImpl = new WebHookServiceImpl();
    webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
        (WebHookTriggerCondition) webhookTrigger.getCondition(), new HashMap<>(), null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateGitHubEventTypeMisMatch() {
    Trigger webhookTrigger = constructWebhookPushTrigger();
    WebHookServiceImpl webHookServiceImpl = new WebHookServiceImpl();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
        (WebHookTriggerCondition) webhookTrigger.getCondition(), new HashMap<>(), httpHeaders, null);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateGitHubPrActionMisMatch() {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                                                .actions(Arrays.asList(GithubAction.OPENED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    WebHookServiceImpl webHookServiceImpl = new WebHookServiceImpl();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.CLOSED.getValue()), httpHeaders, null))
        .hasMessageContaining(" is not associated with the received GitHub action");
  }

  private Trigger constructWebhookPushTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .webHookToken(token)
        .condition(WebHookTriggerCondition.builder()
                       .webhookSource(WebhookSource.GITHUB)
                       .eventTypes(Collections.singletonList(WebhookEventType.PUSH))
                       .webHookToken(WebHookToken.builder().webHookToken(token).build())
                       .build())
        .build();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateBitBucketWebhookEventMissing() {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.BITBUCKET)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PUSH))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    WebHookServiceImpl webHookServiceImpl = new WebHookServiceImpl();
    webHookServiceImpl.validateWebHook(WebhookSource.BITBUCKET, webhookTrigger,
        (WebHookTriggerCondition) webhookTrigger.getCondition(), new HashMap<>(), null, null);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteWithServiceAndParameterizedArtifactStream() {
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).build());
    Map<String, Object> parameterMap = new HashMap<>();
    parameterMap.put("repo", "npm-internal");
    parameterMap.put("package", "npm-app1");
    List<Map<String, Object>> artifacts = Collections.singletonList(
        of("service", SERVICE_NAME, "buildNumber", BUILD_NO, "artifactVariables", parameterMap));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testConstructSuccessResponseURLs() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .uuid(UUID)
                                              .status(SUCCESS)
                                              .workflowType(WorkflowType.ORCHESTRATION)
                                              .envId(ENV_ID)
                                              .workflowId(WORKFLOW_ID)
                                              .build();
    when(configuration.getApiUrl()).thenReturn("app.harness.io/gratis");
    when(configuration.getPortal().getUrl()).thenReturn("app.harness.io");
    WebHookServiceImpl webHookServiceImpl = (WebHookServiceImpl) webHookService;

    Response response = webHookServiceImpl.constructSuccessResponse(APP_ID, ACCOUNT_ID, workflowExecution);
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isInstanceOf(WebHookResponse.class);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getApiUrl())
        .isEqualTo("app.harness.io/gratis/api/external/v1/executions/UUID/status?accountId=ACCOUNT_ID&appId=APP_ID");
    assertThat(webHookResponse.getUiUrl())
        .isEqualTo("app.harness.io/#/account/ACCOUNT_ID/app/APP_ID/env/ENV_ID/executions/UUID/details");
    assertThat(webHookResponse.getUiSetupUrl())
        .isEqualTo("app.harness.io/#/account/ACCOUNT_ID/app/APP_ID/workflows/WORKFLOW_ID/details");

    WorkflowExecution pipelineExecution = WorkflowExecution.builder()
                                              .uuid(UUID)
                                              .status(SUCCESS)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .envId(ENV_ID)
                                              .workflowId(WORKFLOW_ID)
                                              .build();
    response = webHookServiceImpl.constructSuccessResponse(APP_ID, ACCOUNT_ID, pipelineExecution);
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isInstanceOf(WebHookResponse.class);
    webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getApiUrl())
        .isEqualTo("app.harness.io/gratis/api/external/v1/executions/UUID/status?accountId=ACCOUNT_ID&appId=APP_ID");
    assertThat(webHookResponse.getUiUrl())
        .isEqualTo(
            "app.harness.io/#/account/ACCOUNT_ID/app/APP_ID/pipeline-execution/UUID/workflow-execution/undefined/details");
    assertThat(webHookResponse.getUiSetupUrl())
        .isEqualTo("app.harness.io/#/account/ACCOUNT_ID/app/APP_ID/pipelines/WORKFLOW_ID/edit");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestServiceMapping() {
    WebHookServiceImpl webhookServiceImpl = (WebHookServiceImpl) webHookService;
    WebHookRequest webHookRequest =
        WebHookRequest.builder()
            .manifests(Arrays.asList(ImmutableMap.of("service", SERVICE_NAME, "versionNumber", "1.0"),
                ImmutableMap.of("service", SERVICE_NAME + 2, "versionNumber", "2.0")))
            .build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).uuid(SERVICE_ID).build());
    wingsPersistence.save(Service.builder().name(SERVICE_NAME + 2).appId(APP_ID).uuid(SERVICE_ID + 2).build());

    Map<String, ManifestSummary> serviceManifestMapping =
        webhookServiceImpl.resolveServiceHelmChartVersion(APP_ID, webHookRequest);
    assertThat(serviceManifestMapping).isNotEmpty();
    assertThat(serviceManifestMapping.get(SERVICE_ID).getVersionNo()).isEqualTo("1.0");
    assertThat(serviceManifestMapping.get(SERVICE_ID + 2).getVersionNo()).isEqualTo("2.0");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenUserProvidesWrongService() {
    WebHookServiceImpl webhookServiceImpl = (WebHookServiceImpl) webHookService;
    WebHookRequest webHookRequest =
        WebHookRequest.builder()
            .application(APP_ID)
            .manifests(Arrays.asList(ImmutableMap.of("service", SERVICE_NAME, "versionNumber", "1.0"),
                ImmutableMap.of("service", SERVICE_NAME + 2, "versionNumber", "2.0")))
            .build();

    when(triggerService.getTriggerByWebhookToken("TOKEN")).thenReturn(Trigger.builder().appId(APP_ID).build());
    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).uuid(SERVICE_ID).build());

    Response response = webhookServiceImpl.execute("TOKEN", webHookRequest, httpHeaders);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isInstanceOf(WebHookResponse.class);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getError()).isEqualTo("Service Name [" + SERVICE_NAME + 2 + "] does not exist");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotExecuteWithEmptyApiKeyAndIsManualTriggerAuthEnabled_FfOn() {
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).build());
    List<Map<String, Object>> artifacts =
        Collections.singletonList(of("service", SERVICE_NAME, "buildNumber", BUILD_NO));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    Application application =
        anApplication().uuid(APP_ID).appId(APP_ID).accountId(ACCOUNT_ID).isManualTriggerAuthorized(true).build();
    when(appService.get(APP_ID)).thenReturn(application);
    when(httpHeaders.getHeaderString(API_KEY_HEADER)).thenReturn(null);

    Response response = webHookService.execute(token, request, httpHeaders);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getEntity()).isInstanceOf(WebHookResponse.class);
    WebHookResponse webHookResponse = (WebHookResponse) response.getEntity();
    assertThat(webHookResponse.getError()).isEqualTo("Api Key cannot be empty");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldExecuteWithApiKeyAndIsManualTriggerAuthEnabled_FfOn() {
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).build());
    List<Map<String, Object>> artifacts =
        Collections.singletonList(of("service", SERVICE_NAME, "buildNumber", BUILD_NO));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    Application application =
        anApplication().uuid(APP_ID).appId(APP_ID).accountId(ACCOUNT_ID).isManualTriggerAuthorized(true).build();
    when(appService.get(APP_ID)).thenReturn(application);
    when(httpHeaders.getHeaderString(API_KEY_HEADER)).thenReturn(API_KEY);

    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldExecuteWithNoApiKeyAndIsManualTriggerAuthDisabled_FfOn() {
    wingsPersistence.save(Service.builder().name(SERVICE_NAME).appId(APP_ID).build());
    List<Map<String, Object>> artifacts =
        Collections.singletonList(of("service", SERVICE_NAME, "buildNumber", BUILD_NO));
    WebHookRequest request = WebHookRequest.builder().artifacts(artifacts).application(APP_ID).build();
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    when(httpHeaders.getHeaderString(API_KEY_HEADER)).thenReturn("");

    WebHookResponse response = (WebHookResponse) webHookService.execute(token, request, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateGithubWebHook_hashedPayloadHeaderMissing() {
    Trigger webhookTrigger = getGithubTriggerWithSecret();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.OPENED.getValue()), httpHeaders, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Harness trigger has webhook secret but its not present in GITHUB");
  }

  private Trigger getGithubTriggerWithSecret() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .webHookToken(token)
        .condition(WebHookTriggerCondition.builder()
                       .webhookSource(WebhookSource.GITHUB)
                       .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                       .actions(Arrays.asList(GithubAction.OPENED))
                       .webHookToken(WebHookToken.builder().webHookToken(token).build())
                       .webHookSecret(WEBHOOK_SECRET)
                       .build())
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateGithubWebHook_WebhookSecretMissing() {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                                                .actions(Arrays.asList(GithubAction.OPENED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .build())
                                 .build();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn("x-hub-signature-256").when(httpHeaders).getHeaderString(X_HUB_SIGNATURE_256);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.OPENED.getValue()), httpHeaders, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Webhook secret is present in GITHUB but harness trigger doesn't have it");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateGithubWebHook_WebhookSecretMissingInDb() {
    Trigger webhookTrigger = getGithubTriggerWithSecret();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn("x-hub-signature-256").when(httpHeaders).getHeaderString(X_HUB_SIGNATURE_256);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);
    doReturn(Optional.empty()).when(secretManager).encryptedDataDetails(ACCOUNT_ID, null, WEBHOOK_SECRET, null);

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.OPENED.getValue()), httpHeaders, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error fetching the secret from database");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateGithubWebHook_WebHookAuthenticationFailed() throws InterruptedException {
    Trigger webhookTrigger = getGithubTriggerWithSecret();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn("x-hub-signature-256").when(httpHeaders).getHeaderString(X_HUB_SIGNATURE_256);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);
    doReturn(
        Optional.of(
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().kmsId("id").build()).build()))
        .when(secretManager)
        .encryptedDataDetails(ACCOUNT_ID, null, WEBHOOK_SECRET, null);
    doReturn(WebHookTriggerResponseData.builder().executionStatus(FAILED).build())
        .when(delegateService)
        .executeTaskV2(any(DelegateTask.class));

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.OPENED.getValue()), httpHeaders, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Error in webhook authentication: ");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateGithubWebHook_WebHookSecretsMismatch() throws InterruptedException {
    Trigger webhookTrigger = getGithubTriggerWithSecret();
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn("x-hub-signature-256").when(httpHeaders).getHeaderString(X_HUB_SIGNATURE_256);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);
    doReturn(
        Optional.of(
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().kmsId("id").build()).build()))
        .when(secretManager)
        .encryptedDataDetails(ACCOUNT_ID, null, WEBHOOK_SECRET, null);
    doReturn(WebHookTriggerResponseData.builder().executionStatus(SUCCESS).isWebhookAuthenticated(false).build())
        .when(delegateService)
        .executeTaskV2(any(DelegateTask.class));

    assertThatThrownBy(()
                           -> webHookServiceImpl.validateWebHook(WebhookSource.GITHUB, webhookTrigger,
                               (WebHookTriggerCondition) webhookTrigger.getCondition(),
                               ImmutableMap.of("action", GithubAction.OPENED.getValue()), httpHeaders, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Webhook secret present in Harness and GITHUB might be different");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldUpdateSecretRuntimUsagePostSuccesfulExecution() throws IOException, InterruptedException {
    Trigger webhookTrigger = Trigger.builder()
                                 .workflowId(PIPELINE_ID)
                                 .uuid(TRIGGER_ID)
                                 .appId(APP_ID)
                                 .name(TRIGGER_NAME)
                                 .webHookToken(token)
                                 .condition(WebHookTriggerCondition.builder()
                                                .webhookSource(WebhookSource.GITHUB)
                                                .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
                                                .actions(Arrays.asList(GithubAction.CLOSED))
                                                .webHookToken(WebHookToken.builder().webHookToken(token).build())
                                                .webHookSecret(WEBHOOK_SECRET)
                                                .build())
                                 .build();
    when(triggerService.getTriggerByWebhookToken(token)).thenReturn(webhookTrigger);
    doReturn(true).when(featureFlagService).isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID);
    doReturn("pull_request").when(httpHeaders).getHeaderString(X_GIT_HUB_EVENT);
    doReturn("x-hub-signature-256").when(httpHeaders).getHeaderString(X_HUB_SIGNATURE_256);
    doReturn(
        Optional.of(
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().kmsId("id").build()).build()))
        .when(secretManager)
        .encryptedDataDetails(ACCOUNT_ID, null, WEBHOOK_SECRET, null);
    doReturn(WebHookTriggerResponseData.builder().executionStatus(SUCCESS).isWebhookAuthenticated(true).build())
        .when(delegateService)
        .executeTaskV2(any(DelegateTask.class));

    File file = new File("400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json");
    String payLoad = FileUtils.readFileToString(file, Charset.defaultCharset());

    doReturn(execution).when(triggerService).triggerExecutionByWebHook(any(), anyMap(), any());
    wingsPersistence.save(execution);

    WebHookResponse response = (WebHookResponse) webHookService.executeByEvent(token, payLoad, httpHeaders).getEntity();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(RUNNING.name());
    PageResponse<SecretUsageLog> pageResponse = wingsPersistence.query(SecretUsageLog.class,
        aPageRequest()
            .addFilter(SecretChangeLogKeys.encryptedDataId, SearchFilter.Operator.EQ, WEBHOOK_SECRET)
            .build());
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getResponse()).isNotNull().hasSize(1);
    assertThat(pageResponse.getResponse().get(0).getWorkflowExecutionId()).isEqualTo(execution.getUuid());
  }
}
