/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.pms.contracts.plan.ExpansionRequestType.LOCAL_FQN;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PmsSdkInitHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetJsonExpansionInfo() {
    PmsSdkConfiguration sdkConfigurationWithNull = PmsSdkConfiguration.builder().moduleType(ModuleType.CD).build();
    assertThat(PmsSdkInitHelper.getJsonExpansionInfo(sdkConfigurationWithNull)).isEmpty();
    PmsSdkConfiguration sdkConfigurationWithEmpty =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).jsonExpansionHandlers(Collections.emptyList()).build();
    assertThat(PmsSdkInitHelper.getJsonExpansionInfo(sdkConfigurationWithEmpty)).isEmpty();

    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionHandlerInfo connectorRefExpansionHandlerInfo =
        JsonExpansionHandlerInfo.builder()
            .jsonExpansionInfo(JsonExpansionInfo.newBuilder().setKey("connectorRef").setExpansionType(KEY).build())
            .expansionHandler(Dummy1.class)
            .build();
    jsonExpansionHandlers.add(connectorRefExpansionHandlerInfo);
    JsonExpansionHandlerInfo abcExpansionHandlerInfo =
        JsonExpansionHandlerInfo.builder()
            .jsonExpansionInfo(JsonExpansionInfo.newBuilder().setKey("abc").setExpansionType(KEY).build())
            .expansionHandler(Dummy1.class)
            .build();
    jsonExpansionHandlers.add(abcExpansionHandlerInfo);
    JsonExpansionHandlerInfo defExpansionHandlerInfo =
        JsonExpansionHandlerInfo.builder()
            .jsonExpansionInfo(JsonExpansionInfo.newBuilder()
                                   .setKey("def")
                                   .setExpansionType(LOCAL_FQN)
                                   .setStageType(StepType.getDefaultInstance())
                                   .build())
            .expansionHandler(Dummy1.class)
            .build();
    jsonExpansionHandlers.add(defExpansionHandlerInfo);

    PmsSdkConfiguration sdkConfiguration =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).jsonExpansionHandlers(jsonExpansionHandlers).build();
    List<JsonExpansionInfo> jsonExpansionInfo = PmsSdkInitHelper.getJsonExpansionInfo(sdkConfiguration);
    assertThat(jsonExpansionInfo).hasSize(3);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetSupportedSdkFunctorsList() {
    PmsSdkConfiguration sdkConfigurationWithNull = PmsSdkConfiguration.builder().moduleType(ModuleType.CD).build();
    assertThat(PmsSdkInitHelper.getSupportedSdkFunctorsList(sdkConfigurationWithNull)).isEmpty();
    PmsSdkConfiguration sdkConfigurationWithEmpty = PmsSdkConfiguration.builder().moduleType(ModuleType.CD).build();
    assertThat(PmsSdkInitHelper.getSupportedSdkFunctorsList(sdkConfigurationWithEmpty)).isEmpty();

    Map<String, Class<? extends SdkFunctor>> functors = new HashMap<>();
    functors.put("f1", Dummy2.class);
    functors.put("f2", Dummy2.class);
    functors.put("f3", Dummy2.class);
    functors.put("f4", Dummy2.class);

    PmsSdkConfiguration sdkConfiguration =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).sdkFunctors(functors).build();
    List<String> expandableFields = PmsSdkInitHelper.getSupportedSdkFunctorsList(sdkConfiguration);
    assertThat(expandableFields).hasSize(4);
    assertThat(expandableFields).contains("f1", "f2", "f3", "f4");
  }

  private static class Dummy1 implements JsonExpansionHandler {
    @Override
    public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
      return null;
    }
  }

  private static class Dummy2 implements SdkFunctor {
    @Override
    public Object get(Ambiance ambiance, String... args) {
      return null;
    }
  }
}
