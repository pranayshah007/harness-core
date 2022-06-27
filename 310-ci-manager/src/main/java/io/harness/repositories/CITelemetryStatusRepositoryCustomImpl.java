package io.harness.repositories;

import com.google.inject.Inject;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.ci.beans.entities.CITelemetrySentStatus.CITelemetrySentStatusKeys;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject}))
public class CITelemetryStatusRepositoryCustomImpl implements CITelemetryStatusRepositoryCustom{
    private final MongoTemplate mongoTemplate;

    @Override
    public boolean updateTimestampIfOlderThan(String accountId, long olderThanTime, long updateToTime) {
        Query query = new Query().addCriteria(new Criteria()
                .and(CITelemetrySentStatusKeys.accountId)
                .is(accountId)
                .and(CITelemetrySentStatusKeys.lastSent)
                .lte(olderThanTime));
        Update update = new Update().set(CITelemetrySentStatusKeys.lastSent, updateToTime);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
        // Account ID is unique here so setting upsert to true will throw exception
        CITelemetrySentStatus result;
        try {
            result = mongoTemplate.findAndModify(query, update, options, CITelemetrySentStatus.class);
        } catch (DuplicateKeyException e) {
            return false;
        }
        return result != null;
    }
}
