/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

import static io.harness.persistence.HQuery.excludeValidate;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEMetadataRecordMigration implements NGMigration {
    @Inject
    private CEMetadataRecordDao ceMetadataRecordDao;
    @Inject
    private HPersistence hPersistence;

    @Override
    public void migrate() {
        try {
            log.info("Starting migration for CEMetadataRecord table to back-fill the " +
                    "column DataGeneratedForCloudProvider with value true for existing records");
            final List<CEMetadataRecord> ceMetadataRecords = hPersistence.createQuery(CEMetadataRecord.class, excludeValidate).asList();
            for (final CEMetadataRecord ceMetadataRecord : ceMetadataRecords) {
                try {
                    if (Objects.isNull(ceMetadataRecord.getDataGeneratedForCloudProvider())) {
                        ceMetadataRecord.setDataGeneratedForCloudProvider(true);
                        log.info("Updated the record for account {}", ceMetadataRecord.getAccountId());
                    }
                    ceMetadataRecordDao.upsert(ceMetadataRecord);
                } catch (final Exception e) {
                    log.error("Migration Failed for Account {}, ViewId {}", ceMetadataRecord.getAccountId(), ceMetadataRecord.getUuid(), e);
                }
            }
            log.info("CEMetadataRecord Migration finished!");
        } catch (final Exception e) {
            log.error("Failure occurred in CEViewsPreferencesMigration", e);
        }
    }
}
