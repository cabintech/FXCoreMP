@echo off
rem --- Stand alone TOON processor (ASM source --> TOON source)
rem passed order is: 
rem   %1=source file
rem   %2=output file

set mypath=%~dp0

rem ... useful for debugging ...
rem echo 1=%1
rem echo 2=%2
rem echo mypath=%mypath%

java -jar FXCoreMP.jar "%1" "%2" --nomacro --reversetoon --debug=info
