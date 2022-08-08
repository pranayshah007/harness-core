/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.CREATE_NEW_VMSS_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DELETE_OLD_VIRTUAL_MACHINE_SCALE_SETS_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.GALLERY_IMAGE_ID_PATTERN;
import static io.harness.azure.model.AzureConstants.HARNESS_AUTOSCALING_GROUP_TAG_NAME;
import static io.harness.azure.model.AzureConstants.NO_VMSS_FOR_DELETION;
import static io.harness.azure.model.AzureConstants.NO_VMSS_FOR_DOWN_SIZING;
import static io.harness.azure.model.AzureConstants.NUMBER_OF_LATEST_VERSIONS_TO_KEEP;
import static io.harness.azure.model.AzureConstants.SETUP_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.VMSS_AUTH_TYPE_DEFAULT;
import static io.harness.azure.model.AzureConstants.VMSS_AUTH_TYPE_SSH_PUBLIC_KEY;
import static io.harness.azure.model.AzureConstants.VMSS_CREATED_TIME_STAMP_TAG_NAME;
import static io.harness.azure.utility.AzureResourceUtility.getRevisionFromTag;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureMachineImageArtifact;
import io.harness.azure.model.AzureMachineImageArtifact.ImageType;
import io.harness.azure.model.AzureMachineImageArtifact.MachineImageReference;
import io.harness.azure.model.AzureMachineImageArtifact.MachineImageReference.OsState;
import io.harness.azure.model.AzureMachineImageArtifact.OSType;
import io.harness.azure.model.AzureUserAuthVMInstanceData;
import io.harness.azure.model.AzureVMSSAutoScaleSettingsData;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.GalleryImageDefinitionDTO;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSSetupTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.command.ExecutionLogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.GalleryImage;
import com.microsoft.azure.management.compute.GalleryImageIdentifier;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.network.LoadBalancer;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSSetupTaskHandler extends AzureVMSSTaskHandler {
  @Inject private AzureAutoScaleHelper azureAutoScaleHelper;

  @Override
  protected AzureVMSSTaskExecutionResponse executeTaskInternal(
      AzureVMSSTaskParameters azureVMSSTaskParameters, AzureConfig azureConfig) {
    AzureVMSSSetupTaskParameters setupTaskParameters = (AzureVMSSSetupTaskParameters) azureVMSSTaskParameters;
    ExecutionLogCallback setupLogCallback = getLogCallBack(azureVMSSTaskParameters, SETUP_COMMAND_UNIT);

    setupLogCallback.saveExecutionLog("Starting Azure Virtual Machine Scale Set Setup", INFO);

    validateSetupTaskParameters(setupTaskParameters);

    List<VirtualMachineScaleSet> harnessVMSSSortedByCreationTimeReverse =
        logAndGetHarnessMangedVMSSSortedByCreationTime(azureConfig, setupTaskParameters, setupLogCallback);

    VirtualMachineScaleSet mostRecentActiveVMSS =
        logAndGetMostRecentActiveVMSS(harnessVMSSSortedByCreationTimeReverse, setupLogCallback);

    Integer newHarnessRevision = logAndGetNewHarnessRevision(harnessVMSSSortedByCreationTimeReverse, setupLogCallback);

    String newVirtualMachineScaleSetName =
        logAndGetNewVirtualMachineScaleSetName(setupTaskParameters, newHarnessRevision, setupLogCallback);

    downsizeLatestOlderVersions(
        azureConfig, setupTaskParameters, harnessVMSSSortedByCreationTimeReverse, mostRecentActiveVMSS);

    deleteVMSSsOlderThenLatestVersionsToKeep(
        azureConfig, harnessVMSSSortedByCreationTimeReverse, mostRecentActiveVMSS, setupTaskParameters);

    createVirtualMachineScaleSet(azureConfig, setupTaskParameters, newHarnessRevision, newVirtualMachineScaleSetName,
        getLogCallBack(azureVMSSTaskParameters, CREATE_NEW_VMSS_COMMAND_UNIT));

    AzureVMSSSetupTaskResponse azureVMSSSetupTaskResponse = buildAzureVMSSSetupTaskResponse(
        azureConfig, newHarnessRevision, newVirtualMachineScaleSetName, mostRecentActiveVMSS, setupTaskParameters);

    return AzureVMSSTaskExecutionResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .azureVMSSTaskResponse(azureVMSSSetupTaskResponse)
        .build();
  }

  private void validateSetupTaskParameters(AzureVMSSSetupTaskParameters setupTaskParameters) {
    boolean isBlueGreen = setupTaskParameters.isBlueGreen();
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail = setupTaskParameters.getAzureLoadBalancerDetail();
    if (isBlueGreen
        && (azureLoadBalancerDetail == null || isBlank(azureLoadBalancerDetail.getLoadBalancerName())
            || isBlank(azureLoadBalancerDetail.getStageBackendPool())
            || isBlank(azureLoadBalancerDetail.getProdBackendPool()))) {
      throw new InvalidRequestException(
          format("LoadBalancer pool details are empty or null, azureLoadBalancerDetail: %s",
              azureLoadBalancerDetail == null ? null : azureLoadBalancerDetail.toString()));
    }
  }

  @NotNull
  private String logAndGetNewVirtualMachineScaleSetName(AzureVMSSSetupTaskParameters setupTaskParameters,
      Integer newHarnessRevision, ExecutionLogCallback setupLogCallback) {
    String newVirtualMachineScaleSetName =
        getNewVirtualMachineScaleSetName(setupTaskParameters, newHarnessRevision, setupLogCallback);
    setupLogCallback.saveExecutionLog(
        format("New Virtual Machine Scale Set will be created with name: [%s]", newVirtualMachineScaleSetName), INFO,
        SUCCESS);
    return newVirtualMachineScaleSetName;
  }

  @NotNull
  private String getNewVirtualMachineScaleSetName(
      AzureVMSSSetupTaskParameters setupTaskParameters, Integer harnessRevision, ExecutionLogCallback logCallback) {
    String vmssNamePrefix = setupTaskParameters.getVmssNamePrefix();
    if (isBlank(vmssNamePrefix)) {
      String message = "Virtual Machine Scale Set prefix name can't be null or empty";
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message);
    }
    return AzureResourceUtility.getVMSSName(vmssNamePrefix, harnessRevision);
  }

  @NotNull
  private Integer logAndGetNewHarnessRevision(
      List<VirtualMachineScaleSet> harnessVMSSSortedByCreationTimeReverse, ExecutionLogCallback setupLogCallback) {
    Integer newHarnessRevision = getNewHarnessRevision(harnessVMSSSortedByCreationTimeReverse);
    setupLogCallback.saveExecutionLog(
        format("New revision of Virtual Machine Scale Set: [%s]", newHarnessRevision), INFO);
    return newHarnessRevision;
  }

  @NotNull
  private Integer getNewHarnessRevision(List<VirtualMachineScaleSet> harnessManagedVMSS) {
    int latestMaxRevision = getHarnessManagedScaleSetsLatestRevision(harnessManagedVMSS);
    return latestMaxRevision + 1;
  }

  @Nullable
  private VirtualMachineScaleSet logAndGetMostRecentActiveVMSS(
      List<VirtualMachineScaleSet> harnessVMSSSortedByCreationTimeReverse, ExecutionLogCallback setupLogCallback) {
    if (isEmpty(harnessVMSSSortedByCreationTimeReverse)) {
      return null;
    }
    setupLogCallback.saveExecutionLog(
        "Getting the most recent active Virtual Machine Scale Set with non zero capacity", INFO);
    VirtualMachineScaleSet mostRecentActiveVMSS = getMostRecentActiveVMSS(harnessVMSSSortedByCreationTimeReverse);
    setupLogCallback.saveExecutionLog(mostRecentActiveVMSS != null
            ? format("Found most recent active Virtual Machine Scale Set: [%s]", mostRecentActiveVMSS.name())
            : "Couldn't find most recent active Virtual Machine Scale Set with non zero capacity",
        INFO);
    return mostRecentActiveVMSS;
  }

  private VirtualMachineScaleSet getMostRecentActiveVMSS(List<VirtualMachineScaleSet> harnessManagedScaleSets) {
    return harnessManagedScaleSets.stream()
        .filter(Objects::nonNull)
        .filter(scaleSet -> scaleSet.capacity() > 0)
        .findFirst()
        .orElse(null);
  }

  @NotNull
  private List<VirtualMachineScaleSet> logAndGetHarnessMangedVMSSSortedByCreationTime(AzureConfig azureConfig,
      AzureVMSSSetupTaskParameters setupTaskParameters, ExecutionLogCallback setupLogCallback) {
    setupLogCallback.saveExecutionLog("Getting all Harness managed Virtual Machine Scale Sets", INFO);
    List<VirtualMachineScaleSet> harnessVMSSSortedByCreationTimeReverse =
        getHarnessMangedVMSSSortedByCreationTime(azureConfig, setupTaskParameters);
    setupLogCallback.saveExecutionLog(
        format("Found [%s] Harness managed Virtual Machine Scale Sets", harnessVMSSSortedByCreationTimeReverse.size()),
        INFO);
    return harnessVMSSSortedByCreationTimeReverse;
  }

  private List<VirtualMachineScaleSet> getHarnessMangedVMSSSortedByCreationTime(
      AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters) {
    List<VirtualMachineScaleSet> harnessManagedVMSS = listAllHarnessManagedVMSS(azureConfig, setupTaskParameters);
    return sortVMSSByCreationDate(harnessManagedVMSS);
  }

  @VisibleForTesting
  private List<VirtualMachineScaleSet> listAllHarnessManagedVMSS(
      AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters) {
    String subscriptionId = setupTaskParameters.getSubscriptionId();
    String resourceGroupName = setupTaskParameters.getResourceGroupName();
    String infraMappingId = setupTaskParameters.getInfraMappingId();

    List<VirtualMachineScaleSet> virtualMachineScaleSets =
        azureComputeClient.listVirtualMachineScaleSetsByResourceGroupName(
            azureConfig, subscriptionId, resourceGroupName);

    return virtualMachineScaleSets.stream().filter(isHarnessManagedScaleSet(infraMappingId)).collect(toList());
  }

  private Predicate<VirtualMachineScaleSet> isHarnessManagedScaleSet(final String infraMappingId) {
    return scaleSet
        -> scaleSet.tags().entrySet().stream().anyMatch(tagEntry -> isHarnessManagedTag(tagEntry, infraMappingId));
  }

  private boolean isHarnessManagedTag(Map.Entry<String, String> tagEntry, final String infraMappingId) {
    return tagEntry.getKey().equals(HARNESS_AUTOSCALING_GROUP_TAG_NAME)
        && tagEntry.getValue().startsWith(infraMappingId);
  }

  private List<VirtualMachineScaleSet> sortVMSSByCreationDate(List<VirtualMachineScaleSet> harnessManagedVMSS) {
    Comparator<VirtualMachineScaleSet> createdTimeComparator = (vmss1, vmss2) -> {
      Date createdTimeVMss1 =
          AzureResourceUtility.iso8601BasicStrToDate(vmss1.tags().get(VMSS_CREATED_TIME_STAMP_TAG_NAME));
      Date createdTimeVMss2 =
          AzureResourceUtility.iso8601BasicStrToDate(vmss2.tags().get(VMSS_CREATED_TIME_STAMP_TAG_NAME));
      return createdTimeVMss2.compareTo(createdTimeVMss1);
    };
    return harnessManagedVMSS.stream().sorted(createdTimeComparator).collect(Collectors.toList());
  }

  private void downsizeLatestOlderVersions(AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters,
      List<VirtualMachineScaleSet> sortedHarnessManagedVMSSs, VirtualMachineScaleSet mostRecentActiveVMSS) {
    if (isEmpty(sortedHarnessManagedVMSSs) || mostRecentActiveVMSS == null) {
      markCommandUnitLoggingAsComplete(setupTaskParameters);
      return;
    }

    String mostRecentActiveVMSSName = mostRecentActiveVMSS.name();
    int versionsToKeep = NUMBER_OF_LATEST_VERSIONS_TO_KEEP - 1;

    List<VirtualMachineScaleSet> scaleSetWithNonZeroCapacity =
        sortedHarnessManagedVMSSs.stream()
            .filter(Objects::nonNull)
            .filter(vmss -> !vmss.name().equals(mostRecentActiveVMSSName))
            .filter(vmms -> vmms.capacity() > 0)
            .limit(versionsToKeep)
            .collect(toList());

    if (isEmpty(scaleSetWithNonZeroCapacity)) {
      markCommandUnitLoggingAsComplete(setupTaskParameters);
      return;
    }

    String subscriptionId = setupTaskParameters.getSubscriptionId();
    String resourceGroupName = setupTaskParameters.getResourceGroupName();
    int autoScalingSteadyStateVMSSTimeout = setupTaskParameters.getAutoScalingSteadyStateVMSSTimeout();

    downsizeVMSSs(azureConfig, setupTaskParameters, scaleSetWithNonZeroCapacity, subscriptionId, resourceGroupName,
        autoScalingSteadyStateVMSSTimeout);
  }

  public void downsizeVMSSs(AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters,
      List<VirtualMachineScaleSet> vmssForDownsize, String subscriptionId, String resourceGroupName,
      int autoScalingSteadyStateTimeout) {
    ExecutionLogCallback downScaleSetsLogger = getLogCallBack(setupTaskParameters, DOWN_SCALE_COMMAND_UNIT);
    downScaleSetsLogger.saveExecutionLog(
        format("Found [%d] old virtual machine scale set with non-zero capacity", vmssForDownsize.size()));

    vmssForDownsize.forEach(vmss -> {
      String virtualMachineScaleSetName = vmss.name();
      logAndClearAutoScaleSettingOnTargetResourceId(
          azureConfig, subscriptionId, resourceGroupName, vmss, downScaleSetsLogger);
      updateVMSSCapacityAndWaitForSteadyState(azureConfig, setupTaskParameters, virtualMachineScaleSetName,
          subscriptionId, resourceGroupName, 0, autoScalingSteadyStateTimeout, DOWN_SCALE_COMMAND_UNIT,
          DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    });
  }

  public void logAndClearAutoScaleSettingOnTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, VirtualMachineScaleSet vmss, ExecutionLogCallback downScaleSetsLogger) {
    String virtualMachineScaleSetName = vmss.name();
    downScaleSetsLogger.saveExecutionLog(format("Clear autoscale settings: [%s]", virtualMachineScaleSetName), INFO);
    azureAutoScaleSettingsClient.clearAutoScaleSettingOnTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, vmss.id());
    downScaleSetsLogger.saveExecutionLog("Autoscale settings deleted successfully ", INFO);
  }

  private void deleteVMSSsOlderThenLatestVersionsToKeep(AzureConfig azureConfig,
      List<VirtualMachineScaleSet> sortedHarnessManagedVMSSs, VirtualMachineScaleSet mostRecentActiveVMSS,
      AzureVMSSSetupTaskParameters azureVMSSTaskParameters) {
    if (isEmpty(sortedHarnessManagedVMSSs) || mostRecentActiveVMSS == null) {
      createAndFinishEmptyExecutionLog(
          azureVMSSTaskParameters, DELETE_OLD_VIRTUAL_MACHINE_SCALE_SETS_COMMAND_UNIT, NO_VMSS_FOR_DELETION);
      return;
    }

    String mostRecentActiveVMSSName = mostRecentActiveVMSS.name();
    int versionsToKeep = NUMBER_OF_LATEST_VERSIONS_TO_KEEP - 1;

    List<VirtualMachineScaleSet> listOfVMSSsForDelete =
        sortedHarnessManagedVMSSs.stream()
            .filter(Objects::nonNull)
            .filter(vmss -> !vmss.name().equals(mostRecentActiveVMSSName))
            .skip(versionsToKeep)
            .collect(toList());
    deleteVMSSs(azureConfig, listOfVMSSsForDelete, azureVMSSTaskParameters);
  }

  public void deleteVMSSs(AzureConfig azureConfig, List<VirtualMachineScaleSet> scaleSetsForDelete,
      AzureVMSSSetupTaskParameters azureVMSSTaskParameters) {
    if (scaleSetsForDelete.isEmpty()) {
      createAndFinishEmptyExecutionLog(
          azureVMSSTaskParameters, DELETE_OLD_VIRTUAL_MACHINE_SCALE_SETS_COMMAND_UNIT, NO_VMSS_FOR_DELETION);
      return;
    }
    ExecutionLogCallback executionLogCallback =
        getLogCallBack(azureVMSSTaskParameters, DELETE_OLD_VIRTUAL_MACHINE_SCALE_SETS_COMMAND_UNIT);
    List<String> vmssIds = scaleSetsForDelete.stream().map(VirtualMachineScaleSet::id).collect(toList());
    String subscriptionId = azureVMSSTaskParameters.getSubscriptionId();

    StringJoiner virtualMachineScaleSetNamesJoiner = new StringJoiner(",", "[", "]");
    scaleSetsForDelete.forEach(vmss -> virtualMachineScaleSetNamesJoiner.add(vmss.name()));

    executionLogCallback.saveExecutionLog(
        color("# Deleting existing Virtual Machine Scale Sets: " + virtualMachineScaleSetNamesJoiner.toString(), Yellow,
            Bold));
    azureComputeClient.bulkDeleteVirtualMachineScaleSets(azureConfig, subscriptionId, vmssIds);
    executionLogCallback.saveExecutionLog("Successfully deleted Virtual Scale Sets", INFO, SUCCESS);
  }

  private int getHarnessManagedScaleSetsLatestRevision(List<VirtualMachineScaleSet> harnessManagedScaleSets) {
    return harnessManagedScaleSets.stream().mapToInt(this::getScaleSetLatestRevision).max().orElse(0);
  }

  private int getScaleSetLatestRevision(VirtualMachineScaleSet scaleSet) {
    return scaleSet.tags()
        .entrySet()
        .stream()
        .filter(tagEntry -> tagEntry.getKey().equals(HARNESS_AUTOSCALING_GROUP_TAG_NAME))
        .mapToInt(tagEntry -> getRevisionFromTag(tagEntry.getValue()))
        .max()
        .orElse(0);
  }

  private void createVirtualMachineScaleSet(AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters,
      Integer newHarnessRevision, String newVirtualMachineScaleSetName, ExecutionLogCallback logCallback) {
    String subscriptionId = setupTaskParameters.getSubscriptionId();
    String resourceGroupName = setupTaskParameters.getResourceGroupName();
    String baseVirtualMachineScaleSetName = setupTaskParameters.getBaseVMSSName();
    AzureMachineImageArtifactDTO imageArtifactDTO = setupTaskParameters.getImageArtifactDTO();

    AzureMachineImageArtifact imageArtifact = getAzureMachineImageArtifact(azureConfig, imageArtifactDTO, logCallback);
    AzureVMSSTagsData azureVMSSTagsData = getAzureVMSSTagsData(setupTaskParameters, newHarnessRevision);
    AzureUserAuthVMInstanceData azureUserAuthVMInstanceData = buildUserAuthVMInstanceData(setupTaskParameters);

    // Get base VMSS based on provided scale set name, subscriptionId, resourceGroupName provided by task parameters
    logCallback.saveExecutionLog(
        format("Getting base Virtual Machine Scale Set [%s]", baseVirtualMachineScaleSetName), INFO);
    VirtualMachineScaleSet baseVirtualMachineScaleSet = getBaseVirtualMachineScaleSet(
        azureConfig, subscriptionId, resourceGroupName, baseVirtualMachineScaleSetName, logCallback);

    // Create new VMSS based on Base VMSS configuration
    logCallback.saveExecutionLog(
        format("Creating new Virtual Machine Scale Set: [%s]", newVirtualMachineScaleSetName), INFO);
    azureComputeClient.createVirtualMachineScaleSet(azureConfig, subscriptionId, baseVirtualMachineScaleSet,
        newVirtualMachineScaleSetName, azureUserAuthVMInstanceData, imageArtifact, azureVMSSTagsData);

    if (setupTaskParameters.isBlueGreen()) {
      attachNewCreatedVMSSToStageBackendPool(
          azureConfig, setupTaskParameters, newVirtualMachineScaleSetName, logCallback);
    }
    logCallback.saveExecutionLog(
        format("New Virtual Machine Scale Set: [%s] created successfully", newVirtualMachineScaleSetName), INFO,
        SUCCESS);
  }

  @VisibleForTesting
  AzureMachineImageArtifact getAzureMachineImageArtifact(AzureConfig azureConfig,
      AzureMachineImageArtifactDTO azureMachineImageArtifactDTO, ExecutionLogCallback logCallback) {
    GalleryImageDefinitionDTO imageDefinition = azureMachineImageArtifactDTO.getImageDefinition();
    String subscriptionId = imageDefinition.getSubscriptionId();
    String resourceGroupName = imageDefinition.getResourceGroupName();
    String imageDefinitionName = imageDefinition.getDefinitionName();
    String imageGalleryName = imageDefinition.getGalleryName();
    String imageVersion = imageDefinition.getVersion();

    String galleryImageId = String.format(GALLERY_IMAGE_ID_PATTERN, subscriptionId, resourceGroupName, imageGalleryName,
        imageDefinitionName, imageVersion);

    logCallback.saveExecutionLog(format("Start getting gallery image references id [%s]", galleryImageId), INFO);
    Optional<GalleryImage> galleryImageOp = azureComputeClient.getGalleryImage(
        azureConfig, subscriptionId, resourceGroupName, imageGalleryName, imageDefinitionName);
    GalleryImage galleryImage = galleryImageOp.orElseThrow(() -> {
      String message = format(
          "Image reference cannot be found, galleryImageId: %s, imageDefinitionName: %s, subscriptionId: %s, resourceGroupName: %s, imageVersion: %s",
          imageGalleryName, imageDefinitionName, subscriptionId, resourceGroupName, imageVersion);
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      return new InvalidRequestException(message);
    });

    String osState = galleryImage.osState().toString();
    GalleryImageIdentifier identifier = galleryImage.identifier();
    String publisher = identifier.publisher();
    String offer = identifier.offer();
    String sku = identifier.sku();

    logCallback.saveExecutionLog(
        format("Using gallery image id [%s], publisher [%s], offer [%s],sku [%s], osState [%s]", galleryImageId,
            publisher, offer, sku, osState),
        INFO);
    return AzureMachineImageArtifact.builder()
        .imageType(ImageType.valueOf(azureMachineImageArtifactDTO.getImageType().name()))
        .osType(OSType.valueOf(azureMachineImageArtifactDTO.getImageOSType().name()))
        .imageReference(MachineImageReference.builder()
                            .id(galleryImageId)
                            .publisher(publisher)
                            .offer(offer)
                            .sku(sku)
                            .osState(OsState.fromString(osState))
                            .version(imageVersion)
                            .build())
        .build();
  }

  private AzureVMSSTagsData getAzureVMSSTagsData(
      AzureVMSSSetupTaskParameters setupTaskParameters, Integer newHarnessRevision) {
    String infraMappingId = setupTaskParameters.getInfraMappingId();
    boolean isBlueGreen = setupTaskParameters.isBlueGreen();
    return AzureVMSSTagsData.builder()
        .harnessRevision(newHarnessRevision)
        .infraMappingId(infraMappingId)
        .isBlueGreen(isBlueGreen)
        .build();
  }

  private AzureUserAuthVMInstanceData buildUserAuthVMInstanceData(AzureVMSSSetupTaskParameters setupTaskParameters) {
    AzureVMAuthDTO azureVmAuthDTO = setupTaskParameters.getAzureVmAuthDTO();
    String vmssAuthType = azureVmAuthDTO.getAzureVmAuthType().name();
    String username = azureVmAuthDTO.getUserName();
    String decryptedValue = new String(azureVmAuthDTO.getSecretRef().getDecryptedValue());
    String password = vmssAuthType.equals(VMSS_AUTH_TYPE_DEFAULT) ? decryptedValue : StringUtils.EMPTY;
    String sshPublicKey = vmssAuthType.equals(VMSS_AUTH_TYPE_SSH_PUBLIC_KEY) ? decryptedValue : StringUtils.EMPTY;

    return AzureUserAuthVMInstanceData.builder()
        .vmssAuthType(vmssAuthType)
        .userName(username)
        .password(password)
        .sshPublicKey(sshPublicKey)
        .build();
  }

  private VirtualMachineScaleSet getBaseVirtualMachineScaleSet(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String baseVirtualMachineScaleSetName, ExecutionLogCallback logCallback) {
    Optional<VirtualMachineScaleSet> baseVirtualMachineScaleSetOp = azureComputeClient.getVirtualMachineScaleSetByName(
        azureConfig, subscriptionId, resourceGroupName, baseVirtualMachineScaleSetName);

    if (!baseVirtualMachineScaleSetOp.isPresent()) {
      String errorMessage = format(
          "Couldn't find reference baseVirtualMachineScaleSetName: [%s] in resourceGroupName: [%s] and subscriptionId: [%s]",
          baseVirtualMachineScaleSetName, resourceGroupName, subscriptionId);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    return baseVirtualMachineScaleSetOp.get();
  }

  @VisibleForTesting
  void attachNewCreatedVMSSToStageBackendPool(AzureConfig azureConfig, AzureVMSSSetupTaskParameters setupTaskParameters,
      String newVirtualMachineScaleSetName, ExecutionLogCallback logCallback) {
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail = setupTaskParameters.getAzureLoadBalancerDetail();
    String stageBackendPool = azureLoadBalancerDetail.getStageBackendPool();
    String loadBalancerName = azureLoadBalancerDetail.getLoadBalancerName();
    String subscriptionId = setupTaskParameters.getSubscriptionId();
    String resourceGroupName = setupTaskParameters.getResourceGroupName();

    logCallback.saveExecutionLog(
        format("Sending request to detach Virtual Machine Scale Set:[%s] from exiting backend pools",
            newVirtualMachineScaleSetName));
    azureComputeClient.detachVMSSFromBackendPools(
        azureConfig, subscriptionId, resourceGroupName, newVirtualMachineScaleSetName, "*");

    LoadBalancer loadBalancer = getLoadBalancer(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);

    logCallback.saveExecutionLog(format(
        "Sending request to attach Virtual Machine Scale Set:[%s] to load balancer: [%s], stage backend pool:[%s]",
        newVirtualMachineScaleSetName, loadBalancerName, stageBackendPool));
    azureComputeClient.attachVMSSToBackendPools(
        azureConfig, loadBalancer, subscriptionId, resourceGroupName, newVirtualMachineScaleSetName, stageBackendPool);
    logCallback.saveExecutionLog("Successful attached Virtual Machine Scale Set to stage backend pool");
  }

  @VisibleForTesting
  LoadBalancer getLoadBalancer(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String loadBalancerName) {
    Optional<LoadBalancer> loadBalancerOp =
        azureNetworkClient.getLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName);
    return loadBalancerOp.orElseThrow(
        ()
            -> new InvalidRequestException(format(
                "Not found load balancer with name: %s, resourceGroupName: %s", loadBalancerName, resourceGroupName)));
  }

  private AzureVMSSSetupTaskResponse buildAzureVMSSSetupTaskResponse(AzureConfig azureConfig, Integer harnessRevision,
      String newVirtualMachineScaleSetName, VirtualMachineScaleSet mostRecentActiveVMSS,
      AzureVMSSSetupTaskParameters setupTaskParameters) {
    boolean isBlueGreen = setupTaskParameters.isBlueGreen();
    boolean isUseCurrentRunningCount = setupTaskParameters.isUseCurrentRunningCount();
    String baseVirtualMachineScaleSetName = setupTaskParameters.getBaseVMSSName();
    String subscriptionId = setupTaskParameters.getSubscriptionId();
    String resourceGroupName = setupTaskParameters.getResourceGroupName();

    AzureVMSSAutoScaleSettingsData azureVMSSInstanceLimits = azureAutoScaleHelper.getVMSSAutoScaleInstanceLimits(
        azureConfig, setupTaskParameters, mostRecentActiveVMSS, isUseCurrentRunningCount, SETUP_COMMAND_UNIT);

    String mostRecentActiveVMSSName = mostRecentActiveVMSS == null ? StringUtils.EMPTY : mostRecentActiveVMSS.name();

    List<String> baseVMSSScalingPolicyJSONs = azureAutoScaleHelper.getVMSSAutoScaleSettingsJSONs(
        azureConfig, subscriptionId, resourceGroupName, baseVirtualMachineScaleSetName);
    AzureVMSSPreDeploymentData azureVMSSPreDeploymentData =
        populatePreDeploymentData(azureConfig, subscriptionId, mostRecentActiveVMSS);

    return AzureVMSSSetupTaskResponse.builder()
        .lastDeployedVMSSName(mostRecentActiveVMSSName)
        .harnessRevision(harnessRevision)
        .minInstances(azureVMSSInstanceLimits.getMinInstances())
        .maxInstances(azureVMSSInstanceLimits.getMaxInstances())
        .desiredInstances(azureVMSSInstanceLimits.getDesiredInstances())
        .blueGreen(isBlueGreen)
        .newVirtualMachineScaleSetName(newVirtualMachineScaleSetName)
        .baseVMSSScalingPolicyJSONs(baseVMSSScalingPolicyJSONs)
        .preDeploymentData(azureVMSSPreDeploymentData)
        .build();
  }

  private AzureVMSSPreDeploymentData populatePreDeploymentData(
      AzureConfig azureConfig, String subscriptionId, VirtualMachineScaleSet mostRecentActiveVMSS) {
    boolean isMostRecentVMSSAvailable = mostRecentActiveVMSS != null;

    List<String> autoScalingPoliciesJson =
        azureAutoScaleHelper.getVMSSAutoScaleSettingsJSONs(azureConfig, subscriptionId, mostRecentActiveVMSS);

    return AzureVMSSPreDeploymentData.builder()
        .minCapacity(0)
        .desiredCapacity(isMostRecentVMSSAvailable ? mostRecentActiveVMSS.capacity() : 0)
        .scalingPolicyJSON(isMostRecentVMSSAvailable ? autoScalingPoliciesJson : emptyList())
        .oldVmssName(isMostRecentVMSSAvailable ? mostRecentActiveVMSS.name() : StringUtils.EMPTY)
        .build();
  }

  private void markCommandUnitLoggingAsComplete(AzureVMSSSetupTaskParameters setupTaskParameters) {
    createAndFinishEmptyExecutionLog(setupTaskParameters, DOWN_SCALE_COMMAND_UNIT, NO_VMSS_FOR_DOWN_SIZING);
    createAndFinishEmptyExecutionLog(
        setupTaskParameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, NO_VMSS_FOR_DOWN_SIZING);
  }
}
