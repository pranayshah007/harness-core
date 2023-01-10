/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.infraDefinition;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.graphql.utils.nameservice.NameService.infrastructureDefinition;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Environment;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLInfrastructureDefinition;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionConnection;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionConnection.QLInfrastructureDefinitionConnectionBuilder;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionFilter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AuthService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;

@OwnedBy(CDP)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InfrastructureDefinitionConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLInfrastructureDefinitionFilter, QLNoOpSortCriteria,
        QLInfrastructureDefinitionConnection> {
  @Inject HPersistence persistence;
  @Inject InfrastructureDefinitionQueryHelper infrastructureDefinitionQueryHelper;
  @Inject AuthService authService;

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  public QLInfrastructureDefinitionConnection fetchConnection(
      List<QLInfrastructureDefinitionFilter> infrastructureDefinitionFilters, QLPageQueryParameters pageQueryParameters,
      List<QLNoOpSortCriteria> sortCriteria) {
    Query<InfrastructureDefinition> query =
        populateFilters(wingsPersistence, infrastructureDefinitionFilters, InfrastructureDefinition.class, true);
    query.order(Sort.descending(InfrastructureDefinitionKeys.createdAt));
    QLInfrastructureDefinitionConnectionBuilder qlInfrastructureDefinitionConnectionBuilder =
        QLInfrastructureDefinitionConnection.builder();

    qlInfrastructureDefinitionConnectionBuilder.pageInfo(
        utils.populate(pageQueryParameters, query, infrastructureDefinition -> {
          qlInfrastructureDefinitionConnectionBuilder.node(createInfrastructureDefinition(infrastructureDefinition));
        }));

    return qlInfrastructureDefinitionConnectionBuilder.build();
  }
  protected QLInfrastructureDefinition createInfrastructureDefinition(
      InfrastructureDefinition infrastructureDefinition) {
    return QLInfrastructureDefinition.builder()
        .id(infrastructureDefinition.getUuid())
        .name(infrastructureDefinition.getName())
        .deploymentType(infrastructureDefinition.getDeploymentType().getDisplayName())
        .scopedToServices(infrastructureDefinition.getScopedToServices())
        .createdAt(infrastructureDefinition.getCreatedAt())
        .environment(wingsPersistence.get(Environment.class, infrastructureDefinition.getEnvId()))
        .build();
  }
  @Override
  protected void populateFilters(List<QLInfrastructureDefinitionFilter> filters, Query query) {
    infrastructureDefinitionQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  public QLInfrastructureDefinitionFilter generateFilter(
      DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (NameService.environment.equals(key)) {
      return QLInfrastructureDefinitionFilter.builder().environment(idFilter).build();
    } else if (infrastructureDefinition.equals(key)) {
      return QLInfrastructureDefinitionFilter.builder().infrastructureDefinition(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
