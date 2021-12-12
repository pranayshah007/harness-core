package io.harness.pms.sdk;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
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
  public void testGetExpandableFields() {
    PmsSdkConfiguration sdkConfigurationWithNull = PmsSdkConfiguration.builder().moduleType(ModuleType.CD).build();
    assertThat(PmsSdkInitHelper.getExpandableFields(sdkConfigurationWithNull)).isEmpty();
    PmsSdkConfiguration sdkConfigurationWithEmpty =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).jsonExpansionHandlers(Collections.emptyMap()).build();
    assertThat(PmsSdkInitHelper.getExpandableFields(sdkConfigurationWithEmpty)).isEmpty();

    Map<String, Class<? extends JsonExpansionHandler>> jsonExpansionHandlers = new HashMap<>();
    jsonExpansionHandlers.put("connectorRef", Dummy1.class);
    jsonExpansionHandlers.put("serviceRef", Dummy1.class);
    jsonExpansionHandlers.put("image", Dummy1.class);
    jsonExpansionHandlers.put("abc", Dummy1.class);

    PmsSdkConfiguration sdkConfiguration =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).jsonExpansionHandlers(jsonExpansionHandlers).build();
    List<String> expandableFields = PmsSdkInitHelper.getExpandableFields(sdkConfiguration);
    assertThat(expandableFields).hasSize(4);
    assertThat(expandableFields).contains("connectorRef", "serviceRef", "image", "abc");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetSupportedSdkFunctorsList() {
    PmsSdkConfiguration sdkConfigurationWithNull = PmsSdkConfiguration.builder().moduleType(ModuleType.CD).build();
    assertThat(PmsSdkInitHelper.getSupportedSdkFunctorsList(sdkConfigurationWithNull)).isEmpty();
    PmsSdkConfiguration sdkConfigurationWithEmpty =
        PmsSdkConfiguration.builder().moduleType(ModuleType.CD).jsonExpansionHandlers(Collections.emptyMap()).build();
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
    public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata) {
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