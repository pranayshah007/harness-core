/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.Boolean.parseBoolean;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.AWSViewPreferenceCost;
import io.harness.ccm.views.entities.AWSViewPreferences;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.GCPViewPreferences;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class CEViewPreferenceUtils {
  @Inject @Named("PRIVILEGED") private NGSettingsClient settingsClient;
  @Inject private ViewParametersHelper viewParametersHelper;

  public ViewPreferences getCEViewPreferencesForMigration(final CEView ceView) {
    return getCEViewPreferences(ceView);
  }

  public ViewPreferences getCEViewPreferences(final CEView ceView) {
    ViewPreferences viewPreferences = ceView.getViewPreferences();
    final List<SettingResponseDTO> settingsResponse = getDefaultSettingResponse(ceView);
    if (!isEmpty(settingsResponse)) {
      final List<SettingDTO> settingsDTO =
          settingsResponse.stream().map(SettingResponseDTO::getSetting).collect(Collectors.toList());
      final Map<String, String> settingsMap =
          settingsDTO.stream().collect(Collectors.toMap(SettingDTO::getIdentifier, SettingDTO::getValue));
      viewPreferences = getViewPreferences(ceView, settingsMap);
    }
    return viewPreferences;
  }

  private List<SettingResponseDTO> getDefaultSettingResponse(final CEView ceView) {
    return NGRestUtils.getResponse(settingsClient.listSettings(ceView.getAccountId(), null, null, SettingCategory.CE,
        SettingIdentifiers.PERSPECTIVE_PREFERENCES_GROUP_IDENTIFIER));
  }

  private ViewPreferences getViewPreferences(final CEView ceView, final Map<String, String> settingsMap) {
    ViewPreferences viewPreferences;
    if (Objects.nonNull(ceView.getViewPreferences())) {
      viewPreferences = getViewPreferencesFromCEView(ceView, settingsMap);
    } else {
      viewPreferences = getDefaultViewPreferences(ceView, settingsMap);
    }
    return viewPreferences;
  }

  private ViewPreferences getViewPreferencesFromCEView(final CEView ceView, final Map<String, String> settingsMap) {
    return ViewPreferences.builder()
        .includeOthers(getBooleanSettingValue(
            ceView.getViewPreferences().getIncludeOthers(), settingsMap, SettingIdentifiers.SHOW_OTHERS_IDENTIFIER))
        .includeUnallocatedCost(getBooleanSettingValue(ceView.getViewPreferences().getIncludeOthers(), settingsMap,
                                    SettingIdentifiers.SHOW_UNALLOCATED_CLUSTER_COST_IDENTIFIER)
            && viewParametersHelper.isClusterDataSources(new HashSet<>(ceView.getDataSources())))
        .showAnomalies(getBooleanSettingValue(
            ceView.getViewPreferences().getShowAnomalies(), settingsMap, SettingIdentifiers.SHOW_ANOMALIES_IDENTIFIER))
        .awsPreferences(getAWSViewPreferences(ceView, settingsMap))
        .gcpPreferences(getGCPViewPreferences(ceView, settingsMap))
        .build();
  }

  private ViewPreferences getDefaultViewPreferences(final CEView ceView, final Map<String, String> settingsMap) {
    return ViewPreferences.builder()
        .includeOthers(getBooleanSettingValue(settingsMap, SettingIdentifiers.SHOW_OTHERS_IDENTIFIER))
        .includeUnallocatedCost(
            getBooleanSettingValue(settingsMap, SettingIdentifiers.SHOW_UNALLOCATED_CLUSTER_COST_IDENTIFIER)
            && viewParametersHelper.isClusterDataSources(new HashSet<>(ceView.getDataSources())))
        .showAnomalies(getBooleanSettingValue(settingsMap, SettingIdentifiers.SHOW_ANOMALIES_IDENTIFIER))
        .awsPreferences(getAWSViewPreferences(ceView, settingsMap))
        .gcpPreferences(getGCPViewPreferences(ceView, settingsMap))
        .build();
  }

  private GCPViewPreferences getGCPViewPreferences(final CEView ceView, final Map<String, String> settingsMap) {
    final GCPViewPreferences gcpViewPreferences;
    if (Objects.nonNull(ceView) && Objects.nonNull(ceView.getViewPreferences())
        && Objects.nonNull(ceView.getViewPreferences().getGcpPreferences())) {
      gcpViewPreferences = getGCPViewPreferencesFromCEView(ceView, settingsMap);
    } else {
      gcpViewPreferences = getDefaultGCPViewPreferences(settingsMap);
    }
    return gcpViewPreferences;
  }

  private GCPViewPreferences getGCPViewPreferencesFromCEView(
      final CEView ceView, final Map<String, String> settingsMap) {
    final GCPViewPreferences gcpViewPreferences = ceView.getViewPreferences().getGcpPreferences();
    return GCPViewPreferences.builder()
        .includeDiscounts(getBooleanSettingValue(
            gcpViewPreferences.getIncludeDiscounts(), settingsMap, SettingIdentifiers.INCLUDE_GCP_DISCOUNTS_IDENTIFIER))
        .build();
  }

  private GCPViewPreferences getDefaultGCPViewPreferences(final Map<String, String> settingsMap) {
    return GCPViewPreferences.builder()
        .includeDiscounts(getBooleanSettingValue(settingsMap, SettingIdentifiers.INCLUDE_GCP_DISCOUNTS_IDENTIFIER))
        .build();
  }

  private AWSViewPreferences getAWSViewPreferences(final CEView ceView, final Map<String, String> settingsMap) {
    AWSViewPreferences awsViewPreferences;
    if (Objects.nonNull(ceView) && Objects.nonNull(ceView.getViewPreferences())
        && Objects.nonNull(ceView.getViewPreferences().getAwsPreferences())) {
      awsViewPreferences = getAWSViewPreferencesFromCEView(ceView, settingsMap);
    } else {
      awsViewPreferences = getDefaultAWSViewPreferences(settingsMap);
    }
    return awsViewPreferences;
  }

  private AWSViewPreferences getAWSViewPreferencesFromCEView(
      final CEView ceView, final Map<String, String> settingsMap) {
    final AWSViewPreferences awsViewPreferences = ceView.getViewPreferences().getAwsPreferences();
    return AWSViewPreferences.builder()
        .includeDiscounts(getBooleanSettingValue(
            awsViewPreferences.getIncludeDiscounts(), settingsMap, SettingIdentifiers.INCLUDE_AWS_DISCOUNTS_IDENTIFIER))
        .includeCredits(getBooleanSettingValue(
            awsViewPreferences.getIncludeCredits(), settingsMap, SettingIdentifiers.INCLUDE_AWS_CREDIT_IDENTIFIER))
        .includeRefunds(getBooleanSettingValue(
            awsViewPreferences.getIncludeRefunds(), settingsMap, SettingIdentifiers.INCLUDE_AWS_REFUNDS_IDENTIFIER))
        .includeTaxes(getBooleanSettingValue(
            awsViewPreferences.getIncludeTaxes(), settingsMap, SettingIdentifiers.INCLUDE_AWS_TAXES_IDENTIFIER))
        .awsCost(getAWSCostSettingValue(
            awsViewPreferences.getAwsCost(), settingsMap, SettingIdentifiers.SHOW_AWS_COST_AS_IDENTIFIER))
        .build();
  }

  private AWSViewPreferences getDefaultAWSViewPreferences(final Map<String, String> settingsMap) {
    return AWSViewPreferences.builder()
        .includeDiscounts(getBooleanSettingValue(settingsMap, SettingIdentifiers.INCLUDE_AWS_DISCOUNTS_IDENTIFIER))
        .includeCredits(getBooleanSettingValue(settingsMap, SettingIdentifiers.INCLUDE_AWS_CREDIT_IDENTIFIER))
        .includeRefunds(getBooleanSettingValue(settingsMap, SettingIdentifiers.INCLUDE_AWS_REFUNDS_IDENTIFIER))
        .includeTaxes(getBooleanSettingValue(settingsMap, SettingIdentifiers.INCLUDE_AWS_TAXES_IDENTIFIER))
        .awsCost(getAWSCostSettingValue(settingsMap, SettingIdentifiers.SHOW_AWS_COST_AS_IDENTIFIER))
        .build();
  }

  private Boolean getBooleanSettingValue(
      final Boolean value, final Map<String, String> settingsMap, final String settingIdentifier) {
    return Objects.nonNull(value) ? value : getBooleanSettingValue(settingsMap, settingIdentifier);
  }

  private AWSViewPreferenceCost getAWSCostSettingValue(
      final AWSViewPreferenceCost value, final Map<String, String> settingsMap, final String settingIdentifier) {
    return Objects.nonNull(value) ? value : getAWSCostSettingValue(settingsMap, settingIdentifier);
  }

  private Boolean getBooleanSettingValue(final Map<String, String> settingsMap, final String settingIdentifier) {
    return parseBoolean(settingsMap.get(settingIdentifier));
  }

  private AWSViewPreferenceCost getAWSCostSettingValue(
      final Map<String, String> settingsMap, final String settingIdentifier) {
    return AWSViewPreferenceCost.fromString(settingsMap.get(settingIdentifier));
  }
}