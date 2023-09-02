-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

SET SEARCH_PATH = "public";

DROP FUNCTION IF EXISTS harness_time_bucket_list_bigint;

CREATE OR REPLACE FUNCTION harness_time_bucket_list_bigint (p_started_at_epoch bigint, p_ended_at_epoch bigint, p_bucket_width bigint, p_debug bool DEFAULT FALSE)
	RETURNS TABLE (
		c_epoch_time bigint)
	LANGUAGE PLPGSQL
	IMMUTABLE PARALLEL SAFE
	AS $$
DECLARE
	v_started_at_epoch_trunc bigint;
	v_ended_at_epoch_trunc bigint;
	v_dtm_stop_num int;
BEGIN
	v_started_at_epoch_trunc := (p_started_at_epoch / p_bucket_width)::bigint * p_bucket_width;
	v_ended_at_epoch_trunc := (p_ended_at_epoch / p_bucket_width)::bigint * p_bucket_width;
	v_dtm_stop_num := (v_ended_at_epoch_trunc - v_started_at_epoch_trunc) / p_bucket_width;
	IF p_debug THEN
		RAISE INFO 'v_started_at_epoch_trunc = %, v_ended_at_epoch_trunc = %, v_dtm_stop_num = %', v_started_at_epoch_trunc, v_ended_at_epoch_trunc, v_dtm_stop_num;
	END IF;
	RETURN QUERY (
		SELECT
			(v_started_at_epoch_trunc + (t.series * p_bucket_width)) AS c_epoch_time
		FROM (
			SELECT
				*
			FROM
				generate_series(0, v_dtm_stop_num) series) t);
END;
$$;