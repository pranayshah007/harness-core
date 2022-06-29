/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.resourceconstraint;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.QueueHoldingScope;

import io.swagger.annotations.ApiModelProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class QueueStepInfoTest {
  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyRuntimeSpecParameters() {
    QueueStepInfo step = new QueueStepInfo();
    step.setScope(QueueHoldingScope.PIPELINE);
    ParameterField<String> pfKey = ParameterField.<String>builder().value("aKey").build();
    step.setKey(pfKey);

    SpecParameters spec = step.getSpecParameters();
    assertThat(spec).isNotNull();
    assertThat(spec).isInstanceOf(ResourceRestraintSpecParameters.class);

    ResourceRestraintSpecParameters rrSpec = (ResourceRestraintSpecParameters) spec;
    assertThat(rrSpec.getResourceUnit()).isEqualTo(pfKey);
    assertThat(rrSpec.getResourceUnit().getValue()).isEqualTo("aKey");
    assertThat(rrSpec.getHoldingScope()).isEqualTo(HoldingScope.PIPELINE);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyFixedSpecParameters() {
    QueueStepInfo step = new QueueStepInfo();
    step.setScope(QueueHoldingScope.PIPELINE);

    SpecParameters spec = step.getSpecParameters();
    assertThat(spec).isNotNull();
    assertThat(spec).isInstanceOf(ResourceRestraintSpecParameters.class);

    ResourceRestraintSpecParameters rrSpec = (ResourceRestraintSpecParameters) spec;
    assertThat(rrSpec.getAcquireMode()).isEqualTo(AcquireMode.ENSURE);
    assertThat(rrSpec.getPermits()).isEqualTo(1);
    assertThat(rrSpec.getName()).isEqualTo("Queuing");
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldEnforceThatUuidFieldExist() {
    Field field = ReflectionUtils.getFieldByName(QueueStepInfo.class, "uuid");
    assertThat(field).isNotNull();

    ApiModelProperty ann1 = field.getAnnotation(ApiModelProperty.class);
    assertThat(ann1).isNotNull();
    assertThat(ann1.hidden()).isTrue();

    Method getUuid = ReflectionUtils.getMethod(QueueStepInfo.class, "getUuid");
    assertThat(getUuid).isNotNull();

    ApiModelProperty ann2 = getUuid.getAnnotation(ApiModelProperty.class);
    assertThat(ann2).isNotNull();
    assertThat(ann2.hidden()).isTrue();
  }
}
