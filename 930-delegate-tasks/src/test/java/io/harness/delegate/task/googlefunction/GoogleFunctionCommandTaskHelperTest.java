/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionArtifactConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import io.harness.googlefunctions.GoogleCloudRunClient;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.WatchdogTimeoutException;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.Service;
import com.google.cloud.run.v2.TrafficTargetStatus;
import com.google.longrunning.Operation;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GoogleFunctionCommandTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";
  private final String BUCKET = "bucket";

  @InjectMocks private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Mock private GoogleCloudFunctionClient googleCloudFunctionClient;
  @Mock private LogCallback logCallback;
  @Mock private GoogleCloudRunClient googleCloudRunClient;

  private GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig;
  private GoogleFunctionArtifactConfig googleFunctionArtifactConfig;
  private String googleFunctionDeployManifestContent;
  private Function function;
  private Service service;

  @Before
  public void setUp() throws Exception {
    doReturn(Revision.getDefaultInstance()).when(googleCloudRunClient).getRevision(any(), any());

    Operation operation = Operation.newBuilder().setDone(true).build();
    doReturn(operation).when(googleCloudFunctionClient).getOperation(any(), any());
    doReturn(operation).when(googleCloudRunClient).getOperation(any(), any());

    gcpGoogleFunctionInfraConfig =
        GcpGoogleFunctionInfraConfig.builder()
            .region(REGION)
            .project(PROJECT)
            .gcpConnectorDTO(GcpConnectorDTO.builder()
                                 .credential(GcpConnectorCredentialDTO.builder()
                                                 .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                 .build())
                                 .build())
            .build();

    googleFunctionArtifactConfig = GoogleCloudStorageArtifactConfig.builder()
                                       .project(PROJECT)
                                       .bucket(BUCKET)
                                       .filePath("abc")
                                       .connectorDTO(ConnectorInfoDTO.builder().build())
                                       .build();

    googleFunctionDeployManifestContent = "function:\n"
        + "  name: gcf-go-helloworld-automation\n"
        + "  buildConfig:\n"
        + "    runtime: go119\n"
        + "    entryPoint: HelloGet";

    function = Function.newBuilder().setName("gcf-go-helloworld-automation").setState(Function.State.ACTIVE).build();

    doReturn(getOperationFunctionFuture()).when(googleCloudFunctionClient).createFunction(any(), any());

    doReturn(getOperationFunctionFuture()).when(googleCloudFunctionClient).updateFunction(any(), any());

    doReturn(getOperationFunctionFuture()).when(googleCloudFunctionClient).deleteFunction(any(), any());

    doReturn(getOperationFunctionFuture()).when(googleCloudRunClient).updateService(any(), any());
    TrafficTargetStatus trafficTargetStatus = TrafficTargetStatus.newBuilder().setRevision("").setPercent(100).build();
    service = Service.newBuilder().setName("").addTrafficStatuses(trafficTargetStatus).build();

    doReturn(service).when(googleCloudRunClient).getService(any(), any());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void deployExistingFunctionTest() throws ExecutionException, InterruptedException {
    doReturn(function).when(googleCloudFunctionClient).getFunction(any(), any());

    doThrow(
        new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER))
        .when(googleCloudRunClient)
        .getRevision(any(), any());

    Function currentFunction = googleFunctionCommandTaskHelper.deployFunction(gcpGoogleFunctionInfraConfig,
        googleFunctionDeployManifestContent, "", googleFunctionArtifactConfig, true, logCallback);

    verify(googleCloudFunctionClient).updateFunction(any(), any());
    verify(googleCloudFunctionClient, times(3)).getFunction(any(), any());
    verify(googleCloudFunctionClient).getOperation(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void deployNewFunctionWithLatestTrafficFlagTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionClient.getFunction(any(), any()))
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER))
        .thenReturn(function);

    Function currentFunction = googleFunctionCommandTaskHelper.deployFunction(gcpGoogleFunctionInfraConfig,
        googleFunctionDeployManifestContent, "", googleFunctionArtifactConfig, true, logCallback);

    verify(googleCloudFunctionClient).createFunction(any(), any());
    verify(googleCloudFunctionClient, times(3)).getFunction(any(), any());
    verify(googleCloudFunctionClient).getOperation(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void deployNewFunctionWithoutLatestTrafficFlagTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionClient.getFunction(any(), any()))
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER))
        .thenReturn(function);
    when(googleCloudRunClient.getRevision(any(), any()))
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER));
    doReturn(getOperationFunctionFuture()).when(googleCloudRunClient).deleteRevision(any(), any());

    Function currentFunction = googleFunctionCommandTaskHelper.deployFunction(gcpGoogleFunctionInfraConfig,
        googleFunctionDeployManifestContent, "", googleFunctionArtifactConfig, false, logCallback);

    verify(googleCloudFunctionClient).createFunction(any(), any());
    verify(googleCloudFunctionClient, times(3)).getFunction(any(), any());
    verify(googleCloudFunctionClient).getOperation(any(), any());
    verify(googleCloudRunClient).getOperation(any(), any());
    verify(googleCloudRunClient).updateService(any(), any());
    verify(googleCloudRunClient, times(2)).getService(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void createFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionClient.getFunction(any(), any())).thenReturn(function);
    Function currentFunction =
        googleFunctionCommandTaskHelper.createFunction(CreateFunctionRequest.newBuilder().build(),
            gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudFunctionClient).createFunction(any(), any());
    verify(googleCloudFunctionClient).getOperation(any(), any());
    verify(googleCloudFunctionClient, times(2)).getFunction(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void updateFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionClient.getFunction(any(), any())).thenReturn(function);
    Function currentFunction =
        googleFunctionCommandTaskHelper.updateFunction(UpdateFunctionRequest.newBuilder().build(),
            gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudFunctionClient).updateFunction(any(), any());
    verify(googleCloudFunctionClient).getOperation(any(), any());
    verify(googleCloudFunctionClient, times(2)).getFunction(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void updateTrafficTest() throws ExecutionException, InterruptedException {
    when(googleCloudRunClient.getRevision(any(), any()))
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER));
    doReturn(getOperationFunctionFuture()).when(googleCloudRunClient).deleteRevision(any(), any());
    googleFunctionCommandTaskHelper.updateTraffic(
        "abc", 100, "", "", gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudRunClient).updateService(any(), any());
    verify(googleCloudRunClient).getOperation(any(), any());
    verify(googleCloudRunClient, times(2)).getService(any(), any());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void updateTrafficToSingleRevisionTest() throws ExecutionException, InterruptedException {
    when(googleCloudRunClient.getRevision(any(), any()))
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER));
    doReturn(getOperationFunctionFuture()).when(googleCloudRunClient).deleteRevision(any(), any());
    googleFunctionCommandTaskHelper.updateFullTrafficToSingleRevision(
        "abc", "", gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudRunClient).updateService(any(), any());
    verify(googleCloudRunClient).getOperation(any(), any());
    verify(googleCloudRunClient, times(2)).getService(any(), any());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void getGoogleFunctionTest() throws InvalidProtocolBufferException {
    GoogleFunction googleFunction =
        googleFunctionCommandTaskHelper.getGoogleFunction(function, gcpGoogleFunctionInfraConfig, logCallback);
    verify(googleCloudRunClient).getService(any(), any());
    assertThat(googleFunction.getFunctionName()).isEqualTo(function.getName());
    assertThat(googleFunction.getCloudRunService().getServiceName()).isEqualTo(service.getName());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void getCloudRunServiceTest() {
    Service currentService = googleFunctionCommandTaskHelper.getCloudRunService(
        "abc", gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudRunClient).getService(any(), any());
    assertThat(currentService.getName()).isEqualTo(service.getName());
    assertThat(currentService.getTrafficStatuses(0).getRevision())
        .isEqualTo(service.getTrafficStatuses(0).getRevision());
    assertThat(currentService.getTrafficStatuses(0).getPercent()).isEqualTo(service.getTrafficStatuses(0).getPercent());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void parseStringContentAsClassBuilderTest() {
    CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
    googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
        googleFunctionDeployManifestContent, createFunctionRequestBuilder, logCallback, "createFunctionRequest");
    assertThat(createFunctionRequestBuilder.getFunction().getName()).isEqualTo(function.getName());
    assertThat(createFunctionRequestBuilder.getFunction().getBuildConfig().getRuntime()).isEqualTo("go119");
    assertThat(createFunctionRequestBuilder.getFunction().getBuildConfig().getEntryPoint()).isEqualTo("HelloGet");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void deleteFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionClient.getFunction(any(), any()))
        .thenReturn(function)
        .thenThrow(new InvalidRequestException("test",
            new NotFoundException(new Throwable(), WatchdogTimeoutException.LOCAL_ABORTED_STATUS_CODE, false), USER));
    googleFunctionCommandTaskHelper.deleteFunction(
        "abc", gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback);
    verify(googleCloudFunctionClient).deleteFunction(any(), any());
    verify(googleCloudFunctionClient, times(2)).getFunction(any(), any());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void getFunctionTest() {
    when(googleCloudFunctionClient.getFunction(any(), any())).thenReturn(function);
    Function currentFunction =
        googleFunctionCommandTaskHelper
            .getFunction("abc", gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), PROJECT, REGION, logCallback)
            .get();
    verify(googleCloudFunctionClient).getFunction(any(), any());
    assertThat(currentFunction.getName()).isEqualTo(function.getName());
    assertThat(currentFunction.getState()).isEqualTo(function.getState());
  }

  private OperationFuture<Object, Object> getOperationFunctionFuture() {
    return new OperationFuture<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return false;
      }

      @Override
      public Function get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public Function get(long timeout, @NotNull TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }

      @Override
      public void addListener(Runnable runnable, Executor executor) {}

      @Override
      public String getName() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public ApiFuture<OperationSnapshot> getInitialFuture() {
        return new ApiFuture<OperationSnapshot>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public boolean isDone() {
            return false;
          }

          @Override
          public OperationSnapshot get() throws InterruptedException, ExecutionException {
            return null;
          }

          @Override
          public OperationSnapshot get(long timeout, @NotNull TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            return null;
          }

          @Override
          public void addListener(Runnable runnable, Executor executor) {}
        };
      }

      @Override
      public RetryingFuture<OperationSnapshot> getPollingFuture() {
        return null;
      }

      @Override
      public ApiFuture<Object> peekMetadata() {
        return null;
      }

      @Override
      public ApiFuture<Object> getMetadata() {
        return null;
      }
    };
  }
}
