-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

--Simulates https://www.postgresql.org/docs/14/functions-datetime.html#FUNCTIONS-DATETIME-BIN
CREATE OR REPLACE FUNCTION harness_date_bin_ng_mgr(
p_bucket_width_ms bigint,
p_epoch_time_ms bigint)
RETURNS bigint
LANGUAGE PLPGSQL
IMMUTABLE
PARALLEL SAFE
AS $$
DECLARE
BEGIN
    RETURN ((p_epoch_time_ms / p_bucket_width_ms)::bigint * p_bucket_width_ms);
END;
$$ ;