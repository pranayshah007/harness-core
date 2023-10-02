/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.functors;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.IACM)
@RunWith(MockitoJUnitRunner.class)
public class IacmFunctorTest extends CategoryTest {
  @Mock IACMServiceUtils iacmServiceUtils;
  @InjectMocks private IacmFunctor iacmFunctor;

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetNotValidArgs() {
    assertThatThrownBy(() -> iacmFunctor.get(Ambiance.newBuilder().build(), "<+iacm", "foo", "bar", "zar>"))
        .hasMessage(
            "Inappropriate usage of 'iacm' functor. The format should be <+workspace.workspace_id.name_of_the_output_to_use>");
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetWithValidArgumentsQueriesAndNoCache() {
    Map<String, String> outputs = new HashMap<>();
    outputs.put("key1", "value1");
    outputs.put("key2", " value2");
    outputs.put("key3", "value3");

    when(iacmServiceUtils.getIacmWorkspaceOutputs(any(), any(), any(), any())).thenReturn(outputs);

    Object outputRes = iacmFunctor.get(
        Ambiance.newBuilder().setPlanExecutionId("abc").setStageExecutionId("def").build(), "workspace");
    ObjectMapper oMapper = new ObjectMapper();
    Map<String, Object> map = oMapper.convertValue(outputRes, Map.class);
    assertThat(map.size()).isEqualTo(3);
  }
}
