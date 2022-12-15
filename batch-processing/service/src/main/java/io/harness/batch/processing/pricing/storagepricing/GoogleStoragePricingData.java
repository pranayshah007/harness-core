/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.storagepricing;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.pricing.PricingSource;
import io.harness.batch.processing.pricing.appspot.AppSpotPricingClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Instant;
import org.springframework.stereotype.Component;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Component
public class GoogleStoragePricingData {
  // Google Storage API https://stackoverflow.com/questions/51811599/google-compute-engine-persistent-disk-pricing-api

  @Getter public static final PricingSource pricingSource = PricingSource.PUBLIC_API;
  @Getter public static final String defaultRegion = "us-central1";
  private static final long EXPIRE_AFTER = 432000_000L; // 5 days
  // google's default storage pricing
  private static final Double DEFAULT_PRICE = 0.040D;

  /**
   * type: pd-standard or pd-ssd. Default: pd-standard as per k8s docs
   * replication-type: none -> "Storage PD SSD", regional-pd -> "Regional SSD backed PD Capacity"
   * https://kubernetes.io/docs/concepts/storage/storage-classes/#gce-pd
   */
  public enum Type {
    PD_SSD("pd-ssd"),
    PD_STANDARD("pd-standard");

    @Getter String fieldName;
    Type(String fieldName) {
      this.fieldName = fieldName;
    }

    // Reverse-lookup map for getting a day from an abbreviation
    private static final Map<String, Type> lookup = new HashMap<>();
    static {
      for (Type d : Type.values()) {
        lookup.put(d.getFieldName(), d);
      }
    }
    public static Type get(String fieldName) {
      if (lookup.containsKey(fieldName)) {
        return lookup.get(fieldName);
      }
      if (isNotEmpty(fieldName)) {
        log.warn("field:'{}' is not present in GoogleStoragePricingData.Type, please add it.", fieldName);
      }
      return PD_STANDARD;
    }
  }

  private static Instant lastSynced;

  @Value
  @ToString
  private static class MapKey {
    @Nullable Type type;
    @Nullable String region;
    @Nullable Boolean isRegional;
  }
  private static final Map<MapKey, Double> pricingMap = new HashMap<>();

  public static void refresh() throws IOException {
    AppSpotPricingClient.ApiResponse apiResponse = AppSpotPricingClient.fetchParsedResponse();

    for (AppSpotPricingClient.Sku skuOuter : apiResponse.skus) {
      for (AppSpotPricingClient.Sku sku : skuOuter.skus) {
        for (String region : sku.service_regions) {
          // fetch the last (usually 2nd) one from price list, the first is the freemium quota
          Double price = parsePriceFromString(sku.prices.get(sku.prices.size() - 1));
          Type type = null;
          Boolean isRegional = null;
          if (sku.description.startsWith("Regional SSD backed PD Capacity")) {
            isRegional = true;
            type = Type.PD_SSD;
          } else if (sku.description.startsWith("SSD backed PD Capacity")) {
            isRegional = false;
            type = Type.PD_SSD;
          } else if (sku.description.startsWith("Regional Storage PD Capacity")) {
            isRegional = true;
            type = Type.PD_STANDARD;
          } else if (sku.description.startsWith("Storage PD Capacity")) {
            isRegional = false;
            type = Type.PD_STANDARD;
          }
          if (type != null && price != null && region != null) {
            pricingMap.put(new MapKey(type, region, isRegional), price);
          }
        }
      }
    }
    lastSynced = Instant.now();
  }

  public static Double getPricePerGBMonth(final Type type, final String region, final Boolean isRegional) {
    if (pricingMap.isEmpty() || lastSynced == null || lastSynced.plus(EXPIRE_AFTER).isBefore(Instant.now())) {
      log.info("Refreshing {} ...", GoogleStoragePricingData.class.getSimpleName());
      try {
        refresh();
      } catch (Exception e) {
        log.error("Failed to update {}", GoogleStoragePricingData.class.getSimpleName(), e);
      }
    }
    MapKey key = new MapKey(type, region, isRegional);
    if (!pricingMap.containsKey(key)) {
      log.error("{} doesn't have pricing data for {}", GoogleStoragePricingData.class.getSimpleName(), key.toString());
    }
    return pricingMap.getOrDefault(new MapKey(type, region, isRegional), DEFAULT_PRICE);
  }

  private static Double parsePriceFromString(String priceStr) {
    Pattern regex = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    Matcher matcher = regex.matcher(priceStr);
    if (matcher.find()) {
      return Double.parseDouble(matcher.group(1));
    }
    return null;
  }
}
