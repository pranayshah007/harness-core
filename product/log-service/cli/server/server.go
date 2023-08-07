// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package server

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/signal"

	"github.com/harness/harness-core/commons/go/lib/secret"
	"github.com/harness/harness-core/product/platform/client"

	consumerWorker "github.com/harness/harness-core/product/log-service/download/consumer"
	"github.com/harness/harness-core/product/log-service/download/zipwork"
	"github.com/harness/harness-core/product/log-service/queue"
	redisQueue "github.com/harness/harness-core/product/log-service/queue/redis"
	redisStream "github.com/harness/harness-core/product/log-service/stream/redis"

	"github.com/harness/harness-core/product/log-service/cache"
	memoryCache "github.com/harness/harness-core/product/log-service/cache/memory"
	redisCache "github.com/harness/harness-core/product/log-service/cache/redis"
	"github.com/harness/harness-core/product/log-service/config"
	redisDb "github.com/harness/harness-core/product/log-service/db/redis"
	"github.com/harness/harness-core/product/log-service/handler"
	"github.com/harness/harness-core/product/log-service/logger"
	memoryQueue "github.com/harness/harness-core/product/log-service/queue/memory"
	"github.com/harness/harness-core/product/log-service/server"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/store/bolt"
	"github.com/harness/harness-core/product/log-service/store/s3"
	"github.com/harness/harness-core/product/log-service/stream"
	memoryStream "github.com/harness/harness-core/product/log-service/stream/memory"

	"github.com/joho/godotenv"
	"github.com/sirupsen/logrus"
	"gopkg.in/alecthomas/kingpin.v2"
)

type serverCommand struct {
	envfile string
}

func (c *serverCommand) run(*kingpin.ParseContext) error {
	godotenv.Load(c.envfile)

	ctx := context.Background()
	ctx, cancel := context.WithCancel(ctx)

	// load the system configuration from the environment.
	config, err := config.Load()
	if err != nil {
		logrus.WithError(err).
			Errorln("cannot load the service configuration")
		return err
	}

	// Parse the entire config to resolve any secrets (if required)
	err = secret.Resolve(ctx, config.SecretResolution.Enabled, config.SecretResolution.GcpProject,
		config.SecretResolution.GcpJsonPath, &config)
	if err != nil {
		logrus.WithError(err).
			Errorln("could not resolve secrets")
		return err
	}

	// init the system logging.
	initLogging(config)

	if config.Auth.DisableAuth {
		logrus.Warnln("log service is being started without auth, SHOULD NOT BE DONE FOR PROD ENVIRONMENTS")
	}

	var store store.Store
	if config.S3.Bucket != "" {
		// create the s3 store.
		logrus.Infof("configuring log store to use s3 compatible backend with endpoint: %s and bucket name: %s and ACL: %s",
			config.S3.Endpoint, config.S3.Bucket, config.S3.Acl)
		store = s3.NewEnv(
			config.S3.Bucket,
			config.S3.Prefix,
			config.S3.Endpoint,
			config.S3.PathStyle,
			config.S3.AccessKeyID,
			config.S3.AccessKeySecret,
			config.S3.Region,
			config.S3.Acl,
		)
	} else {
		// create the blob store.
		store, err = bolt.New(config.Bolt.Path)
		if err != nil {
			logrus.WithError(err).
				Fatalln("cannot initialize the bolt database")
			return err
		}

		logrus.Warnln("the bolt datastore is configured")
		logrus.Warnln("the bolt datastore is suitable for testing purposes only")
	}

	// create the stream server.
	var stream stream.Stream

	// create the cache server.
	var cache cache.Cache

	var queue queue.Queue

	if config.Redis.UseSentinel {
		// Create Redis Sentinel storage instance
		db := redisDb.NewConnection("", config.Redis.Password, false, true, "", config.Redis.MasterName, config.Redis.SentinelAddrs)
		stream = redisStream.NewWithClient(db, config.Redis.DisableExpiryWatcher, config.Redis.ScanBatch)
		cache = redisCache.NewWithClient(db)
		queue = redisQueue.NewWithClient(db)
		queue.Create(ctx, config.ConsumerWorker.StreamName, config.ConsumerWorker.ConsumerGroup)
		logrus.Infof("configuring log stream, cache and queue to use Redis Sentinel")
	} else if config.Redis.Endpoint != "" {
		db := redisDb.NewConnection(config.Redis.Endpoint, config.Redis.Password, config.Redis.SSLEnabled, false, config.Redis.CertPath, "", nil)
		stream = redisStream.NewWithClient(db, config.Redis.DisableExpiryWatcher, config.Redis.ScanBatch)
		cache = redisCache.NewWithClient(db)
		queue = redisQueue.NewWithClient(db)
		queue.Create(ctx, config.ConsumerWorker.StreamName, config.ConsumerWorker.ConsumerGroup)
		logrus.Infof("configuring log stream, cache and queue to use Redis: %s", config.Redis.Endpoint)
	} else {
		// create the in-memory stream
		stream = memoryStream.New()
		cache = memoryCache.New()
		queue = memoryQueue.New()
		logrus.Infoln("configuring log stream, cache and queue to use in-memory stream")
	}

	// create and start consume download pool
	workerPool := consumerWorker.
		NewWorkerPool(
			config.ConsumerWorker.WorkerPool,
			config.ConsumerWorker.ConsumerGroup,
			config.ConsumerWorker.StreamName,
		)

	workerPool.Execute(zipwork.Work, queue, cache, store, config)

	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")

	// create the http server.
	server := server.Server{
		Acme:    config.Server.Acme,
		Addr:    config.Server.Bind,
		Handler: handler.Handler(queue, cache, stream, store, config, ngClient),
	}

	// trap the os signal to gracefully shutdown the
	// http server.
	s := make(chan os.Signal, 1)
	signal.Notify(s, os.Interrupt)
	defer func() {
		signal.Stop(s)
		cancel()
	}()
	go func() {
		select {
		case val := <-s:
			logrus.Infof("received OS Signal to exit server: %s", val)
			cancel()
		case <-ctx.Done():
			logrus.Infoln("received a done signal to exit server")
		}
	}()

	logrus.Infof(fmt.Sprintf("server listening at port %s", config.Server.Bind))

	// starts the http server.
	err = server.ListenAndServe(ctx)
	if err == context.Canceled {
		logrus.Infoln("program gracefully terminated")
		return nil
	}

	if err != nil {
		logrus.Errorf("program terminated with error: %s", err)
	}

	return err
}

// Register the server commands.
func Register(app *kingpin.Application) {
	c := new(serverCommand)

	cmd := app.Command("server", "start the server").
		Action(c.run)

	cmd.Flag("env-file", "environment file").
		Default(".env").
		StringVar(&c.envfile)
}

// Get stackdriver to display logs correctly
// https://github.com/sirupsen/logrus/issues/403
// TODO: (Vistaar) Move to uber zap similar to other services
type OutputSplitter struct{}

func (splitter *OutputSplitter) Write(p []byte) (n int, err error) {
	if bytes.Contains(p, []byte("level=error")) {
		return os.Stderr.Write(p)
	}
	return os.Stdout.Write(p)
}

// helper function configures the global logger from
// the loaded configuration.
func initLogging(c config.Config) {
	logrus.SetOutput(&OutputSplitter{})
	l := logrus.StandardLogger()
	logger.L = logrus.NewEntry(l)
	if c.Debug {
		l.SetLevel(logrus.DebugLevel)
	}
	if c.Trace {
		l.SetLevel(logrus.TraceLevel)
	}
}
