/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static software.wings.ngmigration.NGMigrationEntityType.USER_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.dto.EntityMigratedStats;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.JsonUtils;

import software.wings.beans.security.UserGroup;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.UserGroupService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class UsergroupImportService {
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;

  @Inject private UserGroupService userGroupService;

  public SaveSummaryDTO importUserGroups(String auth, String accountId, CaseFormat identifierCaseFormat) {
    List<UserGroup> userGroups = userGroupService.listByAccountId(accountId);

    List<UserGroupDTO> ngUserGroups =
        userGroups.stream()
            .map(ug
                -> UserGroupDTO.builder()
                       .identifier(MigratorUtility.generateIdentifier(ug.getName(), identifierCaseFormat))
                       .name(ug.getName())
                       .description(ug.getDescription())
                       .users(ug.getMemberIds())
                       .accountIdentifier(accountId)
                       .build())
            .collect(Collectors.toList());

    NGClient ngClient = MigratorUtility.getRestClient(ngClientConfig, NGClient.class);
    List<MigrationImportSummaryDTO> summaryDTOS =
        ngUserGroups.parallelStream().map(ug -> createUserGroup(auth, ngClient, ug)).collect(Collectors.toList());
    List<ImportError> errors = summaryDTOS.stream().flatMap(s -> s.getErrors().stream()).collect(Collectors.toList());
    return SaveSummaryDTO.builder()
        .errors(errors)
        .stats(ImmutableMap.<NGMigrationEntityType, EntityMigratedStats>builder()
                   .put(USER_GROUP,
                       EntityMigratedStats.builder()
                           .successfullyMigrated(
                               (int) summaryDTOS.stream().filter(MigrationImportSummaryDTO::isSuccess).count())
                           .build())
                   .build())
        .alreadyMigratedDetails(new ArrayList<>())
        .successfullyMigratedDetails(new ArrayList<>())
        .build();
  }

  private MigrationImportSummaryDTO createUserGroup(String auth, NGClient ngClient, UserGroupDTO userGroupDTO) {
    try {
      Response<ResponseDTO<UserGroupDTO>> resp =
          ngClient.createUserGroup(auth, userGroupDTO.getAccountIdentifier(), userGroupDTO).execute();
      if (resp.code() >= 200 && resp.code() < 300) {
        return MigrationImportSummaryDTO.builder().success(true).errors(Collections.emptyList()).build();
      }
      Map<String, Object> error = JsonUtils.asObject(
          resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
      log.error(String.format(
          "There was error creating the user group. Response from NG - %s with error body errorBody -  %s", resp,
          error));
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(
              ImportError.builder()
                  .message(error.containsKey("message") ? error.get("message").toString()
                                                        : "There was an error creating the user group")
                  .build()))
          .build();
    } catch (IOException e) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder().message(e.getLocalizedMessage()).build()))
          .build();
    }
  }
}
