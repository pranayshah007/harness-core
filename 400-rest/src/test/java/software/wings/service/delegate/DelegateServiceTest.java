/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateProfile.DelegateProfileBuilder;
import static io.harness.delegate.beans.DelegateProfile.builder;
import static io.harness.delegate.beans.DelegateRegisterResponse.Action;
import static io.harness.delegate.beans.DelegateType.DOCKER;
import static io.harness.delegate.beans.DelegateType.ECS;
import static io.harness.delegate.beans.DelegateType.HELM_DELEGATE;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.DelegateType.SHELL_SCRIPT;
import static io.harness.delegate.beans.K8sPermissionType.CLUSTER_ADMIN;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ANUPAM;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DESCRIPTION;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.LUCAS;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.service.impl.DelegateServiceImpl.DELEGATE_DIR;
import static software.wings.service.impl.DelegateServiceImpl.DOCKER_DELEGATE;
import static software.wings.service.impl.DelegateServiceImpl.ECS_DELEGATE;
import static software.wings.service.impl.DelegateServiceImpl.KUBERNETES_DELEGATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_GROUP_NAME;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.google.common.base.Charsets.UTF_8;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMinutes;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.beans.EncryptedData;
import io.harness.beans.SearchFilter.Operator;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.AutoUpgrade;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateApproval;
import io.harness.delegate.beans.DelegateApprovalResponse;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateDTO;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateSelector;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSize;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.beans.DelegateTags;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.delegate.beans.K8sConfigDetails;
import io.harness.delegate.beans.K8sPermissionType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.delegate.utils.DelegateJreVersionHelper;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.globalcontex.DelegateTokenGlobalContextData;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.manage.GlobalContextManager;
import io.harness.network.LocalhostUtils;
import io.harness.observer.Subject;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateProfileObserver;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.DelegateTokenService;
import io.harness.version.VersionInfo;
import io.harness.version.VersionInfoManager;

import software.wings.FeatureTestHelper;
import software.wings.WingsBaseTest;
import software.wings.app.DelegateGrpcConfig;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.DelegateScalingGroup;
import software.wings.beans.DelegateStatus;
import software.wings.beans.Event.Type;
import software.wings.beans.LicenseInfo;
import software.wings.beans.ServiceVariable;
import software.wings.beans.TaskType;
import software.wings.cdn.CdnConfig;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.jre.JreConfig;
import software.wings.licensing.LicenseService;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.impl.EventEmitter;
import software.wings.service.impl.infra.InfraDownloadService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.JenkinsExecutionResponse;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import freemarker.template.TemplateException;
import io.fabric8.utils.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import javax.ws.rs.core.MediaType;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.app.MainConfiguration")
@BreakDependencyOn("software.wings.licensing.LicenseService")
@BreakDependencyOn("software.wings.beans.LicenseInfo")
@BreakDependencyOn("software.wings.cdn.CdnConfig")
@BreakDependencyOn("software.wings.beans.Event")
public class DelegateServiceTest extends WingsBaseTest {
  private static final String VERSION = "1.0.0";
  private static final String IMMUTABLE_DELEGATE_VERSION = "22.08.76400";
  private static final String DELEGATE_NAME = "harness-delegate";
  private static final String DELEGATE_PROFILE_ID = "QFWin33JRlKWKBzpzE5A9A";
  private static final String DELEGATE_ID_1 = "delegateId1";
  private static final String DELEGATE_ID_2 = "delegateId2";
  private static final String DELEGATE_TYPE = "dockerType";
  private static final String PRIMARY_PROFILE_NAME = "primary";
  private static final String TOKEN_NAME = "TOKEN_NAME";
  private static final String TOKEN_VALUE = "TOKEN_VALUE";
  private static final String LOCATION = "LOCATION";
  private static final String ANOTHER_LOCATION = "ANOTHER_LOCATION";
  private static final String UNIQUE_DELEGATE_NAME = "delegateNameUnique";
  private static final String DELEGATE_IMAGE_TAG = "harness/delegate:latest";
  private static final String IMMUTABLE_DELEGATE_IMAGE_TAG = "harness/delegate-test:22.09.76810";
  private static final String UPGRADER_IMAGE_TAG = "harness/upgrader:latest";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String UNIQUE_DELEGATE_NAME_ERROR_MESSAGE =
      "Delegate with same name exists. Delegate name must be unique across account.";
  private static final String DELEGATE_TOKEN_ERROR_MESSAGE = "Delegate Token must be provided.";
  private static final String DELEGATE_PERMISSION_NOT_PROVIDED_ERROR_MESSAGE = "K8s permission type must be provided.";
  private static final String HARNESS_NG_DELEGATE = "harness-ng-delegate";
  private static final boolean runAsRoot = true;

  @Mock private AccountService accountService;
  @Mock private LicenseService licenseService;
  @Mock private EventEmitter eventEmitter;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;
  @Mock private DelegateVersionService delegateVersionService;
  @Mock private DelegateProfileService delegateProfileService;
  @Mock private InfraDownloadService infraDownloadService;
  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private FileService fileService;
  @Mock private VersionInfoManager versionInfoManager;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ConfigurationController configurationController;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private DelegateGrpcConfig delegateGrpcConfig;
  @Mock private DelegateTokenService delegateTokenService;
  @Mock private DelegateNgTokenService delegateNgTokenService;
  @Mock private Producer eventProducer;
  @Inject private OutboxService outboxService;

  @Inject private FeatureTestHelper featureTestHelper;
  @Inject private DelegateDao delegateDao;

  private final int port = LocalhostUtils.findFreePort();
  @Rule public WireMockRule wireMockRule = new WireMockRule(port);
  @Rule public ExpectedException thrown = ExpectedException.none();

  @InjectMocks @Inject private DelegateCache delegateCache;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @InjectMocks @Inject private DelegateTaskService delegateTaskService;
  @InjectMocks @Inject private DelegateJreVersionHelper delegateJreVersionHelper;

  @Mock private UsageLimitedFeature delegatesFeature;

  @Inject private HPersistence persistence;

  @Mock private Subject<DelegateProfileObserver> delegateProfileSubject;
  @Mock private Subject<DelegateTaskRetryObserver> retryObserverSubject;
  @Mock private Subject<DelegateObserver> subject;

  public static final Duration TEST_EXPIRY_TIME = ofMinutes(6);

  private final Account account =
      anAccount().withLicenseInfo(LicenseInfo.builder().accountStatus(AccountStatus.ACTIVE).build()).build();

  private DelegateProfileBuilder createDelegateProfileBuilder() {
    return DelegateProfile.builder().name("DELEGATE_PROFILE_NAME").description("DELEGATE_PROFILE_DESC");
  }

