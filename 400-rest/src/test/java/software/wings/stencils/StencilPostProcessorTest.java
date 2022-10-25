/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.stencils;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.DOUBLE_DEFAULT_VALUE;
import static software.wings.utils.WingsTestConstants.FLOAT_DEFAULT_VALUE;
import static software.wings.utils.WingsTestConstants.INTEGER_DEFAULT_VALUE;
import static software.wings.utils.WingsTestConstants.LONG_DEFAULT_VALUE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Value;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 6/28/16.
 */
public class StencilPostProcessorTest extends WingsBaseTest {
  @Mock private Injector injector;

  @InjectMocks @Inject private StencilPostProcessor stencilPostProcessor;

  private static void accept(JsonNode propertiesNode) {
    assertThat(propertiesNode.get("doubleTimeout").get("default").doubleValue()).isEqualTo(DOUBLE_DEFAULT_VALUE);
    assertThat(propertiesNode.get("integerTimeout").get("default").intValue()).isEqualTo(INTEGER_DEFAULT_VALUE);
    assertThat(propertiesNode.get("floatTimeout").get("default").floatValue()).isEqualTo(FLOAT_DEFAULT_VALUE);
    assertThat(propertiesNode.get("longTimeout").get("default").longValue()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(propertiesNode.get("enumField").get("default").textValue()).isEqualTo("hello");
  }

  /**
   * Sets up mocks.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUpMocks() {
    when(injector.getInstance(TestDataProvider.class)).thenReturn(new TestDataProvider());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldExpandStencilOnPostProcess() {
    List<Stencil> processedStencils = stencilPostProcessor.postProcess(
        Collections.singletonList(new StencilType(ExpandStencilObject.class)), APP_ID, new HashMap<>());

    assertThat(processedStencils)
        .hasSize(2)
        .extracting(Stencil::getName, Stencil::getJsonSchema, Stencil::getType)
        .contains(tuple("Value1",
                      JsonUtils.readTree("{\"type\":\"object\",\"properties\":{\"expand\":"
                          + "{\"type\":\"string\","
                          + "\"enum\":[\"Name1\",\"Name2\"],"
                          + "\"enumNames\":[\"Value1\",\"Value2\"],"
                          + "\"default\":\"Name1\"}}}"),
                      "TYPE"),
            tuple("Value2",
                JsonUtils.readTree("{\"type\":\"object\",\"properties\":{\"expand\":"
                    + "{\"type\":\"string\","
                    + "\"enum\":[\"Name1\",\"Name2\"],"
                    + "\"enumNames\":[\"Value1\",\"Value2\"],"
                    + "\"default\":\"Name2\"}}}"),
                "TYPE"));
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldNotExpandForStencilEnumOnPostProcess() {
    List<Stencil> processedStencils = stencilPostProcessor.postProcess(
        Collections.singletonList(new StencilType(EnumStencilObject.class)), APP_ID, new HashMap<>());

    assertThat(processedStencils)
        .hasSize(1)
        .extracting(Stencil::getName, Stencil::getJsonSchema, Stencil::getType)
        .contains(tuple("NAME",
            JsonUtils.readTree(
                "{\"type\":\"object\",\"properties\":{\"enumField\":{\"type\":\"string\",\"enum\":[\"Name1\",\"Name2\"],\"enumNames\":[\"Value1\",\"Value2\"]}}}"),
            "TYPE"));
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldSetDefaultValueForTheField() {
    List<Stencil> processedStencils = stencilPostProcessor.postProcess(
        Collections.singletonList(new StencilType(DefaultStencilObject.class)), APP_ID, new HashMap<>());

    assertThat(processedStencils)
        .hasSize(1)
        .extracting(Stencil::getName, Stencil::getJsonSchema, Stencil::getType)
        .contains(tuple("NAME",
            JsonUtils.readTree(
                "{\"type\":\"object\",\"properties\":{\"enumField\":{\"type\":\"string\",\"default\":\"hello\"}}}"),
            "TYPE"));
  }
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSetDefaultValueForTheAccessorMethod() {
    List<Stencil> processedStencils = stencilPostProcessor.postProcess(
        Collections.singletonList(new StencilType(DefaultMethodStencilObject.class)), APP_ID, new HashMap<>());

    processedStencils.stream()
        .map(Stencil::getJsonSchema)
        .map(inputNode -> inputNode.get("properties"))
        .forEach(StencilPostProcessorTest::accept);
  }

  /**
   * The interface Stencil object.
   */
  public interface StencilObject {}

  /**
   * The type Test data provider.
   */
  @Singleton
  public static class TestDataProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return ImmutableMap.of("Name1", "Value1", "Name2", "Value2");
    }
  }

  /**
   * The type Expand stencil object.
   */
  @Value
  public static class ExpandStencilObject implements StencilObject {
    @EnumData(enumDataProvider = TestDataProvider.class)
    @Expand(dataProvider = TestDataProvider.class)
    private String expand;
  }

  /**
   * The type Enum stencil object.
   */
  @Value
  public static class EnumStencilObject implements StencilObject {
    @EnumData(enumDataProvider = TestDataProvider.class) private String enumField;
  }

  /**
   * The type Enum stencil object.
   */
  @Value
  public static class DefaultStencilObject implements StencilObject {
    @DefaultValue("hello") private String enumField;
  }

  /**
   * The type Enum stencil object.
   */
  @Value
  public static class DefaultMethodStencilObject implements StencilObject {
    private String enumField;
    private Integer integerTimeout;
    private Long longTimeout;
    private Float floatTimeout;
    private Double doubleTimeout;

    @DefaultValue("" + INTEGER_DEFAULT_VALUE)
    public Integer getIntegerTimeout() {
      return integerTimeout;
    }

    @DefaultValue("" + LONG_DEFAULT_VALUE)
    public long getLongTimeout() {
      return longTimeout;
    }

    @DefaultValue("" + FLOAT_DEFAULT_VALUE)
    public Float getFloatTimeout() {
      return floatTimeout;
    }

    @DefaultValue("" + DOUBLE_DEFAULT_VALUE)
    public Double getDoubleTimeout() {
      return doubleTimeout;
    }

    @DefaultValue("hello")
    public String getEnumField() {
      return enumField;
    }
  }

  /**
   * The type Stencil type.
   */
  public static class StencilType implements Stencil<StencilObject> {
    private Class<? extends StencilObject> clazz;

    /**
     * Instantiates a new Stencil type.
     *
     * @param clazz the clazz
     */
    public StencilType(Class<? extends StencilObject> clazz) {
      this.clazz = clazz;
    }

    @Override
    public String getType() {
      return "TYPE";
    }

    @Override
    public Class<?> getTypeClass() {
      return clazz;
    }

    @Override
    public JsonNode getJsonSchema() {
      return JsonUtils.jsonSchema(clazz);
    }

    @Override
    public Object getUiSchema() {
      return new HashMap<String, String>();
    }

    @Override
    public String getName() {
      return "NAME";
    }

    @Override
    public OverridingStencil getOverridingStencil() {
      return new OverridingStencilType(this);
    }

    @Override
    public ExpandStencilObject newInstance(String id) {
      return on(clazz).create().get();
    }

    @Override
    public boolean matches(Object context) {
      return true;
    }

    @Override
    public StencilCategory getStencilCategory() {
      return StencilCategory.OTHERS;
    }

    @Override
    public Integer getDisplayOrder() {
      return DEFAULT_DISPLAY_ORDER;
    }
  }

  /**
   * The type Overriding stencil type.
   */
  public static class OverridingStencilType implements Stencil<StencilObject>, OverridingStencil<StencilObject> {
    private final StencilType stencilType;
    private Optional<String> overridingName = Optional.empty();
    private Optional<JsonNode> overridingJsonSchema = Optional.empty();

    /**
     * Instantiates a new Overriding stencil type.
     *
     * @param stencilType the stencil type
     */
    public OverridingStencilType(StencilType stencilType) {
      this.stencilType = stencilType;
    }

    @Override
    public String getType() {
      return stencilType.getType();
    }

    @Override
    public Class<?> getTypeClass() {
      return stencilType.getTypeClass();
    }

    @Override
    public JsonNode getJsonSchema() {
      return overridingJsonSchema.isPresent() ? overridingJsonSchema.get().deepCopy() : stencilType.getJsonSchema();
    }

    @Override
    public Object getUiSchema() {
      return stencilType.getUiSchema();
    }

    @Override
    public String getName() {
      return overridingName.orElse(stencilType.getName());
    }

    @Override
    public OverridingStencil getOverridingStencil() {
      return stencilType.getOverridingStencil();
    }

    @Override
    public ExpandStencilObject newInstance(String id) {
      return stencilType.newInstance(id);
    }

    @Override
    public boolean matches(Object context) {
      return stencilType.matches(context);
    }

    @Override
    public JsonNode getOverridingJsonSchema() {
      return overridingJsonSchema.orElse(null);
    }

    @Override
    public void setOverridingJsonSchema(JsonNode overridingJsonSchema) {
      this.overridingJsonSchema = Optional.ofNullable(overridingJsonSchema);
    }

    @Override
    public String getOverridingName() {
      return overridingName.orElse(null);
    }

    @Override
    public void setOverridingName(String overridingName) {
      this.overridingName = Optional.ofNullable(overridingName);
    }

    @Override
    public StencilCategory getStencilCategory() {
      return stencilType == null ? null : stencilType.getStencilCategory();
    }

    @Override
    public Integer getDisplayOrder() {
      return stencilType == null ? DEFAULT_DISPLAY_ORDER : stencilType.getDisplayOrder();
    }
  }
}
