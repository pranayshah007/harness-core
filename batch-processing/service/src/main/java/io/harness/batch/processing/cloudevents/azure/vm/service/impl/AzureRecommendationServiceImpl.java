/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service.impl;

import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.ANNUAL_SAVINGS_AMOUNT;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.AZURE_VM_ID_FORMAT;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.BATCH_SIZE;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.CURRENT_SKU;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.DURATION;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.MAX_CPU_P95;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.MAX_MEMORY_P95;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.MAX_TOTAL_NETWORK_P95;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.PRICING_FILTER;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.RECOMMENDATION_MESSAGE;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.RECOMMENDATION_TYPE;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.REGION_ID;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.REGION_ID_TO_REGION;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.SAVINGS_AMOUNT;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.SAVINGS_CURRENCY;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.SHUTDOWN;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.SKU_CHANGE;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.SUBSCRIPTION_ID;
import static io.harness.batch.processing.cloudevents.azure.vm.service.utils.AzureRecommendationConstants.TARGET_SKU;

import io.harness.azure.utility.AzureUtils;
import io.harness.batch.processing.cloudevents.azure.vm.service.AzureRecommendationService;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.azurevmpricing.AzureVmItemDTO;
import io.harness.ccm.azurevmpricing.AzureVmPricingClient;
import io.harness.ccm.azurevmpricing.AzureVmPricingResponseDTO;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.commons.entities.azure.AzureRecommendation.AzureRecommendationBuilder;
import io.harness.ccm.commons.entities.azure.AzureVmDetails;
import io.harness.ccm.graphql.core.recommendation.AzureCpuUtilisationService;

import software.wings.beans.AzureAccountAttributes;

import com.azure.core.http.HttpClient;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.advisor.AdvisorManager;
import com.azure.resourcemanager.advisor.models.ResourceRecommendationBase;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.fluent.VirtualMachineSizesClient;
import com.azure.resourcemanager.compute.fluent.models.VirtualMachineSizeInner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@Service
public class AzureRecommendationServiceImpl implements AzureRecommendationService {
  @Autowired BatchMainConfig configuration;
  @Autowired AzureVmPricingClient azureVmPricingClient;
  @Autowired AzureCpuUtilisationService azureCpuUtilisationService;

  @Override
  public List<AzureRecommendation> getRecommendations(String accountId, AzureAccountAttributes request) {
    String tenantId = request.getTenantId();
    AzureProfile profile = new AzureProfile(tenantId, request.getSubscriptionId(), AzureEnvironment.AZURE);
    ClientSecretCredential clientSecretCredential = getClientSecretCredential(request);
    VirtualMachineSizesClient vmSizeClient = getVirtualMachineSizesClientClient(clientSecretCredential, profile);
    AdvisorManager advisorManager = getAdvisorManager(clientSecretCredential, profile);

    List<AzureRecommendation> allRecommendations = new ArrayList<>();
    HashMap<String, PagedIterable<VirtualMachineSizeInner>> vmStoredDetails = new HashMap<>();

    for (PagedResponse<ResourceRecommendationBase> recommendationsInBatch :
        advisorManager.recommendations().list().iterableByPage(BATCH_SIZE)) {
      for (ResourceRecommendationBase recommendation : recommendationsInBatch.getValue()) {
        AzureRecommendation azureRecommendation = createAzureRecommendation(accountId, tenantId, recommendation,
            vmSizeClient, vmStoredDetails, request.getConnectorId(), request.getConnectorName());
        if (azureRecommendation != null) {
          allRecommendations.add(azureRecommendation);
        }
      }
    }
    return allRecommendations;
  }

  private ClientSecretCredential getClientSecretCredential(AzureAccountAttributes request) {
    HttpClient httpClient = AzureUtils.getAzureHttpClient();
    return new ClientSecretCredentialBuilder()
        .clientId(configuration.getAzureStorageSyncConfig().getAzureAppClientId())
        .clientSecret(configuration.getAzureStorageSyncConfig().getAzureAppClientSecret())
        .tenantId(request.getTenantId())
        .httpClient(httpClient)
        .build();
  }

