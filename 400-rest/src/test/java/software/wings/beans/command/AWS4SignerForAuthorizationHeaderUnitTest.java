/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.delegatetasks.cv.CustomAPMDataCollector.getUnsafeHttpClient;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.BUCKET_NAME;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.network.Http;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.helpers.ext.apm.APMRestClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@RunWith(JUnitParamsRunner.class)
public class AWS4SignerForAuthorizationHeaderUnitTest extends WingsBaseTest {
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAWSV4AuthorizationHeader() throws Exception {
    String expectedAuthHeader =
        "AWS4-HMAC-SHA256 Credential=ACCESS_KEY/20200421/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=98156e562a14473b061764012348e9afb72d1c90cac7f8659c11941c6fe140e7";
    Date now = new Date();
    String accessKey = "", secretKey = "";

    URL endPointUrl = new URL(
        "https://aps-workspaces.us-east-1.amazonaws.com/workspaces/ws-bd297196-b5ca-48c5-9857-972fe759354f/api/v1/query_range?start=1620075620&end=1620077083&query=http_requests_total&step=60s");
    AWSAuthHeader header = new AWSAuthHeader(endPointUrl, "GET", "aps", "us-east-1");
    Map<String, String> canQuery = new LinkedHashMap<>();
    canQuery.put("end", "1620077083");
    canQuery.put("query", "http_requests_total");
    canQuery.put("start", "1620075620");
    canQuery.put("step", "60s");
    Map<String, String> headers = new HashMap<>();
    String authorization =
        header.computeSignature(headers, canQuery, AWSS4SignerBase.EMPTY_BODY_SHA256, accessKey, secretKey);

    headers.put("Authorization", authorization);
    System.out.println(header);
    Map<String, Object> map = new HashMap<>();
    headers.forEach((k, v) -> map.put(k, v));

    Map<String, Object> qmap = new HashMap<>();
    canQuery.forEach((k, v) -> qmap.put(k, v));

    String canonc = AWS4SignerForAuthorizationHeader.getEndpointWithCanonicalizedResourcePath(endPointUrl, true);
    long code = getAPMRestClient(
        "https://aps-workspaces.us-east-1.amazonaws.com/workspaces/ws-bd297196-b5ca-48c5-9857-972fe759354f/api/v1/",
        false)
                    .collect("query_range", map, qmap)
                    .execute()
                    .code();
    assertThat(headers.toString()).isEqualTo(expectedAuthHeader);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testAWSV4AuthorizationHeaderWithNonEmptyToken() throws MalformedURLException {
    String expectedAuthHeader =
        "AWS4-HMAC-SHA256 Credential=ACCESS_KEY/20200421/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=5e1d85d87e1c23947ccf716584d09a9d88301a1e83b957c14ef8a83185d00383";
    Date now = new Date(1587504698159L);
    URL endPointUrl = new URL("https://" + BUCKET_NAME + ".s3.amazonaws.com"
        + "/" + ARTIFACT_PATH);
    String authorizationHeader = AWS4SignerForAuthorizationHeader.getAWSV4AuthorizationHeader(
        endPointUrl, "us-east-1", ACCESS_KEY, String.valueOf(SECRET_KEY), now, "TOKEN");
    assertThat(authorizationHeader).isEqualTo(expectedAuthHeader);
  }

  public APMRestClient getAPMRestClient(final String baseUrl, final boolean validateCert) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(validateCert ? Http.getSafeOkHttpClientBuilder(baseUrl, 15, 60).build()
                                                       : getUnsafeHttpClient(baseUrl))
                                  .build();
    return retrofit.create(APMRestClient.class);
  }
}
