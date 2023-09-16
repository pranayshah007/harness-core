/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.azure;

import software.wings.beans.AzureResourceReference;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AzureVmInstance extends AzureResourceReference {
  @Builder
  private AzureVmInstance(String name, String resourceGroup, String subscriptionId, String type, String id,
      String publicIpAddress, String publicDnsName) {
    super(name, resourceGroup, subscriptionId, type, id);
    this.publicIpAddress = publicIpAddress;
    this.publicDnsName = publicDnsName;
  }

  private String publicIpAddress;
  private String publicDnsName;
}
