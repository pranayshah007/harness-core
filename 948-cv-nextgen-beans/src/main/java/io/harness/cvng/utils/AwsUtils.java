/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class AwsUtils {
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

  public static AWSCredentials getAwsCredentials(AwsConnectorDTO connectorDTO) {
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

  private AwsUtils() {}

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
