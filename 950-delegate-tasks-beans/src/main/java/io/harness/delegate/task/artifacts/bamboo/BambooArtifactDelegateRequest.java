/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;
import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.BAMBOO;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Value
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class BambooArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  String buildRegex;
  String authMechanism;
  List<String> delegateSelectors;
  String connectorRef;
  Map<String, String> parameterEntries;
  List<EncryptedDataDetail> encryptedDataDetails;
  String planKey;
  List<String> artifactPaths;
  BambooConnectorDTO bambooConnectorDTO;
  ArtifactSourceType sourceType;

  private Map<String, String> metadata;
  private String buildNumber;
  private String buildDisplayName;
  private String buildFullDisplayName;
  private String description;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    populateDelegateSelectorCapability(capabilities, bambooConnectorDTO.getDelegateSelectors());
    capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        bambooConnectorDTO.getBambooUrl().endsWith("/") ? bambooConnectorDTO.getBambooUrl()
                                                        : bambooConnectorDTO.getBambooUrl().concat("/"),
        maskingEvaluator));
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return BAMBOO;
  }
}
