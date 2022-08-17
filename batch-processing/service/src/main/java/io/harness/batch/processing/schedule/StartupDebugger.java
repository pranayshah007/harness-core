/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.persistence.HQuery.excludeCount;

import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.events.PublishedMessage.PublishedMessageKeys;
import io.harness.perpetualtask.k8s.watch.NodeInfo;
import io.harness.perpetualtask.k8s.watch.PodInfo;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
//@Configuration
public class StartupDebugger {
  @Autowired @Inject private HPersistence hPersistence;

  private List<PublishedMessage> fetchPublishedMessage(
      String accountId, String messageType, Long startTime, Long endTime) {
    Query<PublishedMessage> query = hPersistence.createQuery(PublishedMessage.class, excludeCount)
                                        .filter(PublishedMessageKeys.accountId, accountId)
                                        .filter(PublishedMessageKeys.type, messageType)
                                        .order(PublishedMessageKeys.createdAt);

    query.and(query.criteria(PublishedMessageKeys.createdAt).greaterThanOrEq(startTime),
        query.criteria(PublishedMessageKeys.createdAt).lessThanOrEq(endTime));
    return query.asList();
  }

  @Scheduled(fixedDelay = Long.MAX_VALUE)
  public void debugProtoMessageUnpack() {
    log.info("Debug start");

    String accountId = "hW63Ny6rQaaGsKkVjE0pJA";
    Long createdAtFrom = 0L;
    Long createdAtTo = Instant.now().toEpochMilli();

    List<PublishedMessage> publishedMessageList =
        fetchPublishedMessage(accountId, EventTypeConstants.K8S_POD_INFO, createdAtFrom, createdAtTo);

    StringBuilder output = new StringBuilder();
    for (PublishedMessage publishedMessage : publishedMessageList) {
      output.append(processPodInfo(publishedMessage));
    }

    log.info("[{}]", output.toString());

    quitBatchProcessing();
  }

  @SneakyThrows
  private void quitBatchProcessing() {
    //    System.exit(0);

    log.info("Debug end");
  }

  private static final List<String> missingIds = Arrays.asList("epg1ejan3mpq8sr0xr1k3q", "c641ag4ehj9v9k3srgifn1",
      "v63k6uw3ghi4vc784vu0nx", "cjprrj9mc80nbtswfj8dh6", "m5lzpwahzhovf7v2f43dwh", "t55nraq2aoz39pfgllkir4",
      "olchwc2qp148ifwvyeo3d8", "kurjtyzd09khbnfyxs6qkb", "x0luxailtrc19lr2t1f1h5", "zra3aygutw1zcwdxw63aws",
      "ssywvmxsxogzrn4izp3dna", "dwp2plrernsuwxvallpnkc", "ghuzm5cul17i9xkrmfurya", "heegqb4f2dwxlyfmk9tj48");

  private String processPodInfo(PublishedMessage publishedMessage) {
    PodInfo podInfo = (PodInfo) publishedMessage.getMessage();

    return "'" + podInfo.getPodUid() + "',";
  }

  private void processNodeInfo(PublishedMessage publishedMessage) {
    NodeInfo nodeInfo = (NodeInfo) publishedMessage.getMessage();
    log.info("NodeInfo: {}", nodeInfo);
  }
}
