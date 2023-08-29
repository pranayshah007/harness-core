/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class PlanCreatorUtilsV1Test extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetAdviserObtainmentsForStage() throws IOException {
    String nextNodeId = "nextNodeId";
    byte[] adviserParamsBytes = "AdviserParams".getBytes();
    assertThat(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, null).size()).isEqualTo(0);
    assertThat(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, Dependency.newBuilder().build()).size())
        .isEqualTo(0);

    assertThat(PlanCreatorUtilsV1
                   .getAdviserObtainmentsForStage(
                       kryoSerializer, Dependency.newBuilder().putMetadata("abc", ByteString.empty()).build())
                   .size())
        .isEqualTo(0);

    doReturn(nextNodeId).when(kryoSerializer).asObject(nextNodeId.getBytes());
    doReturn(adviserParamsBytes)
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder().nextNodeId(nextNodeId).build());
    List<AdviserObtainment> adviserObtainments = PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer,
        Dependency.newBuilder()
            .putMetadata(YAMLFieldNameConstants.NEXT_ID, ByteString.copyFrom(nextNodeId.getBytes()))
            .build());
    assertThat(adviserObtainments.size()).isEqualTo(1);
    assertThat(adviserObtainments.get(0).getType())
        .isEqualTo(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build());
    assertThat(adviserObtainments.get(0).getParameters()).isEqualTo(ByteString.copyFrom(adviserParamsBytes));
  }
}
