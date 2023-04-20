/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.HARNESS_AUTOSCALING_GROUP_TAG_NAME;
import static io.harness.azure.model.AzureConstants.VMSS_CREATED_TIME_STAMP_TAG_NAME;
import static io.harness.delegate.task.azure.request.AzureVMSSTaskParameters.AzureVMSSTaskType.AZURE_VMSS_SETUP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureMachineImageArtifact;
import io.harness.azure.model.AzureUserAuthVMInstanceData;
import io.harness.azure.model.AzureVMSSAutoScaleSettingsData;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.AzureVMAuthType;
import io.harness.delegate.beans.azure.GalleryImageDefinitionDTO;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSSetupTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;

import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.compute.fluent.models.VirtualMachineScaleSetInner;
import com.azure.resourcemanager.compute.models.GalleryImage;
import com.azure.resourcemanager.compute.models.GalleryImageIdentifier;
import com.azure.resourcemanager.compute.models.OperatingSystemStateTypes;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSet;
import com.azure.resourcemanager.network.models.LoadBalancer;
import com.google.common.util.concurrent.TimeLimiter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSSetupTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureComputeClient mockAzureComputeClient;
  @Mock private AzureNetworkClient mockAzureNetworkClient;
  @Mock private AzureAutoScaleSettingsClient mockAzureAutoScaleSettingsClient;
  @Mock private AzureAutoScaleHelper mockAzureAutoScaleHelper;
  @Mock private TimeLimiter timeLimiter;
  private final int minInstances = 0;
  private final int maxInstances = 2;
  private final int desiredInstances = 1;

  @Spy @InjectMocks AzureVMSSSetupTaskHandler azureVMSSSetupTaskHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    mockAzureAutoScaleHelperMethods();
    mockExecutionLogCallbackMethods();
    VirtualMachineScaleSet virtualMachineScaleSetVersion1 = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet virtualMachineScaleSetVersion2 = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet virtualMachineScaleSetVersion3 = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet virtualMachineScaleSetVersion4 = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet virtualMachineScaleSetVersion5 = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet baseVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSSetupTaskParameters azureVMSSSetupTaskParameters = buildAzureVMSSSetupTaskParameters();
    AzureMachineImageArtifact azureMachineImageArtifact = mock(AzureMachineImageArtifact.class);

    Instant instant = Instant.now();
    Date dateVersion1 = Date.from(instant.minus(5, ChronoUnit.DAYS));
    when(virtualMachineScaleSetVersion1.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__1");
        put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(dateVersion1));
      }
    });
    doReturn("virtualMachineScaleSetVersion1ShouldBeDeleted").when(virtualMachineScaleSetVersion1).name();
    doReturn("virtualMachineScaleSetVersion1ShouldBeDeletedId").when(virtualMachineScaleSetVersion1).id();

    Date dateVersion2 = Date.from(instant.minus(4, ChronoUnit.DAYS));
    when(virtualMachineScaleSetVersion2.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__2");
        put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(dateVersion2));
      }
    });
    doReturn(1).when(virtualMachineScaleSetVersion2).capacity();
    doReturn("virtualMachineScaleSetVersion2ShouldBeDownSizedId").when(virtualMachineScaleSetVersion2).id();
    doReturn("virtualMachineScaleSetVersion2ShouldBeDownSized").when(virtualMachineScaleSetVersion2).name();
    doNothing()
        .when(azureVMSSSetupTaskHandler)
        .updateVMSSCapacityAndWaitForSteadyState(any(AzureConfig.class), any(AzureVMSSTaskParameters.class),
            anyString(), anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    Date dateVersion3 = Date.from(instant.minus(3, ChronoUnit.DAYS));
    when(virtualMachineScaleSetVersion3.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__3");
        put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(dateVersion3));
      }
    });
    doReturn(2).when(virtualMachineScaleSetVersion3).capacity();
    doReturn("mostRecentActiveVMSSName").when(virtualMachineScaleSetVersion3).name();
    doReturn("mostRecentActiveVMSSId").when(virtualMachineScaleSetVersion3).id();
    doReturn("resourceGroupName").when(virtualMachineScaleSetVersion3).resourceGroupName();

    Date dateVersion4 = Date.from(instant.minus(2, ChronoUnit.DAYS));
    when(virtualMachineScaleSetVersion4.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__4");
        put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(dateVersion4));
      }
    });
    doReturn("virtualMachineScaleSetVersion4ShouldBeDownSizedId").when(virtualMachineScaleSetVersion4).id();
    doReturn("virtualMachineScaleSetVersion4ShouldBeDownSized").when(virtualMachineScaleSetVersion4).name();

    Date dateVersion5 = Date.from(instant.minus(1, ChronoUnit.DAYS));
    when(virtualMachineScaleSetVersion5.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__5");
        put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(dateVersion5));
      }
    });
    doReturn("virtualMachineScaleSetVersion5ShouldBeRetained").when(virtualMachineScaleSetVersion5).name();
    doReturn("virtualMachineScaleSetVersion5ShouldBeRetainedId").when(virtualMachineScaleSetVersion5).id();

    doReturn("baseVirtualMachineScaleSetId").when(baseVirtualMachineScaleSet).id();

    // downsizeOrDeleteOlderVirtualMachineScaleSets
    doReturn(Arrays.asList(virtualMachineScaleSetVersion1, virtualMachineScaleSetVersion2,
                 virtualMachineScaleSetVersion3, virtualMachineScaleSetVersion4, virtualMachineScaleSetVersion5))
        .when(mockAzureComputeClient)
        .listVirtualMachineScaleSetsByResourceGroupName(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"));
    doReturn(virtualMachineScaleSetVersion4)
        .when(mockAzureComputeClient)
        .updateVMSSCapacity(
            any(), eq("virtualMachineScaleSetVersion4ShouldBeDownSized"), anyString(), anyString(), anyInt());
    doReturn(virtualMachineScaleSetVersion2)
        .when(mockAzureComputeClient)
        .updateVMSSCapacity(
            any(), eq("virtualMachineScaleSetVersion2ShouldBeDownSized"), anyString(), anyString(), anyInt());
    doNothing()
        .when(mockAzureComputeClient)
        .deleteVirtualMachineScaleSetByResourceGroupName(any(), anyString(), anyString(), anyString());

    // createVirtualMachineScaleSet
    doReturn(Optional.of(baseVirtualMachineScaleSet))
        .when(mockAzureComputeClient)
        .getVirtualMachineScaleSetByName(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), anyString());
    doReturn(Optional.of(virtualMachineScaleSetVersion3))
        .when(mockAzureComputeClient)
        .getVirtualMachineScaleSetByName(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), eq("mostRecentActiveVMSSName"));

    VirtualMachineScaleSetInner mockVirtualMachineScaleSetInner = mock(VirtualMachineScaleSetInner.class);
    PollResult<VirtualMachineScaleSetInner> mockPollResult = mock(PollResult.class);
    PollResponse<PollResult<VirtualMachineScaleSetInner>> mockPollResponse = mock(PollResponse.class);
    SyncPoller<PollResult<VirtualMachineScaleSetInner>, VirtualMachineScaleSetInner> mockSyncPoller =
        mock(SyncPoller.class);

    doReturn(mockVirtualMachineScaleSetInner).when(mockPollResult).getValue();
    doReturn(mockPollResult).when(mockPollResponse).getValue();
    doReturn(mockPollResponse).when(mockSyncPoller).waitForCompletion(any());
    doReturn(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED).when(mockPollResponse).getStatus();

    doReturn(mockSyncPoller)
        .when(mockAzureComputeClient)
        .createVirtualMachineScaleSet(any(AzureConfig.class), anyString(), any(VirtualMachineScaleSet.class),
            anyString(), any(AzureUserAuthVMInstanceData.class), any(AzureMachineImageArtifact.class),
            any(AzureVMSSTagsData.class));
    // buildAzureVMSSSetupTaskResponse
    doReturn(Optional.of("{baseScalingPolicies: {...}}"))
        .when(mockAzureAutoScaleSettingsClient)
        .getAutoScaleSettingJSONByTargetResourceId(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), eq("baseVirtualMachineScaleSetId"));

    // populatePreDeploymentData
    doReturn(Optional.of("{mostRecentScalingPolicies: {...}}"))
        .when(mockAzureAutoScaleSettingsClient)
        .getAutoScaleSettingJSONByTargetResourceId(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), eq("mostRecentActiveVMSSId"));

    doReturn(azureMachineImageArtifact)
        .when(azureVMSSSetupTaskHandler)
        .getAzureMachineImageArtifact(any(), any(), any());

    AzureVMSSTaskExecutionResponse response =
        azureVMSSSetupTaskHandler.executeTaskInternal(azureVMSSSetupTaskParameters, azureConfig);

    // createVirtualMachineScaleSet
    verify(mockAzureComputeClient, times(1))
        .getVirtualMachineScaleSetByName(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), anyString());

    verify(mockAzureComputeClient, times(1))
        .createVirtualMachineScaleSet(any(AzureConfig.class), anyString(), any(VirtualMachineScaleSet.class),
            anyString(), any(AzureUserAuthVMInstanceData.class), any(AzureMachineImageArtifact.class),
            any(AzureVMSSTagsData.class));

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskResponse).isNotNull();
    assertThat(azureVMSSTaskResponse instanceof AzureVMSSSetupTaskResponse).isTrue();
    AzureVMSSSetupTaskResponse setupResponse = (AzureVMSSSetupTaskResponse) azureVMSSTaskResponse;
    assertThat(setupResponse.getErrorMessage()).isNull();
    assertThat(setupResponse.getBaseVMSSScalingPolicyJSONs())
        .isEqualTo(Collections.singletonList("{baseScalingPolicies: {...}}"));
    assertThat(setupResponse.getHarnessRevision()).isEqualTo(6);
    assertThat(setupResponse.getLastDeployedVMSSName()).isEqualTo("mostRecentActiveVMSSName");
    assertThat(setupResponse.getNewVirtualMachineScaleSetName()).isEqualTo("newVirtualScaleSetName__6");
    // instance limits
    assertThat(setupResponse.getDesiredInstances()).isEqualTo(desiredInstances);
    assertThat(setupResponse.getMaxInstances()).isEqualTo(maxInstances);
    assertThat(setupResponse.getMinInstances()).isEqualTo(minInstances);

    // for rollback
    assertThat(setupResponse.getPreDeploymentData().getDesiredCapacity()).isEqualTo(2);
    assertThat(setupResponse.getPreDeploymentData().getMinCapacity()).isEqualTo(0);
    assertThat(setupResponse.getPreDeploymentData().getOldVmssName()).isEqualTo("mostRecentActiveVMSSName");
    assertThat(setupResponse.getPreDeploymentData().getScalingPolicyJSON())
        .isEqualTo(Collections.singletonList("{mostRecentScalingPolicies: {...}}"));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAzureMachineImageArtifact() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    ExecutionLogCallback executionLogCallback = mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = buildAzureConfig();
    AzureMachineImageArtifactDTO artifactDTO = buildAzureMachineImageArtifactDTO();
    GalleryImage mockGalleryImage = mockGalleryImage();

    doReturn(Optional.of(mockGalleryImage))
        .when(mockAzureComputeClient)
        .getGalleryImage(azureConfig, subscriptionId, resourceGroupName, "galleryName", "definitionName");

    AzureMachineImageArtifact azureMachineImageArtifact =
        azureVMSSSetupTaskHandler.getAzureMachineImageArtifact(azureConfig, artifactDTO, executionLogCallback);

    assertThat(azureMachineImageArtifact).isNotNull();
    assertThat(azureMachineImageArtifact)
        .isEqualToComparingFieldByField(
            AzureMachineImageArtifact.builder()
                .imageReference(
                    AzureMachineImageArtifact.MachineImageReference.builder()
                        .offer("TestOffer")
                        .id("/subscriptions/subscriptionId/resourceGroups/resourceGroupName/providers"
                            + "/Microsoft.Compute/galleries/galleryName/images/definitionName/versions/version")
                        .version("version")
                        .osState(AzureMachineImageArtifact.MachineImageReference.OsState.GENERALIZED)
                        .publisher("TestPublisher")
                        .sku("TestSku")
                        .build())
                .imageType(AzureMachineImageArtifact.ImageType.IMAGE_GALLERY)
                .osType(AzureMachineImageArtifact.OSType.LINUX)
                .build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAzureMachineImageArtifactWithNoExistingGalleryImage() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    ExecutionLogCallback executionLogCallback = mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = buildAzureConfig();
    AzureMachineImageArtifactDTO artifactDTO = buildAzureMachineImageArtifactDTO();

    doReturn(Optional.empty())
        .when(mockAzureComputeClient)
        .getGalleryImage(azureConfig, subscriptionId, resourceGroupName, "galleryName", "definitionName");

    assertThatThrownBy(
        () -> azureVMSSSetupTaskHandler.getAzureMachineImageArtifact(azureConfig, artifactDTO, executionLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Image reference cannot be found, galleryImageId: galleryName, imageDefinitionName: definitionName, "
            + "subscriptionId: subscriptionId, resourceGroupName: resourceGroupName, imageVersion: version");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLoadBalancer() {
    AzureConfig azureConfig = buildAzureConfig();
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";
    mockGetLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    LoadBalancer loadBalancer =
        azureVMSSSetupTaskHandler.getLoadBalancer(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);

    verify(mockAzureNetworkClient, times(1))
        .getLoadBalancerByName(any(AzureConfig.class), eq(subscriptionId), eq(resourceGroupName), captor.capture());
    String capturedLoadBalancerName = captor.getValue();
    assertThat(capturedLoadBalancerName).isEqualTo(loadBalancerName);
    assertThat(loadBalancer).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLoadBalancerWithNoExistingLoadBalancer() {
    AzureConfig azureConfig = buildAzureConfig();
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";

    doReturn(Optional.empty())
        .when(mockAzureNetworkClient)
        .getLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);

    assertThatThrownBy(()
                           -> azureVMSSSetupTaskHandler.getLoadBalancer(
                               azureConfig, subscriptionId, resourceGroupName, loadBalancerName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found load balancer with name: loadBalancerName, resourceGroupName: resourceGroupName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAttachNewCreatedVMSSToStageBackendPool() throws Exception {
    AzureConfig azureConfig = buildAzureConfig();
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";
    String newVirtualMachineScaleSetName = "newVirtualMachineScaleSetName";
    ExecutionLogCallback executionLogCallback = mockExecutionLogCallbackMethods();
    mockGetLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);
    VirtualMachineScaleSet mockVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    AzureVMSSSetupTaskParameters azureVMSSSetupTaskParameters = buildAzureVMSSSetupTaskParameters();
    azureVMSSSetupTaskParameters.setAzureLoadBalancerDetail(AzureLoadBalancerDetailForBGDeployment.builder()
                                                                .loadBalancerName("loadBalancerName")
                                                                .prodBackendPool("prod")
                                                                .stageBackendPool("stage")
                                                                .build());

    doReturn(mockVirtualMachineScaleSet)
        .when(mockAzureComputeClient)
        .detachVMSSFromBackendPools(any(AzureConfig.class), eq(subscriptionId), eq(resourceGroupName),
            eq(newVirtualMachineScaleSetName), eq("*"));

    doReturn(mockVirtualMachineScaleSet)
        .when(mockAzureComputeClient)
        .attachVMSSToBackendPools(any(AzureConfig.class), any(), eq(subscriptionId), eq(resourceGroupName),
            eq(newVirtualMachineScaleSetName), any());

    ArgumentCaptor<String> backendPoolNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> scaleSetNameCaptor = ArgumentCaptor.forClass(String.class);

    azureVMSSSetupTaskHandler.attachNewCreatedVMSSToStageBackendPool(
        azureConfig, azureVMSSSetupTaskParameters, newVirtualMachineScaleSetName, executionLogCallback);

    verify(mockAzureComputeClient, times(1))
        .attachVMSSToBackendPools(any(AzureConfig.class), any(), eq(subscriptionId), eq(resourceGroupName),
            scaleSetNameCaptor.capture(), backendPoolNameCaptor.capture());
    String capturedBackendPoolName = backendPoolNameCaptor.getValue();
    String capturedScaleSetName = scaleSetNameCaptor.getValue();
    assertThat(capturedBackendPoolName).isEqualTo("stage");
    assertThat(capturedScaleSetName).isEqualTo(newVirtualMachineScaleSetName);
  }

  @NotNull
  private GalleryImage mockGalleryImage() {
    GalleryImageIdentifier galleryImageIdentifier = new GalleryImageIdentifier();
    galleryImageIdentifier.withOffer("TestOffer");
    galleryImageIdentifier.withPublisher("TestPublisher");
    galleryImageIdentifier.withSku("TestSku");
    GalleryImage galleryImage = mock(GalleryImage.class);
    when(galleryImage.osState()).thenReturn(OperatingSystemStateTypes.GENERALIZED);
    when(galleryImage.identifier()).thenReturn(galleryImageIdentifier);
    return galleryImage;
  }

  private AzureMachineImageArtifactDTO buildAzureMachineImageArtifactDTO() {
    return AzureMachineImageArtifactDTO.builder()
        .imageType(AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY)
        .imageDefinition(GalleryImageDefinitionDTO.builder()
                             .subscriptionId("subscriptionId")
                             .resourceGroupName("resourceGroupName")
                             .definitionName("definitionName")
                             .galleryName("galleryName")
                             .version("version")
                             .build())
        .imageOSType(AzureMachineImageArtifactDTO.OSType.LINUX)
        .build();
  }

  private void mockGetLoadBalancerByName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String loadBalancerName) {
    LoadBalancer mockLoadBalancer = mock(LoadBalancer.class);

    doReturn(Optional.of(mockLoadBalancer))
        .when(mockAzureNetworkClient)
        .getLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder()
        .tenantId("tenantId")
        .clientId("clientId")
        .key("key".toCharArray())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  private AzureVMSSSetupTaskParameters buildAzureVMSSSetupTaskParameters() {
    AzureVMAuthDTO azureVMAuthDTO = buildAzureVMAuthDTO();
    return AzureVMSSSetupTaskParameters.builder()
        .infraMappingId("infraMappingId")
        .appId("appId")
        .accountId("accountId")
        .activityId("activityId")
        .autoScalingSteadyStateVMSSTimeout(10)
        .baseVMSSName("baseVMSSName")
        .blueGreen(false)
        .commandName("commandName")
        .azureVmAuthDTO(azureVMAuthDTO)
        .commandType(AZURE_VMSS_SETUP)
        .resourceGroupName("resourceGroupName")
        .subscriptionId("subscriptionId")
        .vmssNamePrefix("newVirtualScaleSetName")
        .useCurrentRunningCount(false)
        .build();
  }

  private AzureVMAuthDTO buildAzureVMAuthDTO() {
    return AzureVMAuthDTO.builder()
        .userName("username")
        .azureVmAuthType(AzureVMAuthType.SSH_PUBLIC_KEY)
        .secretRef(SecretRefData.builder().decryptedValue("sshPublicKey".toCharArray()).build())
        .build();
  }

  private void mockAzureAutoScaleHelperMethods() {
    AzureVMSSAutoScaleSettingsData azureVMSSAutoScaleSettingsData = mock(AzureVMSSAutoScaleSettingsData.class);
    doReturn(minInstances).when(azureVMSSAutoScaleSettingsData).getMinInstances();
    doReturn(maxInstances).when(azureVMSSAutoScaleSettingsData).getMaxInstances();
    doReturn(desiredInstances).when(azureVMSSAutoScaleSettingsData).getDesiredInstances();
    doReturn(azureVMSSAutoScaleSettingsData)
        .when(mockAzureAutoScaleHelper)
        .getVMSSAutoScaleInstanceLimits(any(), any(), any(), anyBoolean(), any());
    doReturn(Collections.singletonList("{baseScalingPolicies: {...}}"))
        .when(mockAzureAutoScaleHelper)
        .getVMSSAutoScaleSettingsJSONs(any(), any(), any(), any());
    doReturn(Collections.singletonList("{mostRecentScalingPolicies: {...}}"))
        .when(mockAzureAutoScaleHelper)
        .getVMSSAutoScaleSettingsJSONs(any(), any(), any());
  }

  private ExecutionLogCallback mockExecutionLogCallbackMethods() throws Exception {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter).thenReturn(Boolean.TRUE);
    doReturn(mockCallback).when(azureVMSSSetupTaskHandler).getLogCallBack(any(), any());
    return mockCallback;
  }
}
