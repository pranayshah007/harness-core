/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.rule.OwnerRule.MEENA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SyncStepHelperTest extends CategoryTest {
  private AgentApplicationTargets agentApplicationTargets;

  @Before
  public void setUp() {
    agentApplicationTargets = AgentApplicationTargets.builder()
                                  .agentId(ParameterField.<String>builder().value("1234").build())
                                  .applicationName(ParameterField.<String>builder().value("test").build())
                                  .build();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetApplicationsToBeSynced() {
    List<AgentApplicationTargets> targets = new ArrayList<>();
    targets.add(agentApplicationTargets);
    List<Application> result = SyncStepHelper.getApplicationsToBeSynced(targets);
    assertEquals(1, result.size());
    assertEquals("1234", result.get(0).getAgentIdentifier());
    assertEquals("test", result.get(0).getName());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetSyncRequest() {
    SyncStepParameters params =
        SyncStepParameters.infoBuilder()
            .dryRun(ParameterField.<Boolean>builder().value(true).build())
            .prune(ParameterField.<Boolean>builder().value(true).build())
            .syncOptions(SyncOptions.builder()
                             .skipSchemaValidation(ParameterField.<Boolean>builder().value(false).build())
                             .autoCreateNamespace(ParameterField.<Boolean>builder().value(false).build())
                             .applyOutOfSyncOnly(ParameterField.<Boolean>builder().value(false).build())
                             .prunePropagationPolicy(PrunePropagationPolicy.FOREGROUND)
                             .pruneResourcesAtLast(ParameterField.<Boolean>builder().value(false).build())
                             .replaceResources(ParameterField.<Boolean>builder().value(false).build())
                             .build())
            .forceApply(ParameterField.<Boolean>builder().value(true).build())
            .applyOnly(ParameterField.<Boolean>builder().value(true).build())
            .build();
    ApplicationSyncRequest result =
        SyncStepHelper.getSyncRequest(Application.builder().name("test").revision("abcd").build(), params);
    assertTrue(result.isDryRun());
    assertTrue(result.isPrune());
    assertEquals("test", result.getApplicationName());
    assertEquals("abcd", result.getTargetRevision());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testToBoolean() {
    assertTrue(SyncStepHelper.toBoolean(true));
    assertFalse(SyncStepHelper.toBoolean(false));
    assertTrue(SyncStepHelper.toBoolean("true"));
    assertFalse(SyncStepHelper.toBoolean("false"));
    assertThatThrownBy(() -> SyncStepHelper.toBoolean(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SyncStepHelper.toBoolean(1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testToNumber() {
    assertEquals(1, SyncStepHelper.toNumber(1));
    assertEquals(1, SyncStepHelper.toNumber(1.0));
    assertEquals(1, SyncStepHelper.toNumber("1"));
    assertThatThrownBy(() -> SyncStepHelper.toNumber(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SyncStepHelper.toNumber(true)).isInstanceOf(IllegalArgumentException.class);
  }
}
