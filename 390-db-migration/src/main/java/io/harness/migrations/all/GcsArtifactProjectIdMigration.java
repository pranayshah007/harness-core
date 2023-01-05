/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcsArtifactProjectIdMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private GcsService gcsService;

  private List<EncryptedDataDetail> getEncryptedDataDetails(EncryptableSetting settingValue) {
    return secretManager.getEncryptionDetails(settingValue, null, null);
  }

  @Override
  public void migrate() {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.artifactStreamType, ArtifactStreamType.GCS.name());
    try (HIterator<ArtifactStream> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        GcsArtifactStream artifactStream = (GcsArtifactStream) records.next();

        if (artifactStream != null && isNotEmpty(artifactStream.getSettingId())
            && isEmpty(artifactStream.getProjectId())) {
          SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
          if (settingAttribute == null) {
            log.info("GCP Cloud provider Settings Attribute is null. Can not set Project Id in Artifact Stream");
            continue;
          } else {
            // Get project Id from GCP Config and service account
            SettingValue settingValue = settingAttribute.getValue();
            List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
            GcpConfig gcpConfig = (GcpConfig) settingValue;
            String projectId = gcsService.getProject(gcpConfig, encryptedDataDetails);

            // Set the project Id for artifact stream
            if (isNotEmpty(projectId)) {
              artifactStream.setProjectId(projectId);
              wingsPersistence.save(artifactStream);
            }
          }
        }
      }
    }
  }
}
