// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// swag init -g cmd/server.go

package cmd

import (
	"fmt"
	"net/http"
	"os"

	"github.com/rs/zerolog"

	"github.com/harness/harness-core/queue-service/hsqs/config"
	_ "github.com/harness/harness-core/queue-service/hsqs/docs"
	"github.com/harness/harness-core/queue-service/hsqs/handler"
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

	if c.EnableProfiler {
		err := profiler.Start(c)
		if err != nil {
			log.Warn(err.Error())
		}
	}

	r := router.New(c)
	r.GET("/swagger/*", echoSwagger.WrapHandler)
	r.GET("/", func(c echo.Context) error {
		return c.String(http.StatusOK, "Hello, World!")
	})

	g := r.Group("v1")

	store := redis.NewRedisStoreWithTLS(c.Redis.Endpoint, c.Redis.Password, c.Redis.SSLEnabled, c.Redis.CertPath)
	h := handler.NewHandler(store)
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
