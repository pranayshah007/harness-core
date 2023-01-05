/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCEEnabledFilter;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderFilter;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderTypeFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;

/**
 * @author rktummala on 07/12/19
 */
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class CloudProviderQueryHelper {
  @Inject protected DataFetcherUtils utils;

  public void setQuery(List<QLCloudProviderFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;

      if (filter.getCloudProvider() != null) {
        field = query.field("_id");
        QLIdFilter cloudProviderFilter = filter.getCloudProvider();
        utils.setIdFilter(field, cloudProviderFilter);
      }
      if (filter.getIsCEEnabled() != null) {
        QLCEEnabledFilter ceEnabledFilter = filter.getIsCEEnabled();
        QLEnumOperator operator = (QLEnumOperator) ceEnabledFilter.getOperator();
        if (operator == null) {
          throw new WingsException("Enum Operator cannot be null");
        }
        if (operator != QLEnumOperator.EQUALS) {
          throw new WingsException("Unknown Enum operator " + operator);
        }
        if (isEmpty(ceEnabledFilter.getValues())) {
          throw new WingsException("Value cannot be empty");
        }
        Boolean[] booleanFilterValues = ceEnabledFilter.getValues();
        if (booleanFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator EQUALS");
        }
        query.disableValidation();
        query.filter(SettingAttributeKeys.isCEEnabled, booleanFilterValues[0]);
      }

      if (filter.getCloudProviderType() != null) {
        field = query.field("value.type");
        QLCloudProviderTypeFilter cloudProviderTypeFilter = filter.getCloudProviderType();
        utils.setEnumFilter(field, cloudProviderTypeFilter);
      }

      if (filter.getCreatedAt() != null) {
        field = query.field("createdAt");
        QLTimeFilter createdAtFilter = filter.getCreatedAt();
        utils.setTimeFilter(field, createdAtFilter);
      }
    });
  }
}
