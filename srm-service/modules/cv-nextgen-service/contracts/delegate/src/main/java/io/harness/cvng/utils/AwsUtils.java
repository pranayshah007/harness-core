/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.serializer.JsonUtils;

import software.wings.beans.AwsCrossAccountAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@UtilityClass
@Slf4j
public class AwsUtils {
  private static final String AWS_CONTAINER_CREDENTIALS_RELATIVE_URI = "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI";
  private static final String AWS_CONTAINER_CREDENTIALS_FULL_URI = "AWS_CONTAINER_CREDENTIALS_FULL_URI";
  public String getBaseUrl(String region, String serviceName) {
    return "https://" + serviceName + "." + region + ".amazonaws.com";
  }
  public List<String> getAwsRegions() {
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

  public AwsAccessKeys getAwsCredentials(AwsConnectorDTO awsConnectorDTO) {
    AwsInternalConfig awsInternalConfig = createAwsInternalConfig(awsConnectorDTO);
    AwsCredentialsProvider awsCredentialsProvider = getAwsCredentialsProvider(awsInternalConfig);
    AwsCredentials awsCredentials = awsCredentialsProvider.resolveCredentials();
    String sessionToken = null;
    if (awsCredentials instanceof AwsSessionCredentials) {
      sessionToken = ((AwsSessionCredentials) awsCredentials).sessionToken();
    }
    AwsAccessKeys awsAccessKeys = AwsAccessKeys.builder()
                                      .accessKeyId(awsCredentials.accessKeyId())
                                      .secretAccessKey(awsCredentials.secretAccessKey())
                                      .sessionToken(sessionToken)
                                      .build();
    return awsAccessKeys;
  }
  @VisibleForTesting
  protected AwsInternalConfig createAwsInternalConfig(AwsConnectorDTO awsConnectorDTO) {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    if (awsConnectorDTO == null) {
      throw new InvalidArgumentsException("Aws Connector DTO cannot be null");
    }

    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    if (MANUAL_CREDENTIALS == credential.getAwsCredentialType()) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) credential.getConfig();

      String accessKey = getSecretAsStringFromPlainTextOrSecretRef(
          awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef());

      if (StringUtils.isEmpty(accessKey)) {
        throw new InvalidArgumentsException(Pair.of("accessKeyId", "Missing or empty"));
      }

      char[] secretKey = awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue();

      awsInternalConfig = AwsInternalConfig.builder().accessKey(accessKey.toCharArray()).secretKey(secretKey).build();

    } else if (INHERIT_FROM_DELEGATE == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseEc2IamCredentials(true);
    } else if (IRSA == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseIRSA(true);
    }

    CrossAccountAccessDTO crossAccountAccess = credential.getCrossAccountAccess();
    if (crossAccountAccess != null) {
      awsInternalConfig.setAssumeCrossAccountRole(true);
      awsInternalConfig.setCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                                      .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
                                                      .externalId(crossAccountAccess.getExternalId())
                                                      .build());
    }
    return awsInternalConfig;
  }
  @VisibleForTesting
  protected AwsCredentialsProvider getAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    AwsCredentialsProvider credentialsProvider;
    if (awsConfig.isUseEc2IamCredentials()) {
      credentialsProvider = getIamRoleAwsCredentialsProvider();
    } else if (awsConfig.isUseIRSA()) {
      credentialsProvider = getIrsaAwsCredentialsProvider(awsConfig);
    } else {
      credentialsProvider = getStaticAwsCredentialsProvider(awsConfig);
    }
    if (awsConfig.isAssumeCrossAccountRole()) {
      return getStsAssumeRoleAwsCredentialsProvider(awsConfig, credentialsProvider);
    }
    return credentialsProvider;
  }

  private AwsCredentialsProvider getIamRoleAwsCredentialsProvider() {
    try {
      if (System.getenv(AWS_CONTAINER_CREDENTIALS_RELATIVE_URI) != null
          || System.getenv(AWS_CONTAINER_CREDENTIALS_FULL_URI) != null) {
        return ContainerCredentialsProvider.builder().build();
      } else {
        return InstanceProfileCredentialsProvider.create();
      }
    } catch (SecurityException var2) {
      if (log.isDebugEnabled()) {
        log.debug("Security manager did not allow access to the ECS credentials environment variable"
            + " AWS_CONTAINER_CREDENTIALS_RELATIVE_URI or the container full URI environment variable"
            + " AWS_CONTAINER_CREDENTIALS_FULL_URI. Please provide access to this environment variable "
            + "if you want to load credentials from ECS Container.");
      }
      return InstanceProfileCredentialsProvider.create();
    }
  }

  private AwsCredentialsProvider getIrsaAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    WebIdentityTokenFileCredentialsProvider.Builder providerBuilder = WebIdentityTokenFileCredentialsProvider.builder();
    providerBuilder.roleSessionName(awsConfig.getAccountId() + UUIDGenerator.generateUuid());
    return providerBuilder.build();
  }

  private AwsCredentialsProvider getStaticAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    AwsBasicCredentials awsBasicCredentials =
        AwsBasicCredentials.create(new String(awsConfig.getAccessKey()), new String(awsConfig.getSecretKey()));
    return StaticCredentialsProvider.create(awsBasicCredentials);
  }

  private AwsCredentialsProvider getStsAssumeRoleAwsCredentialsProvider(
      AwsInternalConfig awsConfig, AwsCredentialsProvider primaryCredentialProvider) {
    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                              .roleArn(awsConfig.getCrossAccountAttributes().getCrossAccountRoleArn())
                                              .roleSessionName(UUID.randomUUID().toString())
                                              .externalId(awsConfig.getCrossAccountAttributes().getExternalId())
                                              .build();

    StsClient stsClient = StsClient.builder()
                              .region(isNotBlank(awsConfig.getDefaultRegion()) ? Region.of(awsConfig.getDefaultRegion())
                                                                               : Region.of(AWS_DEFAULT_REGION))
                              .credentialsProvider(primaryCredentialProvider)
                              .build();

    return StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(assumeRoleRequest).build();
  }

  @Value
  @Builder
  public static class AwsAccessKeys {
    String accessKeyId;
    String secretAccessKey;
    String sessionToken;
  }
}