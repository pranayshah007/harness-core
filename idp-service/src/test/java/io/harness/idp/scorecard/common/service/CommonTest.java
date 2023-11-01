/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.common.service;

import static io.harness.rule.OwnerRule.AASHRIT;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapoints.constants.DataPoints;
import io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CommonTest extends CategoryTest {
  public static HashSet<String> fetchConstants(Class<?> constantsClass) {
    HashSet<String> constantValues = new HashSet<>();

    try {
      Field[] fields = constantsClass.getDeclaredFields();

      for (Field field : fields) {
        if (field.getType() == String.class) {
          constantValues.add((String) field.get(null));
        }
      }
    } catch (IllegalAccessException e) {
      fail("Not able to fetch constants");
    }

    return constantValues;
  }

  public static List<Map<String, Object>> convertJsonToListOfMaps(String resourcePath) throws Exception {
    // Read the JSON content as a string
    String jsonContent = Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);

    // Initialize the Jackson ObjectMapper
    ObjectMapper objectMapper = new ObjectMapper();

    // Define a TypeReference for the list of maps
    TypeReference<List<Map<String, Object>>> typeReference = new TypeReference<>() {};

    // Parse the JSON content and return it as a list of maps
    return objectMapper.readValue(jsonContent, typeReference);
  }

  @Test
  @Owner(developers = AASHRIT)
  @Category(UnitTests.class)
  public void testConstants() {
    HashSet<String> constants = fetchConstants(Constants.class);
    List<Map<String, Object>> dataSourceObjects = new ArrayList<>();

    try {
      dataSourceObjects = convertJsonToListOfMaps("migrations/scorecard/dataSources.json");
    } catch (Exception e) {
      fail("Not able to fetch data sources");
    }
    assertFalse(dataSourceObjects.isEmpty());

    HashSet<String> dataSources = new HashSet<>();
    for (Map<String, Object> dataSourceObject : dataSourceObjects) {
      String dataSource = dataSourceObject.get("identifier").toString();
      dataSources.add(dataSource);
      assertTrue(constants.contains(dataSource));
    }

    List<Map<String, Object>> dataSourceLocationObjects = new ArrayList<>();
    try {
      dataSourceLocationObjects = convertJsonToListOfMaps("migrations/scorecard/datasourceLocations.json");
    } catch (Exception e) {
      fail("Not able to fetch datasource locations");
    }

    HashSet<String> dataSourceLocationConstants = fetchConstants(DataSourceLocations.class);
    HashSet<String> dataSourcesLocations = new HashSet<>();
    for (Map<String, Object> dataSourceLocationObject : dataSourceLocationObjects) {
      String dataSource = dataSourceLocationObject.get("dataSourceIdentifier").toString();
      String dataSourceLocation = dataSourceLocationObject.get("identifier").toString();
      dataSourcesLocations.add(dataSourceLocation);
      assertTrue(dataSources.contains(dataSource));
      assertTrue(dataSourceLocationConstants.contains(dataSourceLocation));
    }

    List<Map<String, Object>> dataPointObjects = new ArrayList<>();
    try {
      dataPointObjects = convertJsonToListOfMaps("migrations/scorecard/dataPoints.json");
    } catch (Exception e) {
      fail("Not able to fetch data points");
    }

    HashSet<String> dataPointConstants = fetchConstants(DataPoints.class);
    HashSet<String> dataPoints = new HashSet<>();
    for (Map<String, Object> dataPointObject : dataPointObjects) {
      String dataSource = dataPointObject.get("dataSourceIdentifier").toString();
      String dataSourceLocation = dataPointObject.get("dataSourceLocationIdentifier").toString();
      String dataPoint = dataPointObject.get("identifier").toString();
      dataPoints.add(dataPoint);
      assertTrue(dataSources.contains(dataSource));
      assertTrue(dataSourcesLocations.contains(dataSourceLocation));
      assertTrue(dataPointConstants.contains(dataPoint));
    }

    List<Map<String, Object>> checkObjects = new ArrayList<>();
    try {
      checkObjects = convertJsonToListOfMaps("migrations/scorecard/checks.json");
    } catch (Exception e) {
      fail("Not able to fetch checks");
    }

    for (Map<String, Object> checkObject : checkObjects) {
      List<Map<String, Object>> rules = (List<Map<String, Object>>) checkObject.get("rules");
      String dataSource = rules.get(0).get("data_source_identifier").toString();
      String dataPoint = rules.get(0).get("data_point_identifier").toString();
      assertTrue(dataSources.contains(dataSource));
      assertTrue(dataPoints.contains(dataPoint));
    }
  }
}
