/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.overview;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.graphql.dto.overview.CCMMetaData;
import io.harness.ccm.graphql.dto.overview.CCMMetaData.CCMMetaDataBuilder;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.dto.DefaultViewIdDto;
import io.harness.ccm.views.service.CEViewService;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CCMMetaDataService {
  public static final String MODULE_INTERFACE_LOADED = "Module Interface Loaded";
  @Inject private CEMetadataRecordDao metadataRecordDao;
  @Inject private CEViewService ceViewService;
  @Inject private TelemetryReporter telemetryReporter;
  @Inject private CCMRbacHelper rbacHelper;

  @NonNull
  public CCMMetaData getCCMMetaData(@NonNull final String accountId) {
    CEMetadataRecord ceMetadataRecord = metadataRecordDao.getByAccountId(accountId);
    CCMMetaDataBuilder ccmMetaDataBuilder = CCMMetaData.builder();
    if (ceMetadataRecord != null) {
      ccmMetaDataBuilder.applicationDataPresent(getFieldBooleanValue(ceMetadataRecord.getApplicationDataPresent()));
      ccmMetaDataBuilder.clusterDataPresent(getFieldBooleanValue(ceMetadataRecord.getClusterDataConfigured()));
      ccmMetaDataBuilder.k8sClusterConnectorPresent(
          getFieldBooleanValue(ceMetadataRecord.getClusterConnectorConfigured()));
      ccmMetaDataBuilder.awsConnectorsPresent(getFieldBooleanValue(ceMetadataRecord.getAwsConnectorConfigured()));
      ccmMetaDataBuilder.gcpConnectorsPresent(getFieldBooleanValue(ceMetadataRecord.getGcpConnectorConfigured()));
      ccmMetaDataBuilder.azureConnectorsPresent(getFieldBooleanValue(ceMetadataRecord.getAzureConnectorConfigured()));
      ccmMetaDataBuilder.cloudDataPresent(isCloudDataPresent(ceMetadataRecord));
    }
    DefaultViewIdDto defaultViewIds = ceViewService.getDefaultViewIds(accountId);
    ccmMetaDataBuilder.defaultAwsPerspectiveId(defaultViewIds.getAwsViewId());
    ccmMetaDataBuilder.defaultAzurePerspectiveId(defaultViewIds.getAzureViewId());
    ccmMetaDataBuilder.defaultGcpPerspectiveId(defaultViewIds.getGcpViewId());
    ccmMetaDataBuilder.defaultClusterPerspectiveId(defaultViewIds.getClusterViewId());

    try {
      Boolean isSegmentModuleInterfaceLoadedEventSent = null;
      if (null != ceMetadataRecord) {
        isSegmentModuleInterfaceLoadedEventSent = ceMetadataRecord.getSegmentModuleInterfaceLoadedEventSent();
      }
      if (isSegmentModuleInterfaceLoadedEventSent == null || !isSegmentModuleInterfaceLoadedEventSent) {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("module", "CCM");
        telemetryReporter.sendTrackEvent(MODULE_INTERFACE_LOADED, null, accountId, properties,
            Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
        if (null == ceMetadataRecord) {
          ceMetadataRecord = CEMetadataRecord.builder().build();
        }
        ceMetadataRecord.setSegmentModuleInterfaceLoadedEventSent(true);
        metadataRecordDao.upsert(ceMetadataRecord);
      }
    } catch (Exception ex) {
      log.error("Encountered exception while getSegmentModuleInterfaceLoadedEventSent.", ex);
    }

    // Checking cost overview permissions
    ccmMetaDataBuilder.showCostOverview(rbacHelper.hasCostOverviewPermission(accountId, null, null));
    return ccmMetaDataBuilder.build();
  }

  private Boolean getFieldBooleanValue(Boolean fieldValue) {
    return Boolean.TRUE.equals(fieldValue);
  }

  private boolean isCloudDataPresent(CEMetadataRecord ceMetadataRecord) {
    return Boolean.TRUE.equals(ceMetadataRecord.getAwsDataPresent())
        || Boolean.TRUE.equals(ceMetadataRecord.getGcpDataPresent())
        || Boolean.TRUE.equals(ceMetadataRecord.getAzureDataPresent());
  }
}
