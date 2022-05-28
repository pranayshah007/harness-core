/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AzureConfigException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;
import software.wings.delegatetasks.azure.AzureTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AzureTaskTest {
  private static final String clientId = "c-l-i-e-n-t-I-d";
  private static final String tenantId = "t-e-n-a-n-t-I-d";
  private static final String secretKey = "s-e-c-r-e-t-k-e-y";
  private static final String subscriptionId = "123456-654321-987654-345678";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureAsyncTaskHelper azureAsyncTaskHelper;

  @InjectMocks
  private AzureTask task =
      new AzureTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    assertThatThrownBy(() -> task.run(new Object[10]))
        .hasMessage("Object Array parameters not supported")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testPassingNonAzureTaskParams() {
    TaskParameters taskParameters = ArtifactTaskParameters.builder().build();

    assertThatThrownBy(() -> task.run(taskParameters))
        .hasMessage("Task Params are not of expected type: AzureTaskParameters")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateTaskType() {
    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.VALIDATE, null);

    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();

    doReturn(connectorValidationResult).when(azureAsyncTaskHelper).getConnectorValidationResult(any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureValidateTaskResponse.class);

    AzureValidateTaskResponse azureValidateTaskResponse = (AzureValidateTaskResponse) delegateResponseData;
    assertThat(azureValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListSubscriptionsTaskType() {
    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_SUBSCRIPTIONS, null);

    Map<String, String> subscriptions = new HashMap<>();
    subscriptions.put("TestSub", subscriptionId);
    AzureSubscriptionsResponse result = AzureSubscriptionsResponse.builder().subscriptions(subscriptions).build();

    doReturn(result).when(azureAsyncTaskHelper).listSubscriptions(any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureSubscriptionsResponse.class);

    AzureSubscriptionsResponse response = (AzureSubscriptionsResponse) delegateResponseData;
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessfulListResourceGroupsTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();
    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_RESOURCE_GROUPS, additionalParamsStringMap);

    List<String> resourceGroups = new ArrayList<>();
    resourceGroups.add("rg1");
    resourceGroups.add("rg2");
    AzureResourceGroupsResponse result = AzureResourceGroupsResponse.builder().resourceGroups(resourceGroups).build();

    doReturn(result).when(azureAsyncTaskHelper).listResourceGroups(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureResourceGroupsResponse.class);

    AzureResourceGroupsResponse response = (AzureResourceGroupsResponse) delegateResponseData;
    assertThat(response).isNotNull();
    assertThat(response.getResourceGroups().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedListResourceGroupsTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_RESOURCE_GROUPS, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Subscription ID not provided");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessfulListContainerRegistriesTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();
    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    TaskParameters taskParameters =
        getAzureTaskParams(AzureTaskType.LIST_CONTAINER_REGISTRIES, additionalParamsStringMap);

    List<String> containerRegistries = new ArrayList<>();
    containerRegistries.add("ACR1");
    containerRegistries.add("ACR2");
    AzureRegistriesResponse result = AzureRegistriesResponse.builder().containerRegistries(containerRegistries).build();

    doReturn(result).when(azureAsyncTaskHelper).listContainerRegistries(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureRegistriesResponse.class);

    AzureRegistriesResponse response = (AzureRegistriesResponse) delegateResponseData;
    assertThat(response).isNotNull();
    assertThat(response.getContainerRegistries().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedListContainerRegistriesTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();

    TaskParameters taskParameters =
        getAzureTaskParams(AzureTaskType.LIST_CONTAINER_REGISTRIES, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Subscription ID not provided");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessfulListClustersTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();
    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParamsStringMap.put(AzureAdditionalParams.RESOURCE_GROUP, "rg1");

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_CLUSTERS, additionalParamsStringMap);

    List<String> clusters = new ArrayList<>();
    clusters.add("aks1");
    clusters.add("aks2");
    AzureClustersResponse result = AzureClustersResponse.builder().clusters(clusters).build();

    doReturn(result).when(azureAsyncTaskHelper).listClusters(any(), any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureClustersResponse.class);

    AzureClustersResponse response = (AzureClustersResponse) delegateResponseData;
    assertThat(response).isNotNull();
    assertThat(response.getClusters().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedListClustersTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_CLUSTERS, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Subscription ID not provided");

    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    TaskParameters taskParameters2 = getAzureTaskParams(AzureTaskType.LIST_CLUSTERS, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters2))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Resource group name not provided");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessfulListRepositoriesTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();
    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParamsStringMap.put(AzureAdditionalParams.CONTAINER_REGISTRY, "ACR1");

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_REPOSITORIES, additionalParamsStringMap);

    List<String> repos = new ArrayList<>();
    repos.add("library/nginx");
    repos.add("library/apache");
    AzureRepositoriesResponse result = AzureRepositoriesResponse.builder().repositories(repos).build();

    doReturn(result).when(azureAsyncTaskHelper).listRepositories(any(), any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureRepositoriesResponse.class);

    AzureRepositoriesResponse response = (AzureRepositoriesResponse) delegateResponseData;
    assertThat(response).isNotNull();
    assertThat(response.getRepositories().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedListRepositoriesTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.LIST_REPOSITORIES, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Subscription ID not provided");

    additionalParamsStringMap.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    TaskParameters taskParameters2 = getAzureTaskParams(AzureTaskType.LIST_REPOSITORIES, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters2))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Container registry name not provided");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessfulGetAcrTokenTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();
    additionalParamsStringMap.put(AzureAdditionalParams.CONTAINER_REGISTRY, "ACR1");

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.GET_ACR_TOKEN, additionalParamsStringMap);

    AzureAcrTokenTaskResponse result = AzureAcrTokenTaskResponse.builder().token("token").build();

    doReturn(result).when(azureAsyncTaskHelper).getServicePrincipalCertificateAcrLoginToken(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(AzureAcrTokenTaskResponse.class);

    AzureAcrTokenTaskResponse response = (AzureAcrTokenTaskResponse) delegateResponseData;
    assertThat(response).isNotNull();
    assertThat(response.getToken()).isNotEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedGetAcrTokenTaskType() {
    Map<AzureAdditionalParams, String> additionalParamsStringMap = new HashMap<>();

    TaskParameters taskParameters = getAzureTaskParams(AzureTaskType.GET_ACR_TOKEN, additionalParamsStringMap);

    assertThatThrownBy(() -> task.run(taskParameters))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureConfigException.class)
        .hasMessage("Container registry name not provided");
  }

  private TaskParameters getAzureTaskParams(
      AzureTaskType taskType, Map<AzureAdditionalParams, String> additionalParamsStringMap) {
    return AzureTaskParams.builder()
        .azureTaskType(taskType)
        .encryptionDetails(null)
        .azureConnector(getAzureConnectorDTO())
        .additionalParams(additionalParamsStringMap)
        .build();
  }

  private AzureConnectorDTO getAzureConnectorDTO() {
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .delegateSelectors(null)
        .credential(
            AzureCredentialDTO.builder()
                .config(AzureManualDetailsDTO.builder()
                            .clientId(clientId)
                            .tenantId(tenantId)
                            .authDTO(
                                AzureAuthDTO.builder()
                                    .azureSecretType(AzureSecretType.SECRET_KEY)
                                    .credentials(
                                        AzureClientSecretKeyDTO.builder()
                                            .secretKey(
                                                SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                                            .build())
                                    .build())
                            .build())
                .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                .build())
        .build();
  }
}
