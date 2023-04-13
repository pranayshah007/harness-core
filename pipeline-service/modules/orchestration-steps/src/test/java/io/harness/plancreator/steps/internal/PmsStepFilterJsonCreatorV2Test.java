/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsStepFilterJsonCreatorV2Test extends CategoryTest {
  @InjectMocks PmsStepFilterJsonCreatorV2 pmsStepFilterJsonCreatorV2;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> strings = pmsStepFilterJsonCreatorV2.getSupportedStepTypes();
    Set<String> strings2 = Sets.newHashSet(StepSpecTypeConstants.HTTP, StepSpecTypeConstants.JIRA_CREATE,
        StepSpecTypeConstants.CUSTOM_APPROVAL, StepSpecTypeConstants.JIRA_UPDATE, StepSpecTypeConstants.JIRA_APPROVAL,
        StepSpecTypeConstants.SERVICENOW_APPROVAL, StepSpecTypeConstants.BARRIER, StepSpecTypeConstants.POLICY_STEP,
        StepSpecTypeConstants.SERVICENOW_CREATE, StepSpecTypeConstants.SERVICENOW_UPDATE,
        StepSpecTypeConstants.SERVICENOW_IMPORT_SET, StepSpecTypeConstants.QUEUE, StepSpecTypeConstants.EMAIL,
        StepSpecTypeConstants.WAIT_STEP, StepSpecTypeConstants.CONTAINER_STEP);
    assertThat(strings).isEqualTo(strings2);
    assertThat(strings).contains(StepSpecTypeConstants.JIRA_CREATE);
  }
}
