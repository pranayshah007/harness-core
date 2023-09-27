/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.moduleversioninfo.runnable;
import static io.harness.ModuleType.*;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.annotations.dev.HarnessTeam.STO;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo.ModuleVersionInfoKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.NextGenConfiguration;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@ValidateOnExecution
@Singleton
@Slf4j
public class UpdateVersionInfoTask {
  private static final String COMING_SOON = "Coming Soon";

  private static final String JOB_INTERRUPTED =
      "UpdateVersionInfoTask Sync job was interrupted for the moduleName = {}  due to= {}";
  public static final String CHAOS_MANAGER_API = "manager/api/";
  private static final String PLATFORM = "Platform";
  private static final String RESOURCE = "resource";
  private static final String VERSION = "version";
  private static final String VERSION_INFO = "versionInfo";
  @Inject private MongoTemplate mongoTemplate;
  @Inject NextGenConfiguration nextGenConfiguration;
  List<ModuleVersionInfo> moduleVersionInfos;
  private static final String BASE_INFO_JSON_FILE = "mvi/baseinfo.json";

  public UpdateVersionInfoTask() {
    moduleVersionInfos = new ArrayList<>();
  }

  public void run() throws InterruptedException, RuntimeException {
    checkVersionChange();
  }

  private void checkVersionChange() {
    if (moduleVersionInfos.isEmpty()) {
      readFromFile();
    }

    // get the latest versions for the supported module types
    moduleVersionInfos.forEach(moduleVersionInfo -> {
      if (!moduleVersionInfo.getVersion().equals(COMING_SOON)) {
        try {
          String baseUrl = getBaseUrl(moduleVersionInfo.getModuleName());
          String latestVersion = getLatestVersion(moduleVersionInfo, baseUrl);
          moduleVersionInfo.setVersion(latestVersion);
        } catch (Exception e) {
          String errorMsg =
              String.format("Encountered an exception while trying to retrieve latest version of module: {}",
                  moduleVersionInfo.getDisplayName());
          log.error(errorMsg, e.getMessage(), e.getStackTrace());
          throw new UnexpectedException("Update VersionInfo Task Sync job interrupted due to:" + e.getMessage());
        }
      }
      updateModuleVersionInfoCollection(moduleVersionInfo);
    });
  }

  private String getBaseUrl(String moduleName) throws InvalidRequestException {
    String baseUrlInfoMsg = String.format("Getting baseUrl for moduleName= {}", moduleName);
    log.info(baseUrlInfoMsg);
    if (PLATFORM.equals(moduleName)) {
      return nextGenConfiguration.getNgManagerClientConfig().getBaseUrl();
    }
    ModuleType moduleType = ModuleType.valueOf(moduleName);

    switch (moduleType) {
      case CD:
        return nextGenConfiguration.getNgManagerClientConfig().getBaseUrl();
      case CHAOS:
        return new StringBuilder(nextGenConfiguration.getChaosServiceClientConfig().getBaseUrl())
            .append(CHAOS_MANAGER_API)
            .toString();
      case CE:
        return nextGenConfiguration.getCeNextGenClientConfig().getBaseUrl();
      case CF:
        return new StringBuilder(nextGenConfiguration.getFfServerClientConfig().getBaseUrl()).toString();
      case CI:
        return nextGenConfiguration.getCiManagerClientConfig().getBaseUrl();
      case SRM:
        return nextGenConfiguration.getCvngClientConfig().getBaseUrl();
      case STO:
        return nextGenConfiguration.getStoCoreClientConfig().getBaseUrl();
      default:
        String errorMsg = String.format("getBaseUrl() not supported for provided moduleType= {}.", moduleType);
        log.error(errorMsg);
        throw new InvalidRequestException(errorMsg);
    }
  }

  private String getLatestVersion(ModuleVersionInfo moduleVersionInfo, String baseUrl)
      throws IOException, InterruptedException {
    StringBuilder baseUrlBuilder = new StringBuilder();
    baseUrlBuilder.append(baseUrl);
    if (!baseUrl.endsWith("/")) {
      baseUrlBuilder.append('/');
    }
    baseUrlBuilder.append(VERSION);

    return getCurrentMicroserviceVersions(moduleVersionInfo.getModuleName(), baseUrlBuilder.toString());
  }

  // Reads from basic configuration file
  private void readFromFile() {
    try {
      ClassLoader classLoader = this.getClass().getClassLoader();
      String parsedYamlFile = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(BASE_INFO_JSON_FILE)), StandardCharsets.UTF_8);

