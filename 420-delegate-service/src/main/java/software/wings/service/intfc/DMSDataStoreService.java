package software.wings.service.intfc;

import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.store.Store;

import java.util.List;

public interface DMSDataStoreService {
  <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate);
  <T extends GoogleDataStoreAware> void saveInStore(
      Class<T> clazz, List<T> records, boolean ignoreDuplicate, Store store);
}
