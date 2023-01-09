package api

import (
	"idp-service/handlers"

	"github.com/labstack/echo/v4"
)

func AppConfigYaml(e *echo.Echo){

	e.POST("/add-appconfig", handlers.AddAppConfigs)
	e.GET("/appconfig/:accountId", handlers.GetAppConfigFromAccountID)
	e.PUT("/update-appconfig/:accountId", handlers.UpdateAppConfigYaml)
	e.DELETE("/delete-appconfig/:accountId", handlers.DeleteAUser)
	
}
