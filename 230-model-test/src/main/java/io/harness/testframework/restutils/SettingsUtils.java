/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.restutils;

import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.framework.Setup;

import software.wings.beans.SettingAttribute;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Assert;

public class SettingsUtils {
  private static String ACCOUNT_ID = "accountId";
  private static String SETTINGS_ENDPOINT = "/settings";
  private static String DEFAULT_USAGE_RESTRICTION =
      "{\"appEnvRestrictions\":[{\"appFilter\":{\"type\":\"GenericEntityFilter\",\"ids\":null,\"filterType\":\"ALL\"},\"envFilter\":{\"type\":\"EnvFilter\",\"ids\":null,\"filterTypes\":[\"PROD\"]}},{\"appFilter\":{\"type\":\"GenericEntityFilter\",\"ids\":null,\"filterType\":\"ALL\"},\"envFilter\":{\"type\":\"EnvFilter\",\"ids\":null,\"filterTypes\":[\"NON_PROD\"]}}]}\n";

  public static JsonPath createGCP(String bearerToken, String accountId, String cloudProviderName) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .given()
        .multiPart("file", new ScmSecret().decryptToString(new SecretName("gcp_playground")))
        .config(RestAssured.config().encoderConfig(
            encoderConfig().encodeContentTypeAs("multipart/form-data", ContentType.JSON)))
        .queryParam(ACCOUNT_ID, accountId)
        .formParam("name", cloudProviderName)
        .formParam("type", "GCP")
        .formParam("usageRestrictions", DEFAULT_USAGE_RESTRICTION)
        .contentType("multipart/form-data")
        .post(SETTINGS_ENDPOINT + "/upload")
        .jsonPath();
  }

  public static JsonPath updateGCP(
      String bearerToken, String accountId, String cloudProviderName, String GCPcloudProviderId) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .given()
        .multiPart("file", new ScmSecret().decryptToString(new SecretName("gcp_playground")))
        .config(RestAssured.config().encoderConfig(
            encoderConfig().encodeContentTypeAs("multipart/form-data", ContentType.JSON)))
        .queryParam(ACCOUNT_ID, accountId)
        .formParam("name", cloudProviderName)
        .formParam("type", "GCP")
        .contentType("multipart/form-data")
        .put(SETTINGS_ENDPOINT + "/" + GCPcloudProviderId + "/upload")
        .jsonPath();
  }

  public static JsonPath create(String bearerToken, String accountId, SettingAttribute setAttr) {
    Response respo = Setup.portal()
                         .auth()
                         .oauth2(bearerToken)
                         .queryParam(ACCOUNT_ID, accountId)
                         .body(setAttr)
                         .contentType(ContentType.JSON)
                         .post(SETTINGS_ENDPOINT);
    return respo.jsonPath();
  }

  public static JsonPath update(String bearerToken, String accountId, SettingAttribute setAttr, String cloudId) {
    Response respo = Setup.portal()
                         .auth()
                         .oauth2(bearerToken)
                         .queryParam(ACCOUNT_ID, accountId)
                         .body(setAttr)
                         .contentType(ContentType.JSON)
                         .put(SETTINGS_ENDPOINT + "/" + cloudId);
    return respo.jsonPath();
  }
  public static JsonPath updateConnector(
      String bearerToken, String accountId, String settingAttrId, SettingAttribute setAttr) {
    Response respo = Setup.portal()
                         .auth()
                         .oauth2(bearerToken)
                         .queryParam(ACCOUNT_ID, accountId)
                         .body(setAttr)
                         .contentType(ContentType.JSON)
                         .put(SETTINGS_ENDPOINT + "/" + settingAttrId);

    return respo.jsonPath();
  }

  public static JsonPath listCloudproviderConnector(String bearerToken, String accountId, String category) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(ACCOUNT_ID, accountId)
        .queryParam("search[0][field]", "category")
        .queryParam("search[0][op]", "EQ")
        .queryParam("search[0][value]", category)
        .contentType(ContentType.JSON)
        .get(SETTINGS_ENDPOINT)
        .jsonPath();
  }

  public static JsonPath validateConnectivity(String bearerToken, String accountId, SettingAttribute setAttr) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(ACCOUNT_ID, accountId)
        .body(setAttr, ObjectMapperType.JACKSON_2)
        .contentType(ContentType.JSON)
        .post("/settings/validate-connectivity")
        .jsonPath();
  }

  public static JsonPath validate(String bearerToken, String accountId, SettingAttribute setAttr) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(ACCOUNT_ID, accountId)
        .body(setAttr, ObjectMapperType.GSON)
        .contentType(ContentType.JSON)
        .post("/validate")
        .jsonPath();
  }

  public static JsonPath saveGCP(String bearerToken, String accntId) {
    return Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(ACCOUNT_ID, accntId)
        .body("", ObjectMapperType.GSON)
        .contentType(ContentType.JSON)
        .post("/upload")
        .jsonPath();
  }

  public static void delete(String bearerToken, String accountId, String settingAttrId) {
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(ACCOUNT_ID, accountId)
        .queryParam("isPluginSetting", true)
        .delete(SETTINGS_ENDPOINT + "/" + settingAttrId)
        .then()
        .statusCode(200);
  }

  /*
    Use this to check how many total number of cloudprovider/connectors are present. Use this in case of,
    1. Should be handy in verifying the new ConnectorDisconnectHandler created by count
    2. Should be handy in verifying the existing ConnectorDisconnectHandler is deleted
   */
  public static int getCloudproviderConnectorCount(String bearerToken, String accountId, String category) {
    // Get all the cloudprovider/connectors
    JsonPath connectors = SettingsUtils.listCloudproviderConnector(bearerToken, accountId, category);
    assertThat(connectors).isNotNull();

    ArrayList<HashMap<String, String>> hashMaps =
        (ArrayList<HashMap<String, String>>) connectors.getMap("resource").get("response");
    for (HashMap<String, String> data : hashMaps) {
      if (!data.get("category").equals(category)) {
        Assert.fail("ERROR: Found an entry which is not of type '" + category + "'");
      }
    }
    return hashMaps.size();
  }

  /*
    Use this function when you want to check if the cloudprovider/ConnectorDisconnectHandler with specific name exist.
    Use this in case of,
    1. to check and create new ConnectorDisconnectHandler (create only when it's not found)
    2. to verify new ConnectorDisconnectHandler created
    3. to delete a ConnectorDisconnectHandler and check if its deleted
   */
  public static boolean checkCloudproviderConnectorExist(
      String bearerToken, String accountId, String category, String name) {
    boolean connFound = false;
    // Get all the cloudprovider/connectors
    JsonPath connectors = SettingsUtils.listCloudproviderConnector(bearerToken, accountId, category);
    assertThat(connectors).isNotNull();

    ArrayList<HashMap<String, String>> hashMaps =
        (ArrayList<HashMap<String, String>>) connectors.getMap("resource").get("response");

    for (HashMap<String, String> data : hashMaps) {
      if (data.get("name").equals(name)) {
        connFound = true;
        break;
      }
    }
    return connFound;
  }
}
