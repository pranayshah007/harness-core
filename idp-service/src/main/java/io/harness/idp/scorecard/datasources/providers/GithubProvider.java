/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITHUB_TOKEN;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubProvider extends DataSourceProvider {
  private static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";
  protected GithubProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      BackstageEnvVariableRepository backstageEnvVariableRepository, SecretManagerClientService ngSecretService) {
    super(GITHUB_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    this.backstageEnvVariableRepository = backstageEnvVariableRepository;
    this.ngSecretService = ngSecretService;
  }

  final BackstageEnvVariableRepository backstageEnvVariableRepository;
  final SecretManagerClientService ngSecretService;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);

    String catalogLocation = entity.getMetadata().getAnnotations().get(SOURCE_LOCATION_ANNOTATION);
    Map<String, String> possibleReplaceableRequestBodyPairs = prepareRequestBodyReplaceablePairs(catalogLocation);

    return processOut(accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders,
        possibleReplaceableRequestBodyPairs, Collections.emptyMap());
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier) {
    BackstageEnvSecretVariableEntity backstageEnvSecretVariableEntity =
        (BackstageEnvSecretVariableEntity) backstageEnvVariableRepository
            .findByEnvNameAndAccountIdentifier(GITHUB_TOKEN, accountIdentifier)
            .orElse(null);
    assert backstageEnvSecretVariableEntity != null;
    String secretIdentifier = backstageEnvSecretVariableEntity.getHarnessSecretIdentifier();
    DecryptedSecretValue decryptedValue =
        ngSecretService.getDecryptedSecretValue(accountIdentifier, null, null, secretIdentifier);
    return Map.of(AUTHORIZATION_HEADER, "Bearer " + decryptedValue.getDecryptedValue());
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(String catalogLocation) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();

    String[] catalogLocationParts = catalogLocation.split("/");

    possibleReplaceableRequestBodyPairs.put(REPO_SCM, catalogLocationParts[2]);
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_OWNER, catalogLocationParts[3]);
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_NAME, catalogLocationParts[4]);
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_BRANCH, catalogLocationParts[6]);

    return possibleReplaceableRequestBodyPairs;
  }
}
