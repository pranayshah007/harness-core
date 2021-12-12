package io.harness.pms.sdk.core.registries;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class JsonExpansionHandlerRegistryTest extends PmsSdkCoreTestBase {
  @Inject private JsonExpansionHandlerRegistry jsonExpansionHandlerRegistry;

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRegisterAndObtain() {
    String key1 = "one";
    DummyJsonExpansionHandler one = DummyJsonExpansionHandler.builder().something(1).build();
    jsonExpansionHandlerRegistry.register(key1, one);

    String key2 = "two";
    DummyJsonExpansionHandler two = DummyJsonExpansionHandler.builder().something(2).build();
    jsonExpansionHandlerRegistry.register(key2, two);

    assertThat(jsonExpansionHandlerRegistry.obtain(key1)).isEqualTo(one);
    assertThat(jsonExpansionHandlerRegistry.obtain(key2)).isEqualTo(two);

    assertThatThrownBy(() -> jsonExpansionHandlerRegistry.register(key1, DummyJsonExpansionHandler.builder().build()))
        .isInstanceOf(DuplicateRegistryException.class);

    String key3 = "three";
    assertThatThrownBy(() -> jsonExpansionHandlerRegistry.obtain(key3))
        .isInstanceOf(UnregisteredKeyAccessException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetType() {
    assertThat(jsonExpansionHandlerRegistry.getType()).isEqualTo(RegistryType.JSON_EXPANSION_HANDLERS.name());
  }

  @Value
  @Builder
  private static class DummyJsonExpansionHandler implements JsonExpansionHandler {
    int something;

    @Override
    public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata) {
      return null;
    }
  }
}