  private VirtualMachineSizesClient getVirtualMachineSizesClientClient(
      ClientSecretCredential clientSecretCredential, AzureProfile profile) {
    return ComputeManager.authenticate(clientSecretCredential, profile).serviceClient().getVirtualMachineSizes();
  }

  private AdvisorManager getAdvisorManager(ClientSecretCredential clientSecretCredential, AzureProfile profile) {
    return AdvisorManager.authenticate(clientSecretCredential, profile);
  }

  private Map<String, String> getExtendedProperties(ResourceRecommendationBase recommendation) {
    return recommendation.extendedProperties() == null
        ? new HashMap<>()
        : recommendation.extendedProperties().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
  }

  private AzureRecommendation createAzureRecommendation(String accountId, String tenantId,
      ResourceRecommendationBase recommendation, VirtualMachineSizesClient vmSizeClient,
      HashMap<String, PagedIterable<VirtualMachineSizeInner>> vmStoredDetails, String connectorId,
      String connectorName) {
    Map<String, String> extendedProperties = getExtendedProperties(recommendation);
    String recommendationType = extendedProperties.getOrDefault(RECOMMENDATION_TYPE, "");
    if (!recommendationType.equals(SHUTDOWN) && !recommendationType.equals(SKU_CHANGE)) {
      return null;
    }
    String regionName = REGION_ID_TO_REGION.get(extendedProperties.get(REGION_ID));
    String currentSku = extendedProperties.get(CURRENT_SKU);
    double currentSkuMonthlySavings = Double.parseDouble(extendedProperties.get(SAVINGS_AMOUNT));
    String targetSku = extendedProperties.get(TARGET_SKU);
    String vmId = getAzureVmId(recommendation.resourceMetadata().resourceId());
    String duration = extendedProperties.get(DURATION);
    AzureRecommendationBuilder azureRecommendationBuilder =
        AzureRecommendation.builder()
            .recommendationId(recommendation.id())
            .accountId(accountId)
            .impactedField(recommendation.impactedField())
            .impactedValue(recommendation.impactedValue())
            .maxCpuP95(extendedProperties.get(MAX_CPU_P95))
            .maxTotalNetworkP95(extendedProperties.get(MAX_TOTAL_NETWORK_P95))
            .maxMemoryP95(extendedProperties.get(MAX_MEMORY_P95))
            .currencyCode(extendedProperties.get(SAVINGS_CURRENCY))
            .expectedMonthlySavings(currentSkuMonthlySavings)
            .expectedAnnualSavings(Double.parseDouble(extendedProperties.get(ANNUAL_SAVINGS_AMOUNT)))
            .recommendationMessage(extendedProperties.get(RECOMMENDATION_MESSAGE))
            .recommendationType(recommendationType)
            .regionName(regionName)
            .subscriptionId(extendedProperties.get(SUBSCRIPTION_ID))
            .duration(duration)
            .tenantId(tenantId)
            .vmId(vmId)
            .connectorId(connectorId)
            .connectorName(connectorName);

    PagedIterable<VirtualMachineSizeInner> virtualMachineSizeInners =
        getVirtualMachineSizes(regionName, vmStoredDetails, vmSizeClient);
    if (virtualMachineSizeInners == null) {
      return null;
    }

    double currentSkuCost =
        targetSku.equals(SHUTDOWN) ? currentSkuMonthlySavings : getSkuPotentialCost(currentSku, regionName);
    AzureVmDetails currentVmDetails = getVirtualMachineDetails(currentSku, virtualMachineSizeInners, currentSkuCost);
    AzureVmDetails targetVmDetails =
        getVirtualMachineDetails(targetSku, virtualMachineSizeInners, currentSkuCost - currentSkuMonthlySavings);

    Double currentSkuAvgCpuUtilisation =
        azureCpuUtilisationService.getAverageAzureVmCpuUtilisationData(vmId, accountId, Integer.parseInt(duration));
    Double targetSkuAvgCpuUtilisation = getTargetAverageAzureVmCpuUtilisation(
        currentVmDetails.getNumberOfCores(), targetVmDetails.getNumberOfCores(), currentSkuAvgCpuUtilisation);
    currentVmDetails.setCpuUtilisation(currentSkuAvgCpuUtilisation);
    targetVmDetails.setCpuUtilisation(targetSkuAvgCpuUtilisation);

    azureRecommendationBuilder.currentVmDetails(currentVmDetails);
    azureRecommendationBuilder.targetVmDetails(targetVmDetails);

    return azureRecommendationBuilder.build();
  }

