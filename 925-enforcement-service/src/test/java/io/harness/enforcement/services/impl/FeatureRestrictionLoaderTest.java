/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.enforcement.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.bases.Restriction;
import io.harness.enforcement.configs.FeatureRestrictionConfig;
import io.harness.enforcement.interfaces.EnforcementSdkSupportInterface;
import io.harness.exception.InvalidArgumentsException;
import io.harness.kryo.KryoConverterFactory;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(GTM)
public class FeatureRestrictionLoaderTest extends CategoryTest {
  FeatureRestrictionLoaderImpl featuresManagementJob;
  EnforcementServiceImpl enforcementService;
  KryoConverterFactory kryoConverterFactory;
  private final List<FeatureRestrictionConfig> featureRestrictionConfigs = new ArrayList<>();

  @Before
  public void setup() throws IllegalAccessException {
    enforcementService = mock(EnforcementServiceImpl.class);
    kryoConverterFactory = mock(KryoConverterFactory.class);
    featuresManagementJob = new FeatureRestrictionLoaderImpl(enforcementService, kryoConverterFactory);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testYamlInitialization() {
    featuresManagementJob.loadYmlToFeaturesConfig(featureRestrictionConfigs);
    for (FeatureRestrictionConfig config : featureRestrictionConfigs) {
      for (FeatureRestriction featureRestriction : config.getFeatures()) {
        featuresManagementJob.validFeatureInfo(featureRestriction);
        for (Restriction restriction : featureRestriction.getRestrictions().values()) {
          featuresManagementJob.validRestriction(restriction, config.getModuleType());

          switch (restriction.getRestrictionType()) {
            case RATE_LIMIT:
            case STATIC_LIMIT:
            case CUSTOM:
            case LICENSE_RATE_LIMIT:
            case LICENSE_STATIC_LIMIT:
              EnforcementSdkSupportInterface enforcementSdkSupport = (EnforcementSdkSupportInterface) restriction;
              if (!validClientDefined(enforcementSdkSupport.getClientName())) {
                throw new InvalidArgumentsException(
                    String.format("Client name [%s] is not defined in clients", enforcementSdkSupport.getClientName()));
              }
              break;
            default:
              break;
          }
        }
      }
    }
  }

  private boolean validClientDefined(String clientName) {
    return featureRestrictionConfigs.stream()
        .map(config -> config.getClients())
        .flatMap(List::stream)
        .anyMatch(client -> client.getName().equals(clientName));
  }
}
