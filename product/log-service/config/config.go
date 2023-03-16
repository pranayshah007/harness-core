// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package config

import (
	"github.com/kelseyhightower/envconfig"
)

// Config provides the system configuration.
type Config struct {
	Debug bool `envconfig:"LOG_SERVICE_DEBUG_MODE"`
	Trace bool `envconfig:"LOG_SERVICE_TRACE"`

	Auth struct {
		DisableAuth bool   `envconfig:"LOG_SERVICE_DISABLE_AUTH"`
		LogSecret   string `envconfig:"LOG_SERVICE_SECRET" default:"secret" secret:"true"`
		GlobalToken string `envconfig:"LOG_SERVICE_GLOBAL_TOKEN" default:"token" secret:"true"`
	}

	Platform struct {
		BaseURL string `envconfig:"LOG_SERVICE_PLATFORM_BASE_URL"`
	}

	Server struct {
		Bind  string `envconfig:"LOG_SERVICE_HTTP_BIND" default:":8079"`
		Proto string `envconfig:"LOG_SERVICE_HTTP_PROTO"`
		Host  string `envconfig:"LOG_SERVICE_HTTP_HOST"`
		Acme  bool   `envconfig:"LOG_SERVICE_HTTP_ACME"`
	}

	Bolt struct {
		Path string `envconfig:"LOG_SERVICE_BOLT_PATH" default:"bolt.db"`
	}

	// S3 compatible store
	S3 struct {
		Bucket          string `envconfig:"LOG_SERVICE_S3_BUCKET"`
		Acl             string `envconfig:"LOG_SERVICE_S3_ACL"`
		Prefix          string `envconfig:"LOG_SERVICE_S3_PREFIX"`
		Endpoint        string `envconfig:"LOG_SERVICE_S3_ENDPOINT"`
		PathStyle       bool   `envconfig:"LOG_SERVICE_S3_PATH_STYLE"`
		Region          string `envconfig:"LOG_SERVICE_S3_REGION"`
		AccessKeyID     string `envconfig:"LOG_SERVICE_S3_ACCESS_KEY_ID" secret:"true"`
		AccessKeySecret string `envconfig:"LOG_SERVICE_S3_SECRET_ACCESS_KEY" secret:"true"`
	}

	Redis struct {
		Endpoint             string `envconfig:"LOG_SERVICE_REDIS_ENDPOINT"`
		Password             string `envconfig:"LOG_SERVICE_REDIS_PASSWORD" secret:"true"`
		SSLEnabled           bool   `envconfig:"LOG_SERVICE_REDIS_SSL_ENABLED"`
		CertPath             string `envconfig:"LOG_SERVICE_REDIS_SSL_CA_CERT_PATH"`
		DisableExpiryWatcher bool   `envconfig:"LOG_SERVICE_REDIS_DISABLE_EXPIRY_WATCHER"`
	}

	// Whether to use secret env variables as they are, or talk to GCP secret
	// manager to resolve them.
	SecretResolution struct {
		Enabled     bool   `envconfig:"LOG_SERVICE_SECRET_RESOLUTION_ENABLED"`
		GcpProject  string `envconfig:"LOG_SERVICE_SECRET_RESOLUTION_GCP_PROJECT"`
		GcpJsonPath string `envconfig:"LOG_SERVICE_SECRET_RESOLUTION_GCP_JSON_PATH"`
	}
}

// Load loads the configuration from the environment.
func Load() (Config, error) {
	cfg := Config{}
	err := envconfig.Process("", &cfg)
	return cfg, err
}
