package api

import (
	"idp-service/handlers"

	"github.com/labstack/echo/v4"
)

func AppConfigYaml(e *echo.Echo){

	e.POST("/idp/appconfig/add", handlers.AddAppConfigs)
	e.GET("/idp/appconfig/:accountId", handlers.GetAppConfigFromAccountID)
	e.PUT("/idp/appconfig/update/:accountId", handlers.UpdateAppConfigYaml)
	e.DELETE("/idp/appconfig/delete/:accountId", handlers.DeleteAUser)
	
}
