/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.dto;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.SRE;

import io.harness.exception.ExceptionUtils;
import io.harness.exception.GraphQLException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

@Slf4j
public class ZendeskHelper {
  private ZendeskConfig zendeskConfig;

  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
  Map<String, String> ticketMap =
      Map.of("question", "question", "problem", "problem", "feature_request", "task", "other", "problem");

  @Inject
  public ZendeskHelper(@Named("zendeskApiConfiguration") ZendeskConfig zendeskConfig) {
    this.zendeskConfig = zendeskConfig;
  }

  public ZendeskResponseDTO create(String emailId, TicketType ticketType, ZendeskPriority priority, String subject,
      ZendeskDescription description, List<FormDataBodyPart> fileParts) {
    try {
      List<String> uploadTokens = uploadFile(fileParts);
      String ticketDescription = createDescription(emailId, ticketType, priority, subject, description);
      Map<String, Object> ticketData = new HashMap<>();
      Map<String, Object> ticketRoot = new HashMap<>();
      Map<String, Object> comment = new HashMap<>();

      comment.put("body", ticketDescription);
      comment.put("public", "True");
      if (!isEmpty(uploadTokens)) {
        comment.put("uploads", uploadTokens);
      }
      ticketData.put("subject", subject);
      ticketData.put("comment", comment);
      ticketData.put("type", ticketMap.get(ticketType.toString().toLowerCase(Locale.ROOT)));
      ticketData.put("priority", priority.toString().toLowerCase(Locale.ROOT));

      Map<String, Object> requester = new HashMap<>();
      requester.put("email", emailId);
      ticketData.put("requester", requester);
      ticketRoot.put("ticket", ticketData);
      ObjectMapper objectMapper = new ObjectMapper();
      String json = objectMapper.writeValueAsString(ticketRoot);
      RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
      Request request = new Request.Builder()
                            .url(zendeskConfig.baseUrl + "/api/v2/tickets")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", "Basic " + zendeskConfig.token)
                            .post(requestBody)
                            .build();
      try (Response response = okHttpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String bodyString = (null != response.body()) ? response.body().string() : "null";
          log.error("Response not Successful. Response body: {}", bodyString);
        }
        return ZendeskResponseDTO.builder().code(response.code()).message(response.body().string()).build();
      }
    } catch (Exception e) {
      log.error("Exception occurred during creation of zendesk ticket : {} ", ExceptionUtils.getMessage(e));
      return ZendeskResponseDTO.builder().code(400).message(ExceptionUtils.getMessage(e)).build();
    }
  }

  private String createDescription(
      String emailId, TicketType ticketType, ZendeskPriority priority, String subject, ZendeskDescription description) {
    return String.format("**Details**\nEmail:\n%s\n\n**Account Id**:\n%s\n\n**Module** :\n%s\n\n**Category**:\n%s"
            + "\n\n**Subject**:\n%s\n\n**Message**:\n%s"
            + "\n\n**Priority**:"
            + "\n%s\n\n**Website**:\n%s\n\n"
            + "**URL**:\n%s\n\n**Browser**:\n%s\n\n**Browser Size**:\n%s\n\n"
            + "**User OS**:\n%s\n\n",
        emailId, description.accountId, description.module, ticketType.name().toLowerCase(Locale.ROOT), subject,
        description.message, priority.name().toLowerCase(Locale.ROOT), description.website, description.url,
        description.userBrowser, description.browserResolution, description.userOS);
  }

  public ZendeskResponseDTO getToken() {
    try {
      String json = "{\n"
          + "    \"userIds\": [\n{"
          + "        \"name\": \"example\",\n"
          + "        \"type\": \"type\",\n"
          + "        \"provider\":\"Security Provider\"\n"
          + "    }\n]"
          + "}";
      RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
      Request request = new Request.Builder()
                            .url("https://platform.cloud.coveo.com/rest/search/v2/token")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", "Bearer " + zendeskConfig.coveoToken)
                            .post(requestBody)
                            .build();
      try (Response response = okHttpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String bodyString = (null != response.body()) ? response.body().string() : "null";
          log.error("Response not Successful. Response body: {}", bodyString);
          return ZendeskResponseDTO.builder().message("Response not Successful").code(response.code()).build();
        }
        InputStream responseBodyStream = response.body().byteStream();
        String responseBodyString = new Scanner(responseBodyStream, "UTF-8").useDelimiter("\\A").next();
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, String> responseMap = objectMapper.readValue(responseBodyString, HashMap.class);
        return ZendeskResponseDTO.builder().message(responseMap.get("token")).code(response.code()).build();
      }
    } catch (Exception e) {
      log.error("Exception occurred at getToken(). Returning 400", e);
      return ZendeskResponseDTO.builder().message(ExceptionUtils.getMessage(e)).code(400).build();
    }
  }
  private List<String> uploadFile(List<FormDataBodyPart> fileParts) throws IOException {
    List<String> tokens = new ArrayList<>();
    for (FormDataBodyPart filePart : fileParts) {
      FormDataContentDisposition contentDisposition = filePart.getFormDataContentDisposition();
      String fileName = contentDisposition.getFileName();
      byte[] binaryData = getInputBytes(filePart.getValueAs(InputStream.class));
      RequestBody requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("filename", fileName)
                                    .addFormDataPart("uploaded_data", fileName,
                                        RequestBody.create(binaryData, MediaType.parse("application/octet-stream")))
                                    .build();

      Request request = new Request.Builder()
                            .url(zendeskConfig.baseUrl + "/api/v2/uploads")
                            .addHeader("Authorization", "Basic " + zendeskConfig.token)
                            .post(requestBody)
                            .build();

      Response response = okHttpClient.newCall(request).execute();

      if (!response.isSuccessful()) {
        String bodyString = (null != response.body()) ? response.body().string() : "null";
        log.error("Response not Successful. Response body: {}", bodyString);
      }

      InputStream responseBodyStream = response.body().byteStream();
      String responseBodyString = new Scanner(responseBodyStream, "UTF-8").useDelimiter("\\A").next();
      ObjectMapper objectMapper = new ObjectMapper();
      HashMap<String, Object> responseMap = objectMapper.readValue(responseBodyString, HashMap.class);
      HashMap<String, String> uploadMap = (HashMap<String, String>) responseMap.get("upload");
      tokens.add(uploadMap.get("token"));
    }
    return tokens;
  }
  private byte[] getInputBytes(InputStream inputStream) {
    byte[] inputBytes = new byte[0];
    if (inputStream != null) {
      try {
        inputBytes = ByteStreams.toByteArray(inputStream);
      } catch (IOException exception) {
        throw new GraphQLException("Unable to convert input stream to bytes", SRE);
      }
    }
    return inputBytes;
  }
}
