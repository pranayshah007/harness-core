package io.harness.cvng.utils;

import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.serializer.JsonUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
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
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CloudWatchUtils {
  public static final String CLOUDWATCH_GET_METRIC_DATA_API_TARGET = "GraniteServiceVersion20100801.GetMetricData";
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

  public static Map<String, Object> getDslEnvVariables(String region, String group, String expression,
      String metricName, String metricIdentifier, String service, AwsConnectorDTO connectorDTO,
      boolean collectHostData) {
    Map<String, Object> dslEnvVariables =
        populateCommonDslEnvVariables(region, group, service, connectorDTO, collectHostData);
    dslEnvVariables.put("body", getRequestPayload(expression, metricName, metricIdentifier));
    return dslEnvVariables;
  }

  public static Map<String, Object> getDslEnvVariables(String region, String group, String service,
      AwsConnectorDTO connectorDTO, List<CloudWatchMetricInfoDTO> cloudWatchMetricInfoDTOs, boolean collectHostData) {
    Map<String, Object> dslEnvVariables =
        populateCommonDslEnvVariables(region, group, service, connectorDTO, collectHostData);

    List<List<Map<String, Object>>> requestBodies = null;
    List<String> metricNames = new ArrayList<>();
    List<String> metricIdentifiers = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(cloudWatchMetricInfoDTOs)) {
      requestBodies =
          cloudWatchMetricInfoDTOs.stream()
              .map(dto -> {
                metricNames.add(dto.getMetricName());
                metricIdentifiers.add(dto.getMetricIdentifier());
                return getRequestPayload(dto.getFinalExpression(), dto.getMetricName(), dto.getMetricIdentifier());
              })
              .collect(Collectors.toList());
    }
    dslEnvVariables.put("bodies", requestBodies);
    dslEnvVariables.put("metricNames", metricNames);
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    return dslEnvVariables;
  }

  private static Map<String, Object> populateCommonDslEnvVariables(
      String region, String group, String service, AwsConnectorDTO connectorDTO, boolean collectHostData) {
    AWSCredentials awsCredentials = getAwsCredentials(connectorDTO);
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("region", region);
    dslEnvVariables.put("groupName", group);
    dslEnvVariables.put("awsSecretKey", awsCredentials.getAWSSecretKey());
    dslEnvVariables.put("awsAccessKey", awsCredentials.getAWSAccessKeyId());
    dslEnvVariables.put("serviceName", service);
    dslEnvVariables.put("url", getBaseUrl(region, service));
    dslEnvVariables.put("awsTarget", CLOUDWATCH_GET_METRIC_DATA_API_TARGET);
    dslEnvVariables.put("collectHostData", collectHostData);
    return dslEnvVariables;
  }

  private static AWSCredentials getAwsCredentials(AwsConnectorDTO connectorDTO) {
    AWSCredentials awsCredentials = null;
    AwsCredentialType awsCredentialType = connectorDTO.getCredential().getAwsCredentialType();
    if (AwsCredentialType.INHERIT_FROM_DELEGATE.equals(awsCredentialType)
        || AwsCredentialType.IRSA.equals(awsCredentialType)) {
      CrossAccountAccessDTO crossAccountAccessDTO = connectorDTO.getCredential().getCrossAccountAccess();
      if (Objects.nonNull(crossAccountAccessDTO)) {
        String arn = crossAccountAccessDTO.getCrossAccountRoleArn();
        String externalId = crossAccountAccessDTO.getExternalId();
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRoleArn(arn);
        roleInfo.setExternalId(externalId);
        STSProfileCredentialsServiceProvider serviceProvider = new STSProfileCredentialsServiceProvider(roleInfo);
        awsCredentials = serviceProvider.getCredentials();
      } else {
        awsCredentials = InstanceProfileCredentialsProvider.getInstance().getCredentials();
      }
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

  private static List<Map<String, Object>> getRequestPayload(
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
