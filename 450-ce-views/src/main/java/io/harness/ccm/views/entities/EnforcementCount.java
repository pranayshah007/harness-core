package io.harness.ccm.views.entities;

import com.google.common.collect.ImmutableList;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.util.HashMap;
import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "EnforcementCount", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Enforcement Count")

public final class EnforcementCount  {
    @Schema(description = "account id") String accountId;
    @Schema(description = "policy ids and list of enforcement") HashMap<String,List<String>> policyIds ;
    @Schema(description = "policy pack ids and list of enforcement") HashMap<String,List<String>> policyPackIds ;

    public EnforcementCount toDTO() {
        return EnforcementCount.builder()
                .accountId(getAccountId())
                .policyIds(getPolicyIds())
                .policyPackIds(getPolicyPackIds())
                .build();
    }
}
