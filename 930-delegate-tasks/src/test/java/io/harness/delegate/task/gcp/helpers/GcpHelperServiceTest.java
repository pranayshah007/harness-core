/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.helpers;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gcp.helpers.GcpHttpTransportHelperService;
import io.harness.rule.Owner;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.compute.Compute;
import com.google.api.services.container.Container;
import com.google.api.services.logging.v2.Logging;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.storage.Storage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class GcpHelperServiceTest extends CategoryTest {
  @Mock private GcpHttpTransportHelperService gcpHttpTransportHelperService;
  @Spy @InjectMocks private GcpHelperService gcpHelperService;
  private static final String TEST_PROJECT_ID = "project-a";
  private static final String TEST_ACCESS_TOKEN = String.format("{\"access_token\": \"%s\"}", TEST_PROJECT_ID);
  private static final String TEST_ACCESS_TOKEN_PREFIX = "Bearer ";
  private static final String TEST_TASK_TYPE = "TASK_TYPE";
  private final char[] serviceAccountKeyFileContent =
      String.format("{\"project_id\": \"%s\"}", TEST_PROJECT_ID).toCharArray();
  private GoogleCredential googleCredential;
  private HttpTransport transport;

  @Before
  public void setup() throws GeneralSecurityException, IOException {
    MockitoAnnotations.initMocks(this);
    transport = GoogleNetHttpTransport.newTrustedTransport();

    doReturn(transport).when(gcpHttpTransportHelperService).checkIfUseProxyAndGetHttpTransport();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGkeContainerService() throws IOException {
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    Container container = gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, false);

    assertThat(container.getApplicationName()).isEqualTo("Harness");
    assertThat(container.getRequestFactory().getTransport()).isEqualTo(transport);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGkeContainerServiceWhenInvalidGoogleCredentials() throws IOException {
    doAnswer(invocation -> { throw new GeneralSecurityException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, false))
        .isInstanceOf(WingsException.class);

    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, false))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGcsStorageService() throws IOException {
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    Storage storage = gcpHelperService.getGcsStorageService(serviceAccountKeyFileContent, false);
    assertThat(storage.getApplicationName()).isEqualTo("Harness");
    assertThat(storage.getRequestFactory().getTransport()).isEqualTo(transport);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGcsStorageServiceWhenInvalidCredentials() throws IOException {
    doAnswer(invocation -> { throw new GeneralSecurityException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGcsStorageService(serviceAccountKeyFileContent, false))
        .isInstanceOf(WingsException.class);

    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGcsStorageService(serviceAccountKeyFileContent, false))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGCEService() throws IOException {
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    Compute compute = gcpHelperService.getGCEService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false);
    assertThat(compute.getApplicationName()).isEqualTo(TEST_PROJECT_ID);
    assertThat(compute.getRequestFactory().getTransport()).isEqualTo(transport);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGCEServiceWhenInvalidCredentials() throws IOException {
    doAnswer(invocation -> { throw new GeneralSecurityException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGCEService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);

    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getGCEService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetMonitoringService() throws IOException {
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    Monitoring monitoring = gcpHelperService.getMonitoringService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false);
    assertThat(monitoring.getApplicationName()).isEqualTo(TEST_PROJECT_ID);
    assertThat(monitoring.getRequestFactory().getTransport()).isEqualTo(transport);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetMonitoringServiceWhenInvalidCredentials() throws IOException {
    doAnswer(invocation -> { throw new GeneralSecurityException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(
        () -> gcpHelperService.getMonitoringService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);

    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(
        () -> gcpHelperService.getMonitoringService(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetLoggingResource() throws IOException {
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    Logging logging = gcpHelperService.getLoggingResource(serviceAccountKeyFileContent, TEST_PROJECT_ID, false);
    assertThat(logging.getApplicationName()).isEqualTo(TEST_PROJECT_ID);
    assertThat(logging.getRequestFactory().getTransport()).isEqualTo(transport);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetLoggingResourceWhenInvalidCredentials() throws IOException {
    doAnswer(invocation -> { throw new GeneralSecurityException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getLoggingResource(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);

    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    assertThatThrownBy(() -> gcpHelperService.getLoggingResource(serviceAccountKeyFileContent, TEST_PROJECT_ID, false))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetGoogleCredentialsWithInvalidParams() {
    assertThatThrownBy(() -> gcpHelperService.getGoogleCredential("".toCharArray(), false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> gcpHelperService.getGoogleCredential(serviceAccountKeyFileContent, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> gcpHelperService.getGoogleCredential("InvalidJson".toCharArray(), false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetProjectId() {
    doReturn(TEST_PROJECT_ID).when(gcpHelperService).getClusterProjectId(any(String.class));
    String projectIdUsingDelegate = gcpHelperService.getProjectId(serviceAccountKeyFileContent, true);
    assertThat(projectIdUsingDelegate).isEqualTo(TEST_PROJECT_ID);

    String projectIdWithoutDelegate = gcpHelperService.getProjectId(serviceAccountKeyFileContent, false);
    assertThat(projectIdWithoutDelegate).isEqualTo(TEST_PROJECT_ID);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetClusterProjectId() throws IOException {
    doReturn(TEST_PROJECT_ID).when(gcpHelperService).getIdentifierFromUrl(any(String.class), any(String.class));

    String projectId = gcpHelperService.getClusterProjectId(TEST_TASK_TYPE);
    assertThat(projectId).isEqualTo(TEST_PROJECT_ID);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetClusterProjectIdWithException() throws IOException {
    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getIdentifierFromUrl(any(String.class), any(String.class));
    assertThatThrownBy(() -> gcpHelperService.getClusterProjectId(TEST_TASK_TYPE))
        .isInstanceOf(InvalidRequestException.class);

    doAnswer(invocation -> { throw new NullPointerException(); })
        .when(gcpHelperService)
        .getIdentifierFromUrl(any(String.class), any(String.class));
    assertThatThrownBy(() -> gcpHelperService.getClusterProjectId(TEST_TASK_TYPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetDefaultCredentialsAccessToken() throws IOException {
    doReturn(TEST_ACCESS_TOKEN).when(gcpHelperService).getIdentifierFromUrl(any(String.class), any(String.class));

    String projectId = gcpHelperService.getDefaultCredentialsAccessToken(TEST_TASK_TYPE);
    assertThat(projectId).isEqualTo(TEST_ACCESS_TOKEN_PREFIX + TEST_PROJECT_ID);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetDefaultCredentialsAccessTokenWithException() throws IOException {
    doAnswer(invocation -> { throw new IOException(); })
        .when(gcpHelperService)
        .getIdentifierFromUrl(any(String.class), any(String.class));
    assertThatThrownBy(() -> gcpHelperService.getDefaultCredentialsAccessToken(TEST_TASK_TYPE))
        .isInstanceOf(InvalidRequestException.class);

    doAnswer(invocation -> { throw new NullPointerException(); })
        .when(gcpHelperService)
        .getIdentifierFromUrl(any(String.class), any(String.class));
    assertThatThrownBy(() -> gcpHelperService.getDefaultCredentialsAccessToken(TEST_TASK_TYPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetTokenFromUrl() throws IOException {
    doReturn(TEST_ACCESS_TOKEN)
        .when(gcpHelperService)
        .extractBodyFromClientAndRequest(any(String.class), any(OkHttpClient.class), any(Request.class));
    String token = gcpHelperService.getIdentifierFromUrl(TEST_TASK_TYPE, "http://localhost:8081");
    assertThat(token).isEqualTo(TEST_ACCESS_TOKEN);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetBasicAuthHeaderUsingDelegate() throws IOException {
    doReturn(TEST_ACCESS_TOKEN).when(gcpHelperService).getDefaultCredentialsAccessToken(any(String.class));

    String token = gcpHelperService.getBasicAuthHeader(serviceAccountKeyFileContent, true);
    assertThat(token).isEqualTo(TEST_ACCESS_TOKEN);
  }
}
