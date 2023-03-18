// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// swag init -g cmd/server.go

package cmd

import (
	"bufio"
	"fmt"
	"net/http"
	"os"
	"strings"

	"github.com/rs/zerolog"

	"github.com/harness/harness-core/queue-service/hsqs/config"
	_ "github.com/harness/harness-core/queue-service/hsqs/docs"
	"github.com/harness/harness-core/queue-service/hsqs/handler"
	"github.com/harness/harness-core/queue-service/hsqs/instrumentation/metrics"
	appdynamics "github.com/harness/harness-core/queue-service/hsqs/middleware"
	"github.com/harness/harness-core/queue-service/hsqs/profiler"
	"github.com/harness/harness-core/queue-service/hsqs/router"
	"github.com/harness/harness-core/queue-service/hsqs/store/redis"
	"github.com/joho/godotenv"
	"github.com/labstack/echo/v4"
	"github.com/labstack/gommon/log"

	"github.com/spf13/cobra"
	echoSwagger "github.com/swaggo/echo-swagger"
)

const envarg = "envfile"

// @title          Swagger Doc- hsqs
// @version        1.0
// @description    This is a queuing client.
// @termsOfService http://swagger.io/terms/

// @contact.name  API Support
// @contact.url   http://www.swagger.io/support
// @contact.email support@swagger.io

// @license.name Apache 2.0
// @license.url  http://www.apache.org/licenses/LICENSE-2.0.html

// @host     localhost:9091
// @BasePath /
// @schemes  http
var serverCmd = &cobra.Command{
	Use:   "server",
	Short: "Start hsqs server",
	Long:  `Start hsqs server`,
	Run: func(cmd *cobra.Command, args []string) {
		path, _ := cmd.Flags().GetString(envarg)
		err := godotenv.Load(path)
		l := zerolog.New(os.Stderr).With().Timestamp().Logger()
		if err != nil {
			l.Error().Msgf("error parsing config file")
		}

		c, err := config.Load()
		if err != nil {
			l.Error().Msgf("error in loading the environment variables")
			panic("error loading environment variables")
		}
		startServer(c)
	},
}

func startServer(c *config.Config) {

	// enabling Profiler for service
	if c.EnableProfiler {
		err := profiler.Start(c)
		if err != nil {
			log.Warn(err.Error())
		}
	}
	log.Info("Initialising AppDynamics...")

	if c.AppDynamicsConfig.Enabled {
		if err := appdynamics.Init(c); err != nil {
			log.Error(err.Error())
		} else {
			log.Info("AppDyanmics initialised")
		}
	}

	// enabling AppDynamics For Service

	log.Info("Initializing redis service with values ", c.Redis.Endpoint, " ", len(c.Redis.Password), " ", c.Redis.SSLEnabled, " ", c.Redis.CertPath, " ", c.ClaimTimeout, c.PendingTimeout)
	store := redis.NewRedisStoreWithTLS(c.Redis.Endpoint, c.Redis.Password, c.Redis.SSLEnabled, c.Redis.CertPath, c.PendingTimeout, c.ClaimTimeout)

	log.Info("redis initialized with store ", store)
	customMetrics := metrics.InitMetrics()
	h := handler.NewHandler(store, customMetrics)

	r := router.New(c)
	r.GET("/swagger/*", echoSwagger.WrapHandler)
	r.GET("/", func(c echo.Context) error {
		return c.String(http.StatusOK, "Hello, World!")
	})
	r.GET("/version", version)

	g := r.Group("v1")

	h.Register(g)

	err := r.Start(fmt.Sprintf("%s:%s", c.Server.Host, c.Server.PORT))
	if err != nil {
		r.Logger.Fatalf(err.Error())
	}
}

func init() {
	rootCmd.AddCommand(serverCmd)
	serverCmd.Flags().StringP(envarg, "e", "local.env", "Config file for the server")
}

func version(c echo.Context) error {

	file, err := os.Open("./build.properties")
	if err != nil {
		fmt.Println("Error opening file:", err)
		return fmt.Errorf("version file not found")
	}
	defer file.Close()

	// Read the file line by line
	scanner := bufio.NewScanner(file)
	version := make(map[string]string)
	for scanner.Scan() {
		line := scanner.Text()
		parts := strings.Split(line, "=")
		if len(parts) != 2 {
			continue
		}
		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])
		version[key] = value
	}

	// Generate the version response
	majorVersion := version["build.majorVersion"]
	minorVersion := version["build.minorVersion"]
	patch := version["build.patch"]
	buildNo := version["BUILD_NO"]

	response := fmt.Sprintf("{\"resource\":{\"versionInfo\":{\"version\":%s.%s.%s,\"buildNo\":%s}}}", majorVersion, minorVersion, patch, buildNo)

	log.Info(response)

	return c.String(http.StatusOK, response)
}
