package io.harness.releaseradar.entities;

import dev.morphia.annotations.Entity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.RELEASE_RADAR)
@Entity(value = "CommitDetails")
@Document("CommitDetails")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommitDetails {
    String eventId;
    String jiraId;
    String sha;
    CommitDetailsMetadata metadata;
}
