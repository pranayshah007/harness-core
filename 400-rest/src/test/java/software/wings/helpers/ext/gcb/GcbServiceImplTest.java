/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcb;

import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static software.wings.helpers.ext.gcb.GcbServiceImpl.GCB_BASE_URL;
import static software.wings.helpers.ext.gcb.GcbServiceImpl.GCS_BASE_URL;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.gcr.exceptions.GcbClientException;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.helpers.ext.gcb.models.BuildOperationDetails;
import software.wings.helpers.ext.gcb.models.BuildStep;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildSource;
import software.wings.helpers.ext.gcb.models.GcbBuildTriggers;
import software.wings.helpers.ext.gcb.models.GcbTrigger;
import software.wings.helpers.ext.gcb.models.OperationMeta;
import software.wings.helpers.ext.gcb.models.RepoSource;
import software.wings.helpers.ext.gcs.GcsRestClient;
import software.wings.service.intfc.security.EncryptionService;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class GcbServiceImplTest extends CategoryTest {
  private static final String VALID_AUTH_TOKEN = "validToken";
  private static final String PROJECT_ID = "projectId";
  private static final String BUILD_ID = "buildId";
  private static final String BUILD_OPERATION_NAME = "operationName";
  private static final String TRIGGER_ID = "triggerId";
  private static final String BRANCH_NAME = "branchName";
  private static final String BUCKET_NAME = "bucketName";
  private static final String FILE_NAME = "fileName";
  private static final String LOGS = "buildLogs";

  @Mock private GcpHelperService gcpHelperService;
  @Mock private GcpConfig gcpConfig;

  private List<EncryptedDataDetail> encryptedDataDetails;
  @Mock private Call<GcbBuildDetails> callForBuildDetails;
  @Mock private Call<BuildOperationDetails> callForOperation;
  @Mock private Call<GcbBuildTriggers> callForTriggers;
  @Mock private Call<ResponseBody> callForLogs;
  @Mock private GcbRestClient gcbRestClient;
  @Mock private GcsRestClient gcsRestClient;
  @Mock private EncryptionService encryptionService;
  @Rule public final ExpectedException exceptionRule = ExpectedException.none();

  private final GcbServiceImpl gcbService = spy(new GcbServiceImpl(gcpHelperService, encryptionService));

  @Before
  public void setUp() throws IOException, InterruptedException {
    doReturn(gcbRestClient).when(gcbService).getRestClient(GcbRestClient.class, GCB_BASE_URL);
    doReturn(gcsRestClient).when(gcbService).getRestClient(GcsRestClient.class, GCS_BASE_URL);
    doReturn(VALID_AUTH_TOKEN).when(gcbService).getBasicAuthHeader(any(), any());
    when(gcpConfig.getServiceAccountKeyFileContent()).thenReturn("{\"project_id\":\"projectId\"}".toCharArray());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnBuildDetails() throws IOException {
    GcbBuildDetails expectedBuildDetails = GcbBuildDetails.builder().id(BUILD_ID).build();
    Response<GcbBuildDetails> response = Response.success(expectedBuildDetails);

    when(gcbRestClient.getBuild(VALID_AUTH_TOKEN, PROJECT_ID, BUILD_ID)).thenReturn(callForBuildDetails);
    when(callForBuildDetails.execute()).thenReturn(response);
    GcbBuildDetails actualBuildDetails = gcbService.getBuild(gcpConfig, encryptedDataDetails, BUILD_ID);
    assertThat(expectedBuildDetails).isEqualTo(actualBuildDetails);
  }
  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowGcbExceptionWhenGettingBuildAndIOExceptionIsThrown() throws IOException {
    exceptionRule.expect(GcbClientException.class);
    exceptionRule.expectMessage("Invalid Google Cloud Platform credentials.");
    when(gcbRestClient.getBuild(VALID_AUTH_TOKEN, PROJECT_ID, BUILD_ID)).thenReturn(callForBuildDetails);
    when(callForBuildDetails.execute()).thenThrow(new IOException());
    gcbService.getBuild(gcpConfig, encryptedDataDetails, BUILD_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnBuildOperationDetailsWhenBuildIsCreated() throws IOException {
    List<BuildStep> buildSteps = new ArrayList<>();
    GcbBuildDetails buildDetails = GcbBuildDetails.builder().projectId(PROJECT_ID).steps(buildSteps).build();
    BuildOperationDetails buildOperationDetails = new BuildOperationDetails();
    OperationMeta operationMeta = new OperationMeta();
    operationMeta.setBuild(buildDetails);
    buildOperationDetails.setName(BUILD_OPERATION_NAME);
    buildOperationDetails.setOperationMeta(operationMeta);

    Response<BuildOperationDetails> response = Response.success(buildOperationDetails);

    when(gcbRestClient.createBuild(VALID_AUTH_TOKEN, PROJECT_ID, buildDetails)).thenReturn(callForOperation);
    when(callForOperation.execute()).thenReturn(response);
    BuildOperationDetails actualOperationDetails =
        gcbService.createBuild(gcpConfig, encryptedDataDetails, buildDetails);
    assertThat(buildOperationDetails).isEqualTo(actualOperationDetails);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowGcbExceptionWhenCreatingBuildAndIOExceptionIsThrown() throws IOException {
    List<BuildStep> buildSteps = new ArrayList<>();
    GcbBuildDetails buildDetails = GcbBuildDetails.builder().projectId(PROJECT_ID).steps(buildSteps).build();
    exceptionRule.expect(GcbClientException.class);
    exceptionRule.expectMessage("Invalid Google Cloud Platform credentials.");
    when(gcbRestClient.createBuild(VALID_AUTH_TOKEN, PROJECT_ID, buildDetails)).thenReturn(callForOperation);
    when(callForOperation.execute()).thenThrow(new IOException());
    gcbService.createBuild(gcpConfig, encryptedDataDetails, buildDetails);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnBuildOperationDetailsWhenTriggerIsRun() throws IOException {
    RepoSource repoSource = new RepoSource();
    repoSource.setBranchName(BRANCH_NAME);
    GcbBuildSource gcbBuildSource = new GcbBuildSource();
    gcbBuildSource.setRepoSource(repoSource);
    GcbBuildDetails buildDetails = GcbBuildDetails.builder().projectId(PROJECT_ID).source(gcbBuildSource).build();
    BuildOperationDetails buildOperationDetails = new BuildOperationDetails();
    OperationMeta operationMeta = new OperationMeta();
    operationMeta.setBuild(buildDetails);
    buildOperationDetails.setName(BUILD_OPERATION_NAME);
    buildOperationDetails.setOperationMeta(operationMeta);

    Response<BuildOperationDetails> response = Response.success(buildOperationDetails);

    when(gcbRestClient.runTrigger(VALID_AUTH_TOKEN, PROJECT_ID, TRIGGER_ID, repoSource)).thenReturn(callForOperation);
    when(callForOperation.execute()).thenReturn(response);
    BuildOperationDetails actualOperationDetails =
        gcbService.runTrigger(gcpConfig, encryptedDataDetails, TRIGGER_ID, repoSource);
    assertThat(buildOperationDetails).isEqualTo(actualOperationDetails);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowGcbExceptionWhenRunningTriggerAndIOExceptionIsThrown() throws IOException {
    RepoSource repoSource = new RepoSource();
    repoSource.setBranchName(BRANCH_NAME);
    exceptionRule.expect(GcbClientException.class);
    exceptionRule.expectMessage("Invalid Google Cloud Platform credentials.");
    when(gcbRestClient.runTrigger(VALID_AUTH_TOKEN, PROJECT_ID, TRIGGER_ID, repoSource)).thenReturn(callForOperation);
    when(callForOperation.execute()).thenThrow(new IOException());
    gcbService.runTrigger(gcpConfig, encryptedDataDetails, TRIGGER_ID, repoSource);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnListOfExistingTriggers() throws IOException {
    GcbTrigger gcbTrigger = new GcbTrigger();
    gcbTrigger.setId(TRIGGER_ID);
    List<GcbTrigger> triggers = singletonList(gcbTrigger);
    GcbBuildTriggers gcbBuildTriggers = new GcbBuildTriggers();

    gcbBuildTriggers.setTriggers(triggers);
    Response<GcbBuildTriggers> response = Response.success(gcbBuildTriggers);

    when(gcbRestClient.getAllTriggers(VALID_AUTH_TOKEN, PROJECT_ID)).thenReturn(callForTriggers);
    when(callForTriggers.execute()).thenReturn(response);
    List<GcbTrigger> gcbTriggers = gcbService.getAllTriggers(gcpConfig, encryptedDataDetails);
    assertThat(triggers).isEqualTo(gcbTriggers);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowGcbExceptionWhenListingForExistentTriggerAndIOExceptionIsThrown() throws IOException {
    RepoSource repoSource = new RepoSource();
    repoSource.setBranchName(BRANCH_NAME);
    exceptionRule.expect(GcbClientException.class);
    exceptionRule.expectMessage("Invalid Google Cloud Platform credentials.");
    when(gcbRestClient.getAllTriggers(VALID_AUTH_TOKEN, PROJECT_ID)).thenReturn(callForTriggers);
    when(callForTriggers.execute()).thenThrow(new IOException());
    gcbService.getAllTriggers(gcpConfig, encryptedDataDetails);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFetchBuildLogs() throws IOException {
    Response<ResponseBody> response = Response.success(ResponseBody.create(null, LOGS));
    when(gcsRestClient.fetchLogs(VALID_AUTH_TOKEN, BUCKET_NAME, FILE_NAME)).thenReturn(callForLogs);
    when(callForLogs.execute()).thenReturn(response);
    String logs = gcbService.fetchBuildLogs(gcpConfig, encryptedDataDetails, BUCKET_NAME, FILE_NAME);
    assertThat(LOGS).isEqualTo(logs);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowGcbExceptionWhenFetchingLogsAndIOExceptionIsThrown() throws IOException {
    exceptionRule.expect(GcbClientException.class);
    exceptionRule.expectMessage("Invalid Google Cloud Platform credentials.");
    when(gcsRestClient.fetchLogs(VALID_AUTH_TOKEN, BUCKET_NAME, FILE_NAME)).thenReturn(callForLogs);
    when(callForLogs.execute()).thenThrow(new IOException());
    gcbService.fetchBuildLogs(gcpConfig, encryptedDataDetails, BUCKET_NAME, FILE_NAME);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRetryThrowGcbClientExceptionWhenUnknowExceptionIsThrow() {
    when(gcsRestClient.fetchLogs(anyString(), anyString(), anyString())).thenThrow(NullPointerException.class);
    assertThatCode(() -> gcbService.retry(() -> gcsRestClient.fetchLogs("A", "B", "C")))
        .isInstanceOf(GcbClientException.class)
        .hasMessage("Invalid Google Cloud Platform integration.")
        .hasCause(new NullPointerException());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleRetryRethrowInterruptedException() {
    assertThatCode(() -> gcbService.handleRetryException(new InterruptedException()))
        .isInstanceOf(InterruptedException.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleRetryRethrowInterruptedIOException() {
    assertThatCode(() -> gcbService.handleRetryException(new InterruptedIOException()))
        .isInstanceOf(InterruptedIOException.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleRetryRethrowIOException() {
    assertThatCode(() -> gcbService.handleRetryException(new IOException())).isInstanceOf(IOException.class);
    assertThatCode(() -> gcbService.handleRetryException(new EOFException())).isInstanceOf(IOException.class);
  }
}
