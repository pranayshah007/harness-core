-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
ALTER TABLE billing_data ADD COLUMN IF NOT EXISTS usagestarttime TIMESTAMPTZ;
ALTER TABLE billing_data_hourly ADD COLUMN IF NOT EXISTS usagestarttime TIMESTAMPTZ;
ALTER TABLE billing_data_aggregated ADD COLUMN IF NOT EXISTS usagestarttime TIMESTAMPTZ;
ALTER TABLE billing_data_hourly_aggregated ADD COLUMN IF NOT EXISTS usagestarttime TIMESTAMPTZ;
ALTER TABLE billing_data ADD COLUMN IF NOT EXISTS usagestoptime TIMESTAMPTZ;
ALTER TABLE billing_data_hourly ADD COLUMN IF NOT EXISTS usagestoptime TIMESTAMPTZ;
ALTER TABLE billing_data_aggregated ADD COLUMN IF NOT EXISTS usagestoptime TIMESTAMPTZ;
ALTER TABLE billing_data_hourly_aggregated ADD COLUMN IF NOT EXISTS usagestoptime TIMESTAMPTZ;
ALTER TABLE billing_data_aggregated ADD COLUMN IF NOT EXISTS usagedurationseconds DOUBLE PRECISION;
ALTER TABLE billing_data_hourly_aggregated ADD COLUMN IF NOT EXISTS usagedurationseconds DOUBLE PRECISION;
ALTER TABLE billing_data_aggregated ADD COLUMN IF NOT EXISTS pricingsource TEXT;
ALTER TABLE billing_data_hourly_aggregated ADD COLUMN IF NOT EXISTS pricingsource TEXT;
COMMIT;
