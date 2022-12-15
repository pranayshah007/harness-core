/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.NameValuePairWithDefault;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CustomSecretManagerHelperTest extends CategoryTest {
  private static final String ENVIRONMENT_VARIABLES = "environmentVariables";
  private ObjectMapper objectMapper;
  @InjectMocks CustomSecretManagerHelper customSecretManagerHelper;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testMergeDefaultValuesInInputValues() {
    Map<String, List<NameValuePairWithDefault>> inputValues = new HashMap<>();
    Map<String, List<NameValuePairWithDefault>> connectorValues = new HashMap<>();

    NameValuePairWithDefault var1 =
        NameValuePairWithDefault.builder().name("var1").value("value1").type("String").build();
    NameValuePairWithDefault var2 =
        NameValuePairWithDefault.builder().name("var2").value("value2").type("String").build();
    NameValuePairWithDefault var3 =
        NameValuePairWithDefault.builder().name("var3").value("value3").type("String").useAsDefault(true).build();

    List<NameValuePairWithDefault> inputEnvironmentVariables = new LinkedList<>();
    inputEnvironmentVariables.add(var1);
    inputEnvironmentVariables.add(var2);

    List<NameValuePairWithDefault> connectorEnvironmentVariables = new LinkedList<>();
    connectorEnvironmentVariables.add(var1);
    connectorEnvironmentVariables.add(var2);
    connectorEnvironmentVariables.add(var3);

    inputValues.put(ENVIRONMENT_VARIABLES, inputEnvironmentVariables);

    connectorValues.put(ENVIRONMENT_VARIABLES, connectorEnvironmentVariables);

    customSecretManagerHelper.mergeDefaultValuesInInputValues(inputValues, connectorValues);

    // Assert size is 1 -> there is only environment variables
    assertThat(inputValues.size()).isEqualTo(1);

    // assert single key
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES)).isNotNull();

    // Assert env vars now has 3 fields
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES).size()).isEqualTo(3);

    // Assert it has field which had useAsDefault as true
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES).stream().filter(list -> list.getName().equals("var3")).count())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testMergeDefaultValuesInInputValuesWithuseDefaultAsFalse() {
    Map<String, List<NameValuePairWithDefault>> inputValues = new HashMap<>();
    Map<String, List<NameValuePairWithDefault>> connectorValues = new HashMap<>();

    NameValuePairWithDefault var1 =
        NameValuePairWithDefault.builder().name("var1").value("value1").type("String").build();
    NameValuePairWithDefault var2 =
        NameValuePairWithDefault.builder().name("var2").value("value2").type("String").build();
    NameValuePairWithDefault var3 = NameValuePairWithDefault.builder()
                                        .name("var3")
                                        .value("value3")
                                        .type("String")
                                        .useAsDefault(Boolean.FALSE)
                                        .build();

    List<NameValuePairWithDefault> inputEnvironmentVariables = new LinkedList<>();
    inputEnvironmentVariables.add(var1);
    inputEnvironmentVariables.add(var2);

    List<NameValuePairWithDefault> connectorEnvironmentVariables = new LinkedList<>();
    connectorEnvironmentVariables.add(var1);
    connectorEnvironmentVariables.add(var2);
    connectorEnvironmentVariables.add(var3);

    inputValues.put(ENVIRONMENT_VARIABLES, inputEnvironmentVariables);

    connectorValues.put(ENVIRONMENT_VARIABLES, connectorEnvironmentVariables);

    customSecretManagerHelper.mergeDefaultValuesInInputValues(inputValues, connectorValues);

    // Assert size is 1 -> there is only environment variables
    assertThat(inputValues.size()).isEqualTo(1);

    // assert single key
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES)).isNotNull();

    // Assert env vars now has 3 fields
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES).size()).isEqualTo(2);

    // Assert it has field which had useAsDefault as true
    assertThat(inputValues.get(ENVIRONMENT_VARIABLES).stream().filter(list -> list.getName().equals("var3")).count())
        .isEqualTo(0);
  }
}
