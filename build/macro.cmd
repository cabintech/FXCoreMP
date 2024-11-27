@echo off
rem --- Stand alone macro processor
rem passed order is: 
rem   %1=source file
rem   %2=output file

set mypath=%~dp0

rem ... useful for debugging ...
rem echo 1=%1
rem echo 2=%2
rem echo mypath=%mypath%

java -jar FXCoreMP.jar "%1" "%2" --debug=info
