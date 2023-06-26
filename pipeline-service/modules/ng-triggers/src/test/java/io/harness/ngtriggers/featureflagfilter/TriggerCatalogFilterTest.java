/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.featureflagfilter;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import io.jsonwebtoken.lang.Collections;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerCatalogFilterTest extends CategoryTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks private TriggerCatalogFilter triggerCatalogFilter;

  String accountId = "someAcct";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testIsFeatureFlagEnabled() {
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.BAMBOO_ARTIFACT_NG)).thenReturn(true);
    triggerCatalogFilter.isFeatureFlagEnabled(FeatureName.BAMBOO_ARTIFACT_NG, accountId);
    verify(pmsFeatureFlagHelper, times(1)).isEnabled(accountId, FeatureName.BAMBOO_ARTIFACT_NG);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFilterWithFFsDisabled() {
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CD_TRIGGER_V2)).thenReturn(false);
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(false);
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.BAMBOO_ARTIFACT_NG)).thenReturn(false);
    List<TriggerCatalogType> triggerTypes =
        Collections.arrayToList(new TriggerCatalogType[] {TriggerCatalogType.JENKINS,
            TriggerCatalogType.AZURE_ARTIFACTS, TriggerCatalogType.NEXUS3, TriggerCatalogType.NEXUS2,
            TriggerCatalogType.AMI, TriggerCatalogType.GOOGLE_CLOUD_STORAGE, TriggerCatalogType.BAMBOO});
    assertThat(triggerTypes.stream()
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.CD_TRIGGER_V2))
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.NG_SVC_ENV_REDESIGN))
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.BAMBOO_ARTIFACT_NG))
                   .collect(Collectors.toList()))
        .isEmpty();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFilterWithFFsEnabled() {
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CD_TRIGGER_V2)).thenReturn(true);
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.BAMBOO_ARTIFACT_NG)).thenReturn(true);
    List<TriggerCatalogType> triggerTypes =
        Collections.arrayToList(new TriggerCatalogType[] {TriggerCatalogType.JENKINS,
            TriggerCatalogType.AZURE_ARTIFACTS, TriggerCatalogType.NEXUS3, TriggerCatalogType.NEXUS2,
            TriggerCatalogType.AMI, TriggerCatalogType.GOOGLE_CLOUD_STORAGE, TriggerCatalogType.BAMBOO});
    assertThat(triggerTypes.stream()
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.CD_TRIGGER_V2))
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.NG_SVC_ENV_REDESIGN))
                   .filter(triggerCatalogFilter.filter(accountId, FeatureName.BAMBOO_ARTIFACT_NG))
                   .collect(Collectors.toList()))
        .isEqualTo(triggerTypes);
  }
}