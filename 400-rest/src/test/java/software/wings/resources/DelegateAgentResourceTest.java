/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateConfiguration.Action.SELF_DESTRUCT;
import static io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder.aDelegateTaskEvent;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;

import static java.util.Collections.singletonList;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.heartbeat.polling.DelegatePollingHeartbeatService;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.common.AccessTokenBean;
import io.harness.managerclient.AccountPreference;
import io.harness.managerclient.AccountPreferenceQuery;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;
import io.harness.manifest.ManifestCollectionResponseHandler;
import io.harness.perpetualtask.connector.ConnectorHearbeatPublisher;
import io.harness.perpetualtask.instancesync.InstanceSyncResponsePublisher;
import io.harness.polling.client.PollingResourceClient;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateRingService;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.Account;
import software.wings.beans.account.AccountPreferences;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.jersey.KryoMessageBodyProvider;
import software.wings.service.impl.LoggingTokenCache;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.RequestBody;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateAgentResourceTest extends JerseyTest {
  @Mock private DelegateService delegateService;
  @Mock private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Mock private ConfigurationController configurationController;
  @Mock private HttpServletRequest httpServletRequest;
  @Mock private AccountService accountService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private InstanceHelper instanceSyncResponseHandler;
  @Mock private ArtifactCollectionResponseHandler artifactCollectionResponseHandler;
  @Mock private DelegateTaskService delegateTaskService;
  @Mock private InstanceSyncResponsePublisher instanceSyncResponsePublisher;
  @Mock private DelegateCapacityManagementService delegateCapacityManagementService;
  @Mock private ManifestCollectionResponseHandler manifestCollectionResponseHandler;
  @Mock private ConnectorHearbeatPublisher connectorHearbeatPublisher;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private PollingResourceClient pollResourceClient;
  @Mock private DelegatePollingHeartbeatService delegatePollingHeartbeatService;
  @Mock private DelegateRingService delegateRingService;
  @Mock private LoggingTokenCache loggingTokenCache;

  @Override
  protected Application configure() {
    // need to initialize mocks here, MockitoJUnitRunner won't help since this is not @Before, but happens only once per
    // test.
    initMocks(this);
    final ResourceConfig resourceConfig = new ResourceConfig();
    resourceConfig.register(new DelegateAgentResource(delegateService, accountService, wingsPersistence,
        subdomainUrlHelper, artifactCollectionResponseHandler, instanceSyncResponseHandler,
        manifestCollectionResponseHandler, connectorHearbeatPublisher, kryoSerializer, configurationController,
        delegateTaskServiceClassic, pollResourceClient, instanceSyncResponsePublisher, delegatePollingHeartbeatService,
        delegateCapacityManagementService, delegateRingService, loggingTokenCache));
    resourceConfig.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bind(httpServletRequest).to(HttpServletRequest.class);
      }
    });
    return resourceConfig;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
  }

  @Override
  protected void configureClient(final ClientConfig config) {
    config.register(KryoMessageBodyProvider.class, 0);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetDelegateConfiguration() {
    final DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(List.of()).build();

    when(accountService.getDelegateConfiguration(ACCOUNT_ID)).thenReturn(delegateConfiguration);
    final RestResponse<DelegateConfiguration> actual =
        client().target("/agent/delegates/configuration?accountId=" + ACCOUNT_ID).request().get(new GenericType<>() {});

    verify(accountService).getDelegateConfiguration(ACCOUNT_ID);
    assertThat(actual.getResource()).isInstanceOf(DelegateConfiguration.class).isNotNull();
    assertThat(actual.getResource().getAction()).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetDelegateConfigurationWithSelfDestruct() {
    when(accountService.getDelegateConfiguration(ACCOUNT_ID))
        .thenThrow(new InvalidRequestException("Deleted AccountId: " + ACCOUNT_ID));

    final RestResponse<DelegateConfiguration> actual =
        client().target("/agent/delegates/configuration?accountId=" + ACCOUNT_ID).request().get(new GenericType<>() {});

    assertThat(actual.getResource()).isInstanceOf(DelegateConfiguration.class).isNotNull();
    assertThat(actual.getResource().getAction()).isEqualTo(SELF_DESTRUCT);
    assertThat(actual.getResource().getDelegateVersions()).isNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldRegisterDelegate() {
    final DelegateRegisterResponse expected = DelegateRegisterResponse.builder().delegateId(DELEGATE_ID).build();
    when(delegateService.register(any(DelegateParams.class), any(boolean.class))).thenReturn(expected);

    final RestResponse<DelegateRegisterResponse> actual =
        client()
            .target("/agent/delegates/register?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(DelegateParams.builder().delegateId(DELEGATE_ID).build(), MediaType.APPLICATION_JSON),
                new GenericType<>() {});

    assertThat(actual.getResource()).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldAddDelegate() {
    final Delegate delegate = Delegate.builder().build();

    when(delegateService.add(any(Delegate.class))).thenAnswer(invocation -> invocation.getArgument(0, Delegate.class));
    final RestResponse<Delegate> actual =
        client()
            .target("/agent/delegates?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegate, MediaType.APPLICATION_JSON), new GenericType<>() {});

    final ArgumentCaptor<Delegate> captor = ArgumentCaptor.forClass(Delegate.class);
    verify(delegateService).add(captor.capture());
    assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actual.getResource().getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldProcessArtifactCollection() {
    final var expected = BuildSourceExecutionResponse.builder()
                             .commandExecutionStatus(SUCCESS)
                             .artifactStreamId(ARTIFACT_STREAM_ID)
                             .buildSourceResponse(BuildSourceResponse.builder().build())
                             .build();

    when(kryoSerializer.asObject(any(byte[].class))).thenReturn(expected);

    final var requestBody = RequestBody.create(okhttp3.MediaType.parse("application/octet-stream"), "");

    client()
        .target("/agent/delegates/artifact-collection/12345679?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(requestBody, "application/x-kryo"), new GenericType<RestResponse<Boolean>>() {});

    verify(artifactCollectionResponseHandler).processArtifactCollectionResult(ACCOUNT_ID, "12345679", expected);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldProcessInstanceSyncResponse() {
    final var expected = CfCommandExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();

    client()
        .target("/agent/delegates/instance-sync/12345679?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(expected, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});

    verify(instanceSyncResponseHandler).processInstanceSyncResponseFromPerpetualTask("12345679", expected);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldGetDelegateTaskEvents() {
    final var delegateTaskEvents = singletonList(aDelegateTaskEvent().build());
    when(delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false))
        .thenReturn(delegateTaskEvents);
    final var url = String.format("/agent/delegates/%s/task-events?delegateId=%s&accountId=%s&syncOnly=%s", DELEGATE_ID,
        DELEGATE_ID, ACCOUNT_ID, false);
    final DelegateTaskEventsResponse actual = client().target(url).request().get(new GenericType<>() {});

    verify(delegateTaskServiceClassic).getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false);
    assertThat(actual).isInstanceOf(DelegateTaskEventsResponse.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldCheckForUpgrade() throws IOException {
    final String version = "0.0.0";
    final String verificationUrl = String.format("%s://%s:%s", httpServletRequest.getScheme(),
        httpServletRequest.getServerName(), httpServletRequest.getServerPort());
    when(delegateService.getDelegateScripts(ACCOUNT_ID, version,
             subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, null))
        .thenReturn(DelegateScripts.builder().build());
    final var url =
        String.format("/agent/delegates/%s/upgrade?delegateId=%s&accountId=%s", DELEGATE_ID, DELEGATE_ID, ACCOUNT_ID);
    final RestResponse<DelegateScripts> actual =
        client().target(url).request().header("Version", version).get(new GenericType<>() {});

    verify(delegateService)
        .getDelegateScripts(ACCOUNT_ID, version, subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID),
            verificationUrl, null);
    assertThat(actual.getResource()).isInstanceOf(DelegateScripts.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateHB() {
    final DelegateParams delegateParams = DelegateParams.builder().pollingModeEnabled(true).build();

    when(delegatePollingHeartbeatService.process(any(DelegateParams.class)))
        .thenReturn(DelegateHeartbeatResponse.builder().build());
    final RestResponse<Delegate> actual =
        client()
            .target("/agent/delegates/heartbeat-with-polling?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegateParams, MediaType.APPLICATION_JSON), new GenericType<>() {});
    verify(delegatePollingHeartbeatService).process(any(DelegateParams.class));
    assertThat(actual.getResource()).isInstanceOf(Delegate.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldUpdateECSDelegateHB() {
    final String delegateType = "ECS";

    final DelegateParams delegateParams =
        DelegateParams.builder().pollingModeEnabled(true).delegateType(delegateType).build();

    final Delegate delegate = Delegate.builder().polllingModeEnabled(true).delegateType(delegateType).build();

    when(delegateService.handleEcsDelegateRequest(any(Delegate.class))).thenReturn(delegate);
    final RestResponse<Delegate> actual =
        client()
            .target("/agent/delegates/heartbeat-with-polling?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegateParams, MediaType.APPLICATION_JSON), new GenericType<>() {});
    verify(delegateService).handleEcsDelegateRequest(delegate);
    assertThat(actual.getResource()).isInstanceOf(Delegate.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldCheckForProfile() {
    final var profileId = "profile1";
    when(delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, profileId, 99L))
        .thenReturn(DelegateProfileParams.builder().build());
    final var url =
        String.format("/agent/delegates/%s/profile?accountId=%s&delegateId=%s&profileId=%s&lastUpdatedAt=%d",
            DELEGATE_ID, ACCOUNT_ID, DELEGATE_ID, profileId, 99L);
    final RestResponse<DelegateProfileParams> actual = client().target(url).request().get(new GenericType<>() {});

    verify(delegateService, atLeastOnce()).checkForProfile(ACCOUNT_ID, DELEGATE_ID, profileId, 99L);
    assertThat(actual.getResource()).isInstanceOf(DelegateProfileParams.class).isNotNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetDelegateScriptsNg() throws IOException {
    final String delegateVersion = "0.0.0";
    final String verificationUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":"
        + httpServletRequest.getServerPort();
    final DelegateScripts delegateScripts = DelegateScripts.builder()
                                                .delegateScript("delegateScript")
                                                .doUpgrade(false)
                                                .setupProxyScript("proxyScript")
                                                .startScript("startScript")
                                                .stopScript("stopScript")
                                                .build();
    when(delegateService.getDelegateScriptsNg(ACCOUNT_ID, delegateVersion,
             subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, null))
        .thenReturn(delegateScripts);

    RestResponse<DelegateScripts> restResponse = client()
                                                     .target("/agent/delegates/delegateScriptsNg?accountId="
                                                         + ACCOUNT_ID + "&delegateVersion=" + delegateVersion)
                                                     .request()
                                                     .get(new GenericType<RestResponse<DelegateScripts>>() {});

    verify(delegateService, atLeastOnce())
        .getDelegateScriptsNg(ACCOUNT_ID, delegateVersion,
            subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, null);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateScripts.class).isNotNull().isEqualTo(delegateScripts);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldGetDelegateScripts() throws IOException {
    String delegateVersion = "0.0.0";
    String verificationUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":"
        + httpServletRequest.getServerPort();
    DelegateScripts delegateScripts = DelegateScripts.builder().build();
    when(delegateService.getDelegateScripts(ACCOUNT_ID, delegateVersion,
             subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, null))
        .thenReturn(delegateScripts);

    RestResponse<DelegateScripts> restResponse =
        client()
            .target("/agent/delegates/delegateScripts?accountId=" + ACCOUNT_ID + "&delegateVersion=" + delegateVersion)
            .request()
            .get(new GenericType<RestResponse<DelegateScripts>>() {});

    verify(delegateService, atLeastOnce())
        .getDelegateScripts(ACCOUNT_ID, delegateVersion,
            subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, null);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateScripts.class).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void getdelegateScripts() throws IOException {
    String delegateVersion = "0.0.0";
    String delegateName = "TEST_DELEGATE";
    String verificationUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":"
        + httpServletRequest.getServerPort();
    DelegateScripts delegateScripts = DelegateScripts.builder().build();
    when(delegateService.getDelegateScripts(ACCOUNT_ID, delegateVersion,
             subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, delegateName))
        .thenReturn(delegateScripts);

    RestResponse<DelegateScripts> restResponse =
        client()
            .target("/agent/delegates/delegateScripts?accountId=" + ACCOUNT_ID + "&delegateVersion=" + delegateVersion
                + "&delegateName=" + delegateName)
            .request()
            .get(new GenericType<RestResponse<DelegateScripts>>() {});

    verify(delegateService, atLeastOnce())
        .getDelegateScripts(ACCOUNT_ID, delegateVersion,
            subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl, delegateName);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateScripts.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldSaveApiCallLogs() {
    int numOfApiCallLogs = 10;
    List<ThirdPartyApiCallLog> apiCallLogs = new ArrayList<>();
    for (int i = 0; i < numOfApiCallLogs; i++) {
      apiCallLogs.add(ThirdPartyApiCallLog.builder()
                          .accountId(ACCOUNT_ID)
                          .stateExecutionId(STATE_EXECUTION_ID)
                          .requestTimeStamp(i)
                          .request(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                          .name(generateUuid())
                                                          .value(generateUuid())
                                                          .type(FieldType.TEXT)
                                                          .build()))
                          .response(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                           .name(generateUuid())
                                                           .value(generateUuid())
                                                           .type(FieldType.TEXT)
                                                           .build()))
                          .delegateId(DELEGATE_ID)
                          .delegateTaskId(generateUuid())
                          .build());
    }

    when(kryoSerializer.asObject(any(byte[].class))).thenReturn(apiCallLogs);

    client()
        .target("/agent/delegates/" + DELEGATE_ID + "/state-executions?delegateId=" + DELEGATE_ID
            + "&accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(apiCallLogs, MediaType.APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    verify(wingsPersistence, atLeastOnce())
        .save(apiCallLogs.stream()
                  .map(software.wings.service.impl.ThirdPartyApiCallLog::fromDto)
                  .collect(Collectors.toList()));
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  @Ignore("TODO: refactor to allow initialization of KryoFeature")
  public void shouldReportConnectionResults() {
    String taskId = generateUuid();
    List<DelegateConnectionResult> connectionResults = singletonList(DelegateConnectionResult.builder()
                                                                         .accountId(ACCOUNT_ID)
                                                                         .delegateId(DELEGATE_ID)
                                                                         .duration(100L)
                                                                         .criteria("aaa")
                                                                         .validated(true)
                                                                         .build());
    client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "/report?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(connectionResults, "application/x-kryo"), new GenericType<RestResponse<DelegateTaskPackage>>() {});
    verify(delegateTaskServiceClassic, atLeastOnce())
        .reportConnectionResults(ACCOUNT_ID, DELEGATE_ID, taskId, null, connectionResults);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  @Ignore("TODO: refactor to allow initialization of KryoFeature")
  public void shouldUpdateTaskResponse() {
    String taskId = generateUuid();
    DelegateTaskResponse taskResponse = DelegateTaskResponse.builder().build();
    client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(taskResponse, "application/x-kryo"), new GenericType<RestResponse<String>>() {});
    verify(delegateTaskService, atLeastOnce()).processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, taskId, taskResponse);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldAcquireDelegateTask() {
    String taskId = generateUuid();
    DelegateTaskResponse taskResponse = DelegateTaskResponse.builder().build();
    client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "/acquire?accountId=" + ACCOUNT_ID)
        .request()
        .put(entity(taskResponse, "application/x-kryo"), Response.class);
    verify(delegateTaskServiceClassic, atLeastOnce()).acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, taskId, null);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void shouldPublishNGConnectorHeartbeatResult() {
    String taskId = generateUuid();
    ConnectorHeartbeatDelegateResponse taskResponse =
        ConnectorHeartbeatDelegateResponse.builder().accountIdentifier(ACCOUNT_ID).build();

    client()
        .target("/agent/delegates/connectors/" + taskId + "?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(taskResponse, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(connectorHearbeatPublisher, atLeastOnce()).pushConnectivityCheckActivity(ACCOUNT_ID, taskResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetDelegateProperties() throws TextFormat.ParseException {
    GetDelegatePropertiesRequest request = GetDelegatePropertiesRequest.newBuilder()
                                               .setAccountId(ACCOUNT_ID)
                                               .addRequestEntry(Any.pack(AccountPreferenceQuery.newBuilder().build()))
                                               .build();

    Account account =
        Account.Builder.anAccount()
            .withUuid(ACCOUNT_ID)
            .withAccountPreferences(AccountPreferences.builder().delegateSecretsCacheTTLInHours(2).build())
            .build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    RestResponse<String> restResponse =
        client()
            .target("/agent/delegates/properties?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(request.toByteArray(), MediaType.APPLICATION_OCTET_STREAM), new GenericType<>() {});
    GetDelegatePropertiesResponse responseProto =
        TextFormat.parse(restResponse.getResource(), GetDelegatePropertiesResponse.class);
    assertThat(responseProto).isNotNull();
    assertThat(responseProto.getResponseEntryList().get(0).is(AccountPreference.class)).isTrue();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldGetDelegateTaskEventsContainerWithFF() {
    when(delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, true))
        .thenReturn(singletonList(aDelegateTaskEvent().withDelegateTaskId("123").build()));
    DelegateTaskEventsResponse delegateTaskEventsResponse =
        client()
            .target(
                String.format("/agent/delegates/%s/task-events?accountId=%s&syncOnly=true", DELEGATE_ID, ACCOUNT_ID))
            .request()
            .get(new GenericType<>() {});
    assertThat(delegateTaskEventsResponse.getDelegateTaskEvents().get(0).getDelegateTaskId()).isEqualTo("123");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void testGetDelegateTaskEventsContainerWithFF() throws ExecutionException {
    final var expected = new AccessTokenBean("projectId", "token", 1000L);
    when(loggingTokenCache.getToken(ACCOUNT_ID)).thenReturn(expected);
    final RestResponse<AccessTokenBean> actual =
        client().target("/agent/delegates/logging-token?accountId=" + ACCOUNT_ID).request().get(new GenericType<>() {});
    assertThat(actual.getResource()).isEqualTo(expected);
  }
}
