/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.jayway.jsonpath.internal.DefaultsImpl.INSTANCE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.experimental.UtilityClass;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@UtilityClass
@OwnedBy(PL)
public class GlobalSecretManagerUtils {
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Configuration configuration = new Configuration.ConfigurationBuilder()
                                                         .jsonProvider(INSTANCE.jsonProvider())
                                                         .mappingProvider(INSTANCE.mappingProvider())
                                                         .options(INSTANCE.options())
                                                         .build();

  public static boolean isNgHarnessSecretManager(NGSecretManagerMetadata ngSecretManagerMetadata) {
    return ngSecretManagerMetadata != null
        && (Boolean.TRUE.equals(ngSecretManagerMetadata.getHarnessManaged())
            || HARNESS_SECRET_MANAGER_IDENTIFIER.equals(ngSecretManagerMetadata.getIdentifier()));
  }

  public static String getValueByJsonPath(Object input, String key) throws JsonProcessingException {
    return isValidJson(input) ? getValueByJsonPath(parse(input), key) : input.toString();
  }

  public static String getValueByJsonPath(String input, String key) throws JsonProcessingException {
    return isValidJson(input) ? getValueByJsonPath(parse(input), key) : input;
  }

  public static String getValueByJsonPath(DocumentContext context, String key) throws JsonProcessingException {
    Object value = null;
    if (isEmpty(key)) {
      value = context.read("$");
    } else {
      try {
        value = context.read("$." + key);
      } catch (PathNotFoundException exception) {
        value = context.read("$.['" + key + "']"); // This is to handle json key with dots
      }
    }

    if (value instanceof String) {
      return value.toString();
    }

    return mapper.writeValueAsString(value);
  }

  public static DocumentContext parse(Object json) {
    return JsonPath.using(configuration).parse(json);
  }

  public static DocumentContext parse(String json) {
    return JsonPath.using(configuration).parse(json);
  }

  public boolean isValidJson(Object json) {
    try {
      new JSONObject(json);
    } catch (JSONException e) {
      try {
        new JSONArray(json);
      } catch (JSONException ne) {
        return false;
      }
    }
    return true;
  }

  public boolean isValidJson(String json) {
    try {
      new JSONObject(json);
    } catch (JSONException e) {
      try {
        new JSONArray(json);
      } catch (JSONException ne) {
        return false;
      }
    }
    return true;
  }
}
