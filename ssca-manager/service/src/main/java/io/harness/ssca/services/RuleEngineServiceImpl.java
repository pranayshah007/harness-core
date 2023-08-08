/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.ssca.beans.AllowList;
import io.harness.ssca.beans.DenyList;
import io.harness.ssca.beans.RuleDTO;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.json.JSONObject;

@Slf4j
public class RuleEngineServiceImpl implements RuleEngineService {
  @Inject NextGenService nextGenService;

  @Override
  public RuleDTO getRules(String accountId, String orgIdentifier, String projectIdentifier, String policyFileId) {
    String filePath = getFilePath(accountId, orgIdentifier, projectIdentifier, policyFileId);
    Response response = nextGenService.getRequest(null, null, filePath, accountId);

    ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    String data;
    try {
      JSONObject jsonObject = new JSONObject(response.body().string());
      data = jsonObject.getString("data");
    } catch (IOException e) {
      log.error(String.format("Failed to read allow list"));
      throw new RuntimeException("Failed Deserialization.");
    }

    AllowList allowList;
    DenyList denyList;

    try {
      allowList = objectMapper.readValue(data.getBytes(StandardCharsets.UTF_8), AllowList.class);
    } catch (IOException e) {
      log.error(String.format("Failed to read allow list"));
      throw new RuntimeException("Failed Deserialization.");
    }

    try {
      denyList = objectMapper.readValue(data.getBytes(StandardCharsets.UTF_8), DenyList.class);
    } catch (IOException e) {
      log.error(String.format("Failed to read deny list, Exception: %s", e));
      throw new RuntimeException("Failed Deserialization.");
    }

    return RuleDTO.builder().allowList(allowList).denyList(denyList).build();
  }

  private String getFilePath(String accountId, String orgIdentifier, String projectIdentifier, String policyFileId) {
    String encodedQueryFileId = null;
    try {
      encodedQueryFileId = URLEncoder.encode(policyFileId, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      log.error("Error while encoding Policy File Id");
      throw new RuntimeException();
    }

    /*String[] splitFileId = policyFileId.split(":");
    String actualFileName;
    Scope scope;
    if (splitFileId.length == 1) {
      scope = Scope.PROJECT;
      actualFileName = splitFileId[0];
    } else {
      scope = Scope.getScope(splitFileId[0]);
      actualFileName = splitFileId[1];
    }

    if (scope == Scope.ACCOUNT) {
      return String.format(
          "file-store/files%s/download?routingId=%s&accountIdentifier=%s", actualFileName, accountId, accountId);
    }
    if (scope == Scope.ORG) {
      return String.format("file-store/files%s/download?routingId=%s&accountIdentifier=%s&orgIdentifier=%s",
          actualFileName, accountId, accountId, orgIdentifier);
    }
    if (scope == Scope.PROJECT) {
      return String.format(
          "file-store/files%s/download?routingId=%s&accountIdentifier=%s&orgIdentifier=%s&projectIdentifier=%s",
          actualFileName, accountId, accountId, orgIdentifier, projectIdentifier);
    }*/
    return String.format(
        "file-store/files/%s/content?routingId=%s&accountIdentifier=%s&orgIdentifier=%s&projectIdentifier=%s",
        encodedQueryFileId, accountId, accountId, orgIdentifier, projectIdentifier);
  }
}
