/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateSize.LAPTOP;
import static io.harness.delegate.beans.DelegateSize.LARGE;
import static io.harness.delegate.beans.DelegateSize.MEDIUM;
import static io.harness.delegate.beans.DelegateSize.SMALL;
import static io.harness.delegate.beans.DelegateType.DOCKER;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.K8sPermissionType.CLUSTER_ADMIN;
import static io.harness.delegate.beans.K8sPermissionType.CLUSTER_VIEWER;
import static io.harness.delegate.beans.K8sPermissionType.NAMESPACE_ADMIN;

import static java.lang.String.format;

import io.harness.delegate.DelegateDownloadResponse;
import io.harness.delegate.beans.DelegateDownloadRequest;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSetupDetails.DelegateSetupDetailsBuilder;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.beans.K8sConfigDetails;
import io.harness.delegate.beans.K8sPermissionType;
import io.harness.delegate.service.intfc.DelegateDownloadService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateDownloadServiceImpl implements DelegateDownloadService {
  private final DelegateService delegateService;
  private final DelegateNgTokenService delegateNgTokenService;

  @Inject
  public DelegateDownloadServiceImpl(DelegateService delegateService, DelegateNgTokenService delegateNgTokenService) {
    this.delegateService = delegateService;
    this.delegateNgTokenService = delegateNgTokenService;
  }

  @Override
  public DelegateDownloadResponse downloadKubernetesDelegate(String accountId, String orgIdentifier,
      String projectIdentifier, DelegateDownloadRequest delegateDownloadRequest, String managerHost,
      String verificationServiceUrl) {
    try {
      DelegateSetupDetails delegateSetupDetails = buildProperDelegateSetupDetails(
          accountId, orgIdentifier, projectIdentifier, delegateDownloadRequest, KUBERNETES);
      File delegateFile = delegateService.generateKubernetesYaml(
          accountId, delegateSetupDetails, managerHost, verificationServiceUrl, MediaType.TEXT_PLAIN_TYPE);
      return new DelegateDownloadResponse(
          null, new String(Files.readAllBytes(Paths.get(delegateFile.getAbsolutePath()))));
    } catch (Exception e) {
      log.error("Error occurred during downloading ng kubernetes delegate.", e);
      return new DelegateDownloadResponse(ExceptionUtils.getMessage(e), null);
    }
  }

  public DelegateDownloadResponse downloadDockerDelegate(String accountId, String orgIdentifier,
      String projectIdentifier, DelegateDownloadRequest delegateDownloadRequest, String managerHost,
      String verificationServiceUrl) {
    try {
      DelegateSetupDetails delegateSetupDetails =
          buildProperDelegateSetupDetails(accountId, orgIdentifier, projectIdentifier, delegateDownloadRequest, DOCKER);
      File delegateFile =
          delegateService.downloadNgDocker(managerHost, verificationServiceUrl, accountId, delegateSetupDetails);
      return new DelegateDownloadResponse(
          null, new String(Files.readAllBytes(Paths.get(delegateFile.getAbsolutePath()))));
    } catch (Exception e) {
      log.error("Error occurred during downloading ng docker delegate.", e);
      return new DelegateDownloadResponse(ExceptionUtils.getMessage(e), null);
    }
  }

  private DelegateSetupDetails buildProperDelegateSetupDetails(String accountId, String orgIdentifier,
      String projectIdentifier, DelegateDownloadRequest delegateDownloadRequest, String delegateType) {
    delegateService.checkUniquenessOfDelegateName(accountId, delegateDownloadRequest.getName(), true);

    DelegateSetupDetailsBuilder delegateSetupDetails = DelegateSetupDetails.builder()
                                                           .name(delegateDownloadRequest.getName())
                                                           .orgIdentifier(orgIdentifier)
                                                           .projectIdentifier(projectIdentifier)
                                                           .description(delegateDownloadRequest.getDescription())
                                                           .tags(delegateDownloadRequest.getTags())
                                                           .delegateType(delegateType);

    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);

    String delegateTokenName = delegateDownloadRequest.getTokenName();
    if (isEmpty(delegateTokenName)) {
      delegateTokenName = delegateNgTokenService.getDefaultTokenName(owner);
    }
    DelegateTokenDetails delegateTokenDetails = delegateNgTokenService.getDelegateToken(accountId, delegateTokenName);
    if (delegateTokenDetails == null || DelegateTokenStatus.REVOKED.equals(delegateTokenDetails.getStatus())) {
      throw new InvalidRequestException(format(
          "Can not use %s delegate token. This token does not exists or has been revoked. Please specify a valid delegate token.",
          delegateTokenName));
    }
    delegateSetupDetails.tokenName(delegateTokenName);

    // properties specific for k8s delegate
    if (delegateType.equals(KUBERNETES)) {
      if (!Arrays.asList(LAPTOP, SMALL, MEDIUM, LARGE).contains(delegateDownloadRequest.getSize())) {
        delegateSetupDetails.size(LAPTOP);
      }

      K8sPermissionType clusterPermissionType = delegateDownloadRequest.getClusterPermissionType();
      if (isNotEmpty(delegateDownloadRequest.getCustomClusterNamespace())) {
        delegateSetupDetails.k8sConfigDetails(K8sConfigDetails.builder()
                                                  .k8sPermissionType(NAMESPACE_ADMIN)
                                                  .namespace(delegateDownloadRequest.getCustomClusterNamespace())
                                                  .build());
      } else if (!CLUSTER_ADMIN.equals(clusterPermissionType) && !CLUSTER_VIEWER.equals(clusterPermissionType)) {
        delegateSetupDetails.k8sConfigDetails(K8sConfigDetails.builder().k8sPermissionType(CLUSTER_ADMIN).build());
      }
    }
    return delegateSetupDetails.build();
  }
}
