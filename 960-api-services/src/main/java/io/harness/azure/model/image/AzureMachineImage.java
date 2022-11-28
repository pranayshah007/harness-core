/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model.image;

import io.harness.azure.model.AzureMachineImageArtifact;

import com.azure.resourcemanager.compute.fluent.models.VirtualMachineScaleSetInner;

public abstract class AzureMachineImage {
  protected AzureMachineImageArtifact image;

  public AzureMachineImage(AzureMachineImageArtifact image) {
    this.image = image;
  }

  public void updateVMSSInner(VirtualMachineScaleSetInner inner) {
    updateVirtualMachineScaleSetOSProfile(inner);
    updateVirtualMachineScaleSetStorageProfile(inner);
  }

  protected abstract void updateVirtualMachineScaleSetOSProfile(VirtualMachineScaleSetInner inner);

  protected abstract void updateVirtualMachineScaleSetStorageProfile(VirtualMachineScaleSetInner inner);
}
