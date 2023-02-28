/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

@Slf4j
public class AwsUtils {
  @Inject private static SimpleAwsClientHelper simpleAwsClientHelper;
  public static String getBaseUrl(String region, String serviceName) {
    return "https://" + serviceName + "." + region + ".amazonaws.com";
  }
  public static List<String> getAwsRegions() {
    List<String> awsRegions = new ArrayList<>();
    try {
      HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI("https://api.regional-table.region-services.aws.a2z.com/index.json"))
                                .GET()
                                .build();
      HttpResponse<String> response =
          HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
      Map<String, Object> rawResponse = JsonUtils.asMap(response.body());
      Set<String> uniqueRegions = new TreeSet<>();
      if (Objects.nonNull(rawResponse)) {
        JsonNode responseJson = JsonUtils.asTree(rawResponse);
        ArrayNode arrayNode = (ArrayNode) responseJson.get("prices");
        arrayNode.forEach(node -> {
          JsonNode regionNode = node.get("attributes").get("aws:region");
          if (Objects.nonNull(regionNode)) {
            String region = regionNode.asText();
            uniqueRegions.add(region);
          }
        });
      }
      awsRegions.addAll(uniqueRegions);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      log.error("Error while fetching AWS regions", e);
    }
    return awsRegions;
  }

  public static AwsAccessKeysPair getAwsCredentials(AwsConnectorDTO awsConnectorDTO) {
    AwsCredentials awsCredentials = simpleAwsClientHelper.getAwsCredentials(awsConnectorDTO);
    return AwsAccessKeysPair.builder()
        .accessKeyId(awsCredentials.accessKeyId())
        .secretAccessKey(awsCredentials.secretAccessKey())
        .build();
  }

  private AwsUtils() {}
  @Value
  @Builder
  public static class AwsAccessKeysPair {
    String accessKeyId;
    String secretAccessKey;
  }
}
