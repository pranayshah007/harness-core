// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package cache

import (
	"context"
	"time"
)

// Cache defines the cache functions interface
type Cache interface {
	// Create an entry into log
	Create(context.Context, string, interface{}, time.Duration) error

	// Get an entry into log
	Get(context.Context, string) ([]byte, error)

	// Ping the cache for readiness
	Ping(context.Context) error

	// Exists file in bucket
	Exists(context.Context, string) bool

	// Delete the entry in cache
	Delete(context.Context, string) error
}
