/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.infra;

import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GUNA;
import static io.harness.rule.OwnerRule.RUSHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.environment.SystemEnvironment;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccessTokenBean;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.utils.GcsUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;

@RunWith(MockitoJUnitRunner.class)
public class InfraDownloadServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private GcsUtils gcsUtils;
  @Mock private SystemEnvironment sysenv;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private FeatureFlagService featureFlagService;
  private MockedStatic<GoogleCredential> googleCredentialMockedStatic;
  @InjectMocks @Inject InfraDownloadServiceImpl infraDownloadService;

  @Mock private GoogleCredential credential;
  @Mock private PortalConfig portalConfig;

  private String prodTestKey = "{\n"
      + "  \"type\": \"service_account\",\n"
      + "  \"project_id\": \"prod-testaccount-fake-key-1234\",\n"
      + "  \"private_key_id\": \"4321\",\n"
      + "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\naaaa\\nbbbb\\ncccc\\ndddd\\neeee\\nffff\\ngggg\\nhhhh\\niiii\\njjjj\\nkkkk\\nllll\\nmmmm\\nnnnn\\noooo\\npppp\\nqqqq\\nrrrr\\nssss\\ntttt\\nuuuu\\nvvvv\\nwwww\\nxxxx\\nyyyy\\nzzzz\\n-----END PRIVATE KEY-----\\n\",\n"
      + "  \"client_email\": \"testserviceaccount@prod-testaccount-fake-key-1234.iam.gserviceaccount.com\",\n"
      + "  \"client_id\": \"106843626166628319689\",\n"
      + "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
      + "  \"token_uri\": \"https://accounts.google.com/o/oauth2/token\",\n"
      + "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n"
      + "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/testserviceaccount%40prod-testaccount-fake-key-1234.iam.gserviceaccount.com\"\n"
      + "}\n";

  @Before
  public void setUp() throws Exception {
    googleCredentialMockedStatic = mockStatic(GoogleCredential.class);
    when(portalConfig.getUrl()).thenReturn("testUrl");
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    googleCredentialMockedStatic.when(() -> GoogleCredential.fromStream(any(InputStream.class)))
        .thenAnswer(invocationOnMock -> credential);
    when(credential.createScoped(any())).thenReturn(credential);
  }

  @After
  public void cleanup() {
    googleCredentialMockedStatic.close();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testInfraDownloadFailForEnvWhenNoServiceAccDefined() {
    when(sysenv.get("ENV")).thenReturn("dummy");
    String url = infraDownloadService.getDownloadUrlForDelegate("4333", null);
    assertThat(url).isEqualTo(InfraDownloadServiceImpl.DEFAULT_ERROR_STRING);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testInfraDownloadFailForWatcherEnvWhenNoServiceAccDefined() {
    when(sysenv.get("ENV")).thenReturn("dummy");
    String url = infraDownloadService.getDownloadUrlForWatcher("4333", null);
    assertThat(url).isEqualTo(InfraDownloadServiceImpl.DEFAULT_ERROR_STRING);
  }

  @Test
  @Owner(developers = GUNA)
  @Category(UnitTests.class)
  public void testInfraDownloadForDelegateLocal() {
    String url = infraDownloadService.getDownloadUrlForDelegate("4333", null);
    assertThat(url).isEqualTo(InfraDownloadServiceImpl.LOCAL_DELEGATE);
  }

  @Test
  @Owner(developers = GUNA)
  @Category(UnitTests.class)
  public void testInfraDownloadForWatcherLocal() {
    String url = infraDownloadService.getDownloadUrlForWatcher("4333", null);
    assertThat(url).isEqualTo(InfraDownloadServiceImpl.LOCAL_WATCHER);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testInfraDownloadDelegate() throws Exception {
    String path = "tmp.json";
    File serviceAccFile = new File(path);
    try {
      FileUtils.writeStringToFile(serviceAccFile, prodTestKey, StandardCharsets.UTF_8);
      when(sysenv.get("SERVICE_ACC")).thenReturn(path);
      when(sysenv.get("ENV")).thenReturn("dummy");
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      when(gcsUtils.getSignedUrlForServiceAccount(captor.capture(), any(), anyLong(), any())).thenReturn("abc");
      String url = infraDownloadService.getDownloadUrlForDelegate("123", null);
      assertThat(url).isEqualTo("abc");
      assertThat(captor.getValue()).isEqualTo("/harness-dummy-delegates/builds/123/delegate.jar");
    } finally {
      FileUtils.deleteQuietly(serviceAccFile);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testStackdriverLoggingToken() throws Exception {
    String path = "tmp.json";
    File serviceAccFile = new File(path);
    try {
      FileUtils.writeStringToFile(serviceAccFile, prodTestKey, StandardCharsets.UTF_8);
      when(sysenv.get("LOGGING_SERVICE_ACC")).thenReturn(path);
      PowerMockito.stub(credential.getClass().getMethod("refreshToken")).toReturn(true);
      PowerMockito.stub(credential.getClass().getMethod("getAccessToken")).toReturn("access-token");
      PowerMockito.stub(credential.getClass().getMethod("getExpirationTimeMilliseconds")).toReturn(1234L);
      PowerMockito.stub(credential.getClass().getMethod("getServiceAccountProjectId")).toReturn("project-id");

      AccessTokenBean token = infraDownloadService.getStackdriverLoggingToken();
      assertThat(token).isNotNull();
      assertThat(token.getProjectId()).isEqualTo("project-id");
      assertThat(token.getTokenValue()).isEqualTo("access-token");
      assertThat(token.getExpirationTimeMillis()).isEqualTo(1234L);
    } finally {
      FileUtils.deleteQuietly(serviceAccFile);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testStackdriverLoggingTokenCached() throws Exception {
    String path = "tmp.json";
    File serviceAccFile = new File(path);
    try {
      FileUtils.writeStringToFile(serviceAccFile, prodTestKey, StandardCharsets.UTF_8);
      when(sysenv.get("LOGGING_SERVICE_ACC")).thenReturn(path);
      PowerMockito.stub(credential.getClass().getMethod("refreshToken")).toReturn(true);
      PowerMockito.stub(credential.getClass().getMethod("getAccessToken")).toReturn("access-token");
      PowerMockito.stub(credential.getClass().getMethod("getExpirationTimeMilliseconds"))
          .toReturn(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
      PowerMockito.stub(credential.getClass().getMethod("getServiceAccountProjectId")).toReturn("project-id");

      AccessTokenBean token1 = infraDownloadService.getStackdriverLoggingToken();
      assertThat(token1).isNotNull();
      AccessTokenBean token2 = infraDownloadService.getStackdriverLoggingToken();
      assertThat(token2).isNotNull();
      assertThat(token2 == token1).isTrue();
    } finally {
      FileUtils.deleteQuietly(serviceAccFile);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testStackdriverLoggingTokenBadToken() throws Exception {
    String path = "tmp.json";
    File serviceAccFile = new File(path);
    try {
      FileUtils.writeStringToFile(serviceAccFile, prodTestKey, StandardCharsets.UTF_8);
      when(sysenv.get("LOGGING_SERVICE_ACC")).thenReturn(path);
      PowerMockito.stub(credential.getClass().getMethod("refreshToken")).toReturn(false);
      AccessTokenBean token = infraDownloadService.getStackdriverLoggingToken();
      assertThat(token).isNull();
    } finally {
      FileUtils.deleteQuietly(serviceAccFile);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testStackdriverLoggingTokenNoServiceAcc() {
    AccessTokenBean token = infraDownloadService.getStackdriverLoggingToken();
    assertThat(token).isNull();
  }
}
