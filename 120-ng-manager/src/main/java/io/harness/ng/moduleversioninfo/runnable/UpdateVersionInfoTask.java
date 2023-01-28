/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.moduleversioninfo.runnable;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo.ModuleVersionInfoKeys;
import io.harness.exception.UnexpectedException;
import io.harness.ng.NextGenConfiguration;

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
import org.json.JSONObject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(CDP)
@ValidateOnExecution
@Singleton
@Slf4j
public class UpdateVersionInfoTask {
  @Inject private MongoTemplate mongoTemplate;
  @Inject NextGenConfiguration nextGenConfiguration;

  List<ModuleVersionInfo> allModulesFromDB;
  private static final String pathToFile = "mvi/baseinfo.json";
  // Edit here for adding new module
  private static final String totalModules = "CCM_CD_CI_FF_Platform_SRM_SRT_Delegate";

  public UpdateVersionInfoTask() {
    allModulesFromDB = new ArrayList<>();
  }

  public void run() throws InterruptedException {
    checkVersionChange();
  }

  private void checkVersionChange() {
    // read from DB
    List<String> moduleNames = new ArrayList<>(List.of(totalModules.split("_")));
    moduleNames.forEach(moduleName -> {
      Criteria criteria = Criteria.where(ModuleVersionInfoKeys.moduleName).is(moduleName);
      ModuleVersionInfo newModule = mongoTemplate.findOne(new Query(criteria), ModuleVersionInfo.class);
      if (newModule != null) {
        allModulesFromDB.add(newModule);
      }
    });

    if (allModulesFromDB.isEmpty()) {
      // read from base file
      readFromFile();
    }

    // get current versions
    allModulesFromDB.forEach(module -> {
      if (!module.getVersion().equals("Coming Soon")) {
        String currentVersion = "";
        try {
          String baseUrl = nextGenConfiguration.getNgManagerClientConfig().getBaseUrl();
          StringBuilder baseUrlBuilder = new StringBuilder();
          baseUrlBuilder.append(baseUrl);
          if (!baseUrl.endsWith("/")) {
            baseUrlBuilder.append('/');
          }
          baseUrlBuilder.append("version");
          currentVersion = getCurrentMicroserviceVersions(module.getModuleName(), baseUrlBuilder.toString());
        } catch (IOException e) {
          throw new UnexpectedException("Update VersionInfo Task Sync job interrupted:" + e);
        }
        module.setVersion(currentVersion);
      }
    });

    mongoTemplate.insertAll(allModulesFromDB);
  }

  private void readFromFile() {
    try {
      ClassLoader classLoader = this.getClass().getClassLoader();
      String parsedYamlFile =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(pathToFile)), StandardCharsets.UTF_8);

      ObjectMapper objectMapper = new ObjectMapper();
      allModulesFromDB = Arrays.asList(objectMapper.readValue(parsedYamlFile, ModuleVersionInfo[].class));
      allModulesFromDB.forEach(module -> { getFormattedDateTime(module); });
    } catch (Exception ex) {
      log.error("Update VersionInfo Task Sync job interrupted", ex);
    }
  }

  private void getFormattedDateTime(ModuleVersionInfo module) {
    // as we'll add more modules this check will go away
    if (module.getVersion().equals("Coming Soon")) {
      return;
    }

    // TODO: move this logic to UI in future.
    LocalDateTime dateTime = LocalDateTime.now();
    DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("MMM-dd-yyyy");
    String formattedDate = dateTime.format(myFormatObj);
    module.setLastModifiedAt(formattedDate);
  }

  private String getCurrentMicroserviceVersions(String serviceName, String serviceVersionUrl) throws IOException {
    if (serviceName == null || serviceName.equals("") || serviceVersionUrl == null || serviceVersionUrl.equals("")) {
      return "Coming Soon";
    }

    HttpRequest request = HttpRequest.newBuilder()
                              .uri(URI.create(serviceVersionUrl))
                              .method("GET", HttpRequest.BodyPublishers.noBody())
                              .build();
    HttpResponse<String> response = null;
    try {
      response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UnexpectedException("Update VersionInfo Task Sync job interrupted:" + e);
    } catch (InterruptedException e) {
      log.error("Update VersionInfo Task Sync job interrupted", e);
    }

    if (response != null) {
      log.info(response.body());
    }

    if (response == null) {
      return "";
    }
    JSONObject jsonObject = new JSONObject(response.body().toString().trim());
    JSONObject resourcejsonObject = (JSONObject) jsonObject.get("resource");
    JSONObject versionInfojsonObject = (JSONObject) resourcejsonObject.get("versionInfo");

    return versionInfojsonObject.getString("version");
  }
}