      ObjectMapper objectMapper = new ObjectMapper();
      moduleVersionInfos = Arrays.asList(objectMapper.readValue(parsedYamlFile, ModuleVersionInfo[].class));
      moduleVersionInfos.forEach(module -> { getFormattedDateTime(module); });
    } catch (Exception ex) {
      log.error("Update VersionInfo Task Sync job interrupted", ex);
    }
  }

  private void getFormattedDateTime(ModuleVersionInfo module) {
    // as we'll add more modules this check will go away
    if (module.getVersion().equals(COMING_SOON)) {
      return;
    }

    // TODO: move this logic to UI in future.
    LocalDateTime dateTime = LocalDateTime.now();
    DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("MMM-dd-yyyy");
    String formattedDate = dateTime.format(myFormatObj);
    module.setLastModifiedAt(formattedDate);
  }

  private String getCurrentMicroserviceVersions(String serviceName, String serviceVersionUrl)
      throws IOException, InterruptedException, JSONException {
    if (StringUtils.isNullOrEmpty(serviceName) || StringUtils.isNullOrEmpty(serviceVersionUrl)) {
      return COMING_SOON;
    }
    HttpRequest request = HttpRequest.newBuilder()
                              .uri(URI.create(serviceVersionUrl))
                              .method("GET", HttpRequest.BodyPublishers.noBody())
                              .build();
    HttpResponse<String> response = null;
    try {
      response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      String errorMsg = String.format(JOB_INTERRUPTED, serviceName, e.getMessage());
      log.error(errorMsg);
      throw new IOException(errorMsg, e);
    } catch (InterruptedException e) {
      String errorMsg = String.format(JOB_INTERRUPTED, serviceName, e.getMessage());
      log.error(errorMsg);
      throw new InterruptedException(errorMsg);
    }
    if (response == null) {
      return "";
    }
    log.info("Getting version for ModuleName = {} with Request: {} and Response Body: {}", serviceName, request,
        response.body());
    String responseString = response.body().toString().trim();
    JSONObject jsonObject = new JSONObject(responseString);

    try {
      ModuleType moduleType = ModuleType.valueOf(serviceName);
      switch (moduleType) {
        case CF:
          if (jsonObject.has(VERSION_INFO)) {
            return jsonObject.get(VERSION_INFO).toString();
          } else {
            String errorMsg = String.format(
                "Response from FF version endpoint doesn't have field 'versionInfo'. response={}", jsonObject);
            log.error(errorMsg);
            throw new JSONException(errorMsg);
          }
        case STO:
          if (jsonObject.has(VERSION)) {
            return jsonObject.get(VERSION).toString();
          } else {
            String errorMsg = String.format(
                "Response from STO version endpoint doesn't have field 'version'. response={}", jsonObject);
            log.error(errorMsg);
            throw new JSONException(errorMsg);
          }
        default:
          JSONObject resourceJsonObject = (JSONObject) jsonObject.get(RESOURCE);
          JSONObject versionInfoJsonObject = (JSONObject) resourceJsonObject.get(VERSION_INFO);
          return versionInfoJsonObject.getString(VERSION);
      }
    } catch (JSONException je) {
      String errorMsg =
          String.format("Error while trying to jsonify the response=%s for moduleName=%s", responseString, serviceName);
      log.error(errorMsg, je);
      throw new JSONException(errorMsg, je);
    }
  }

  private void updateModuleVersionInfoCollection(ModuleVersionInfo module) {
    Criteria criteria = Criteria.where(ModuleVersionInfoKeys.moduleName).is(module.getModuleName());

    Update update = update(ModuleVersionInfoKeys.version, module.getVersion());

    update.set(ModuleVersionInfoKeys.uuid, module.getUuid())
        .set(ModuleVersionInfoKeys.versionUrl, module.getVersionUrl())
        .set(ModuleVersionInfoKeys.microservicesVersionInfo, module.getMicroservicesVersionInfo())
        .set(ModuleVersionInfoKeys.moduleName, module.getModuleName())
        .set(ModuleVersionInfoKeys.releaseNotesLink, module.getReleaseNotesLink())
        .set(ModuleVersionInfoKeys.lastModifiedAt, module.getLastModifiedAt())
        .set(ModuleVersionInfoKeys.displayName, module.getDisplayName());

    mongoTemplate.upsert(new Query(criteria), update, ModuleVersionInfo.class);
  }
}
