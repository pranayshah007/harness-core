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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;

@Slf4j
public class DelegateInstallationCommandServiceImpl implements DelegateInstallationCommandService {
  private static final String DEFAULT_TOKEN_NAME = "default_token";
  private final DelegateNgTokenService delegateNgTokenService;
  private final DelegateVersionService delegateVersionService;
  private static final String TERRAFORM_TEMPLATE_FLE = "/delegatetemplates/delegate-terraform-example-module.ftl";

  private static final String DOCKER_COMMAND = "docker run --cpus=1 --memory=2g \\\n"
      + "  -e DELEGATE_NAME=docker-delegate \\\n"
      + "  -e NEXT_GEN=\"true\" \\\n"
      + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
      + "  -e ACCOUNT_ID=${account_id} \\\n"
      + "  -e DELEGATE_TOKEN=${token} \\\n"
      + "  -e MANAGER_HOST_AND_PORT=${manager_url} ${image}";

  private static final String HELM_COMMAND =
      "helm upgrade -i helm-delegate --namespace harness-delegate-ng --create-namespace \\\n"
      + "  harness-delegate/harness-delegate-ng \\\n"
      + "  --set delegateName=helm-delegate \\\n"
      + "  --set accountId=${account_id} \\\n"
      + "  --set delegateToken=${token} \\\n"
      + "  --set managerEndpoint=${manager_url} \\\n"
      + "  --set delegateDockerImage=${image} \\\n"
      + "  --set replicas=1 --set upgrader.enabled=false";

  private static final String TERRAFORM_COMMAND = "terraform apply \\\n"
      + "-var delegate_name=terraform-delegate \\\n"
      + "-var account_id=${account_id} \\\n"
      + "-var delegate_token=${token} \\\n"
      + "-var manager_endpoint=${manager_url} \\\n"
      + "-var delegate_image=${image} \\\n"
      + "-var replicas=1 \\\n"
      + "-var upgrader_enabled=false";

  @Inject
  public DelegateInstallationCommandServiceImpl(
      DelegateNgTokenService delegateNgTokenService, DelegateVersionService delegateVersionService) {
    this.delegateNgTokenService = delegateNgTokenService;
    this.delegateVersionService = delegateVersionService;
  }

  @Override
  public String getCommand(@NotBlank String commandType, @NotBlank String managerUrl, @NotBlank String accountId) {
    final String tokenValue = getDefaultNgToken(accountId);
    final String image = delegateVersionService.getImmutableDelegateImageTag(accountId);
    final Map<String, String> values = getScriptParams(managerUrl, accountId, tokenValue, image);

    final StringSubstitutor substitute = new StringSubstitutor(values);

    switch (commandType) {
      case "DOCKER":
        return substitute.replace(DOCKER_COMMAND);
      case "HELM":
        return substitute.replace(HELM_COMMAND);
      case "TERRAFORM":
        return substitute.replace(TERRAFORM_COMMAND);
      default:
        final String error = String.format("Unsupported installation command type %s.", commandType);
        log.error(error);
        throw new DelegateInstallationCommandNotSupportedException(error);
    }
  }

  @Override
  public String getTerraformExampleModuleFile(final String managerUrl, final String accountId) throws IOException {
    final String tokenValue = getDefaultNgToken(accountId);
    final String image = delegateVersionService.getImmutableDelegateImageTag(accountId);
    final Map<String, String> values = getScriptParams(managerUrl, accountId, tokenValue, image);
    String content = IOUtils.toString(this.getClass().getResourceAsStream(TERRAFORM_TEMPLATE_FLE), "UTF-8");
    final StringSubstitutor substitute = new StringSubstitutor(values);
    return substitute.replace(content);
  }

  private Map<String, String> getScriptParams(
      final String managerUrl, final String accountId, final String tokenValue, final String image) {
    return ImmutableMap.<String, String>builder()
        .put("account_id", accountId)
        .put("token", tokenValue)
        .put("manager_url", managerUrl)
        .put("image", image)
        .build();
  }

  private String getDefaultNgToken(String accountId) {
    final Map<String, Boolean> activeStatus =
        delegateNgTokenService.isDelegateTokenActive(accountId, List.of(DEFAULT_TOKEN_NAME));
    if (Objects.isNull(activeStatus) || activeStatus.isEmpty()) {
      final String errorMsg = String.format("default token not found for account %s.", accountId);
      log.error(errorMsg);
      throw new DelegateTokenNotFoundException(errorMsg);
    }
    return Boolean.TRUE.equals(activeStatus.get(DEFAULT_TOKEN_NAME))
        ? delegateNgTokenService.getDelegateTokenValue(accountId, DEFAULT_TOKEN_NAME)
        : "<PUT YOUR TOKEN>";
  }
}
