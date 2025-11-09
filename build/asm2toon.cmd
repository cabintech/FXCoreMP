@echo off
rem --- Stand alone TOON processor (ASM source --> TOON source)
rem passed order is: 
rem   %1=source file
rem   %2=output file

set mypath=%~dp0

rem ... useful for debugging ...
echo 1=%1
echo 2=%2
echo mypath=%mypath%

rem If the macro/TOON jar is not here, assume /jar or /bin subdir
set jarfile="%mypath%FXCoreMP.jar"
IF NOT EXIST %jarfile% (
  set jarfile="%mypath%jar\FXCoreMP.jar"
  rem IF NOT EXIST %jarfile% (
  rem  set jarfile="%mypath%bin\FXCoreMP.jar"
  rem )
)

rem If FXCore tools are not here, assume a /bin subdir
set fxcoretools=%mypath%
IF NOT EXIST "%mypath%\FXCoreCmdAsm.exe" (
  set fxcoretools=%mypath%\bin
)

rem Macro processor: reads input file and outputs .fxc-mp
echo -- Running FXCore Macro Preprocessor
java -jar %jarfile% %1 %2 --nomacro --reversetoon --debug=info
if not %errorlevel% EQU 0 (
	echo ERROR running macro preprocessor
)

