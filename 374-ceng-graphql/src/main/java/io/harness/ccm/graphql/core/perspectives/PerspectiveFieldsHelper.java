/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.perspectives;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.graphql.QLCEViewFieldIdentifierData;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.ff.FeatureFlagService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerspectiveFieldsHelper {
  @Inject private ViewCustomFieldService viewCustomFieldService;
  @Inject private CEMetadataRecordDao metadataRecordDao;
  @Inject private CEViewService ceViewService;
  @Inject private ViewsBillingService viewsBillingService;
  @Inject private BusinessMappingService businessMappingService;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject FeatureFlagService featureFlagService;

  private static final long CACHE_SIZE = 100;
  public static final String columnView = "COLUMNS";

  private LoadingCache<String, List<QLCEViewField>> accountIdToSupportedAzureFields =
      Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).maximumSize(CACHE_SIZE).build(this::getAzureFields);

  public PerspectiveFieldsData fetch(String accountId, List<QLCEViewFilterWrapper> filters) {
    List<ViewField> customFields = new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    boolean isExplorerQuery = false;
    boolean isClusterPerspective = viewsBillingService.isClusterPerspective(filters, Collections.emptyList());
    String viewId = null;
    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      isExplorerQuery = !metadataFilter.isPreview();
      viewId = metadataFilter.getViewId();
      customFields = viewCustomFieldService.getCustomFieldsPerView(viewId, accountId);
    }

    List<QLCEViewFieldIdentifierData> fieldIdentifierData = new ArrayList<>();
    fieldIdentifierData.add(getViewField(ViewFieldUtils.getCommonFields(), ViewFieldIdentifier.COMMON));
    fieldIdentifierData.add(getViewCustomField(customFields));
    fieldIdentifierData.add(getBusinessMappingFields(businessMappingService.getBusinessMappingViewFields(accountId)));

    Set<ViewFieldIdentifier> viewFieldIdentifierSetFromCustomFields = new HashSet<>();
    for (ViewField customField : customFields) {
      List<ViewField> customFieldViewFields = viewCustomFieldService.get(customField.getFieldId()).getViewFields();
      for (ViewField field : customFieldViewFields) {
        if (field.getIdentifier() == ViewFieldIdentifier.LABEL) {
          for (QLCEViewFieldIdentifierData viewFieldIdentifierData :
              getFieldIdentifierDataFromCEMetadataRecord(accountId, isClusterPerspective)) {
            viewFieldIdentifierSetFromCustomFields.add(viewFieldIdentifierData.getIdentifier());
          }
        } else {
          viewFieldIdentifierSetFromCustomFields.add(field.getIdentifier());
        }
      }
    }

    if (isExplorerQuery) {
      CEView ceView = ceViewService.get(viewId);
      if (ceView.getDataSources() != null && isNotEmpty(ceView.getDataSources())) {
        for (ViewFieldIdentifier viewFieldIdentifier : viewFieldIdentifierSetFromCustomFields) {
          if (viewFieldIdentifier == ViewFieldIdentifier.AWS) {
            fieldIdentifierData.add(getViewField(ViewFieldUtils.getAwsFields(), viewFieldIdentifier));
          } else if (viewFieldIdentifier == ViewFieldIdentifier.GCP) {
            fieldIdentifierData.add(getViewField(ViewFieldUtils.getGcpFields(), viewFieldIdentifier));
          } else if (viewFieldIdentifier == ViewFieldIdentifier.CLUSTER) {
            fieldIdentifierData.add(
                getViewField(ViewFieldUtils.getClusterFields(isClusterPerspective), viewFieldIdentifier));
          } else if (viewFieldIdentifier == ViewFieldIdentifier.AZURE) {
            fieldIdentifierData.add(getViewField(accountIdToSupportedAzureFields.get(accountId), viewFieldIdentifier));
          }
        }

        for (ViewFieldIdentifier viewFieldIdentifier : ceView.getDataSources()) {
          addFieldIdentifierData(accountId, isClusterPerspective, fieldIdentifierData,
              viewFieldIdentifierSetFromCustomFields, viewFieldIdentifier);
          if (viewFieldIdentifier == ViewFieldIdentifier.BUSINESS_MAPPING) {
            final Set<ViewFieldIdentifier> businessMappingViewFieldIdentifiers =
                businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromViewRules(
                    accountId, ceView.getViewRules());
            businessMappingViewFieldIdentifiers.forEach(businessMappingViewFieldIdentifier
                -> addFieldIdentifierData(accountId, isClusterPerspective, fieldIdentifierData,
                    viewFieldIdentifierSetFromCustomFields, businessMappingViewFieldIdentifier));
          }
        }
      } else {
        fieldIdentifierData.addAll(getFieldIdentifierDataFromCEMetadataRecord(accountId, isClusterPerspective));
      }
    } else {
      fieldIdentifierData.addAll(getFieldIdentifierDataFromCEMetadataRecord(accountId, isClusterPerspective));
    }
    return PerspectiveFieldsData.builder().fieldIdentifierData(fieldIdentifierData).build();
  }

  private void addFieldIdentifierData(final String accountId, final boolean isClusterPerspective,
      final List<QLCEViewFieldIdentifierData> fieldIdentifierData,
      final Set<ViewFieldIdentifier> viewFieldIdentifierSetFromCustomFields,
      final ViewFieldIdentifier viewFieldIdentifier) {
    if (shouldAddFieldIdentifierData(
            viewFieldIdentifierSetFromCustomFields, viewFieldIdentifier, ViewFieldIdentifier.AWS)) {
      fieldIdentifierData.add(getViewField(ViewFieldUtils.getAwsFields(), viewFieldIdentifier));
      viewFieldIdentifierSetFromCustomFields.add(ViewFieldIdentifier.AWS);
    } else if (shouldAddFieldIdentifierData(
                   viewFieldIdentifierSetFromCustomFields, viewFieldIdentifier, ViewFieldIdentifier.GCP)) {
      fieldIdentifierData.add(getViewField(ViewFieldUtils.getGcpFields(), viewFieldIdentifier));
      viewFieldIdentifierSetFromCustomFields.add(ViewFieldIdentifier.GCP);
    } else if (shouldAddFieldIdentifierData(
                   viewFieldIdentifierSetFromCustomFields, viewFieldIdentifier, ViewFieldIdentifier.CLUSTER)) {
      fieldIdentifierData.add(getViewField(ViewFieldUtils.getClusterFields(isClusterPerspective), viewFieldIdentifier));
      viewFieldIdentifierSetFromCustomFields.add(ViewFieldIdentifier.CLUSTER);
    } else if (shouldAddFieldIdentifierData(
                   viewFieldIdentifierSetFromCustomFields, viewFieldIdentifier, ViewFieldIdentifier.AZURE)) {
      fieldIdentifierData.add(getViewField(accountIdToSupportedAzureFields.get(accountId), viewFieldIdentifier));
      viewFieldIdentifierSetFromCustomFields.add(ViewFieldIdentifier.AZURE);
    }
  }

  private boolean shouldAddFieldIdentifierData(final Set<ViewFieldIdentifier> viewFieldIdentifierSetFromCustomFields,
      final ViewFieldIdentifier viewFieldIdentifier1, final ViewFieldIdentifier viewFieldIdentifier2) {
    return viewFieldIdentifier1 == viewFieldIdentifier2
        && !viewFieldIdentifierSetFromCustomFields.contains(viewFieldIdentifier2);
  }

  private List<QLCEViewFieldIdentifierData> getFieldIdentifierDataFromCEMetadataRecord(
      String accountId, boolean isClusterPerspective) {
    List<QLCEViewFieldIdentifierData> fieldIdentifierData = new ArrayList<>();
    CEMetadataRecord ceMetadataRecord = metadataRecordDao.getByAccountId(accountId);
    Boolean clusterDataConfigured = true;
    Boolean awsConnectorConfigured = true;
    Boolean gcpConnectorConfigured = true;
    Boolean azureConnectorConfigured = true;

    if (ceMetadataRecord != null) {
      clusterDataConfigured = ceMetadataRecord.getClusterDataConfigured();
      awsConnectorConfigured = ceMetadataRecord.getAwsConnectorConfigured();
      gcpConnectorConfigured = ceMetadataRecord.getGcpConnectorConfigured();
      azureConnectorConfigured = ceMetadataRecord.getAzureConnectorConfigured();
    }

    if (clusterDataConfigured == null || clusterDataConfigured) {
      fieldIdentifierData.add(
          getViewField(ViewFieldUtils.getClusterFields(isClusterPerspective), ViewFieldIdentifier.CLUSTER));
    }
    if (awsConnectorConfigured == null || awsConnectorConfigured) {
      fieldIdentifierData.add(getViewField(ViewFieldUtils.getAwsFields(), ViewFieldIdentifier.AWS));
    }
    if (gcpConnectorConfigured == null || gcpConnectorConfigured) {
      fieldIdentifierData.add(getViewField(ViewFieldUtils.getGcpFields(), ViewFieldIdentifier.GCP));
    }
    if (azureConnectorConfigured != null && azureConnectorConfigured) {
      fieldIdentifierData.add(getViewField(accountIdToSupportedAzureFields.get(accountId), ViewFieldIdentifier.AZURE));
    }
    return fieldIdentifierData;
  }

  private QLCEViewFieldIdentifierData getViewField(
      List<QLCEViewField> ceViewFieldList, ViewFieldIdentifier viewFieldIdentifier) {
    return QLCEViewFieldIdentifierData.builder()
        .identifier(viewFieldIdentifier)
        .identifierName(viewFieldIdentifier.getDisplayName())
        .values(ceViewFieldList)
        .build();
  }

  private QLCEViewFieldIdentifierData getViewCustomField(List<ViewField> customFields) {
    List<QLCEViewField> ceViewFieldList = customFields.stream()
                                              .map(field
                                                  -> QLCEViewField.builder()
                                                         .fieldId(field.getFieldId())
                                                         .fieldName(field.getFieldName())
                                                         .identifier(field.getIdentifier())
                                                         .identifierName(field.getIdentifier().getDisplayName())
                                                         .build())
                                              .collect(Collectors.toList());
    return QLCEViewFieldIdentifierData.builder()
        .identifier(ViewFieldIdentifier.CUSTOM)
        .identifierName(ViewFieldIdentifier.CUSTOM.getDisplayName())
        .values(ceViewFieldList)
        .build();
  }

  private QLCEViewFieldIdentifierData getBusinessMappingFields(List<ViewField> businessMappings) {
    List<QLCEViewField> ceViewFieldList = businessMappings.stream()
                                              .map(field
                                                  -> QLCEViewField.builder()
                                                         .fieldId(field.getFieldId())
                                                         .fieldName(field.getFieldName())
                                                         .identifier(field.getIdentifier())
                                                         .identifierName(field.getIdentifier().getDisplayName())
                                                         .build())
                                              .collect(Collectors.toList());
    return QLCEViewFieldIdentifierData.builder()
        .identifier(ViewFieldIdentifier.BUSINESS_MAPPING)
        .identifierName(ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName())
        .values(ceViewFieldList)
        .build();
  }

  private static Optional<QLCEViewFilterWrapper> getViewMetadataFilter(List<QLCEViewFilterWrapper> filters) {
    return filters.stream().filter(f -> f.getViewMetadataFilter().getViewId() != null).findFirst();
  }

  private List<QLCEViewField> getAzureFields(String accountId) {
    List<QLCEViewField> supportedAzureFields = new ArrayList<>();

    // Getting supported fields from information schema
    String informationSchemaView = bigQueryHelper.getInformationSchemaViewForDataset(accountId, columnView);
    String tableName = bigQueryHelper.getTableName("AZURE");
    List<String> supportedFields = viewsBillingService.getColumnsForTable(informationSchemaView, tableName);

    // Adding fields which are common across all account types of azure
    supportedAzureFields.addAll(ViewFieldUtils.getAzureFields());

    // Adding other fields which are supported
    List<QLCEViewField> variableAzureFields = ViewFieldUtils.getVariableAzureFields();
    variableAzureFields.forEach(field -> {
      if (supportedFields.contains(getFieldNameWithoutAzurePrefix(field.getFieldId()))) {
        supportedAzureFields.add(field);
      }
    });

    return supportedAzureFields;
  }

  private String getFieldNameWithoutAzurePrefix(String field) {
    return field.substring(5);
  }
}
