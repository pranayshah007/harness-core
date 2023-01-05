/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.alert;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@Builder
@TargetModule(HarnessModule._470_ALERT)
public class ManifestCollectionFailedAlert implements AlertData {
  @Inject @Transient @SchemaIgnore ApplicationManifestService applicationManifestService;
  @Inject @Transient @SchemaIgnore ServiceResourceService serviceResourceService;
  @Inject @Transient @SchemaIgnore AppService appService;
  @Inject @Transient @SchemaIgnore SettingsService settingsService;

  private String appId;
  private String serviceId;
  private String appManifestId;

  @Override
  public boolean matches(AlertData alertData) {
    ManifestCollectionFailedAlert otherAlert = (ManifestCollectionFailedAlert) alertData;
    return StringUtils.equals(otherAlert.getAppManifestId(), appManifestId);
  }

  @Override
  public String buildTitle() {
    StringBuilder title = new StringBuilder(128);
    title.append("Manifest collection ");
    ApplicationManifest applicationManifest = applicationManifestService.getById(appId, appManifestId);
    if (applicationManifest != null) {
      if (isNotBlank(appId)) {
        if (isNotBlank(serviceId)) {
          Service service = serviceResourceService.getWithDetails(appId, serviceId);
          title.append("for manifest in service ").append(service.getName()).append(' ');
        }
        Application app = appService.get(appId);
        title.append("for application ").append(app.getName()).append(' ');
      }
    }

    title.append("is disabled because of multiple failed attempts. Fix manifest setup or do a manual pull");
    return title.toString();
  }
}
