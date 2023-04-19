/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.delegate.task.citasks.cik8handler.k8java.CIK8JavaClientHandler;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1StatusBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIK8CleanupTaskHandlerTest extends CategoryTest {
  @Mock private K8sConnectorHelper k8sConnectorHelper;
  @Mock private ApiClientFactory apiClientFactory;
  @Mock private ApiClient apiClient;
  @Mock private CIK8JavaClientHandler cik8JavaClientHandler;
  @Mock private SecretVolumesHelper secretVolumesHelper;
  @InjectMocks private CIK8CleanupTaskHandler cik8DeleteSetupTaskHandler;

  private static final String namespace = "default";
  private static final String podName = "pod";
  private static final String serviceName = "svc";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private CIK8CleanupTaskParams getTaskParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    List<String> podList = new ArrayList<>();
    podList.add(podName);
    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .podNameList(podList)
        .namespace(namespace)
        .build();
  }

  private CIK8CleanupTaskParams getTaskParamsWithService() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    List<String> podList = new ArrayList<>();
    podList.add(podName);
    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .podNameList(podList)
        .serviceNameList(Arrays.asList(serviceName))
        .namespace(namespace)
        .build();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskWithFailure() throws ApiException {
    CIK8CleanupTaskParams taskParams = getTaskParams();

    on(cik8DeleteSetupTaskHandler).set("cik8JavaClientHandler", cik8JavaClientHandler);

    when(cik8JavaClientHandler.deletePodWithRetries(any(), any(), any())).thenThrow(new ApiException());

    K8sTaskExecutionResponse response = cik8DeleteSetupTaskHandler.executeTaskInternal(taskParams, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskWithDeleteException() throws ApiException {
    CIK8CleanupTaskParams taskParams = getTaskParams();

    on(cik8DeleteSetupTaskHandler).set("cik8JavaClientHandler", cik8JavaClientHandler);

    when(cik8JavaClientHandler.deletePodWithRetries(any(), any(), any())).thenThrow(new ApiException());
    when(cik8JavaClientHandler.deleteService(any(), any(), any())).thenReturn(Boolean.TRUE);
    when(cik8JavaClientHandler.deleteSecret(any(), any(), any())).thenReturn(Boolean.TRUE);

    K8sTaskExecutionResponse response = cik8DeleteSetupTaskHandler.executeTaskInternal(taskParams, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskWithServiceSuccess() throws ApiException {
    CIK8CleanupTaskParams taskParams = getTaskParamsWithService();

    on(cik8DeleteSetupTaskHandler).set("cik8JavaClientHandler", cik8JavaClientHandler);

    when(secretVolumesHelper.getAllSecretKeys(any())).thenReturn(new ArrayList<>());
    when(cik8JavaClientHandler.deletePodWithRetries(any(), any(), any()))
        .thenReturn(new V1StatusBuilder().withStatus("Success").build());
    when(cik8JavaClientHandler.deleteService(any(), any(), any())).thenReturn(Boolean.TRUE);
    when(cik8JavaClientHandler.deleteSecret(any(), any(), any())).thenReturn(Boolean.TRUE);

    K8sTaskExecutionResponse response = cik8DeleteSetupTaskHandler.executeTaskInternal(taskParams, "");
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskWithServiceFailure() throws ApiException {
    CIK8CleanupTaskParams taskParams = getTaskParamsWithService();

    on(cik8DeleteSetupTaskHandler).set("cik8JavaClientHandler", cik8JavaClientHandler);

    when(cik8JavaClientHandler.deletePodWithRetries(any(), any(), any()))
        .thenReturn(new V1StatusBuilder().withStatus("Failure").build());
    when(cik8JavaClientHandler.deleteService(any(), any(), any())).thenReturn(Boolean.FALSE);
    when(cik8JavaClientHandler.deleteSecret(any(), any(), any())).thenReturn(Boolean.FALSE);

    K8sTaskExecutionResponse response = cik8DeleteSetupTaskHandler.executeTaskInternal(taskParams, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getType() {
    assertEquals(CICleanupTaskHandler.Type.GCP_K8, cik8DeleteSetupTaskHandler.getType());
  }
}
