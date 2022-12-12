/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservices;

import io.harness.beans.EmbeddedUser;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.persistence.artifact.Artifact;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AzureAppServiceStateData {
  private Artifact artifact;
  private Application application;
  private Service service;
  private String serviceId;
  private Environment environment;
  private AzureConfig azureConfig;
  private List<EncryptedDataDetail> azureEncryptedDataDetails;
  private AzureWebAppInfrastructureMapping infrastructureMapping;
  private String resourceGroup;
  private String subscriptionId;
  private EmbeddedUser currentUser;
}
