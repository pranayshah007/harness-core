/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.dto.BulkCreateProjectsDTO;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.ProjectCreateResultDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Application;
import software.wings.service.intfc.AppService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class CreateProjectService {
  @Inject private AppService appService;
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;

  public List<ProjectCreateResultDTO> bulkCreateProjects(
      String accountId, String apiKey, BulkCreateProjectsDTO createRequest) throws IOException {
    List<Application> apps = appService.getAppsByAccountId(accountId);
    NGClient ngClient = MigratorUtility.getRestClient(ngClientConfig, NGClient.class);

    if (EmptyPredicate.isEmpty(apps)) {
      return Collections.emptyList();
    }

    List<ProjectCreateResultDTO> allResults = new ArrayList<>();
    for (Application app : apps) {
      String identifier = MigratorUtility.generateIdentifier(app.getName(), createRequest.getIdentifierCaseFormat());
      String name = MigratorUtility.generateName(app.getName());
      Response<ResponseDTO<ProjectResponse>> resp =
          ngClient
              .createProject(apiKey, accountId, createRequest.getOrgIdentifier(),
                  ProjectRequest.builder()
                      .project(ProjectDTO.builder()
                                   .orgIdentifier(createRequest.getOrgIdentifier())
                                   .color("#0063f7")
                                   .description(app.getDescription())
                                   .identifier(identifier)
                                   .modules(Collections.singletonList(ModuleType.CD))
                                   .name(name)
                                   .tags(new HashMap<>())
                                   .build())
                      .build())
              .execute();
      log.info("Connector creation Response details {} {}", resp.code(), resp.message());
      ImportError error = handleResp(resp);
      allResults.add(ProjectCreateResultDTO.builder()
                         .appId(app.getAppId())
                         .projectName(name)
                         .projectIdentifier(identifier)
                         .appName(app.getName())
                         .orgIdentifier(createRequest.getOrgIdentifier())
                         .error(error)
                         .build());
    }

    return allResults;
  }

  private <T> ImportError handleResp(Response<ResponseDTO<T>> resp) throws IOException {
    if (resp.code() >= 200 && resp.code() < 300) {
      return null;
    }
    log.error("There was error processing the request");
    Map<String, Object> error = JsonUtils.asObject(
        resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
    return ImportError.builder()
        .message(
            error.containsKey("message") ? error.get("message").toString() : "There was an error creating the project")
        .build();
  }
}
