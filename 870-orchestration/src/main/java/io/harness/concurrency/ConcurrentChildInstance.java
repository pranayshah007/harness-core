package io.harness.concurrency;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Entity(value = "concurrentChildInstance", noClassnameStored = true)
@Document("concurrentChildInstance")
@TypeAlias("concurrentChildInstance")
@StoreIn(DbAliases.PMS)
@OwnedBy(HarnessTeam.PIPELINE)
public class ConcurrentChildInstance {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @FdUniqueIndex private String parentNodeExecutionId;
  private List<String> childrenNodeExecutionIds;
  @Builder.Default private Map<String, ByteString> combinedResponse = new HashMap<>();
  private int cursor;
}
