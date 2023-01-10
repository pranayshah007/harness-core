// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package config

import (
	"github.com/kelseyhightower/envconfig"
)

// Config provides the system configuration.
type Config struct {
	Debug bool `envconfig:"HSQS_DEBUG"`

	Version string `envconfig:"HSQS_VERSION" default:"1.0.0"`

	ServiceName string `envconfig:"HSQS_SERVICE_NAME" default:"queue_service"`

	DisableAuth bool `envconfig:"HSQS_DISABLE_AUTH"`

	EnableProfiler bool `envconfig:"HSQS_ENABLE_PROFILER"`

	Server struct {
		PORT string `envconfig:"HSQS_PORT" default:"9091"`
		Host string `envconfig:"HSQS_HOST"`
	}

	Redis struct {
		Endpoint   string `envconfig:"HSQS_REDIS_ENDPOINT"`
		Password   string `envconfig:"HSQS_REDIS_PASSWORD" secret:"true"`
		SSLEnabled bool   `envconfig:"HSQS_REDIS_REDIS_SSL_ENABLED"`
		CertPath   string `envconfig:"HSQS_REDIS_SSL_CA_CERT_PATH"`
	}

	Secret string `envconfig:"JWT_SECRET" default:"vhtftr5486786764563452fhtfthffuygiogyuyryr543432453"`
}

// Load loads the configuration from the environment.
func Load() (*Config, error) {
	cfg := Config{}
	err := envconfig.Process("", &cfg)
	return &cfg, err
}
