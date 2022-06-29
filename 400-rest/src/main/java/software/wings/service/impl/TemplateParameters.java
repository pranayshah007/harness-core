/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.delegate.beans.K8sPermissionType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TemplateParameters {
  private final String delegateXmx;
  private final String accountId;
  private final String version;
  private final String managerHost;
  private final String verificationHost;
  private final String delegateName;
  private final String delegateProfile;
  private final String delegateGroupId;
  private final String delegateType;
  private final boolean ceEnabled;
  private final boolean ciEnabled;
  private final String logStreamingServiceBaseUrl;
  private final String delegateDescription;
  private final String delegateSize;
  private final int delegateReplicas;
  private final int delegateRam;
  private final double delegateCpu;
  private final int delegateRequestsRam;
  private final double delegateRequestsCpu;
  private final String delegateNamespace;
  private final String delegateTokenName;
  private final String delegateTags;
  private final K8sPermissionType k8sPermissionsType;
  private final String delegateGroupName;
  private final boolean watcher;
  private final boolean mtlsEnabled;
}
