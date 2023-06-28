/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.rule.OwnerRule.ANUPAM;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.service.impl.DelegateInstallationCommandServiceImpl;
import io.harness.delegate.service.intfc.DelegateInstallationCommandService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;

import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DEL)
public class DelegateInstallationCommandServiceTest {
  private final DelegateNgTokenService delegateNgTokenService = mock(DelegateNgTokenService.class);
  private final DelegateVersionService delegateVersionService = mock(DelegateVersionService.class);

  private final MainConfiguration mainConfiguration = mock(MainConfiguration.class);
  private static final String ACCOUNT_ID = "1234567";
  private static final String MANAGER_URL = "https://app.harness.io";
  private static final String ENCODED_TOKEN_VALUE = "eHh4eHhiYmJiYgo=";
  private static final String TOKEN_VALUE = decodeBase64ToString(ENCODED_TOKEN_VALUE);
  private static final String IMAGE = "delegate:latest";
  private static final DelegateTokenDetails TOKEN_DETAIL = DelegateTokenDetails.builder()
                                                               .accountId(ACCOUNT_ID)
                                                               .status(DelegateTokenStatus.ACTIVE)
                                                               .value(ENCODED_TOKEN_VALUE)
                                                               .build();

  private final DelegateInstallationCommandService delegateInstallationCommandService =
      new DelegateInstallationCommandServiceImpl(delegateNgTokenService, delegateVersionService, mainConfiguration);

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testDockerCommandOnSmp() {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);
    final String result = String.format("docker run --cpus=1 --memory=2g \\\n"
            + "  -e DELEGATE_NAME=docker-delegate \\\n"
            + "  -e DEPLOY_MODE=KUBERNETES_ONPREM \\\n"
            + "  -e NEXT_GEN=\"true\" \\\n"
            + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
            + "  -e ACCOUNT_ID=%s \\\n"
            + "  -e DELEGATE_TOKEN=%s \\\n"
            + "  -e LOG_STREAMING_SERVICE_URL=%s/log-service/ \\\n"
            + "  -e MANAGER_HOST_AND_PORT=%s %s",
        ACCOUNT_ID, ENCODED_TOKEN_VALUE, MANAGER_URL, MANAGER_URL, IMAGE);

    assertThat(delegateInstallationCommandService.getCommand("DOCKER", MANAGER_URL, ACCOUNT_ID, null))
        .isEqualTo(result);
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testDockerCommand() {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    final String result = String.format("docker run --cpus=1 --memory=2g \\\n"
            + "  -e DELEGATE_NAME=docker-delegate \\\n"
            + "  -e NEXT_GEN=\"true\" \\\n"
            + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
            + "  -e ACCOUNT_ID=%s \\\n"
            + "  -e DELEGATE_TOKEN=%s \\\n"
            + "  -e LOG_STREAMING_SERVICE_URL=%s/log-service/ \\\n"
            + "  -e MANAGER_HOST_AND_PORT=%s %s",
        ACCOUNT_ID, ENCODED_TOKEN_VALUE, MANAGER_URL, MANAGER_URL, IMAGE);

    assertThat(delegateInstallationCommandService.getCommand("DOCKER", MANAGER_URL, ACCOUNT_ID, null))
        .isEqualTo(result);
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testHelmCommand() {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    final String result =
        String.format("helm upgrade -i helm-delegate --namespace harness-delegate-ng --create-namespace \\\n"
                + "  harness-delegate/harness-delegate-ng \\\n"
                + "  --set delegateName=helm-delegate \\\n"
                + "  --set accountId=%s \\\n"
                + "  --set delegateToken=%s \\\n"
                + "  --set managerEndpoint=%s \\\n"
                + "  --set delegateDockerImage=%s \\\n"
                + "  --set replicas=1 --set upgrader.enabled=false",
            ACCOUNT_ID, ENCODED_TOKEN_VALUE, MANAGER_URL, IMAGE);

    assertThat(delegateInstallationCommandService.getCommand("HELM", MANAGER_URL, ACCOUNT_ID, null)).isEqualTo(result);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testHelmCommandOnSmp() {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);
    final String result =
        String.format("helm upgrade -i helm-delegate --namespace harness-delegate-ng --create-namespace \\\n"
                + "  harness-delegate/harness-delegate-ng \\\n"
                + "  --set delegateName=helm-delegate \\\n"
                + "  --set deployMode=KUBERNETES_ONPREM \\\n"
                + "  --set accountId=%s \\\n"
                + "  --set delegateToken=%s \\\n"
                + "  --set managerEndpoint=%s \\\n"
                + "  --set delegateDockerImage=%s \\\n"
                + "  --set replicas=1 --set upgrader.enabled=false",
            ACCOUNT_ID, ENCODED_TOKEN_VALUE, MANAGER_URL, IMAGE);

    assertThat(delegateInstallationCommandService.getCommand("HELM", MANAGER_URL, ACCOUNT_ID, null)).isEqualTo(result);
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testKubernetesInstructions() {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    final String result = String.format("\"PUT_YOUR_DELEGATE_NAME\" with kubernetes-delegate\n"
            + "\"PUT_YOUR_ACCOUNT_ID\" with %s\n"
            + "\"PUT_YOUR_MANAGER_ENDPOINT\" with %s\n"
            + "\"PUT_YOUR_DELEGATE_TOKEN\" with %s\n"
            + "\"PUT_YOUR_DELEGATE_IMAGE\" with %s",
        ACCOUNT_ID, MANAGER_URL, ENCODED_TOKEN_VALUE, IMAGE);

    assertThat(delegateInstallationCommandService.getCommand("KUBERNETES", MANAGER_URL, ACCOUNT_ID, null))
        .isEqualTo(result);
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testGetTerraformExampleModuleFile() throws IOException {
    String defaultTokenName = delegateNgTokenService.getDefaultTokenName(null);
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, defaultTokenName, true)).thenReturn(TOKEN_DETAIL);
    when(delegateVersionService.getImmutableDelegateImageTag(ACCOUNT_ID)).thenReturn(IMAGE);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    final String result =
        delegateInstallationCommandService.getTerraformExampleModuleFile(MANAGER_URL, ACCOUNT_ID, null);
    String expected =
        IOUtils.toString(this.getClass().getResourceAsStream("/expectedTerraformExampleModule.yaml"), "UTF-8");
    assertThat(result).isEqualTo(expected);
  }
}
