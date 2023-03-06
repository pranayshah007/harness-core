/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateInstallationCommandService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.exception.DelegateInstallationCommandNotSupportedException;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;

@Slf4j
public class DelegateInstallationCommandServiceImpl implements DelegateInstallationCommandService {
  private final DelegateNgTokenService delegateNgTokenService;
  private final DelegateVersionService delegateVersionService;
  private static final String TERRAFORM_TEMPLATE_FLE = "/delegatetemplates/delegate-terraform-example-module.ftl";

  private static final String DOCKER_COMMAND = "docker run --cpus=1 --memory=2g \\\n"
      + "  -e DELEGATE_NAME=docker-delegate \\\n"
      + "  -e NEXT_GEN=\"true\" \\\n"
      + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
      + "  -e ACCOUNT_ID=${account_id} \\\n"
      + "  -e DELEGATE_TOKEN=${token} \\\n"
      + "  -e LOG_STREAMING_SERVICE_URL=${manager_url}/log-service/ \\\n"
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

  private static final String KUBERNETES_MANIFEST_INSTRUCTIONS = "\"PUT_YOUR_DELEGATE_NAME\" with kubernetes-delegate\n"
      + "\"PUT_YOUR_ACCOUNT_ID\" with ${account_id}\n"
      + "\"PUT_YOUR_MANAGER_ENDPOINT\" with ${manager_url}\n"
      + "\"PUT_YOUR_DELEGATE_TOKEN\" with ${token}\n"
      + "\"PUT_YOUR_DELEGATE_IMAGE\" with ${image}";

  @Inject
  public DelegateInstallationCommandServiceImpl(
      DelegateNgTokenService delegateNgTokenService, DelegateVersionService delegateVersionService) {
    this.delegateNgTokenService = delegateNgTokenService;
    this.delegateVersionService = delegateVersionService;
  }

  @Override
  public String getCommand(@NotBlank final String commandType, @NotBlank final String managerUrl,
      @NotBlank final String accountId, final DelegateEntityOwner owner) {
    final String tokenValue = getDefaultNgToken(accountId, owner);
    final String image = delegateVersionService.getImmutableDelegateImageTag(accountId);
    final Map<String, String> values = getScriptParams(managerUrl, accountId, tokenValue, image);

    final StringSubstitutor substitute = new StringSubstitutor(values);

    switch (commandType) {
      case "DOCKER":
        return substitute.replace(DOCKER_COMMAND);
      case "HELM":
        return substitute.replace(HELM_COMMAND);
      case "KUBERNETES":
        return substitute.replace(KUBERNETES_MANIFEST_INSTRUCTIONS);
      default:
        final String error = String.format("Unsupported installation command type %s.", commandType);
        log.error(error);
        throw new DelegateInstallationCommandNotSupportedException(error);
    }
  }

  @Override
  public String getTerraformExampleModuleFile(
      final String managerUrl, final String accountId, final DelegateEntityOwner owner) throws IOException {
    final String tokenValue = getDefaultNgToken(accountId, owner);
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

  private String getDefaultNgToken(String accountId, DelegateEntityOwner owner) {
    final DelegateTokenDetails delegateTokenDetails =
        delegateNgTokenService.getDelegateToken(accountId, delegateNgTokenService.getDefaultTokenName(owner), true);
    String tokenValue;
    if (Objects.isNull(delegateTokenDetails) || !delegateTokenDetails.getStatus().equals(DelegateTokenStatus.ACTIVE)) {
      tokenValue = "<please create a Delegate Token>";
    } else {
      tokenValue = delegateTokenDetails.getValue();
    }
    return tokenValue;
  }
}
