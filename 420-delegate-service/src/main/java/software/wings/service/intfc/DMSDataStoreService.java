package software.wings.service.intfc;

import io.harness.persistence.GoogleDataStoreAware;

import java.util.List;

public interface DMSDataStoreService {
  <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate);
}
