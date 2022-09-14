package io.harness.cvng.utils;

import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.serializer.JsonUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CloudWatchUtils {
  public static String getBaseUrl(String region, String serviceName) {
    return "https://" + serviceName + "." + region + ".amazonaws.com";
  }

  public static List<Map<String, Object>> getRequestPayload(
      String expression, String metricName, String metricIdentifier) {
    List<Map<String, Object>> metricQueries = new ArrayList<>();
    Map<String, Object> queryMap = new HashMap<>();
    queryMap.put("Expression", expression);
    queryMap.put("Label", metricName);
    queryMap.put("Id", metricIdentifier);
    queryMap.put("Period", 60);
    metricQueries.add(queryMap);
    return metricQueries;
  }

  public static Map<String, Object> getDslEnvVariables(String region, String group, String expression,
      String metricName, String metricIdentifier, String service, AwsConnectorDTO connectorDTO) {
    AWSCredentials awsCredentials = getAwsCredentials(connectorDTO);
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("region", region);
    dslEnvVariables.put("groupName", group);
    dslEnvVariables.put("awsSecretKey", awsCredentials.getAWSSecretKey());
    dslEnvVariables.put("awsAccessKey", awsCredentials.getAWSAccessKeyId());
    dslEnvVariables.put("body", CloudWatchUtils.getRequestPayload(expression, metricName, metricIdentifier));
    dslEnvVariables.put("serviceName", service);
    dslEnvVariables.put("url", getBaseUrl(region, service));
    dslEnvVariables.put("awsTarget", "GraniteServiceVersion20100801.GetMetricData");
    return dslEnvVariables;
  }

  public static AWSCredentials getAwsCredentials(AwsConnectorDTO connectorDTO) {
    AWSCredentials awsCredentials = null;
    AwsCredentialType awsCredentialType = connectorDTO.getCredential().getAwsCredentialType();
    if (AwsCredentialType.INHERIT_FROM_DELEGATE.equals(awsCredentialType)
        || AwsCredentialType.IRSA.equals(awsCredentialType)) {
      // TODO: Add handling for STS
      awsCredentials = InstanceProfileCredentialsProvider.getInstance().getCredentials();
    } else {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) connectorDTO.getCredential().getConfig();
      String accessKeyId = StringUtils.isEmpty(awsManualConfigSpecDTO.getAccessKey())
          ? String.valueOf(awsManualConfigSpecDTO.getAccessKeyRef().getDecryptedValue())
          : awsManualConfigSpecDTO.getAccessKey();
      String secretKey = String.valueOf(awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue());
      awsCredentials = ManualAWSCredentials.builder().accessKeyId(accessKeyId).secretKey(secretKey).build();
    }
    return awsCredentials;
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
      Set<String> uniqueRegions = new HashSet<>();
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

  @Builder
  private static class ManualAWSCredentials implements AWSCredentials {
    private String accessKeyId;
    private String secretKey;
    @Override
    public String getAWSAccessKeyId() {
      return accessKeyId;
    }

    @Override
    public String getAWSSecretKey() {
      return secretKey;
    }
  }
}
