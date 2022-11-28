/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ServiceNowChangeWindowSpecDTOTest extends OrchestrationStepsTestBase {
  // Testing the method fromServiceNowChangeWindowSpec which converts serviceNowChangeWindowSpec into
  // serviceNowChangeWindowSpecDTO
  // Also testing the edge cases like when some non-empty fields are passed as null, error should be thrown
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFromServiceNowChangeWindowSpec() {
    assertNull(ServiceNowChangeWindowSpecDTO.fromServiceNowChangeWindowSpec(null));
    assertThatThrownBy(()
                           -> ServiceNowChangeWindowSpecDTO.fromServiceNowChangeWindowSpec(
                               ServiceNowChangeWindowSpec.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Start Field can't be empty");
    assertThatThrownBy(
        ()
            -> ServiceNowChangeWindowSpecDTO.fromServiceNowChangeWindowSpec(
                ServiceNowChangeWindowSpec.builder().startField(ParameterField.createValueField("start")).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("End Field can't be empty");
    assertEquals(ServiceNowChangeWindowSpecDTO.fromServiceNowChangeWindowSpec(
                     ServiceNowChangeWindowSpec.builder()
                         .startField(ParameterField.createValueField("start"))
                         .endField(ParameterField.createValueField("end"))
                         .build()),
        ServiceNowChangeWindowSpecDTO.builder().startField("start").endField("end").build());
  }
}
