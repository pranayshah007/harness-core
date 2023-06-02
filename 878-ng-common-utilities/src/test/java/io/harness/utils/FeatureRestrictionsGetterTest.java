/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.rule.Owner;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class FeatureRestrictionsGetterTest {
  @Mock EnforcementClientService enforcementClientService;
  @InjectMocks FeatureRestrictionsGetter featureRestrictionsGetter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetFeatureRestrictionsAvailability() {
    List<YamlSchemaWithDetails> yamlSchemaWithDetails = Collections.singletonList(
        YamlSchemaWithDetails.builder()
            .moduleType(ModuleType.CD)
            .yamlSchemaMetadata(
                YamlSchemaMetadata.builder().featureRestrictions(Collections.singletonList("TEST1")).build())
            .build());
    Map<FeatureRestrictionName, Boolean> enforcementClientResponse = new HashMap<>();
    enforcementClientResponse.put(FeatureRestrictionName.TEST1, true);
    Map<String, Boolean> featureRestrictionResponse =
        featureRestrictionsGetter.getFeatureRestrictionsAvailability(yamlSchemaWithDetails, "accountId");
    assertEquals(featureRestrictionResponse.size(), 0);

    doReturn(enforcementClientResponse).when(enforcementClientService).getAvailabilityMap(any(), eq("accountId"));
    featureRestrictionResponse =
        featureRestrictionsGetter.getFeatureRestrictionsAvailability(yamlSchemaWithDetails, "accountId");
    assertEquals(featureRestrictionResponse.size(), 1);
    for (FeatureRestrictionName featureRestrictionName : enforcementClientResponse.keySet()) {
      assertTrue(featureRestrictionResponse.containsKey(featureRestrictionName.toString()));
      assertEquals(enforcementClientResponse.get(featureRestrictionName),
          featureRestrictionResponse.get(featureRestrictionName.toString()));
    }
  }
}
