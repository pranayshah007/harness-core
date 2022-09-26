package io.harness.springdata;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
class Handler implements InvocationHandler {
  private final MongoTemplate mongoTemplate;
  private final long MAX_TIME_IN_MILLIS_FOR_MONGO_OPERATIONS = 1;

  public Handler(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public Object invoke(Object proxy, Method method, Object[] args)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Query query = null;
    try {
      Iterator<Object> argIt = Arrays.stream(args).iterator();
      ArrayList<Object> newArgs = new ArrayList<>();
      while (argIt.hasNext()) {
        if (argIt.next().equals(Query.class)) {
          query = (Query) argIt.next();
        } else {
          newArgs.add(argIt.next());
        }
      }
      if (query != null) {
        query.maxTimeMsec(MAX_TIME_IN_MILLIS_FOR_MONGO_OPERATIONS);
        newArgs.add(query);
      }
      return method.invoke(mongoTemplate, newArgs);
    } catch (UncategorizedMongoDbException ex) {
      log.error("query {} exceeded max time limit.", query);
      throw ex;
    }
  }
}
