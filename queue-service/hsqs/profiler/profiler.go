// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package profiler

import (
	"cloud.google.com/go/profiler"
	"github.com/harness/harness-core/queue-service/hsqs/config"
)

// Start initializes the profiler.
func Start(config *config.Config) error {
	cfg := profiler.Config{
		Service:        config.ServiceName,
		ServiceVersion: config.Version,
	}
	return profiler.Start(cfg)
}