  @Before
  public void setUp() throws IllegalAccessException {
    CdnConfig cdnConfig = new CdnConfig();
    cdnConfig.setUrl("http://localhost:9500");
    when(subdomainUrlHelper.getDelegateMetadataUrl(any(), any(), any()))
        .thenReturn("http://localhost:" + port + "/delegateci.txt");
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    when(mainConfiguration.getScmVersion()).thenReturn("542f4642");
    when(mainConfiguration.getOcVersion()).thenReturn("v4.2.16");
    when(mainConfiguration.getCdnConfig()).thenReturn(cdnConfig);
    HashMap<String, JreConfig> jreConfigMap = new HashMap<>();
    jreConfigMap.put("openjdk8u242", getOpenjdkJreConfig());
    jreConfigMap.put("openjdk11014_9", getOpenjdk11JreConfig());
    when(mainConfiguration.getCurrentJre()).thenReturn("openjdk8u242");
    when(mainConfiguration.getJreConfigs()).thenReturn(jreConfigMap);
    when(mainConfiguration.getMigrateToJre()).thenReturn("openjdk11014_9");
    when(subdomainUrlHelper.getWatcherMetadataUrl(any(), any(), any()))
        .thenReturn("http://localhost:" + port + "/watcherci.txt");
    FileUploadLimit fileUploadLimit = new FileUploadLimit();
    fileUploadLimit.setProfileResultLimit(1000000000L);
    when(mainConfiguration.getFileUploadLimits()).thenReturn(fileUploadLimit);

    LogStreamingServiceConfig logSteamingServiceConfig =
        LogStreamingServiceConfig.builder().serviceToken("token").baseUrl("http://localhost:8079").build();
    when(mainConfiguration.getLogStreamingServiceConfig()).thenReturn(logSteamingServiceConfig);

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setCriticalDelegateTaskRejectAtLimit(100000);
    portalConfig.setJwtNextGenManagerSecret("**********");
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(delegateGrpcConfig.getPort()).thenReturn(8080);

    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList("0.0.0")).build());
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(infraDownloadService.getDownloadUrlForDelegate(anyString(), any()))
        .thenReturn("http://localhost:" + port + "/builds/9/delegate.jar");
    when(infraDownloadService.getCdnWatcherBaseUrl()).thenReturn("http://localhost:9500/builds");
    wireMockRule.stubFor(get(urlEqualTo("/delegateci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("9.9.9 jobs/delegateci/9/delegate.jar")
                                             .withHeader("Content-Type", "text/plain")));

    wireMockRule.stubFor(head(urlEqualTo("/builds/9/delegate.jar")).willReturn(aResponse().withStatus(200)));

    wireMockRule.stubFor(get(urlEqualTo("/watcherci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("8.8.8 jobs/deploy-ci-watcher/8/watcher.jar")
                                             .withHeader("Content-Type", "text/plain")));

    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    when(versionInfoManager.getVersionInfo()).thenReturn(VersionInfo.builder().version(VERSION).build());
    when(delegateVersionService.getWatcherJarVersions(ACCOUNT_ID)).thenReturn(VERSION);

    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, TOKEN_NAME, false))
        .thenReturn(DelegateTokenDetails.builder().status(DelegateTokenStatus.ACTIVE).build());
    when(delegateNgTokenService.getDelegateTokenValue(ACCOUNT_ID, TOKEN_NAME)).thenReturn("ACCOUNT_KEY");
    when(delegateTokenService.getTokenValue(ACCOUNT_ID, TOKEN_NAME)).thenReturn("ACCOUNT_KEY");
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, TOKEN_NAME))
        .thenReturn(DelegateTokenDetails.builder()
                        .name(TOKEN_NAME)
                        .accountId(ACCOUNT_ID)
                        .status(DelegateTokenStatus.ACTIVE)
                        .build());

    FieldUtils.writeField(delegateService, "delegateProfileSubject", delegateProfileSubject, true);
    FieldUtils.writeField(delegateService, "subject", subject, true);
    FieldUtils.writeField(delegateTaskService, "retryObserverSubject", retryObserverSubject, true);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldList() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    persistence.save(delegate);
    assertThat(delegateService.list(aPageRequest().addFilter(DelegateKeys.accountId, Operator.EQ, accountId).build()))
        .hasSize(1)
        .containsExactly(delegate);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGet() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    persistence.save(delegate);
    assertThat(delegateCache.get(accountId, delegate.getUuid(), true)).isEqualTo(delegate);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldGetDelegateStatus() {
    String accountId = generateUuid();
    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(asList(VERSION)).build());
    Delegate delegate = createDelegateBuilder().build();

    delegate.setAccountId(accountId);

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);

    persistence.save(Arrays.asList(delegate, deletedDelegate));

    DelegateStatus delegateStatus = delegateService.getDelegateStatus(accountId);
    assertThat(delegateStatus.getPublishedVersions()).hasSize(1).contains(VERSION);
    assertThat(delegateStatus.getDelegates()).hasSize(1);
    assertThat(delegateStatus.getDelegates().get(0).isActivelyConnected()).isTrue();

    validateDelegateInnerProperties(delegate.getUuid(), delegateStatus.getDelegates().get(0));
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldGetDelegateStatus2WithoutScalingGroups() {
    String accountId = generateUuid();
    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build());
    when(delegateVersionService.getImmutableDelegateImageTag(accountId)).thenReturn(IMMUTABLE_DELEGATE_IMAGE_TAG);

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);

    Delegate delegateWithoutScalingGroup = createDelegateBuilder().build();
    delegateWithoutScalingGroup.setAccountId(accountId);

    persistence.save(Arrays.asList(delegateWithoutScalingGroup, deletedDelegate));
    DelegateStatus delegateStatus = delegateService.getDelegateStatusWithScalingGroups(accountId);

    assertThat(delegateStatus.getPublishedVersions()).hasSize(1).contains(VERSION);
    assertThat(delegateStatus.getDelegates()).hasSize(1);
    assertThat(delegateStatus.getScalingGroups()).hasSize(0);
    assertThat(delegateStatus.getPublishedImmutableDelegateVersion()).isEqualTo("22.09.76810");
    validateDelegateInnerProperties(delegateWithoutScalingGroup.getUuid(), delegateStatus.getDelegates().get(0));
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldGetDelegateStatus2ScalingGroupHasCorrectItems() {
    String accountId = generateUuid();
    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build());
    when(delegateVersionService.getImmutableDelegateImageTag(accountId)).thenReturn(IMMUTABLE_DELEGATE_IMAGE_TAG);

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);

    // these three delegates should be returned
    Delegate delegateWithScalingGroup1 = createDelegateBuilder().build();
    delegateWithScalingGroup1.setAccountId(accountId);
    delegateWithScalingGroup1.setDelegateGroupName("test1");
    Delegate delegateWithScalingGroup2 = createDelegateBuilder().build();
    delegateWithScalingGroup2.setAccountId(accountId);
    delegateWithScalingGroup2.setDelegateGroupName("test1");
    Delegate delegateWithScalingGroup3 = createDelegateBuilder().build();
    delegateWithScalingGroup3.setAccountId(accountId);
    delegateWithScalingGroup3.setDelegateGroupName("test2");
    // these two delegates should not appear.
    Delegate delegateWithScalingGroup4 = createDelegateBuilder().build();
    delegateWithScalingGroup4.setAccountId(accountId);
    delegateWithScalingGroup4.setDisconnected(true);
    delegateWithScalingGroup4.setDelegateGroupName("test2");
    // this delegate should cause an empty group to be returned
    Delegate delegateWithScalingGroup5 = createDelegateBuilder().build();
    delegateWithScalingGroup5.setAccountId(accountId);
    delegateWithScalingGroup5.setDelegateGroupName("test3");
    delegateWithScalingGroup5.setDisconnected(true);

    persistence.save(Arrays.asList(deletedDelegate, delegateWithScalingGroup1, delegateWithScalingGroup2,
        delegateWithScalingGroup3, delegateWithScalingGroup4, delegateWithScalingGroup5));

    DelegateStatus delegateStatus = delegateService.getDelegateStatusWithScalingGroups(accountId);

    assertThat(delegateStatus.getPublishedVersions()).hasSize(1).contains(VERSION);

    assertThat(delegateStatus.getScalingGroups()).hasSize(2);
    assertThat(delegateStatus.getScalingGroups())
        .extracting(DelegateScalingGroup::getGroupName)
        .containsOnly("test1", "test2");

    for (DelegateScalingGroup group : delegateStatus.getScalingGroups()) {
      if (group.getGroupName().equals("test1")) {
        assertThat(group.getDelegates()).hasSize(2);
        assertThat(group.getDelegates())
            .extracting(DelegateStatus.DelegateInner::getUuid)
            .containsOnly(delegateWithScalingGroup1.getUuid(), delegateWithScalingGroup2.getUuid());
      } else if (group.getGroupName().equals("test2")) {
        assertThat(group.getDelegates()).hasSize(1);
        assertThat(group.getDelegates().get(0).getUuid()).isEqualTo(delegateWithScalingGroup3.getUuid());
      }
    }
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldGetDelegateStatusWithScalingGroupForImmutableDelegates() {
    String accountId = generateUuid();
    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build());
    when(delegateVersionService.getImmutableDelegateImageTag(accountId)).thenReturn(IMMUTABLE_DELEGATE_IMAGE_TAG);

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);

    // these two delegates should be returned
    Delegate delegateWithScalingGroup1 = createDelegateBuilder().build();
    delegateWithScalingGroup1.setAccountId(accountId);
    delegateWithScalingGroup1.setDelegateGroupName("test1");
    delegateWithScalingGroup1.setVersion(IMMUTABLE_DELEGATE_VERSION);
    DelegateGroup scalingGroup1 = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name("test1")
                                      .status(DelegateGroupStatus.ENABLED)
                                      .upgraderLastUpdated(System.currentTimeMillis())
                                      .ng(false)
                                      .build();

    Delegate delegateWithScalingGroup2 = createDelegateBuilder().build();
    delegateWithScalingGroup2.setAccountId(accountId);
    delegateWithScalingGroup2.setDelegateGroupName("test2");
    delegateWithScalingGroup2.setImmutable(true);
    delegateWithScalingGroup2.setVersion(IMMUTABLE_DELEGATE_VERSION);
    DelegateGroup scalingGroup2 = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name("test2")
                                      .status(DelegateGroupStatus.ENABLED)
                                      .ng(false)
                                      .build();

    // these two delegates should not appear.
    Delegate delegateWithScalingGroup4 = createDelegateBuilder().build();
    delegateWithScalingGroup4.setAccountId(accountId);
    delegateWithScalingGroup4.setDelegateGroupName("test2");
    delegateWithScalingGroup4.setDisconnected(true);
    // this delegate should cause an empty group to be returned
    Delegate delegateWithScalingGroup5 = createDelegateBuilder().build();
    delegateWithScalingGroup5.setAccountId(accountId);
    delegateWithScalingGroup5.setDelegateGroupName("test3");
    delegateWithScalingGroup5.setDisconnected(true);

    persistence.save(Arrays.asList(deletedDelegate, delegateWithScalingGroup1, delegateWithScalingGroup2,
        delegateWithScalingGroup4, delegateWithScalingGroup5));
    persistence.save(Arrays.asList(scalingGroup1, scalingGroup2));

    DelegateStatus delegateStatus = delegateService.getDelegateStatusWithScalingGroups(accountId);

    assertThat(delegateStatus.getPublishedVersions()).hasSize(1).contains(VERSION);

    assertThat(delegateStatus.getScalingGroups()).hasSize(2);
    assertThat(delegateStatus.getScalingGroups())
        .extracting(DelegateScalingGroup::getGroupName)
        .containsOnly("test1", "test2");

    for (DelegateScalingGroup group : delegateStatus.getScalingGroups()) {
      if (group.getGroupName().equals("test1")) {
        assertThat(group.getDelegates()).hasSize(1);
        assertThat(group.getDelegates())
            .extracting(DelegateStatus.DelegateInner::getUuid)
            .containsOnly(delegateWithScalingGroup1.getUuid());
        assertThat(group.getAutoUpgrade()).isEqualTo(AutoUpgrade.ON);
      } else if (group.getGroupName().equals("test2")) {
        assertThat(group.getDelegates()).hasSize(1);
        assertThat(group.getDelegates().get(0).getUuid()).isEqualTo(delegateWithScalingGroup2.getUuid());
        assertThat(group.getAutoUpgrade()).isEqualTo(AutoUpgrade.DETECTING);
      }
    }
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldGetDelegateStatus2ScalingGroupEmpty() {
    String accountId = generateUuid();
    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build());
    when(delegateVersionService.getImmutableDelegateImageTag(accountId)).thenReturn(IMMUTABLE_DELEGATE_IMAGE_TAG);

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);
    deletedDelegate.setDelegateGroupName("test");
    persistence.save(deletedDelegate);

    DelegateStatus status = delegateService.getDelegateStatusWithScalingGroups(accountId);

    assertThat(status.getScalingGroups()).isNotNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    persistence.save(delegate);
    delegate.setLastHeartBeat(System.currentTimeMillis());
    delegate.setStatus(DelegateInstanceStatus.DISABLED);
    delegate.setDelegateProfileId(delegateProfileId);
    delegateService.update(delegate);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(updatedDelegate).isEqualToIgnoringGivenFields(delegate, DelegateKeys.validUntil);
    verify(auditServiceHelper).reportForAuditingUsingAccountId(eq(accountId), any(), any(), eq(Type.UPDATE));
    verify(auditServiceHelper).reportForAuditingUsingAccountId(eq(accountId), any(), any(), eq(Type.APPLY));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testUpdateApprovalStatusShouldSetStatusToEnabled() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate = createDelegateBuilder().build();
    existingDelegate.setUuid(delegateId);
    existingDelegate.setAccountId(accountId);
    existingDelegate.setStatus(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
    persistence.save(existingDelegate);

    Delegate updatedDelegate = delegateService.updateApprovalStatus(accountId, delegateId, DelegateApproval.ACTIVATE);

    assertThat(existingDelegate).isEqualToIgnoringGivenFields(updatedDelegate, DelegateKeys.status);
    assertThat(DelegateInstanceStatus.ENABLED).isEqualTo(updatedDelegate.getStatus());
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(accountId, existingDelegate, updatedDelegate, Type.DELEGATE_APPROVAL);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testUpdateApprovalStatusShouldSetStatusToDeleted() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate = createDelegateBuilder().build();
    existingDelegate.setUuid(delegateId);
    existingDelegate.setAccountId(accountId);
    existingDelegate.setStatus(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
    persistence.save(existingDelegate);

    Delegate updatedDelegate = delegateService.updateApprovalStatus(accountId, delegateId, DelegateApproval.REJECT);

    assertThat(existingDelegate).isEqualToIgnoringGivenFields(updatedDelegate, DelegateKeys.status);
    assertThat(DelegateInstanceStatus.DELETED).isEqualTo(updatedDelegate.getStatus());
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(accountId, existingDelegate, updatedDelegate, Type.DELEGATE_REJECTION);
    verify(broadcaster).broadcast(SELF_DESTRUCT + delegateId);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUpdateEcs() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    delegate.setDelegateType(ECS);
    persistence.save(delegate);
    delegate.setLastHeartBeat(System.currentTimeMillis());
    delegate.setStatus(DelegateInstanceStatus.DISABLED);
    delegateService.update(delegate);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(updatedDelegate).isEqualToIgnoringGivenFields(delegate, DelegateKeys.validUntil);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAdd() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    delegate.setUuid(generateUuid());

    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).uuid(generateUuid()).build();
    delegate.setDelegateProfileId(delegateProfile.getUuid());

    when(delegateProfileService.get(delegate.getAccountId(), delegateProfile.getUuid())).thenReturn(delegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);

    assertThat(persistence.get(Delegate.class, delegate.getUuid())).isEqualTo(delegate);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldAddWithWaitingForApprovalStatus() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    delegate.setUuid(generateUuid());

    DelegateProfile delegateProfile = createDelegateProfileBuilder()
                                          .accountId(delegate.getAccountId())
                                          .uuid(generateUuid())
                                          .approvalRequired(true)
                                          .build();
    delegate.setDelegateProfileId(delegateProfile.getUuid());

    when(delegateProfileService.get(delegate.getAccountId(), delegateProfile.getUuid())).thenReturn(delegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);

    assertThat(delegate).isEqualToIgnoringGivenFields(delegate, DelegateKeys.status);
    assertThat(delegate.getStatus()).isEqualTo(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldAddWithPrimaryProfile() {
    String accountId = generateUuid();
    Delegate delegateWithoutProfile = createDelegateBuilder().build();
    delegateWithoutProfile.setAccountId(accountId);
    delegateWithoutProfile.setUuid(generateUuid());

    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegateWithoutProfile.getAccountId()).primary(true).build();
    when(delegateProfileService.fetchCgPrimaryProfile(delegateWithoutProfile.getAccountId()))
        .thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegateWithoutProfile = delegateService.add(delegateWithoutProfile);

    Delegate savedDelegate = persistence.get(Delegate.class, delegateWithoutProfile.getUuid());
    assertThat(savedDelegate).isEqualToIgnoringGivenFields(delegateWithoutProfile, DelegateKeys.delegateProfileId);
    assertThat(savedDelegate.getDelegateProfileId()).isEqualTo(primaryDelegateProfile.getUuid());
    Delegate delegateWithNonExistingProfile = createDelegateBuilder().build();
    delegateWithNonExistingProfile.setAccountId(accountId);
    delegateWithNonExistingProfile.setUuid(generateUuid());
    delegateWithNonExistingProfile.setDelegateProfileId("nonExistingProfile");
    when(delegateProfileService.get(
             delegateWithoutProfile.getAccountId(), delegateWithNonExistingProfile.getDelegateProfileId()))
        .thenReturn(null);

    delegateWithNonExistingProfile = delegateService.add(delegateWithNonExistingProfile);

    savedDelegate = persistence.get(Delegate.class, delegateWithNonExistingProfile.getUuid());
    assertThat(savedDelegate)
        .isEqualToIgnoringGivenFields(delegateWithNonExistingProfile, DelegateKeys.delegateProfileId);
    assertThat(savedDelegate.getDelegateProfileId()).isEqualTo(primaryDelegateProfile.getUuid());

    // Test Ng primary profile
    Delegate ngDelegateWithoutProfile = createDelegateBuilder().build();
    ngDelegateWithoutProfile.setAccountId(accountId);
    ngDelegateWithoutProfile.setUuid(generateUuid());
    ngDelegateWithoutProfile.setNg(true);

    DelegateProfile ngPrimaryDelegateProfile = createDelegateProfileBuilder()
                                                   .accountId(ngDelegateWithoutProfile.getAccountId())
                                                   .ng(true)
                                                   .primary(true)
                                                   .build();
    when(delegateProfileService.fetchNgPrimaryProfile(ngDelegateWithoutProfile.getAccountId(), null))
        .thenReturn(ngPrimaryDelegateProfile);

    ngDelegateWithoutProfile = delegateService.add(ngDelegateWithoutProfile);

    savedDelegate = persistence.get(Delegate.class, ngDelegateWithoutProfile.getUuid());
    assertThat(savedDelegate.getDelegateProfileId()).isEqualTo(ngPrimaryDelegateProfile.getUuid());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldNotAddMoreThanAllowedDelegates() {
    String accountId = generateUuid();
    int maxDelegatesAllowed = 1;
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(maxDelegatesAllowed);

    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    IntStream.range(0, maxDelegatesAllowed).forEach(i -> delegateService.add(delegate));
    try {
      Delegate maxUsageDelegate = createDelegateBuilder().build();
      maxUsageDelegate.setAccountId(accountId);
      delegateService.add(delegate);
      fail("");
    } catch (WingsException ignored) {
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDelete() {
    String id = persistence.save(
        createDelegateBuilder().lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis()).build());
    delegateService.delete(ACCOUNT_ID, id);
    assertThat(persistence.get(Delegate.class, id)).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldForceDelete() {
    String id = persistence.save(
        createDelegateBuilder().lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis()).build());
    delegateService.delete(ACCOUNT_ID, id);
    assertThat(persistence.get(Delegate.class, id)).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldMarkDelegateAsDeleted() {
    String id = persistence.save(
        createDelegateBuilder().lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis()).build());
    delegateService.delete(ACCOUNT_ID, id);
    Delegate deletedDelegate = persistence.get(Delegate.class, id);
    assertThat(deletedDelegate).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldDeleteDelegateGroup() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder()
            .accountId(accountId)
            .name("groupname")
            .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
            .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
            .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroup(accountId, delegateGroup.getUuid());

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
    verify(eventProducer).send(any());

    // Account level delegates
    delegateGroup = DelegateGroup.builder()
                        .accountId(accountId)
                        .name("groupname-acct")
                        .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                        .build();
    persistence.save(delegateGroup);

    d1 = createDelegateBuilder()
             .accountId(accountId)
             .delegateName("groupname-acct")
             .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
             .delegateGroupId(delegateGroup.getUuid())
             .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
             .build();
    persistence.save(d1);
    d2 = createDelegateBuilder()
             .accountId(accountId)
             .delegateName("groupname-acct")
             .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
             .delegateGroupId(delegateGroup.getUuid())
             .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
             .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroup(accountId, delegateGroup.getUuid());

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
    verify(eventProducer, times(2)).send(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldForceDeleteDelegateGroup() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder()
            .accountId(accountId)
            .name("groupname")
            .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
            .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
            .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroup(accountId, delegateGroup.getUuid());

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldForceDeleteDelegateGroupByIdentifier() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name("groupname")
                                      .identifier("identifier")
                                      .owner(DelegateEntityOwner.builder().identifier("orgId/projectId").build())
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                      .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroupV2(accountId, "orgId", "projectId", delegateGroup.getIdentifier());

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getIdentifier())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldMarkDelegateGroupAsDeleted() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder()
            .accountId(accountId)
            .name("groupname2")
            .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
            .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
            .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname2")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname2")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier(generateUuid() + "/" + generateUuid()).build())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroup(accountId, delegateGroup.getUuid());

    DelegateGroup deletedDelegateGroup = persistence.get(DelegateGroup.class, delegateGroup.getUuid());
    assertThat(deletedDelegateGroup).isNull();

    Delegate deletedDelegate1 = persistence.get(Delegate.class, d1.getUuid());
    assertThat(deletedDelegate1).isNull();

    Delegate deletedDelegate2 = persistence.get(Delegate.class, d2.getUuid());
    assertThat(deletedDelegate2).isNull();
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldMarkDelegateGroupAsDeletedByIdentifier() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name("groupname2")
                                      .identifier("identifier2")
                                      .owner(DelegateEntityOwner.builder().identifier("orgId/projectId").build())
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                      .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname2")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier("orgId/projectId").build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname2")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier("orgId/projectId").build())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroupV2(accountId, "orgId", "projectId", delegateGroup.getIdentifier());

    DelegateGroup deletedDelegateGroup = persistence.get(DelegateGroup.class, delegateGroup.getUuid());
    assertThat(deletedDelegateGroup).isNull();

    Delegate deletedDelegate1 = persistence.get(Delegate.class, d1.getUuid());
    assertThat(deletedDelegate1).isNull();

    Delegate deletedDelegate2 = persistence.get(Delegate.class, d2.getUuid());
    assertThat(deletedDelegate2).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldRegister() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(delegate);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);
    assertThat(delegateFromDb).isEqualTo(delegate);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParams() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name(DELEGATE_GROUP_NAME)
                                      .status(DelegateGroupStatus.ENABLED)
                                      .ng(true)
                                      .build();
    persistence.save(delegateGroup);

    // for ng delegates DelegateName and DelegateGroupName has always been same
    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(delegateGroup.getUuid())
                                .ng(true)
                                .version(VERSION)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .tags(ImmutableList.of("tag1", "tag2"))
                                .build();

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, null)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, true);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);
    DelegateGroup delegateGroupFromDb = delegateCache.getDelegateGroup(accountId, delegateGroup.getUuid());

    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.isNg()).isTrue();
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(delegateGroup.getName());
    assertThat(delegateFromDb.getDelegateGroupId()).isEqualTo(params.getDelegateGroupId());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateGroupFromDb.getTags()).containsExactlyInAnyOrder("tag1", "tag2");
    assertThat(delegateFromDb.isMtls()).isTrue();
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParamsWithExpirationTime() {
    String accountId = generateUuid();
    long expirationTimestamp = 1667260800000L;

    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name(DELEGATE_GROUP_NAME)
                                      .status(DelegateGroupStatus.ENABLED)
                                      .ng(true)
                                      .build();
    persistence.save(delegateGroup);

    // for ng delegates DelegateName and DelegateGroupName has always been same
    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(delegateGroup.getUuid())
                                .ng(true)
                                .version(IMMUTABLE_DELEGATE_VERSION)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .immutable(true)
                                .tags(ImmutableList.of("tag1", "tag2"))
                                .build();

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, null)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, true);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);
    DelegateGroup delegateGroupFromDb = delegateCache.getDelegateGroup(accountId, delegateGroup.getUuid());

    assertThat(delegateFromDb.getExpirationTime()).isEqualTo(expirationTimestamp);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParamsWithNoProfile() {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name(DELEGATE_GROUP_NAME)
                                      .status(DelegateGroupStatus.ENABLED)
                                      .ng(true)
                                      .build();
    persistence.save(delegateGroup);

    // for ng delegates DelegateName and DelegateGroupName has always been same
    DelegateParams params = DelegateParams.builder()
                                .accountId(ACCOUNT_ID)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .ng(true)
                                .version(VERSION)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    Delegate delegateFromDb = delegateCache.get(ACCOUNT_ID, registerResponse.getDelegateId(), true);
    DelegateGroup delegateGroupFromDb = delegateCache.getDelegateGroup(ACCOUNT_ID, delegateGroup.getUuid());

    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.isNg()).isTrue();
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(delegateGroup.getName());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateGroupFromDb.getAccountId()).isEqualTo(delegateGroup.getAccountId());
    assertThat(delegateGroupFromDb.getName()).isEqualTo(delegateGroup.getName());
    assertThat(delegateFromDb.isMtls()).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParamsWithOrgId() {
    final String accountId = generateUuid();
    final String delegateGroupId = generateUuid();
    final String orgId = "orgId";
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, StringUtils.EMPTY);

    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(delegateGroupId)
                                .version(VERSION)
                                .ng(true)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    DelegateGroup group =
        DelegateGroup.builder().accountId(accountId).uuid(delegateGroupId).name(DELEGATE_GROUP_NAME).build();
    persistence.save(group);
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, owner)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);

    assertThat(delegateFromDb.getOwner().getIdentifier()).isEqualTo(orgId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParamsWithProjectId() {
    final String accountId = generateUuid();
    final String delegateGroupId = generateUuid();
    final String orgId = "orgId";
    final String projectId = "projectId";
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(delegateGroupId)
                                .version(VERSION)
                                .proxy(true)
                                .ng(true)
                                .pollingModeEnabled(true)
                                .build();

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    DelegateGroup group =
        DelegateGroup.builder().accountId(accountId).uuid(delegateGroupId).name(DELEGATE_GROUP_NAME).build();
    persistence.save(group);
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, owner)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);

    assertThat(delegateFromDb.getOwner().getIdentifier()).isEqualTo("orgId/projectId");
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldRegisterExistingDelegateParams() {
    String accountId = generateUuid();
    DelegateParams params = DelegateParams.builder()
                                .delegateId(generateUuid())
                                .accountId(accountId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(DOCKER_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateGroupName(DELEGATE_GROUP_NAME)
                                .version(VERSION)
                                .ng(false)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();
    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    when(delegateProfileService.fetchCgPrimaryProfile(accountId)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);

    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(params.getDelegateGroupName());
    assertThat(delegateFromDb.getDelegateGroupId()).isNotNull();
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateFromDb.isNg()).isEqualTo(params.isNg());
    assertThat(delegateFromDb.isMtls()).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldRegisterExistingDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    delegate.setMtls(true);
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    delegateService.register(delegate);
    Delegate registeredDelegate = delegateCache.get(accountId, delegate.getUuid(), true);
    assertThat(registeredDelegate).isEqualToIgnoringGivenFields(delegate, DelegateKeys.validUntil);
    assertThat(registeredDelegate.isMtls()).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldRegisterParamsWithExistingDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder()
                            .accountId(accountId)
                            .hostName(HOST_NAME)
                            .description(DESCRIPTION)
                            .delegateType(DOCKER_DELEGATE)
                            .ip("127.0.0.1")
                            .delegateGroupName(DELEGATE_GROUP_NAME)
                            .version(VERSION)
                            .proxy(false)
                            .polllingModeEnabled(false)
                            .mtls(false)
                            .build();
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    DelegateParams params = DelegateParams.builder()
                                .delegateId(delegate.getUuid())
                                .accountId(accountId)
                                .hostName(HOST_NAME + "UPDATED")
                                .description(DESCRIPTION + "UPDATED")
                                .delegateType(DOCKER_DELEGATE + "UPDATED")
                                .ip("127.0.0.2")
                                .delegateGroupName(DELEGATE_GROUP_NAME + "UPDATED")
                                .ng(false)
                                .version(VERSION + "UPDATED")
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    delegateService.register(params, true);

    Delegate delegateFromDb = delegateCache.get(accountId, delegate.getUuid(), true);
    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.isNg()).isFalse();
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(params.getDelegateGroupName());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateFromDb.isMtls()).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldRegisterParamsWithExistingDelegateForECS() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder()
                            .accountId(accountId)
                            .hostName(HOST_NAME)
                            .description(DESCRIPTION)
                            .delegateType(ECS)
                            .ip("127.0.0.1")
                            .delegateGroupName(DELEGATE_GROUP_NAME)
                            .version(VERSION)
                            .proxy(false)
                            .ng(false)
                            .polllingModeEnabled(false)
                            .mtls(false)
                            .build();
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    DelegateParams params = DelegateParams.builder()
                                .delegateId(delegate.getUuid())
                                .accountId(accountId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION + "UPDATED")
                                .delegateType(ECS)
                                .ip("127.0.0.2")
                                .delegateGroupName(DELEGATE_GROUP_NAME + "UPDATED")
                                .delegateGroupId(delegate.getDelegateGroupId())
                                .delegateRandomToken("13")
                                .version(VERSION + "UPDATED")
                                .ng(false)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    delegateService.register(params, true);

    Delegate delegateFromDb = delegateCache.get(accountId, delegate.getUuid(), true);

    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(params.getDelegateGroupName());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateFromDb.isNg()).isEqualTo(params.isNg());
    assertThat(delegateFromDb.isMtls()).isTrue();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldRegisterHeartbeatPolling() throws IllegalAccessException {
    DelegateDao mockConnectionDao = mock(DelegateDao.class);
    FieldUtils.writeField(delegateService, "delegateDao", mockConnectionDao, true);
    String delegateConnectionId = generateTimeBasedUuid();
    DelegateConnectionHeartbeat heartbeat =
        DelegateConnectionHeartbeat.builder().version(VERSION).delegateConnectionId(delegateConnectionId).build();
    FieldUtils.writeField(delegateService, "delegateDao", delegateDao, true);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldRegisterHeartbeatStreaming() throws IllegalAccessException {
    DelegateDao mockConnectionDao = mock(DelegateDao.class);
    FieldUtils.writeField(delegateService, "delegateDao", mockConnectionDao, true);
    String delegateConnectionId = generateTimeBasedUuid();
    DelegateConnectionHeartbeat heartbeat =
        DelegateConnectionHeartbeat.builder().version(VERSION).delegateConnectionId(delegateConnectionId).build();
    FieldUtils.writeField(delegateService, "delegateDao", delegateDao, true);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldRegisterHeartbeatEcsDelegateNotSendSelfDestruct()
      throws IllegalAccessException, InterruptedException {
    Delegate delegate = createDelegateBuilder()
                            .accountId(ACCOUNT_ID)
                            .uuid(DELEGATE_ID)
                            .hostName(HOST_NAME)
                            .description(DESCRIPTION)
                            .delegateType(ECS)
                            .ip("127.0.0.1")
                            .delegateGroupName(DELEGATE_GROUP_NAME)
                            .version(VERSION)
                            .proxy(false)
                            .polllingModeEnabled(false)
                            .build();
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);
    delegateService.add(delegate);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    String delegateConnectionId = generateTimeBasedUuid();
    //@TODO
    // verify(broadcaster, never()).broadcast(SELF_DESTRUCT + DELEGATE_ID + "-" + delegateConnectionId);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void registerHeartbeatNotSelfDestruct() throws IllegalAccessException, InterruptedException {
    try {
      Delegate delegate = createDelegateBuilder()
                              .accountId(ACCOUNT_ID)
                              .uuid(DELEGATE_ID)
                              .hostName(HOST_NAME)
                              .description(DESCRIPTION)
                              .delegateType(ECS)
                              .ip("127.0.0.1")
                              .delegateGroupName(DELEGATE_GROUP_NAME)
                              .version(VERSION)
                              .proxy(false)
                              .polllingModeEnabled(false)
                              .build();
      DelegateProfile primaryDelegateProfile =
          createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

      delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
      when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);
      when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);
      delegateService.add(delegate);

      DelegateDao mockConnectionDao = mock(DelegateDao.class);
      FieldUtils.writeField(delegateService, "delegateDao", mockConnectionDao, true);
      String delegateConnectionId = generateTimeBasedUuid();
      Thread.sleep(2L);
      String newerDelegateConnectionId = generateTimeBasedUuid();
      /* verify(mockConnectionDao)
           .findAndDeletePreviousConnections(ACCOUNT_ID, DELEGATE_ID, delegateConnectionId, VERSION);
       verify(mockConnectionDao, never()).replaceWithNewerConnection(delegateConnectionId, newerExistingConnection);*/
    } finally {
      FieldUtils.writeField(delegateService, "delegateDao", delegateDao, true);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotFailOnNullRegisterParamsWithExistingDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder()
                            .accountId(accountId)
                            .hostName(HOST_NAME)
                            .description(DESCRIPTION)
                            .delegateType(DOCKER_DELEGATE)
                            .ip("127.0.0.1")
                            .delegateGroupName(DELEGATE_GROUP_NAME)
                            .ng(false)
                            .version(VERSION)
                            .proxy(false)
                            .polllingModeEnabled(false)
                            .build();
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    DelegateParams params = DelegateParams.builder()
                                .delegateId(delegate.getUuid())
                                .accountId(accountId)
                                .delegateType(DOCKER_DELEGATE)
                                .hostName(HOST_NAME + "UPDATED")
                                .version(VERSION + "UPDATED")
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .ng(false)
                                .build();

    delegateService.register(params, false);

    Delegate delegateFromDb = delegateCache.get(accountId, delegate.getUuid(), true);
    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(params.getDelegateGroupName());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateFromDb.isNg()).isFalse();
    assertThat(delegateFromDb.isMtls()).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldRegisterHeartbeatShellScriptDelegateWithSameLocation() {}

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotRegisterExistingDelegateForDeletedAccount() {
    String accountId = generateUuid();
    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("localhost")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();

    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);

    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    when(licenseService.isAccountDeleted(accountId)).thenReturn(true);

    DelegateRegisterResponse registerResponse = delegateService.register(delegate);
    assertThat(registerResponse.getAction()).isEqualTo(DelegateRegisterResponse.Action.SELF_DESTRUCT);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotRegisterNewDelegateForDeletedAccount() {
    DelegateParams delegateParams = DelegateParams.builder()
                                        .accountId("DELETED_ACCOUNT")
                                        .ip("127.0.0.1")
                                        .hostName("localhost")
                                        .version(VERSION)
                                        .lastHeartBeat(System.currentTimeMillis())
                                        .build();
    when(licenseService.isAccountDeleted("DELETED_ACCOUNT")).thenReturn(true);
    DelegateRegisterResponse registerResponse = delegateService.register(delegateParams, false);
    assertThat(registerResponse.getAction()).isEqualTo(DelegateRegisterResponse.Action.SELF_DESTRUCT);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotRegisterNewDelegateForDeletedDelegateGroup() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder().accountId(accountId).name(generateUuid()).status(DelegateGroupStatus.DELETED).build();
    persistence.save(delegateGroup);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("localhost")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateGroupId(delegateGroup.getUuid())
                            .build();

    DelegateRegisterResponse registerResponse = delegateService.register(delegate);
    assertThat(registerResponse.getAction()).isEqualTo(DelegateRegisterResponse.Action.SELF_DESTRUCT);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotRegisterDelegateParamsNewDelegateForDeletedDelegateGroup() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder().accountId(accountId).name(generateUuid()).status(DelegateGroupStatus.DELETED).build();
    persistence.save(delegateGroup);

    DelegateParams delegateParams = DelegateParams.builder()
                                        .accountId(accountId)
                                        .ip("127.0.0.1")
                                        .hostName("localhost")
                                        .version(VERSION)
                                        .lastHeartBeat(System.currentTimeMillis())
                                        .delegateGroupId(delegateGroup.getUuid())
                                        .delegateType(SHELL_SCRIPT)
                                        .build();

    DelegateRegisterResponse registerResponse = delegateService.register(delegateParams, false);
    assertThat(registerResponse.getAction()).isEqualTo(DelegateRegisterResponse.Action.SELF_DESTRUCT);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownloadScripts() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn("harness/delegate:latest");
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn("harness/upgrader:latest");
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());
    File gzipFile = delegateService.downloadScripts(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME);
    verifyDownloadScriptsResult(gzipFile, "/expectedStartOpenJdk.sh", "/expectedDelegateOpenJdk.sh");
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldDownloadCeKubernetesYaml() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());

    File downloadedFile = delegateService.downloadCeKubernetesYaml(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME);

    assertThat(IOUtils.readLines(new FileInputStream(downloadedFile), Charset.defaultCharset()).get(0)).isNotNull();
    assertThat(downloadedFile.getName().startsWith("harness-delegate")).isTrue();
    assertThat(downloadedFile.getName().endsWith("yaml")).isTrue();
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldDownloadDelegateValuesYamlFile() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn("harness/delegate:latest");
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn("harness/upgrader:latest");
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());

    File downloadedFile = delegateService.downloadDelegateValuesYamlFile(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME);

    assertThat(IOUtils.readLines(new FileInputStream(downloadedFile), Charset.defaultCharset()).get(0)).isNotNull();
    assertThat(downloadedFile.getName().startsWith("harness-delegate")).isTrue();
    assertThat(downloadedFile.getName().endsWith("yaml")).isTrue();
  }

  @Test
  @Owner(developers = LUCAS)
  @Category(UnitTests.class)
  public void shouldDownloadECSDelegate() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn("harness/delegate:latest");
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn("harness/upgrader:latest");
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());

    File downloadedFile = delegateService.downloadECSDelegate("https://localhost:9090", "https://localhost:7070",
        ACCOUNT_ID, false, HOST_NAME, DELEGATE_GROUP_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME);

    assertThat(IOUtils.readLines(new FileInputStream(downloadedFile), Charset.defaultCharset()).get(0)).isNotNull();
    assertThat(downloadedFile.getName().startsWith("harness-delegate")).isTrue();
    assertThat(downloadedFile.getName().endsWith("tar.gz")).isTrue();

    verifyDownloadECSDelegateResult(downloadedFile);
  }

  private void verifyDownloadECSDelegateResult(File gzipFile) throws IOException {
    File tarFile = File.createTempFile(ECS_DELEGATE, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(ECS_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(ECS_DELEGATE + "/ecs-task-spec.json");

      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file)
          .extracting(TarArchiveEntry::getName)
          .isEqualTo(ECS_DELEGATE + "/service-spec-for-awsvpc-mode.json");

      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(ECS_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldDownloadScriptsWithPrimaryProfile() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(createDelegateProfileBuilder().uuid(DELEGATE_PROFILE_ID).build());
    File gzipFile = delegateService.downloadScripts(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, null, null);
    verifyDownloadScriptsResult(gzipFile, "/expectedStartOpenJdk.sh", "/expectedDelegateOpenJdk.sh");

    when(delegateProfileService.get(ACCOUNT_ID, "invalidProfile")).thenReturn(null);
    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(createDelegateProfileBuilder().uuid(DELEGATE_PROFILE_ID).build());
    gzipFile = delegateService.downloadScripts(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, "invalidProfile", null);
    verifyDownloadScriptsResult(gzipFile, "/expectedStartOpenJdk.sh", "/expectedDelegateOpenJdk.sh");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldDownloadScriptsWithoutDelegateName() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());
    File gzipFile = delegateService.downloadScripts(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "", DELEGATE_PROFILE_ID, null);
    verifyDownloadScriptsResult(
        gzipFile, "/expectedStartWithoutDelegateName.sh", "/expectedDelegateWithoutDelegateName.sh");
  }

  private void verifyDownloadScriptsResult(File gzipFile, String expectedStartFilepath, String expectedDelegateFilepath)
      throws IOException {
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(DELEGATE_DIR + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/start.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      String expectedD =
          CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(expectedDelegateFilepath)))
              .replaceAll("8888", "" + port);
      String actualD = new String(buffer);
      assertThat(actualD.equals(expectedD));
      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/stop.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);

      String expectedFile = "/expectedStopOpenJdk.sh";
      String expectedS = CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(expectedFile)))
                             .replaceAll("8888", "" + port);
      String actualS = new String(buffer);
      assertThat(actualS.equals(expectedS));
      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/setup-proxy.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      String expectedP = CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(expectedFile)))
                             .replaceAll("8888", "" + port);
      String actualP = new String(buffer);
      assertThat(actualP.equals(expectedP));
      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/init.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      String expectedR = CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(expectedFile)))
                             .replaceAll("8888", "" + port);
      String actualR = new String(buffer);
      assertThat(actualR.equals(expectedR));
      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldDownloadScriptsForOpenJdk() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(createDelegateProfileBuilder().uuid(DELEGATE_PROFILE_ID).build());
    File gzipFile = delegateService.downloadScripts(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, DELEGATE_PROFILE_ID, null);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(DELEGATE_DIR + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/start.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(
              CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream("/expectedStartOpenJdk.sh")))
                  .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/stop.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(
              CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream("/expectedStopOpenJdk.sh")))
                  .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/setup-proxy.sh");
      buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(
              CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream("/expectedSetupProxy.sh"))));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/init.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(DELEGATE_DIR + "/README.txt");
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownloadDocker() throws IOException, TemplateException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());
    File gzipFile = delegateService.downloadDocker(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME);
    verifyDownloadDockerResult(gzipFile, "/expectedLaunchHarnessDelegate.sh");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownloadDockerWithoutDelegateName() throws IOException, TemplateException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID))
        .thenReturn(createDelegateProfileBuilder().build());
    File gzipFile = delegateService.downloadDocker(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, null, DELEGATE_PROFILE_ID, null);
    verifyDownloadDockerResult(gzipFile, "/expectedLaunchHarnessDelegateWithoutName.sh");

    gzipFile = delegateService.downloadDocker(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "", DELEGATE_PROFILE_ID, null);
    verifyDownloadDockerResult(gzipFile, "/expectedLaunchHarnessDelegateWithoutName.sh");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldDownloadDockerWithPrimaryProfile() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(createDelegateProfileBuilder().uuid(DELEGATE_PROFILE_ID).build());
    File gzipFile = delegateService.downloadDocker(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, null, TOKEN_NAME);
    verifyDownloadDockerResult(gzipFile, "/expectedLaunchHarnessDelegate.sh");

    when(delegateProfileService.get(ACCOUNT_ID, "invalidProfile")).thenReturn(null);
    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(createDelegateProfileBuilder().uuid(DELEGATE_PROFILE_ID).build());
    gzipFile = delegateService.downloadDocker(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, DELEGATE_NAME, "invalidProfile", null);
    verifyDownloadDockerResult(gzipFile, "/expectedLaunchHarnessDelegate.sh");
  }

  private void verifyDownloadDockerResult(File gzipFile, String expectedLaunchDelegateFilepath) throws IOException {
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(DOCKER_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(DOCKER_DELEGATE + "/launch-harness-delegate.sh");
      assertThat(file).extracting(TarArchiveEntry::getMode).isEqualTo(0755);

      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(
              CharStreams
                  .toString(new InputStreamReader(getClass().getResourceAsStream(expectedLaunchDelegateFilepath)))
                  .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(DOCKER_DELEGATE + "/README.txt");
    }
  }

  private static void uncompressGzipFile(File gzipFile, File file) throws IOException {
    try (FileInputStream fis = new FileInputStream(gzipFile); FileOutputStream fos = new FileOutputStream(file);
         GZIPInputStream gzipIS = new GZIPInputStream(fis)) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = gzipIS.read(buffer)) != -1) {
        fos.write(buffer, 0, len);
      }
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownloadKubernetes() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn("harness/delegate:latest");
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn("harness/upgrader:latest");
    File gzipFile = delegateService.downloadKubernetes(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "harness-delegate", "", TOKEN_NAME, runAsRoot);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(
              CharStreams
                  .toString(new InputStreamReader(getClass().getResourceAsStream("/expectedHarnessDelegate.yaml")))
                  .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownloadKubernetesImmutable() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, true)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);
    File gzipFile = delegateService.downloadKubernetes(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "harness-delegate", "", TOKEN_NAME, runAsRoot);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateImmutable.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldDownloadKubernetesImmutableWithNonRootAccess() throws IOException {
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, true)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);
    File gzipFile = delegateService.downloadKubernetes(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "harness-delegate", "", TOKEN_NAME, false);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateWithNonRoot.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldDownloadKubernetesWithCiEnabled() throws IOException {
    Account account = anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).withNextGenEnabled(true).build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    File gzipFile = delegateService.downloadKubernetes(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "harness-delegate", "", null, runAsRoot);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateWithCiEnabled.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldDownloadKubernetesImmutableWithCiEnabled() throws IOException {
    Account account = anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).withNextGenEnabled(true).build();
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, true)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    File gzipFile = delegateService.downloadKubernetes(
        "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, "harness-delegate", "", null, runAsRoot);
    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateImmutableWithCiEnabled.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateKubernetesYamlWithoutName() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder().build();

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Name must be provided.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateKubernetesYamlWithoutSize() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder().delegateConfigurationId("delConfigId").name("name").build();
    when(delegateProfileService.get(accountId, "delConfigId")).thenReturn(DelegateProfile.builder().build());

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Size must be provided.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateKubernetesYamlWithoutOrWithInvalidK8sConfig() {
    String accountId = generateUuid();

    // K8sConfig is null
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .delegateConfigurationId("delConfigId")
                                            .name("name")
                                            .size(DelegateSize.LARGE)
                                            .description("desc")
                                            .build();
    when(delegateProfileService.get(accountId, "delConfigId")).thenReturn(DelegateProfile.builder().build());

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("K8s permission type must be provided.");

    // K8sConfig does not have permission set
    DelegateSetupDetails setupDetails2 = DelegateSetupDetails.builder()
                                             .delegateConfigurationId("delConfigId")
                                             .name("name")
                                             .size(DelegateSize.LARGE)
                                             .description("desc")
                                             .k8sConfigDetails(K8sConfigDetails.builder().build())
                                             .build();

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("K8s permission type must be provided.");

    // K8sConfig does not have namespace set for namespace admin permission
    DelegateSetupDetails setupDetails3 =
        DelegateSetupDetails.builder()
            .delegateConfigurationId("delConfigId")
            .name("name")
            .size(DelegateSize.LARGE)
            .description("desc")
            .k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(K8sPermissionType.NAMESPACE_ADMIN).build())
            .build();

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("K8s namespace must be provided for this type of permission.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGenerateKubernetesClusterAdminImmutableYaml() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder()
            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
            .delegateConfigurationId("delConfigId")
            .name("harness-delegate")
            .identifier("_delegateGroupId1")
            .size(DelegateSize.LARGE)
            .description("desc")
            .k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(CLUSTER_ADMIN).build())
            .delegateType(DelegateType.KUBERNETES)
            .tokenName(TOKEN_NAME)
            .build();
    when(delegateProfileService.get(ACCOUNT_ID, "delConfigId")).thenReturn(DelegateProfile.builder().build());
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);

    File gzipFile = delegateService.generateKubernetesYaml(ACCOUNT_ID, setupDetails, "https://localhost:9090",
        "https://localhost:7070", MediaType.MULTIPART_FORM_DATA_TYPE);

    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateNgClusterAdminImmutable.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGenerateKubernetesClusterViewerImmutableYaml() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, "delConfigId")).thenReturn(DelegateProfile.builder().build());
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder()
            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
            .delegateConfigurationId("delConfigId")
            .name("harness-delegate")
            .identifier("_delegateGroupId1")
            .size(DelegateSize.LARGE)
            .description("desc")
            .delegateType(DelegateType.KUBERNETES)
            .tokenName(TOKEN_NAME)
            .k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(K8sPermissionType.CLUSTER_VIEWER).build())
            .build();

    File gzipFile = delegateService.generateKubernetesYaml(ACCOUNT_ID, setupDetails, "https://localhost:9090",
        "https://localhost:7070", MediaType.MULTIPART_FORM_DATA_TYPE);

    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateNgClusterViewerImmutable.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGenerateKubernetesNamespaceAdminImmutable() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(delegateProfileService.get(ACCOUNT_ID, "delConfigId")).thenReturn(DelegateProfile.builder().build());
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
                                            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
                                            .delegateConfigurationId("delConfigId")
                                            .name("harness-delegate")
                                            .identifier("_delegateGroupId1")
                                            .size(DelegateSize.LARGE)
                                            .description("desc")
                                            .delegateType(DelegateType.KUBERNETES)
                                            .k8sConfigDetails(K8sConfigDetails.builder()
                                                                  .k8sPermissionType(K8sPermissionType.NAMESPACE_ADMIN)
                                                                  .namespace("test-namespace")
                                                                  .build())
                                            .tokenName(TOKEN_NAME)
                                            .build();

    File gzipFile = delegateService.generateKubernetesYaml(ACCOUNT_ID, setupDetails, "https://localhost:9090",
        "https://localhost:7070", MediaType.MULTIPART_FORM_DATA_TYPE);

    File tarFile = File.createTempFile(DELEGATE_DIR, ".tar");
    uncompressGzipFile(gzipFile, tarFile);
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
      assertThat(tarArchiveInputStream.getNextEntry().getName()).isEqualTo(KUBERNETES_DELEGATE + "/");

      TarArchiveEntry file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(ArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/harness-delegate.yaml");
      byte[] buffer = new byte[(int) file.getSize()];
      IOUtils.read(tarArchiveInputStream, buffer);
      assertThat(new String(buffer))
          .isEqualTo(CharStreams
                         .toString(new InputStreamReader(
                             getClass().getResourceAsStream("/expectedHarnessDelegateNgNamespaceAdminImmutable.yaml")))
                         .replaceAll("8888", "" + port));

      file = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
      assertThat(file).extracting(TarArchiveEntry::getName).isEqualTo(KUBERNETES_DELEGATE + "/README.txt");
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetLatestVersion() {
    assertThat(delegateService.getLatestDelegateVersion(ACCOUNT_ID)).isEqualTo("9.9.9");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testProcessDelegateTaskResponseWithDelegateMetaInfo() {
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED, false);
    DelegateMetaInfo delegateMetaInfo = DelegateMetaInfo.builder().id(DELEGATE_ID).hostName(HOST_NAME).build();
    JenkinsExecutionResponse jenkinsExecutionResponse =
        JenkinsExecutionResponse.builder().delegateMetaInfo(delegateMetaInfo).build();

    delegateTaskService.processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .accountId(ACCOUNT_ID)
            .responseCode(DelegateTaskResponse.ResponseCode.OK)
            .response(jenkinsExecutionResponse)
            .build());
    DelegateTaskNotifyResponseData delegateTaskNotifyResponseData = jenkinsExecutionResponse;
    assertThat(delegateTaskNotifyResponseData.getDelegateMetaInfo().getHostName()).isEqualTo(HOST_NAME);
    assertThat(delegateTaskNotifyResponseData.getDelegateMetaInfo().getId()).isEqualTo(DELEGATE_ID);

    jenkinsExecutionResponse = JenkinsExecutionResponse.builder().delegateMetaInfo(delegateMetaInfo).build();
    delegateTaskNotifyResponseData = jenkinsExecutionResponse;
    persistence.save(delegateTask);
    delegateTaskService.processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .accountId(ACCOUNT_ID)
            .responseCode(DelegateTaskResponse.ResponseCode.OK)
            .response(jenkinsExecutionResponse)
            .build());
    assertThat(delegateTaskNotifyResponseData.getDelegateMetaInfo().getId()).isEqualTo(DELEGATE_ID);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotCheckForProfileIfManagerNotPrimary() {
    when(configurationController.isNotPrimary()).thenReturn(Boolean.TRUE);
    DelegateProfileParams delegateProfileParams = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "", 0);

    assertThat(delegateProfileParams).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotCheckForProfileIfNotEnabled() {
    when(configurationController.isNotPrimary()).thenReturn(Boolean.FALSE);
    String accountId = generateUuid();

    Delegate deletedDelegate = createDelegateBuilder().build();
    deletedDelegate.setStatus(DelegateInstanceStatus.DELETED);
    deletedDelegate.setAccountId(accountId);
    deletedDelegate.setUuid(generateUuid());
    persistence.save(deletedDelegate);

    DelegateProfileParams delegateProfileParams =
        delegateService.checkForProfile(accountId, deletedDelegate.getUuid(), "", 0);
    assertThat(delegateProfileParams).isNull();

    Delegate wapprDelegate = createDelegateBuilder().build();
    wapprDelegate.setStatus(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
    wapprDelegate.setAccountId(accountId);
    wapprDelegate.setUuid(generateUuid());
    persistence.save(wapprDelegate);

    delegateProfileParams = delegateService.checkForProfile(accountId, wapprDelegate.getUuid(), "", 0);

    assertThat(delegateProfileParams).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCheckForProfile() {
    when(configurationController.isNotPrimary()).thenReturn(Boolean.FALSE);
    Delegate delegate =
        Delegate.builder().uuid(DELEGATE_ID).accountId(ACCOUNT_ID).delegateProfileId("profile1").build();
    persistence.save(delegate);
    DelegateProfile profile = builder().accountId(ACCOUNT_ID).name("A Profile").startupScript("rm -rf /*").build();
    profile.setUuid("profile1");
    profile.setLastUpdatedAt(100L);
    when(delegateProfileService.get(ACCOUNT_ID, "profile1")).thenReturn(profile);

    DelegateProfileParams init = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "", 0);
    assertThat(init).isNotNull();
    assertThat(init.getProfileId()).isEqualTo("profile1");
    assertThat(init.getName()).isEqualTo("A Profile");
    assertThat(init.getProfileLastUpdatedAt()).isEqualTo(100L);
    assertThat(init.getScriptContent()).isEqualTo("rm -rf /*");

    init = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "profile2", 200L);
    assertThat(init).isNotNull();
    assertThat(init.getProfileId()).isEqualTo("profile1");
    assertThat(init.getName()).isEqualTo("A Profile");
    assertThat(init.getProfileLastUpdatedAt()).isEqualTo(100L);
    assertThat(init.getScriptContent()).isEqualTo("rm -rf /*");

    init = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "profile1", 99L);
    assertThat(init).isNotNull();
    assertThat(init.getProfileId()).isEqualTo("profile1");
    assertThat(init.getName()).isEqualTo("A Profile");
    assertThat(init.getProfileLastUpdatedAt()).isEqualTo(100L);
    assertThat(init.getScriptContent()).isEqualTo("rm -rf /*");

    init = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "profile1", 100L);
    assertThat(init).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCheckForProfileWithSecrets() {
    EncryptedData encryptedData = EncryptedData.builder().build();
    encryptedData.setUuid(generateUuid());
    List<EncryptedDataDetail> encryptionDetails = ImmutableList.of(
        EncryptedDataDetail.builder().encryptedData(SecretManager.buildRecordData(encryptedData)).build());
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(ACCOUNT_ID)
                                          .type(ENCRYPTED_TEXT)
                                          .encryptedValue(encryptedData.getUuid())
                                          .secretTextName("My Secret")
                                          .build();
    when(secretManager.getSecretMappedToAccountByName(ACCOUNT_ID, "My Secret")).thenReturn(encryptedData);
    when(secretManager.getEncryptionDetails(eq(serviceVariable), eq(null), eq(null))).thenReturn(encryptionDetails);

    Delegate delegate =
        Delegate.builder().uuid(DELEGATE_ID).accountId(ACCOUNT_ID).delegateProfileId("profileSecret").build();
    persistence.save(delegate);
    DelegateProfile profile = builder()
                                  .accountId(ACCOUNT_ID)
                                  .name("A Secret Profile")
                                  .startupScript("A secret: ${secrets.getValue(\"My Secret\")}")
                                  .build();
    profile.setUuid("profileSecret");
    profile.setLastUpdatedAt(100L);
    when(delegateProfileService.get(ACCOUNT_ID, "profileSecret")).thenReturn(profile);

    doAnswer(invocation -> {
      ((ServiceVariable) invocation.getArguments()[0]).setValue("Shhh! This is a secret!".toCharArray());
      return null;
    })
        .when(managerDecryptionService)
        .decrypt(eq(serviceVariable), eq(encryptionDetails));

    DelegateProfileParams init = delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "", 0);

    assertThat(init).isNotNull();
    assertThat(init.getProfileId()).isEqualTo("profileSecret");
    assertThat(init.getName()).isEqualTo("A Secret Profile");
    assertThat(init.getProfileLastUpdatedAt()).isEqualTo(100L);
    assertThat(init.getScriptContent()).isEqualTo("A secret: Shhh! This is a secret!");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSaveProfileResult_NoPrevious() {
    Delegate previousDelegate =
        Delegate.builder().uuid(DELEGATE_ID).accountId(ACCOUNT_ID).hostName("hostname").ip("1.2.3.4").build();
    persistence.save(previousDelegate);

    String content = "This is the profile result text";
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("profile-result").fileName("profile.result").build();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));

    when(fileService.saveFile(any(FileMetadata.class), any(InputStream.class), eq(FileBucket.PROFILE_RESULTS)))
        .thenReturn("file_id");

    long now = System.currentTimeMillis();
    delegateService.saveProfileResult(
        ACCOUNT_ID, DELEGATE_ID, false, FileBucket.PROFILE_RESULTS, inputStream, fileDetail);

    Delegate delegate = persistence.get(Delegate.class, DELEGATE_ID);
    assertThat(delegate.getProfileExecutedAt()).isGreaterThanOrEqualTo(now);
    assertThat(delegate.isProfileError()).isFalse();
    assertThat(delegate.getProfileResult()).isEqualTo("file_id");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSaveProfileResult_WithPrevious() {
    Delegate previousDelegate =
        Delegate.builder().uuid(DELEGATE_ID).accountId(ACCOUNT_ID).hostName("hostname").ip("1.2.3.4").build();
    previousDelegate.setProfileResult("previous-result");
    persistence.save(previousDelegate);

    String content = "This is the profile result text";
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("profile-result").fileName("profile.result").build();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));

    when(fileService.saveFile(any(FileMetadata.class), any(InputStream.class), eq(FileBucket.PROFILE_RESULTS)))
        .thenReturn("file_id");

    long now = System.currentTimeMillis();
    delegateService.saveProfileResult(
        ACCOUNT_ID, DELEGATE_ID, true, FileBucket.PROFILE_RESULTS, inputStream, fileDetail);

    verify(fileService).deleteFile(eq("previous-result"), eq(FileBucket.PROFILE_RESULTS));

    Delegate delegate = persistence.get(Delegate.class, DELEGATE_ID);
    assertThat(delegate.getProfileExecutedAt()).isGreaterThanOrEqualTo(now);
    assertThat(delegate.isProfileError()).isTrue();
    assertThat(delegate.getProfileResult()).isEqualTo("file_id");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetProfileResult() {
    Delegate delegate =
        Delegate.builder().uuid(DELEGATE_ID).accountId(ACCOUNT_ID).hostName("hostname").ip("1.2.3.4").build();
    delegate.setProfileResult("result_file_id");
    persistence.save(delegate);

    String content = "This is the profile result text";

    doAnswer(invocation -> {
      ((OutputStream) invocation.getArguments()[1]).write(content.getBytes(UTF_8));
      return null;
    })
        .when(fileService)
        .downloadToStream(eq("result_file_id"), any(OutputStream.class), eq(FileBucket.PROFILE_RESULTS));

    String result = delegateService.getProfileResult(ACCOUNT_ID, DELEGATE_ID);
    assertThat(result).isEqualTo(content);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetKubernetesDelegateNames() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);
    delegate = Delegate.builder()
                   .accountId(ACCOUNT_ID)
                   .ip("127.0.0.1")
                   .hostName("d.e.f")
                   .delegateName("k8s-name")
                   .version(VERSION)
                   .status(DelegateInstanceStatus.ENABLED)
                   .lastHeartBeat(System.currentTimeMillis())
                   .build();
    persistence.save(delegate);
    List<String> k8sNames = delegateService.getKubernetesDelegateNames(ACCOUNT_ID);
    assertThat(k8sNames.size()).isEqualTo(1);
    assertThat(k8sNames.get(0)).isEqualTo("k8s-name");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectorsUpTheHierarchyAcct() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    final DelegateGroup acctGroup = DelegateGroup.builder().name("acctGrp").accountId(accountId).ng(true).build();
    final DelegateGroup orgGroup = DelegateGroup.builder()
                                       .name("orgGrp")
                                       .accountId(accountId)
                                       .ng(true)
                                       .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                                       .build();
    final DelegateGroup projectGroup = DelegateGroup.builder()
                                           .name("projectGrp")
                                           .accountId(accountId)
                                           .ng(true)
                                           .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                           .build();

    persistence.saveBatch(Arrays.asList(acctGroup, orgGroup, projectGroup));

    final Set<String> actual = delegateService.getAllDelegateSelectorsUpTheHierarchy(accountId, null, null);
    assertThat(actual).containsExactlyInAnyOrder("acctgrp");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectorsUpTheHierarchyOrg() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    final DelegateGroup acctGroup = DelegateGroup.builder().name("acctGrp").accountId(accountId).ng(true).build();
    final DelegateGroup orgGroup = DelegateGroup.builder()
                                       .name("orgGrp")
                                       .accountId(accountId)
                                       .ng(true)
                                       .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                                       .build();
    final DelegateGroup projectGroup = DelegateGroup.builder()
                                           .name("projectGrp")
                                           .accountId(accountId)
                                           .ng(true)
                                           .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                           .build();

    persistence.saveBatch(Arrays.asList(acctGroup, orgGroup, projectGroup));

    final Set<String> actual = delegateService.getAllDelegateSelectorsUpTheHierarchy(accountId, orgId, null);
    assertThat(actual).containsExactlyInAnyOrder("acctgrp", "orggrp");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectorsUpTheHierarchyProj() {
    final String accountId = generateUuid();
    final String orgId = generateUuid();
    final String projectId = generateUuid();

    final DelegateGroup acctGroup = DelegateGroup.builder()
                                        .name("acctGrp")
                                        .accountId(accountId)
                                        .ng(true)
                                        .tags(ImmutableSet.of("custom-acct"))
                                        .build();
    final DelegateGroup orgGroup = DelegateGroup.builder()
                                       .name("orgGrp")
                                       .accountId(accountId)
                                       .ng(true)
                                       .tags(ImmutableSet.of("custom-org"))
                                       .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                                       .build();
    final DelegateGroup projectGroup = DelegateGroup.builder()
                                           .name("projectGrp")
                                           .accountId(accountId)
                                           .ng(true)
                                           .tags(ImmutableSet.of("custom-proj"))
                                           .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                           .build();

    persistence.saveBatch(Arrays.asList(acctGroup, orgGroup, projectGroup));

    final Set<String> actual = delegateService.getAllDelegateSelectorsUpTheHierarchy(accountId, orgId, projectId);
    assertThat(actual).containsExactlyInAnyOrder(
        "acctgrp", "orggrp", "projectgrp", "custom-acct", "custom-org", "custom-proj");
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectorsInSortedOrder() {
    String accountId = generateUuid();

    final DelegateGroup acctGrpPartiallyConnected =
        DelegateGroup.builder().name("acctGrpPartiallyConnected").accountId(accountId).ng(true).build();
    final DelegateGroup acctGrpDisconnected =
        DelegateGroup.builder().name("acctGrpDisconnected").accountId(accountId).ng(true).build();
    final DelegateGroup acctGrpConnected =
        DelegateGroup.builder().name("acctGrpConnected").accountId(accountId).ng(true).build();

    persistence.saveBatch(Arrays.asList(acctGrpPartiallyConnected, acctGrpDisconnected, acctGrpConnected));

    // Add delegates to acctGrpPartiallyConnected group.
    Delegate delegate1 = createDelegateBuilder().build();
    delegate1.setDelegateGroupId(acctGrpPartiallyConnected.getUuid());
    delegate1.setLastHeartBeat(System.currentTimeMillis());
    delegate1.setAccountId(accountId);
    delegate1.setNg(true);
    persistence.save(delegate1);

    Delegate delegate2 = createDelegateBuilder().build();
    delegate2.setDelegateGroupId(acctGrpPartiallyConnected.getUuid());
    delegate2.setLastHeartBeat(0);
    delegate2.setAccountId(accountId);
    delegate2.setNg(true);
    persistence.save(delegate2);

    // Add delegates to acctGrpDisconnected group.
    Delegate delegate3 = createDelegateBuilder().build();
    delegate3.setDelegateGroupId(acctGrpDisconnected.getUuid());
    delegate3.setLastHeartBeat(0);
    delegate3.setAccountId(accountId);
    delegate3.setNg(true);
    persistence.save(delegate3);

    // Add delegates to acctGrpConnected group
    Delegate delegate4 = createDelegateBuilder().build();
    delegate4.setDelegateGroupId(acctGrpConnected.getUuid());
    delegate4.setLastHeartBeat(System.currentTimeMillis());
    delegate4.setAccountId(accountId);
    delegate4.setNg(true);
    persistence.save(delegate4);

    final List<DelegateSelector> delegateSelectors =
        delegateService.getAllDelegateSelectorsUpTheHierarchyV2(accountId, null, null);

    DelegateSelector expectedSelector1 = new DelegateSelector("acctgrpconnected", true);
    DelegateSelector expectedSelector2 = new DelegateSelector("acctgrppartiallyconnected", true);
    DelegateSelector expectedSelector3 = new DelegateSelector("acctgrpdisconnected", false);
    assertThat(delegateSelectors).containsExactly(expectedSelector1, expectedSelector2, expectedSelector3);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectors() {
    DelegateProfile delegateProfile =
        DelegateProfile.builder().uuid(generateUuid()).accountId(ACCOUNT_ID).name(generateUuid()).build();
    persistence.save(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .delegateName("testDelegateName1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .tags(ImmutableList.of("abc"))
                            .build();
    persistence.save(delegate);
    delegate = Delegate.builder()
                   .accountId(ACCOUNT_ID)
                   .ip("127.0.0.1")
                   .hostName("d.e.f")
                   .delegateName("testDelegateName2")
                   .version(VERSION)
                   .status(DelegateInstanceStatus.ENABLED)
                   .lastHeartBeat(System.currentTimeMillis())
                   .delegateProfileId(delegateProfile.getUuid())
                   .tags(ImmutableList.of("def"))
                   .build();

    persistence.save(delegate);

    Set<String> tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags).containsExactlyInAnyOrder("abc", "def", "testdelegatename1", "testdelegatename2", "a.b.c", "d.e.f",
        delegateProfile.getName().toLowerCase());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetCGDelegate_whenNgFieldNotSetOrFalse() {
    DelegateProfile delegateProfile =
        DelegateProfile.builder().uuid(generateUuid()).accountId(ACCOUNT_ID).name(generateUuid()).build();
    persistence.save(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .delegateName("testDelegateName1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .ng(false)
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags).isNotEmpty();
    persistence.update(persistence.createQuery(Delegate.class, excludeAuthority),
        persistence.createUpdateOperations(Delegate.class).unset(DelegateKeys.ng));
    tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags).isNotEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetCGDelegate_whenNgFieldTrue() {
    DelegateProfile delegateProfile =
        DelegateProfile.builder().uuid(generateUuid()).accountId(ACCOUNT_ID).name(generateUuid()).build();
    persistence.save(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .delegateName("testDelegateName1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .ng(true)
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags).isEmpty();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectorsEmptySelectors() {
    DelegateProfile delegateProfile =
        DelegateProfile.builder().uuid(generateUuid()).accountId(ACCOUNT_ID).name(PRIMARY_PROFILE_NAME).build();
    persistence.save(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .delegateName("testDelegateName1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags.size()).isEqualTo(3);
    assertThat(tags).containsExactlyInAnyOrder("testdelegatename1", "a.b.c", PRIMARY_PROFILE_NAME);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateSelectors() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .tags(ImmutableList.of("abc", "qwe", "xde"))
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateService.retrieveDelegateSelectors(delegate, false);
    assertThat(tags.size()).isEqualTo(3);
    assertThat(tags).containsExactlyInAnyOrder("abc", "qwe", "xde");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateSelectors_Empty() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateService.retrieveDelegateSelectors(delegate, false);
    assertThat(tags.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetAllDelegateSelectors_Empty() {
    Set<String> tags = delegateService.getAllDelegateSelectors(ACCOUNT_ID);
    assertThat(tags.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetAvailableVersions() {
    List<String> versions = delegateService.getAvailableVersions(ACCOUNT_ID);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions).containsExactly("0.0.0");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testDelegateDisconnected() {
    final String version = versionInfoManager.getVersionInfo().getVersion();
    when(accountService.getAccountPrimaryDelegateVersion(ACCOUNT_ID)).thenReturn(version);
    String delegateId = persistence.save(createDelegateBuilder().build());
    assertThat(delegateService.checkDelegateConnected(ACCOUNT_ID, delegateId)).isTrue();
    Delegate updatedDelegate = persistence.get(Delegate.class, delegateId);
    updatedDelegate.setLastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis());
    String UpdatedDelegateId = persistence.save(updatedDelegate);
    delegateDao.delegateDisconnected(ACCOUNT_ID, UpdatedDelegateId);
    assertThat(delegateService.checkDelegateConnected(ACCOUNT_ID, UpdatedDelegateId)).isFalse();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldGetCountOfDelegatesForAccounts() {
    String accountId1 = generateUuid();
    String accountId2 = generateUuid();
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId1);
    delegate.setUuid(generateUuid());

    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).uuid(generateUuid()).build();
    delegate.setDelegateProfileId(delegateProfile.getUuid());

    when(delegateProfileService.get(delegate.getAccountId(), delegateProfile.getUuid())).thenReturn(delegateProfile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId1)).thenReturn(Integer.MAX_VALUE);

    delegate = delegateService.add(delegate);
    assertThat(persistence.get(Delegate.class, delegate.getUuid())).isEqualTo(delegate);

    List<String> accountIds = Arrays.asList(accountId1, accountId2);
    List<Integer> countOfDelegatesForAccounts = delegateService.getCountOfDelegatesForAccounts(accountIds);
    assertThat(countOfDelegatesForAccounts).hasSize(2);

    assertThat(countOfDelegatesForAccounts.get(0)).isEqualTo(1);
    assertThat(countOfDelegatesForAccounts.get(1)).isEqualTo(0);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldUpdateHeartbeatForDelegateWithPollingEnabled() {
    Delegate delegate = Delegate.builder()
                            .uuid(generateUuid())
                            .hostName(HOST_NAME)
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .tags(ImmutableList.of("abc", "qwe"))
                            .build();

    persistence.save(delegate);
    when(licenseService.isAccountDeleted(anyString())).thenReturn(true);

    Delegate result = delegateService.updateHeartbeatForDelegateWithPollingEnabled(delegate);

    assertThat(result).extracting(Delegate::getStatus).isEqualTo(DelegateInstanceStatus.DELETED);
    assertThat(result).extracting(Delegate::getUuid).isEqualTo(delegate.getUuid());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchAvailableSizes() {
    List<DelegateSizeDetails> delegateSizeDetails = delegateService.fetchAvailableSizes();
    assertThat(delegateSizeDetails).isNotNull();
    assertThat(delegateSizeDetails).hasSize(4);
    assertThat(delegateSizeDetails)
        .containsExactlyInAnyOrder(DelegateSizeDetails.builder()
                                       .size(DelegateSize.LAPTOP)
                                       .label("Laptop")
                                       .replicas(1)
                                       .ram(2048)
                                       .cpu(0.5)
                                       .build(),
            DelegateSizeDetails.builder().size(DelegateSize.SMALL).label("Small").replicas(2).ram(4096).cpu(1).build(),
            DelegateSizeDetails.builder()
                .size(DelegateSize.MEDIUM)
                .label("Medium")
                .replicas(4)
                .ram(8192)
                .cpu(2)
                .build(),
            DelegateSizeDetails.builder()
                .size(DelegateSize.LARGE)
                .label("Large")
                .replicas(8)
                .ram(16384)
                .cpu(4)
                .build());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldDeleteDelegateGroupByIdentifier() {
    String accountId = generateUuid();

    String owner_identifier = "orgId"
        + "/"
        + "projectId";
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .accountId(accountId)
                                      .name("groupname")
                                      .identifier("identifier")
                                      .owner(DelegateEntityOwner.builder().identifier(owner_identifier).build())
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                      .build();
    persistence.save(delegateGroup);

    Delegate d1 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .delegateGroupId(delegateGroup.getUuid())
                      .owner(DelegateEntityOwner.builder().identifier(owner_identifier).build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d1);
    Delegate d2 = createDelegateBuilder()
                      .accountId(accountId)
                      .delegateName("groupname")
                      .delegateGroupId(delegateGroup.getUuid())
                      .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
                      .owner(DelegateEntityOwner.builder().identifier(owner_identifier).build())
                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                      .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroupV2(accountId, "orgId", "projectId", "identifier");

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getIdentifier())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
    verify(eventProducer).send(any());

    // Account level delegates
    delegateGroup = DelegateGroup.builder()
                        .accountId(accountId)
                        .name("groupname-acct")
                        .identifier("identifier")
                        .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                        .build();
    persistence.save(delegateGroup);

    d1 = createDelegateBuilder()
             .accountId(accountId)
             .delegateName("groupname-acct")
             .delegateGroupId(delegateGroup.getUuid())
             .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
             .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
             .build();
    persistence.save(d1);
    d2 = createDelegateBuilder()
             .accountId(accountId)
             .delegateName("groupname-acct")
             .lastHeartBeat(currentTimeMillis() - TEST_EXPIRY_TIME.toMillis())
             .delegateGroupId(delegateGroup.getUuid())
             .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
             .build();
    persistence.save(d2);

    delegateService.deleteDelegateGroupV2(accountId, "", "", "identifier");

    assertThat(persistence.get(DelegateGroup.class, delegateGroup.getIdentifier())).isNull();
    assertThat(persistence.get(Delegate.class, d1.getUuid())).isNull();
    assertThat(persistence.get(Delegate.class, d2.getUuid())).isNull();
    verify(eventProducer, times(2)).send(any());
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotRegisterDelegateIfGroupIdIsPresentButGroupIsAbsent() {
    Delegate delegate = createDelegateBuilder().delegateGroupId(generateUuid()).build();
    DelegateProfile primaryDelegateProfile =
        createDelegateProfileBuilder().accountId(delegate.getAccountId()).primary(true).build();

    delegate.setDelegateProfileId(primaryDelegateProfile.getUuid());
    when(delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId())).thenReturn(primaryDelegateProfile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(delegate);
    assertThat(registerResponse.getAction()).isEqualTo(Action.SELF_DESTRUCT);
    assertThat(registerResponse.getDelegateId()).isNull();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotRegisterParamsIfGroupIdIsPresentButGroupIsAbsent() {
    String accountId = generateUuid();

    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .delegateSize(DelegateSize.LAPTOP.name())
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateGroupName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(generateUuid())
                                .ng(true)
                                .version(VERSION)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, null)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    assertThat(registerResponse.getAction()).isEqualTo(Action.SELF_DESTRUCT);
    assertThat(registerResponse.getDelegateId()).isNull();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRegisterParamsWithNoDelegateGroup() {
    DelegateParams params = DelegateParams.builder()
                                .accountId(ACCOUNT_ID)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_NAME)
                                .ng(true)
                                .version(VERSION)
                                .proxy(true)
                                .pollingModeEnabled(true)
                                .build();

    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, true);
    Delegate delegateFromDb = delegateCache.get(ACCOUNT_ID, registerResponse.getDelegateId(), true);
    DelegateGroup delegateGroupFromDb = delegateCache.getDelegateGroup(ACCOUNT_ID, delegateFromDb.getDelegateGroupId());

    assertThat(delegateFromDb.getAccountId()).isEqualTo(params.getAccountId());
    assertThat(delegateFromDb.isNg()).isTrue();
    assertThat(delegateFromDb.getHostName()).isEqualTo(params.getHostName());
    assertThat(delegateFromDb.getDescription()).isEqualTo(params.getDescription());
    assertThat(delegateFromDb.getDelegateType()).isEqualTo(params.getDelegateType());
    assertThat(delegateFromDb.getIp()).isEqualTo(params.getIp());
    assertThat(delegateFromDb.getDelegateGroupName()).isEqualTo(delegateGroupFromDb.getName());
    assertThat(delegateFromDb.getVersion()).isEqualTo(params.getVersion());
    assertThat(delegateFromDb.isProxy()).isEqualTo(params.isProxy());
    assertThat(delegateFromDb.isPolllingModeEnabled()).isEqualTo(params.isPollingModeEnabled());
    assertThat(delegateGroupFromDb.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateFromDb.isMtls()).isTrue();
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testDownloadNgDockerDelegateShouldReturnComposeFile() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey(TOKEN_VALUE).withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, false)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
                                            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
                                            .name("harness-delegate")
                                            .identifier("_delegateGroupId1")
                                            .description("desc")
                                            .delegateType(DelegateType.DOCKER)
                                            .tokenName(TOKEN_NAME)
                                            .build();

    File file =
        delegateService.downloadNgDocker("https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, setupDetails);

    String fileContent = new String(FileUtils.readFileToByteArray(file));
    String expected =
        CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream("/expectedDockerCompose.yaml")))
            .replaceAll("8888", "" + port);

    assertThat(fileContent).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testDownloadNgImmutableDockerDelegateShouldReturnComposeFile() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey(TOKEN_VALUE).withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getDelegateImageTag(ACCOUNT_ID, true)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, true)).thenReturn(UPGRADER_IMAGE_TAG);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    when(accountService.isImmutableDelegateEnabled(ACCOUNT_ID)).thenReturn(Boolean.TRUE);

    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
                                            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
                                            .name("harness-delegate")
                                            .identifier("_delegateGroupId1")
                                            .description("desc")
                                            .delegateType(DelegateType.DOCKER)
                                            .tokenName(TOKEN_NAME)
                                            .build();

    File file =
        delegateService.downloadNgDocker("https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, setupDetails);

    String fileContent = new String(FileUtils.readFileToByteArray(file));
    String expected =
        CharStreams
            .toString(new InputStreamReader(getClass().getResourceAsStream("/expectedImmutableDockerCompose.yaml")))
            .replaceAll("8888", "" + port);

    assertThat(fileContent).isEqualTo(expected);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testDownloadNgDockerDelegateShouldThrowException_missingDetails() {
    String accountId = generateUuid();
    assertThatThrownBy(
        () -> delegateService.downloadNgDocker("https://localhost:9090", "https://localhost:7070", accountId, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Setup Details must be provided.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testDownloadNgDockerDelegateShouldThrowException_missingName() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder().delegateType(DelegateType.DOCKER).build();
    assertThatThrownBy(()
                           -> delegateService.downloadNgDocker(
                               "https://localhost:9090", "https://localhost:7070", accountId, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Name must be provided.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testDownloadNgDockerDelegateShouldThrowException_nameDuplicate() {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey(TOKEN_VALUE).withUuid(ACCOUNT_ID).build());
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(true).delegateName(UNIQUE_DELEGATE_NAME).build());
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder().name(UNIQUE_DELEGATE_NAME).delegateType(DelegateType.DOCKER).build();
    assertThatThrownBy(()
                           -> delegateService.downloadNgDocker(
                               "https://localhost:9090", "https://localhost:7070", ACCOUNT_ID, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testDownloadKubernetesYamlShouldThrowException() {
    String accountId = generateUuid();
    K8sConfigDetails k8sConfigDetails = K8sConfigDetails.builder().k8sPermissionType(CLUSTER_ADMIN).build();
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .name("test")
                                            .size(DelegateSize.LAPTOP)
                                            .delegateType(DOCKER)
                                            .k8sConfigDetails(k8sConfigDetails)
                                            .build();
    assertThatThrownBy(()
                           -> delegateService.generateKubernetesYaml(accountId, setupDetails, "https://localhost:9090",
                               "https://localhost:7070", MediaType.MULTIPART_FORM_DATA_TYPE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate type must be KUBERNETES OR HELM_DELEGATE.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testValidateDockerDelegateDetails() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder().name("test").delegateType(DelegateType.DOCKER).tokenName(TOKEN_NAME).build();

    assertDoesNotThrow(() -> delegateService.validateDockerDelegateSetupDetails(accountId, setupDetails, DOCKER));
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testValidateDockerDelegateDetailsShouldThrowException_missingDetails() {
    String accountId = generateUuid();
    assertThatThrownBy(() -> delegateService.validateDockerDelegateSetupDetails(accountId, null, DOCKER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Setup Details must be provided.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testValidateDockerDelegateDetailsShouldThrowException_missingName() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder().delegateType(DelegateType.DOCKER).build();
    assertThatThrownBy(() -> delegateService.validateDockerDelegateSetupDetails(accountId, setupDetails, DOCKER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate Name must be provided.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testValidateDockerDelegateDetailsShouldThrowException_nameDuplicate() {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey(TOKEN_VALUE).withUuid(ACCOUNT_ID).build());
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(true).delegateName(UNIQUE_DELEGATE_NAME).build());
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder().name(UNIQUE_DELEGATE_NAME).delegateType(DelegateType.DOCKER).build();
    assertThatThrownBy(() -> delegateService.validateDockerDelegateSetupDetails(ACCOUNT_ID, setupDetails, DOCKER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateDelegateGroup() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey(TOKEN_VALUE).withUuid(ACCOUNT_ID).build());

    DelegateSetupDetails setupDetails = DelegateSetupDetails.builder()
                                            .orgIdentifier("9S5HMP0xROugl3_QgO62rQO")
                                            .projectIdentifier("9S5HMP0xROugl3_QgO62rQP")
                                            .name("harness-delegate")
                                            .identifier("_delegateGroupId1")
                                            .description("desc")
                                            .delegateType(DelegateType.DOCKER)
                                            .tokenName(TOKEN_NAME)
                                            .build();

    String id = delegateService.createDelegateGroup(ACCOUNT_ID, setupDetails);

    assertThat(id).isNotNull();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCgShellDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(()
                           -> delegateService.downloadScripts("https://localhost:9090", "https://localhost:7070",
                               ACCOUNT_ID, UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCgDockerDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(()
                           -> delegateService.downloadDocker("https://localhost:9090", "https://localhost:7070",
                               ACCOUNT_ID, UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCgKubernetesDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(()
                           -> delegateService.downloadKubernetes("https://localhost:9090", "https://localhost:7070",
                               ACCOUNT_ID, UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME, runAsRoot))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCeKubernetesDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(
        ()
            -> delegateService.downloadCeKubernetesYaml("https://localhost:9090", "https://localhost:7070", ACCOUNT_ID,
                UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCgECSDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(()
                           -> delegateService.downloadECSDelegate("https://localhost:9090", "https://localhost:7070",
                               ACCOUNT_ID, false, HOST_NAME, UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDownloadCgHelmDelegateShouldThrowException_nameDuplicate() {
    persistence.save(Delegate.builder().accountId(ACCOUNT_ID).ng(false).delegateName(UNIQUE_DELEGATE_NAME).build());
    assertThatThrownBy(
        ()
            -> delegateService.downloadDelegateValuesYamlFile("https://localhost:9090", "https://localhost:7070",
                ACCOUNT_ID, UNIQUE_DELEGATE_NAME, DELEGATE_PROFILE_ID, TOKEN_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(UNIQUE_DELEGATE_NAME_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldGenerateNgHelmValuesYamlFile() throws IOException {
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withAccountKey("ACCOUNT_KEY").withUuid(ACCOUNT_ID).build());
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(DELEGATE_IMAGE_TAG);
    when(delegateVersionService.getUpgraderImageTag(ACCOUNT_ID, false)).thenReturn(UPGRADER_IMAGE_TAG);
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder()
            .delegateConfigurationId("delConfigId")
            .name("harness-delegate")
            .identifier("_delegateGroupId1")
            .size(DelegateSize.LAPTOP)
            .delegateType(HELM_DELEGATE)
            .k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(CLUSTER_ADMIN).build())
            .tokenName(TOKEN_NAME)
            .build();
    when(delegateProfileService.get(ACCOUNT_ID, "delConfigId")).thenReturn(DelegateProfile.builder().build());

    File yamlFile = delegateService.generateNgHelmValuesYaml(
        ACCOUNT_ID, setupDetails, "https://localhost:9090", "https://localhost:7070");

    byte[] buffer = new byte[(int) yamlFile.length()];
    FileInputStream fileInputStream = new FileInputStream(yamlFile);
    IOUtils.read(fileInputStream, buffer);
    assertThat(new String(buffer))
        .isEqualTo(
            CharStreams
                .toString(new InputStreamReader(getClass().getResourceAsStream("/expectedNgHelmDelegateValues.yaml")))
                .replaceAll("8888", "" + port));
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRegisterDelegateParamsWithOwnerFromGlobalContext() {
    final String accountId = generateUuid();
    final String delegateGroupId = generateUuid();
    final String orgId = "orgId";
    final String projectId = "projectId";
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    DelegateParams params = DelegateParams.builder()
                                .accountId(accountId)
                                .projectIdentifier(projectId)
                                .hostName(HOST_NAME)
                                .description(DESCRIPTION)
                                .delegateType(KUBERNETES_DELEGATE)
                                .ip("127.0.0.1")
                                .delegateName(DELEGATE_GROUP_NAME)
                                .delegateGroupId(delegateGroupId)
                                .version(VERSION)
                                .proxy(true)
                                .ng(true)
                                .pollingModeEnabled(true)
                                .build();

    DelegateTokenDetails delegateTokenDetails = DelegateTokenDetails.builder()
                                                    .accountId(accountId)
                                                    .name(TOKEN_NAME)
                                                    .status(DelegateTokenStatus.ACTIVE)
                                                    .ownerIdentifier(owner.getIdentifier())
                                                    .build();

    when(delegateNgTokenService.getDelegateToken(accountId, TOKEN_NAME)).thenReturn(delegateTokenDetails);

    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(
        DelegateTokenGlobalContextData.builder().tokenName(TOKEN_NAME).build());

    DelegateProfile profile = createDelegateProfileBuilder().accountId(accountId).primary(true).build();
    DelegateGroup group = DelegateGroup.builder()
                              .accountId(accountId)
                              .uuid(delegateGroupId)
                              .name(DELEGATE_GROUP_NAME)
                              .owner(owner)
                              .build();
    persistence.save(group);
    when(delegateProfileService.fetchNgPrimaryProfile(accountId, owner)).thenReturn(profile);
    when(delegatesFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);

    DelegateRegisterResponse registerResponse = delegateService.register(params, false);
    Delegate delegateFromDb = delegateCache.get(accountId, registerResponse.getDelegateId(), true);

    assertThat(delegateFromDb.getOwner()).isEqualTo(owner);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotValidateKubernetesYamlWithoutTokenName() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder()
            .name("harness-delegate")
            .identifier("_delegateGroupId1")
            .size(DelegateSize.LARGE)
            .description("desc")
            .delegateType(DelegateType.KUBERNETES)
            .k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(K8sPermissionType.CLUSTER_VIEWER).build())
            .build();

    assertThatThrownBy(() -> delegateService.validateKubernetesSetupDetails(accountId, setupDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(DELEGATE_TOKEN_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotValidateDockerDelegateDetailsWithoutTokenName() {
    String accountId = generateUuid();
    DelegateSetupDetails setupDetails =
        DelegateSetupDetails.builder().name(DELEGATE_GROUP_NAME).delegateType(DelegateType.DOCKER).build();
    assertThatThrownBy(() -> delegateService.validateDockerDelegateSetupDetails(accountId, setupDetails, DOCKER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(DELEGATE_TOKEN_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldListDelegateTags() {
    Delegate delegate = createDelegateBuilder()
                            .uuid("delegateId1")
                            .accountId(ACCOUNT_ID)
                            .ng(false)
                            .tags(Lists.newArrayList("tag123", "tag456"))
                            .delegateType(KUBERNETES)
                            .delegateName("delegate1")
                            .description("description1")
                            .hostName("kube-0")
                            .delegateProfileId("delegateProfileId1")
                            .build();

    persistence.save(delegate);

    DelegateDTO delegateFromDB = delegateService.listDelegateTags(ACCOUNT_ID, "delegateId1");

    assertThat(delegateFromDB).isNotNull();
    assertThat(delegateFromDB.getDelegateId()).isEqualTo("delegateId1");
    assertThat(delegateFromDB.getTags()).containsExactly("tag123", "tag456", "kube-0", "delegate1");
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldAddDelegateTags() {
    Delegate delegate = createDelegateBuilder()
                            .uuid("delegateId1")
                            .accountId(ACCOUNT_ID)
                            .ng(false)
                            .tags(Lists.newArrayList("tag123", "tag456"))
                            .delegateType(KUBERNETES)
                            .delegateName("delegate1")
                            .description("description1")
                            .hostName("kube-0")
                            .delegateProfileId("delegateProfileId1")
                            .build();

    persistence.save(delegate);

    DelegateTags delegateTags = new DelegateTags(Arrays.asList("  tag1  ", "  tag2", "tag123"));

    DelegateDTO updatedDelegate = delegateService.addDelegateTags(ACCOUNT_ID, "delegateId1", delegateTags);

    assertThat(updatedDelegate).isNotNull();
    assertThat(updatedDelegate.getDelegateId()).isEqualTo("delegateId1");
    assertThat(updatedDelegate.getTags()).containsExactlyInAnyOrder("tag123", "tag456", "tag1", "tag2");
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateTags() {
    Delegate delegate = createDelegateBuilder()
                            .uuid("delegateId1")
                            .accountId(ACCOUNT_ID)
                            .ng(false)
                            .tags(Lists.newArrayList("tag123", "tag456"))
                            .delegateType(KUBERNETES)
                            .delegateName("delegate1")
                            .description("description1")
                            .hostName("kube-0")
                            .delegateProfileId("delegateProfileId1")
                            .build();

    persistence.save(delegate);

    DelegateTags delegateTags = new DelegateTags(Arrays.asList("  tag1  ", "  tag2", "tag123"));

    DelegateDTO updatedDelegate = delegateService.updateDelegateTags(ACCOUNT_ID, "delegateId1", delegateTags);

    assertThat(updatedDelegate).isNotNull();
    assertThat(updatedDelegate.getDelegateId()).isEqualTo("delegateId1");
    assertThat(updatedDelegate.getTags()).containsExactlyInAnyOrder("tag123", "tag1", "tag2");
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldDeleteDelegateTags() {
    Delegate delegate = createDelegateBuilder()
                            .uuid("delegateId1")
                            .accountId(ACCOUNT_ID)
                            .ng(false)
                            .tags(Lists.newArrayList("tag123", "tag456"))
                            .delegateType(KUBERNETES)
                            .delegateName("delegate1")
                            .description("description1")
                            .hostName("kube-0")
                            .delegateProfileId("delegateProfileId1")
                            .build();

    persistence.save(delegate);

    DelegateDTO updatedDelegate = delegateService.deleteDelegateTags(ACCOUNT_ID, "delegateId1");

    assertThat(updatedDelegate).isNotNull();
    assertThat(updatedDelegate.getDelegateId()).isEqualTo("delegateId1");
    assertThat(updatedDelegate.getTags()).isEmpty();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldApproveDelegatesUsingProfile() {
    Delegate delegate1 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_1)
                             .delegateProfileId(DELEGATE_PROFILE_ID)
                             .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                             .build();
    Delegate delegate2 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_2)
                             .delegateProfileId(DELEGATE_PROFILE_ID)
                             .status(DelegateInstanceStatus.ENABLED)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    DelegateApprovalResponse delegateApprovalResponse =
        delegateService.approveDelegatesUsingProfile(ACCOUNT_ID, DELEGATE_PROFILE_ID, DelegateApproval.ACTIVATE);

    Delegate delegateFromDB1 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_1, true);
    Delegate delegateFromDB2 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_2, true);

    assertThat(delegateApprovalResponse).isNotNull();
    assertThat(delegateApprovalResponse.getListOfUpdatedDelegateIds()).isEqualTo(singletonList(DELEGATE_ID_1));

    assertThat(delegateFromDB1).isNotNull();
    assertThat(delegateFromDB1.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    assertThat(delegateFromDB2).isNotNull();
    assertThat(delegateFromDB2.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(ACCOUNT_ID, delegate1, delegateFromDB1, Type.DELEGATE_APPROVAL);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRejectDelegatesUsingProfile() {
    Delegate delegate1 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_1)
                             .delegateProfileId(DELEGATE_PROFILE_ID)
                             .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                             .build();
    Delegate delegate2 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_2)
                             .delegateProfileId(DELEGATE_PROFILE_ID)
                             .status(DelegateInstanceStatus.ENABLED)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    DelegateApprovalResponse delegateApprovalResponse =
        delegateService.approveDelegatesUsingProfile(ACCOUNT_ID, DELEGATE_PROFILE_ID, DelegateApproval.REJECT);

    Delegate delegateFromDB1 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_1, true);
    Delegate delegateFromDB2 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_2, true);

    assertThat(delegateApprovalResponse).isNotNull();
    assertThat(delegateApprovalResponse.getListOfUpdatedDelegateIds()).isEqualTo(singletonList(DELEGATE_ID_1));

    assertThat(delegateFromDB1).isNotNull();
    assertThat(delegateFromDB1.getStatus()).isEqualTo(DelegateInstanceStatus.DELETED);
    assertThat(delegateFromDB2).isNotNull();
    assertThat(delegateFromDB2.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(ACCOUNT_ID, delegate1, delegateFromDB1, Type.DELEGATE_REJECTION);
    verify(broadcaster).broadcast(SELF_DESTRUCT + DELEGATE_ID_1);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldApproveDelegatesUsingToken() {
    Delegate delegate1 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_1)
                             .delegateTokenName(TOKEN_NAME)
                             .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                             .build();
    Delegate delegate2 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_2)
                             .delegateTokenName(TOKEN_NAME)
                             .status(DelegateInstanceStatus.ENABLED)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    DelegateApprovalResponse delegateApprovalResponse =
        delegateService.approveDelegatesUsingToken(ACCOUNT_ID, TOKEN_NAME, DelegateApproval.ACTIVATE);

    Delegate delegateFromDB1 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_1, true);
    Delegate delegateFromDB2 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_2, true);

    assertThat(delegateApprovalResponse).isNotNull();
    assertThat(delegateApprovalResponse.getListOfUpdatedDelegateIds()).isEqualTo(singletonList(DELEGATE_ID_1));

    assertThat(delegateFromDB1).isNotNull();
    assertThat(delegateFromDB1.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    assertThat(delegateFromDB2).isNotNull();
    assertThat(delegateFromDB2.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(ACCOUNT_ID, delegate1, delegateFromDB1, Type.DELEGATE_APPROVAL);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRejectDelegatesUsingToken() {
    Delegate delegate1 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_1)
                             .delegateTokenName(TOKEN_NAME)
                             .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                             .build();
    Delegate delegate2 = createDelegateBuilder()
                             .uuid(DELEGATE_ID_2)
                             .delegateTokenName(TOKEN_NAME)
                             .status(DelegateInstanceStatus.ENABLED)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    DelegateApprovalResponse delegateApprovalResponse =
        delegateService.approveDelegatesUsingToken(ACCOUNT_ID, TOKEN_NAME, DelegateApproval.REJECT);

    Delegate delegateFromDB1 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_1, true);
    Delegate delegateFromDB2 = delegateCache.get(ACCOUNT_ID, DELEGATE_ID_2, true);

    assertThat(delegateApprovalResponse).isNotNull();
    assertThat(delegateApprovalResponse.getListOfUpdatedDelegateIds()).isEqualTo(singletonList(DELEGATE_ID_1));

    assertThat(delegateFromDB1).isNotNull();
    assertThat(delegateFromDB1.getStatus()).isEqualTo(DelegateInstanceStatus.DELETED);

    assertThat(delegateFromDB2).isNotNull();
    assertThat(delegateFromDB2.getStatus()).isEqualTo(DelegateInstanceStatus.ENABLED);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(ACCOUNT_ID, delegate1, delegateFromDB1, Type.DELEGATE_REJECTION);
    verify(broadcaster).broadcast(SELF_DESTRUCT + DELEGATE_ID_1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetDelegatesFromDelegateCache() {
    String accountId = generateUuid();
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .name("grp1")
                                      .accountId(accountId)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .build();
    persistence.save(delegateGroup);

    // 2 delegates with same delegate group
    Delegate delegate1 = createDelegateBuilder().build();
    delegate1.setDelegateGroupId(delegateGroup.getUuid());
    delegate1.setAccountId(accountId);
    delegate1.setNg(true);
    persistence.save(delegate1);

    Delegate delegate2 = createDelegateBuilder().build();
    delegate2.setDelegateGroupId(delegateGroup.getUuid());
    delegate2.setAccountId(accountId);
    delegate2.setNg(true);
    persistence.save(delegate2);

    assertThat(delegateCache.getDelegatesForGroup(accountId, delegateGroup.getUuid()).size()).isEqualTo(2);
  }

  private CapabilitySubjectPermission buildCapabilitySubjectPermission(
      String accountId, String delegateId, String capabilityId, PermissionResult permissionResult) {
    return CapabilitySubjectPermission.builder()
        .uuid(generateUuid())
        .accountId(accountId)
        .delegateId(delegateId)
        .capabilityId(capabilityId)
        .maxValidUntil(120000)
        .revalidateAfter(180000)
        .validUntil(Date.from(Instant.now().plus(Duration.ofDays(30))))
        .permissionResult(permissionResult)
        .build();
  }

  private DelegateTask saveDelegateTask(
      boolean async, Set<String> validatingTaskIds, DelegateTask.Status status, boolean setDelegateId) {
    final DelegateTaskBuilder delegateTaskBuilder =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .version(VERSION)
            .data(TaskData.builder()
                      .async(async)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validatingDelegateIds(validatingTaskIds)
            .validationCompleteDelegateIds(ImmutableSet.of(DELEGATE_ID));

    if (setDelegateId) {
      delegateTaskBuilder.delegateId(DELEGATE_ID);
    }

    final DelegateTask delegateTask = delegateTaskBuilder.build();
    delegateTaskServiceClassic.processDelegateTaskV2(delegateTask, status);
    return delegateTask;
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  private static JreConfig getOpenjdkJreConfig() {
    return JreConfig.builder()
        .version("1.8.0_242")
        .jreDirectory("jdk8u242-b08-jre")
        .jreMacDirectory("jdk8u242-b08-jre")
        .jreTarPath("jre/openjdk-8u242/jre_x64_${OS}_8u242b08.tar.gz")
        .alpnJarPath("tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar")
        .build();
  }

  public static JreConfig getOpenjdk11JreConfig() {
    return JreConfig.builder()
        .version("11.0.19")
        .jreDirectory("jdk-11.0.19+7-jre")
        .jreMacDirectory("jdk-11.0.19+7-jre")
        .jreTarPath("jre/openjdk-11.0.19_7/OpenJDK11U-jre_x64_${OS}_hotspot_11.0.19_7.tar.gz")
        .build();
  }

  private void validateDelegateInnerProperties(String delegateId, DelegateStatus.DelegateInner delegateFromStatus) {
    assertThat(delegateFromStatus.getHostName()).isEqualTo("localhost");
    assertThat(delegateFromStatus.getIp()).isEqualTo("127.0.0.1");
    assertThat(delegateFromStatus.getDelegateName()).isEqualTo("testDelegateName");
    assertThat(delegateFromStatus.getDelegateType()).isEqualTo("dockerType");
    assertThat(delegateFromStatus).hasFieldOrPropertyWithValue("uuid", delegateId);
  }

  private static void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception ex) {
      Assert.fail();
    }
  }
}
