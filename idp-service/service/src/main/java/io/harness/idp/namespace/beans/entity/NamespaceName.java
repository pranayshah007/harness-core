package io.harness.idp.namespace.beans.entity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NamespaceNameValue")
@StoreIn(DbAliases.IDP)
@Entity(value = "backstageNamespace", noClassnameStored = true)
@Document("backstageNamespace")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class NamespaceName implements PersistentEntity {
    @Id @org.mongodb.morphia.annotations.Id private String id;
    @FdUniqueIndex private String accountIdentifier;
    private String namespaceName;
//    @CreatedDate Long createdAt;
//    @LastModifiedDate Long lastModifiedAt;
//    private boolean isDeleted;
//    private long deletedAt;
}
