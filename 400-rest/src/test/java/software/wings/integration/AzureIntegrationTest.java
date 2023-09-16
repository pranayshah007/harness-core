/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.ccm.config.CCMConfig;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureAvailabilitySet;
import software.wings.beans.AzureConfig;
import software.wings.beans.azure.AzureVirtualMachineScaleSet;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.azure.AzureHelperService;
import software.wings.rules.Integration;
import software.wings.service.intfc.security.EncryptionService;

import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.containerservice.models.OSType;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
@Integration
@Slf4j
public class AzureIntegrationTest extends WingsBaseTest {
  private String clientId = "";
  private String tenantId = "";
  private String key = "";
  private final String subscriptionId = "20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0"; // Harness-QA subscription

  @Inject private ScmSecret scmSecret;
  @Inject private AzureHelperService azureHelperService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EncryptionService encryptionService;

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(azureHelperService, "encryptionService", encryptionService, true);
    clientId = scmSecret.decryptToString(new SecretName("azure_client_id"));
    tenantId = scmSecret.decryptToString(new SecretName("azure_tenant_id"));
    key = scmSecret.decryptToString(new SecretName("azure_key"));
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void azureAuthenticationTest() {
    AzureConfig config = getAzureConfig();
    azureHelperService.validateAzureAccountCredential(config, Collections.emptyList());
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getSubscriptionsTest() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    assertThat(subscriptions).isNotEmpty();
    log.info("Azure Subscriptions: " + subscriptions);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getContainerRegistriesTest() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    List<String> registries = new ArrayList<>();

    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      registries.addAll(azureDelegateHelperService.listContainerRegistryNames(config, subscriptionId));
    }
    assertThat(registries).isNotEmpty();

    log.info("Azure Container Registries: " + registries);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getVirtualMachineScaleSetsTest() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      List<AzureVirtualMachineScaleSet> virtualMachineScaleSets =
          azureHelperService.listVirtualMachineScaleSets(config, Collections.emptyList(), subscriptionId);
      for (AzureVirtualMachineScaleSet vmss : virtualMachineScaleSets) {
        log.info("Details: " + subscriptionId + " " + vmss.getResourceId());
      }
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAvailabilitySets() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      List<AzureAvailabilitySet> availabilitySets =
          azureHelperService.listAvailabilitySets(config, Collections.emptyList(), subscriptionId);
      for (AzureAvailabilitySet as : availabilitySets) {
        log.info("Details: " + subscriptionId + " " + as.getResourceId());
      }
    }
  }

  @Test
  @Owner(developers = PUNEET, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAvailableTags() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    subscriptions.forEach((subId, Desc) -> log.info(subId + Desc));
    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      log.info("Subscription: " + subscriptionId);
      Set<String> tags =
          azureDelegateHelperService.listTagsBySubscription(subscriptionId, config, Collections.emptyList());
      assertThat(tags).isNotEmpty();
      for (String tag : tags) {
        log.info("Tag: " + tag);
      }
    }
  }

  @Test
  @Owner(developers = PUNEET, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("Ignoring for now as its flaky")
  public void getHostsByResourceGroupAndTag() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    subscriptions.forEach((subId, Desc) -> log.info(subId + Desc));
    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      log.info("Subscription: " + subscriptionId);
      Set<String> resourceGroups =
          azureDelegateHelperService.listResourceGroups(config, Collections.EMPTY_LIST, subscriptionId);
      assertThat(resourceGroups).isNotEmpty();

      // Win hosts
      for (String rg : resourceGroups) {
        List<VirtualMachine> vms = azureHelperService.listVmsByTagsAndResourceGroup(
            config, Collections.EMPTY_LIST, subscriptionId, rg, Collections.EMPTY_MAP, OSType.WINDOWS);
        // assert that only windows instances are returned
        for (VirtualMachine vm : vms) {
          assertThat(vm.osProfile().windowsConfiguration()).isNotNull();
          log.info("Resource group :" + rg + " VM :" + vm.name());
        }
      }

      // Linux hosts
      for (String rg : resourceGroups) {
        List<VirtualMachine> vms = azureHelperService.listVmsByTagsAndResourceGroup(
            config, Collections.EMPTY_LIST, subscriptionId, rg, Collections.EMPTY_MAP, OSType.LINUX);
        // assert that only linux instances are returned
        for (VirtualMachine vm : vms) {
          assertThat(vm.osProfile().linuxConfiguration()).isNotNull();
          log.info("Resource group :" + rg + " VM :" + vm.name());
        }
      }
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getRepositoryTags() {
    AzureConfig config = getAzureConfig();
    Map<String, String> subscriptions = listSubscriptions(config);
    subscriptions.forEach((subId, Desc) -> log.info(subId + Desc));
    for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
      String subscriptionId = entry.getKey();
      List<String> registries = azureDelegateHelperService.listContainerRegistryNames(config, subscriptionId);
      for (String registry : registries) {
        if (registry.equals("harnessexample")) {
          List<String> repositories = azureDelegateHelperService.listRepositories(config, subscriptionId, registry);
          for (String repository : repositories) {
            List<String> tags = azureDelegateHelperService.listRepositoryTags(
                config, Collections.emptyList(), subscriptionId, registry, repository);
            log.info("Details: " + subscriptionId + " " + registry + " " + repository + " " + tags.toString());
          }
        }
      }
    }
  }

  private AzureConfig getAzureConfig() {
    CCMConfig ccmConfig = CCMConfig.builder().cloudCostEnabled(false).build();
    return new AzureConfig(clientId, tenantId, key.toCharArray(), "", ccmConfig, "", AzureEnvironmentType.AZURE);
  }

  private Map<String, String> listSubscriptions(AzureConfig config) {
    return azureDelegateHelperService.listSubscriptions(config, Collections.emptyList());
  }
}
