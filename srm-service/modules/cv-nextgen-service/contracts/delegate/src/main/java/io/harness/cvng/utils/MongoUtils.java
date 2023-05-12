package io.harness.cvng.utils;

import org.bson.types.ObjectId;

public class MongoUtils {
    public static Object convertToObjectIdIfRequired(final String uuid) {
        if (ObjectId.isValid(uuid)) {
            ObjectId objectIdFromGivenUuid = new ObjectId(uuid);
            String uuidFromNewObjectId = objectIdFromGivenUuid.toString();
            if (uuidFromNewObjectId.equals(uuid)) {
                return objectIdFromGivenUuid;
            } else {
                return uuid;
            }
        } else {
            return uuid;
        }
    }
}
