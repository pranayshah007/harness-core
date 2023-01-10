/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateInstallationCommandService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.exception.DelegateInstallationCommandNotSupportedException;
import io.harness.exception.DelegateTokenNotFoundException;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;

@Slf4j
public class DelegateInstallationCommandServiceImpl implements DelegateInstallationCommandService {
  private static final String DEFAULT_TOKEN_NAME = "default_token";
  private final DelegateNgTokenService delegateNgTokenService;
  private final DelegateVersionService delegateVersionService;

  private static final String DOCKER_COMMAND = "docker run -d --name=docker-delegate --cpus=0.5 --memory=2g "
      + "-e DELEGATE_NAME=docker-delegate "
      + "-e NEXT_GEN=\"true\" "
      + "-e DELEGATE_TYPE=\"DOCKER\" "
      + "-e ACCOUNT_ID=${account_id} "
      + "-e DELEGATE_TOKEN=${token} "
      + "-e MANAGER_HOST_AND_PORT=${manager_url} ${image}";

  private static final String HELM_COMMAND =
      "helm upgrade -i helm-delegate --namespace harness-delegate-ng --create-namespace "
      + "harness/harness-delegate-ng "
      + "--set delegateName=helm-delegate "
      + "--set accountId=${account_id} "
      + "--set delegateToken=${token} "
      + "--set managerEndpoint=${manager_url} "
      + "--set delegateDockerImage=${image} "
      + "--set replicas=1 --set upgrader.enabled=false";

  private static final String TERRAFORM_COMMAND = "terraform apply "
      + "-var delegate_name=terraform-delegate "
      + "-var account_id=${account_id} "
      + "-var delegate_token=${token} "
      + "-var manager_endpoint=${manager_url} "
      + "-var delegate_image=${image} "
      + "-var replicas=1 "
      + "-var upgrader_enabled=false";

  @Inject
  public DelegateInstallationCommandServiceImpl(
      DelegateNgTokenService delegateNgTokenService, DelegateVersionService delegateVersionService) {
    this.delegateNgTokenService = delegateNgTokenService;
    this.delegateVersionService = delegateVersionService;
  }

  @Override
  public String getCommand(@NotBlank String commandType, @NotBlank String managerUrl, @NotBlank String accountId) {
    final Map<String, Boolean> activeStatus =
        delegateNgTokenService.isDelegateTokenActive(accountId, List.of(DEFAULT_TOKEN_NAME));
    if (Objects.isNull(activeStatus) || activeStatus.isEmpty()) {
      final String errorMsg = String.format("default token not found for account %s.", accountId);
      log.error(errorMsg);
      throw new DelegateTokenNotFoundException(errorMsg);
    }
    final String image = delegateVersionService.getImmutableDelegateImageTag(accountId);
    final String tokenValue = activeStatus.get(DEFAULT_TOKEN_NAME)
        ? delegateNgTokenService.getDelegateTokenValue(accountId, DEFAULT_TOKEN_NAME)
        : "<PUT YOUR TOKEN>";

    final Map<String, String> values = ImmutableMap.<String, String>builder()
                                           .put("account_id", accountId)
                                           .put("token", tokenValue)
                                           .put("manager_url", managerUrl)
                                           .put("image", image)
                                           .build();

    final StringSubstitutor substitutor = new StringSubstitutor(values);

    switch (commandType) {
      case "DOCKER":
        return substitutor.replace(DOCKER_COMMAND);
      case "HELM":
        return substitutor.replace(HELM_COMMAND);
      case "TERRAFORM":
        return substitutor.replace(TERRAFORM_COMMAND);
      default:
        final String error = String.format("Unsupported installation command type %s.", commandType);
        log.error(error);
        throw new DelegateInstallationCommandNotSupportedException(error);
    }
  }
}
