/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CE)
public final class AzureRecommendationConstants {
  public static final int BATCH_SIZE = 100;
  public static final String RECOMMENDATION_TYPE = "recommendationType";
  public static final String SHUTDOWN = "Shutdown";
  public static final String SKU_CHANGE = "SkuChange";
  public static final String MAX_CPU_P95 = "MaxCpuP95";
  public static final String MAX_TOTAL_NETWORK_P95 = "MaxTotalNetworkP95";
  public static final String MAX_MEMORY_P95 = "MaxMemoryP95";
  public static final String SAVINGS_CURRENCY = "savingsCurrency";
  public static final String SAVINGS_AMOUNT = "savingsAmount";
  public static final String ANNUAL_SAVINGS_AMOUNT = "annualSavingsAmount";
  public static final String CURRENT_SKU = "currentSku";
  public static final String TARGET_SKU = "targetSku";
  public static final String RECOMMENDATION_MESSAGE = "recommendationMessage";
  public static final String REGION_ID = "regionId";
  public static final String SUBSCRIPTION_ID = "subscriptionId";
  public static final String DURATION = "Duration";
  public static final String AZURE_VM_ID_FORMAT =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s";
  public static final String PRICING_FILTER =
      "serviceName eq 'Virtual Machines' and priceType eq 'Consumption' and armSkuName eq '%s' and armRegionName eq '%s'";
  public static final Map<String, String> REGION_ID_TO_REGION = new HashMap<>();
  static {
    REGION_ID_TO_REGION.put("useast", "eastus");
    REGION_ID_TO_REGION.put("useast2", "eastus2");
    REGION_ID_TO_REGION.put("uswest", "westus");
    REGION_ID_TO_REGION.put("uswest2", "westus2");
    REGION_ID_TO_REGION.put("uswest3", "westus3");
    REGION_ID_TO_REGION.put("uscentral", "centralus");
    REGION_ID_TO_REGION.put("usnorth", "northus");
    REGION_ID_TO_REGION.put("usnorthcentral", "northcentralus");
    REGION_ID_TO_REGION.put("ussouthcentral", "southcentralus");
    REGION_ID_TO_REGION.put("ussouth", "southcentralus");
    REGION_ID_TO_REGION.put("uswestcentral", "westcentralus");
    REGION_ID_TO_REGION.put("govusvirginia", "usgovvirginia");
    REGION_ID_TO_REGION.put("govusiowa", "usgoviowa");
    REGION_ID_TO_REGION.put("govusarizona", "usgovarizona");
    REGION_ID_TO_REGION.put("govustexas", "usgovtexas");
    REGION_ID_TO_REGION.put("govusdodeast", "usdodeast");
    REGION_ID_TO_REGION.put("govusdodcentral", "usdodcentral");
    REGION_ID_TO_REGION.put("europenorth", "northeurope");
    REGION_ID_TO_REGION.put("europewest", "westeurope");
    REGION_ID_TO_REGION.put("asiaeast", "eastasia");
    REGION_ID_TO_REGION.put("asiasoutheast", "southeastasia");
    REGION_ID_TO_REGION.put("japaneast", "japaneast");
    REGION_ID_TO_REGION.put("japanwest", "japanwest");
    REGION_ID_TO_REGION.put("australiaeast", "australiaeast");
    REGION_ID_TO_REGION.put("australiasoutheast", "australiasoutheast");
    REGION_ID_TO_REGION.put("australiacentral", "australiacentral");
    REGION_ID_TO_REGION.put("australiacentral2", "australiacentral2");
    REGION_ID_TO_REGION.put("brazilsouth", "brazilsouth");
    REGION_ID_TO_REGION.put("brazilsoutheast", "brazilsoutheast");
    REGION_ID_TO_REGION.put("indiawest", "westindia");
    REGION_ID_TO_REGION.put("indiacentral", "centralindia");
    REGION_ID_TO_REGION.put("indiasouth", "southindia");
    REGION_ID_TO_REGION.put("canadacentral", "canadacentral");
    REGION_ID_TO_REGION.put("canadaeast", "canadaeast");
    REGION_ID_TO_REGION.put("uksouth", "uksouth");
    REGION_ID_TO_REGION.put("ukwest", "ukwest");
    REGION_ID_TO_REGION.put("koreacentral", "koreacentral");
    REGION_ID_TO_REGION.put("koreasouth", "koreasouth");
    REGION_ID_TO_REGION.put("francecentral", "francecentral");
    REGION_ID_TO_REGION.put("francesouth", "francesouth");
    REGION_ID_TO_REGION.put("southafricanorth", "southafricanorth");
    REGION_ID_TO_REGION.put("southafricawest", "southafricawest");
    REGION_ID_TO_REGION.put("uaenorth", "uaenorth");
    REGION_ID_TO_REGION.put("uaecentral", "uaecentral");
    REGION_ID_TO_REGION.put("switzerlandnorth", "switzerlandnorth");
    REGION_ID_TO_REGION.put("switzerlandwest", "switzerlandwest");
    REGION_ID_TO_REGION.put("norwayeast", "norwayeast");
    REGION_ID_TO_REGION.put("norwaywest", "norwaywest");
    REGION_ID_TO_REGION.put("jioindiawest", "jioindiawest");
    REGION_ID_TO_REGION.put("swedencentral", "swedencentral");
    REGION_ID_TO_REGION.put("qatarcentral", "qatarcentral");
    REGION_ID_TO_REGION.put("polandcentral", "polandcentral");
    REGION_ID_TO_REGION.put("chinanorth", "chinanorth");
    REGION_ID_TO_REGION.put("chinaeast", "chinaeast");
    REGION_ID_TO_REGION.put("chinanorth2", "chinanorth2");
    REGION_ID_TO_REGION.put("chinaeast2", "chinaeast2");
    REGION_ID_TO_REGION.put("chinanorth3", "chinanorth3");
    REGION_ID_TO_REGION.put("chinaeast3", "chinaeast3");
    REGION_ID_TO_REGION.put("germanywc", "germanywestcentral");
    REGION_ID_TO_REGION.put("germanyc", "germanycentral");
    REGION_ID_TO_REGION.put("germanyne", "germanynortheast");
    REGION_ID_TO_REGION.put("germanyn", "germanynorth");
  }
}
