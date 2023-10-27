/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model.image;

import static io.harness.azure.model.AzureMachineImageArtifact.OSType.LINUX;
import static io.harness.azure.model.AzureMachineImageArtifact.OSType.WINDOWS;

import io.harness.azure.model.AzureMachineImageArtifact;
import io.harness.azure.model.AzureMachineImageArtifact.MachineImageReference;
import io.harness.azure.model.AzureMachineImageArtifact.MachineImageReference.OsState;
import io.harness.azure.model.AzureMachineImageArtifact.OSType;

import com.azure.resourcemanager.compute.fluent.models.VirtualMachineScaleSetInner;
import com.azure.resourcemanager.compute.models.ImageReference;
import com.azure.resourcemanager.compute.models.LinuxConfiguration;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetOSProfile;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetStorageProfile;
import com.azure.resourcemanager.compute.models.WindowsConfiguration;

public class SharedGalleryImage extends AzureMachineImage {
  public SharedGalleryImage(AzureMachineImageArtifact image) {
    super(image);
  }

  @Override
  protected void updateVirtualMachineScaleSetOSProfile(VirtualMachineScaleSetInner inner) {
    OsState osState = image.getImageReference().getOsState();
    if (OsState.SPECIALIZED == osState) {
      // specialized images should not have an osProfile associated with them
      inner.virtualMachineProfile().withOsProfile(null);
      return;
    }
    // only applied on generalized images
    VirtualMachineScaleSetOSProfile osProfile = inner.virtualMachineProfile().osProfile();
    OSType osType = image.getOsType();
    osProfile.withRequireGuestProvisionSignal(null);

    if (LINUX == osType) {
      osProfile.withLinuxConfiguration(new LinuxConfiguration());
      osProfile.linuxConfiguration().withDisablePasswordAuthentication(false);
      osProfile.linuxConfiguration().withProvisionVMAgent(true);
      osProfile.withWindowsConfiguration(null);
    } else if (WINDOWS == osType) {
      osProfile.withWindowsConfiguration(new WindowsConfiguration());
      osProfile.windowsConfiguration().withProvisionVMAgent(true);
      osProfile.windowsConfiguration().withEnableAutomaticUpdates(true);
      osProfile.withLinuxConfiguration(null);
    }
  }

  @Override
  protected void updateVirtualMachineScaleSetStorageProfile(VirtualMachineScaleSetInner inner) {
    VirtualMachineScaleSetStorageProfile storageProfile = inner.virtualMachineProfile().storageProfile();
    storageProfile.withImageReference(getManagedImageReference());
  }

  private ImageReference getManagedImageReference() {
    MachineImageReference artifactImageReference = image.getImageReference();
    ImageReference imageReference = new ImageReference();
    imageReference.withId(artifactImageReference.getId());
    return imageReference;
  }
}
