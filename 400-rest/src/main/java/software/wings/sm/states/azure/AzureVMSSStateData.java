/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.persistence.artifact.Artifact;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AzureVMSSStateData {
  private Application application;
  private Service service;
  private String serviceId;
  private Artifact artifact;
  private AzureConfig azureConfig;
  private Environment environment;
  private AzureVMSSInfrastructureMapping infrastructureMapping;
  private List<EncryptedDataDetail> azureEncryptedDataDetails;
}
