package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.HPersistence;

import software.wings.service.intfc.DMSDataStoreService;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DMSMongoDataStoreServiceImpl implements DMSDataStoreService {
  @Inject private HPersistence persistence;

  @Override
  public <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate) {
    log.info("saving records into seelction log collection");
    if (isEmpty(records)) {
      return;
    }
    if (ignoreDuplicate) {
      persistence.saveIgnoringDuplicateKeys(records);
    } else {
      persistence.save(records);
    }
  }
}