  private PagedIterable<VirtualMachineSizeInner> getVirtualMachineSizes(String regionName,
      HashMap<String, PagedIterable<VirtualMachineSizeInner>> vmStoredDetails, VirtualMachineSizesClient vmSizeClient) {
    PagedIterable<VirtualMachineSizeInner> virtualMachineSizeInners = vmStoredDetails.get(regionName);
    if (virtualMachineSizeInners == null) {
      virtualMachineSizeInners = vmSizeClient.list(regionName);
      vmStoredDetails.put(regionName, virtualMachineSizeInners);
    }
    return virtualMachineSizeInners;
  }

  private VirtualMachineSizeInner getVirtualMachineSize(
      String skuName, PagedIterable<VirtualMachineSizeInner> virtualMachineSizeInners) {
    return virtualMachineSizeInners.stream()
        .filter(vm -> vm.name().equals(skuName))
        .collect(Collectors.toList())
        .get(0);
  }

  private AzureVmDetails getVirtualMachineDetails(
      String sku, PagedIterable<VirtualMachineSizeInner> virtualMachineSizeInners, double cost) {
    if (!sku.equals(SHUTDOWN)) {
      VirtualMachineSizeInner skuDetails = getVirtualMachineSize(sku, virtualMachineSizeInners);
      return constructAzureVmDetailsDTO(sku, skuDetails.numberOfCores(), skuDetails.memoryInMB(), cost);
    }
    return constructAzureVmDetailsDTO(sku, 0, 0, 0.0);
  }

  private AzureVmDetails constructAzureVmDetailsDTO(String name, int numberOfCores, int memoryInMB, double cost) {
    return AzureVmDetails.builder().name(name).numberOfCores(numberOfCores).memoryInMB(memoryInMB).cost(cost).build();
  }

  private double getSkuPotentialCost(String skuName, String regionName) {
    double price = 0.0;
    try {
      String filter = String.format(PRICING_FILTER, skuName, regionName);
      Call<AzureVmPricingResponseDTO> azurePricingCall = azureVmPricingClient.getAzureVmPrice(filter);
      Response<AzureVmPricingResponseDTO> pricingInfo = azurePricingCall.execute();
      if (null != pricingInfo.body() && null != pricingInfo.body().getItems()) {
        // This API return list of potential prices for the VM, we get average of it
        price =
            pricingInfo.body().getItems().stream().mapToDouble(AzureVmItemDTO::getRetailPrice).average().orElse(0.0);
        // Multiply with 730.5 since API returns price of 1 hour, and we need price for a month
        price *= 730.5;
      }
    } catch (Exception e) {
      log.info(
          "Error while calculating price of Azure VM for skuName {} and regionName {}: {}", skuName, regionName, e);
    }
    return price;
  }

  private Double getTargetAverageAzureVmCpuUtilisation(
      int currentNumberOfCores, int targetNumberOfCores, Double currentSkuAvgCpuUtilisation) {
    if (currentSkuAvgCpuUtilisation == null) {
      return null;
    }
    if (targetNumberOfCores == 0) {
      return 0.0;
    }
    return (currentSkuAvgCpuUtilisation * currentNumberOfCores) / targetNumberOfCores;
  }

  private String getAzureVmId(String resourceId) {
    String[] splitResourceId = resourceId.split("/");
    if (splitResourceId.length > 8) {
      return String.format(AZURE_VM_ID_FORMAT, splitResourceId[2], splitResourceId[4], splitResourceId[8]);
    }
    return "";
  }
}
