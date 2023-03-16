/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.helm.HelmConstants.HELM_PATH_PLACEHOLDER;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.k8s.kubectl.Utils.executeCommand;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.capability.HelmInstallationParameters;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;

import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class HelmInstallationCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  private static final String CLIENT_ONLY_COMMAND_FLAG = "--client";

  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    HelmInstallationCapability capability = (HelmInstallationCapability) delegateCapability;
    String helmPath = k8sGlobalConfigService.getHelmPath(capability.getVersion());
    if (isEmpty(helmPath)) {
      return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
    }
    String helmVersionCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.VERSION, capability.getVersion())
            .replace(HELM_PATH_PLACEHOLDER, encloseWithQuotesIfNeeded(helmPath))
            .replace("${COMMAND_FLAGS}",
                HelmVersion.V2 == capability.getVersion() ? CLIENT_ONLY_COMMAND_FLAG : StringUtils.EMPTY)
            .replace("KUBECONFIG=${KUBECONFIG_PATH} ", StringUtils.EMPTY);
    return CapabilityResponse.builder()
        .validated(executeCommand(helmVersionCommand, 2))
        .delegateCapability(capability)
        .build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.HELM_INSTALLATION_PARAMETERS) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }

    HelmVersion helmVersion = convertHelmVersion(parameters.getHelmInstallationParameters().getHelmVersion());
    String helmPath = k8sGlobalConfigService.getHelmPath(helmVersion);
    if (isEmpty(helmPath)) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    String helmVersionCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.VERSION, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, encloseWithQuotesIfNeeded(helmPath))
            .replace("${COMMAND_FLAGS}", HelmVersion.V2 == helmVersion ? CLIENT_ONLY_COMMAND_FLAG : StringUtils.EMPTY)
            .replace("KUBECONFIG=${KUBECONFIG_PATH} ", StringUtils.EMPTY);
    return builder
        .permissionResult(executeCommand(helmVersionCommand, 2) ? PermissionResult.ALLOWED : PermissionResult.DENIED)
        .build();
  }

  private static HelmVersion convertHelmVersion(HelmInstallationParameters.HelmVersion protoVersion) {
    switch (protoVersion) {
      case V2:
        return HelmVersion.V2;
      case V3:
        return HelmVersion.V3;
      case V380:
        return HelmVersion.V380;
      default:
        throw new RuntimeException("Helm version not found");
    }
  }
}
