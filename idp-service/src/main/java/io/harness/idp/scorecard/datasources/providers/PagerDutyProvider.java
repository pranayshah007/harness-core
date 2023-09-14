/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.PAGERDUTY_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PagerDutyProvider extends DataSourceProvider {
  private static final String PAGERDUTY_ANNOTATION = "pagerduty.com/service-id";
  protected PagerDutyProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory) {
    super(PAGERDUTY_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory);
    //        this.backstageEnvVariableRepository = backstageEnvVariableRepository;
    //        this.ngSecretService = ngSecretService;
    //        this.configEnvVariablesRepository =configEnvVariablesRepository;
  }

  //    final BackstageEnvVariableRepository backstageEnvVariableRepository;
  //    final SecretManagerClientService ngSecretService;
  //
  //    final ConfigEnvVariablesRepository configEnvVariablesRepository;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);

    String pagerDutyServiceId = entity.getMetadata().getAnnotations().get(PAGERDUTY_ANNOTATION);
    //    String pagerDutyServiceId = "PQIRSV3";
    log.info("Pager Duty Service Id fetched from catalog - {}", pagerDutyServiceId);

    return processOut(accountIdentifier, entity, dataPointsAndInputValues, replaceableHeaders, Collections.emptyMap(),
        prepareUrlReplaceablePairs(pagerDutyServiceId));
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier) {
    //        configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, )
    //
    //        BackstageEnvSecretVariableEntity backstageEnvSecretVariableEntity =
    //                (BackstageEnvSecretVariableEntity) backstageEnvVariableRepository
    //                        .findByEnvNameAndAccountIdentifier(GITHUB_TOKEN, accountIdentifier)
    //                        .orElse(null);
    //        assert backstageEnvSecretVariableEntity != null;
    //        String secretIdentifier = backstageEnvSecretVariableEntity.getHarnessSecretIdentifier();
    //        DecryptedSecretValue decryptedValue =
    //                ngSecretService.getDecryptedSecretValue(accountIdentifier, null, null, secretIdentifier);

    // Todo::will add fetching of token from the generic parser from config
    return Map.of(AUTHORIZATION_HEADER,
        ""
            + "Token token=xxxxxxxxxxxxxxx");
  }

  private Map<String, String> prepareUrlReplaceablePairs(String pagerDutyServiceId) {
    Map<String, String> possibleReplaceableUrlPairs = new HashMap<>();

    possibleReplaceableUrlPairs.put(PAGERDUTY_SERVICE_ID, pagerDutyServiceId);

    return possibleReplaceableUrlPairs;
  }
}
