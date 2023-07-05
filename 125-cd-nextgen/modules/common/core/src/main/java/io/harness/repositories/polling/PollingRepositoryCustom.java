/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.polling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.PollingType;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public interface PollingRepositoryCustom {
  PollingDocument addSubscribersToExistingPollingDoc(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures,
      Map<String, List<String>> signaturesLock);
  PollingDocument addSubscribersToExistingPollingDoc(
      String accountId, String uuId, List<String> signatures, Map<String, List<String>> signaturesLock);
  PollingDocument deleteDocumentIfOnlySubscriber(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures);
  PollingDocument removeDocumentIfOnlySubscriber(String accountId, String pollingDocId, List<String> signatures);
  PollingDocument deleteSubscribersFromExistingPollingDoc(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures);
  PollingDocument removeSubscribersFromExistingPollingDoc(
      String accountId, String pollingDocId, List<String> signatures);
  UpdateResult updateSelectiveEntity(String accountId, String pollDocId, String key, Object value);
  PollingDocument findByUuidAndAccountIdAndSignature(String pollingDocId, String accountId, List<String> signature);
  List<PollingDocument> findManyByUuidsAndAccountId(List<String> pollingDocIds, String accountId);
  List<PollingDocument> findUuidsBySignaturesAndAccountId(List<String> signatures, String accountId);

  DeleteResult deleteAll(Criteria criteria);
}
