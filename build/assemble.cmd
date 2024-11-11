@echo off
rem --- Modified version of assemble.cmd from the FXCore tools distribution
rem passed order is: 
rem   %1="batch" (optional)
rem   %1=full_current_path
rem   %2=current_directory
rem   %3=name_part
rem   %4=preproc_library_path
rem   %5=output_path
rem   %6=assembler_directive
rem   %7=preprocessor_directive

set mypath=%~dp0
set batch=no
if %1.==batch. (
	set batch=yes
	shift
)

rem ... useful for debugging ...
rem echo 1=%1
rem echo 2=%2
rem echo 3=%3
rem echo 4=%4
rem echo 5=%5
rem echo 6=%6
rem echo 7=%7
rem echo batch=%batch%
rem echo mypath=%mypath%

rem if old .fxo exists delete it
echo -- Removing output files
IF EXIST %5\%3.fxo (
 del %5\%3.fxo
)
rem if old .fxc-mp exists delete it
IF EXIST %5\%3.fxc-mp (
 del %5\%3.fxc-mp
)

rem Macro processor: reads .fxc and outputs bin\.fxc-mp
echo -- Running FXCore Macro Preprocessor
java -jar "%mypath%FXCoreMP.jar" "%~2\%3.fxc" "%~5\%3.fxc-mp"
if not %errorlevel% EQU 0 (
	echo ERROR running macro preprocessor
	color 4F
	if %batch%==yes exit /b 1
	pause
	exit /b 1
)

rem FXCore Preprocessor: read .fxc-mp and outputs .fxo
echo -- Running FXCore Preprocessor
"%mypath%FXCorePreProc.exe" -l %4 %5\%3.fxc-mp

if not %errorlevel% EQU 0 (
	echo ERROR running FXCore preprocessor see %5\%3.fpl
	color 4F
	if %batch%==yes exit /b 1
	pause
	exit /b 1
)

rem preprocessor ran fine now assemble the new .fxo file
echo -- Running Assembler
"%mypath%FXCoreCmdAsm.exe" %6 %5\%3.fxo
if not %errorlevel% EQU 0 (
	echo ERROR running FXCore assembler
	color 4F
	if %batch%==yes exit /b 1
	pause
	exit /b 1
)

echo Assembly complete, no errors detected
if %batch%==no pause
exit /b 0
