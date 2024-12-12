@echo off
rem --- Modified version of assemble.cmd from the FXCore tools distribution. Assumes FXCore tools
rem --- including macro processor and assembler are in the same dir as this file, or a bin subdirectory.
rem --- All build artifacts (intermediate code files, assembler listings, final HEX file) are placed
rem --- in the output directory named by argument 2.

rem --- If the first arg is not "called" this script will PAUSE before exit. If the first arg is "called"
rem --- then this script will exit without PAUSE and set the return exit code to zero if no errors, or non-zero
rem --- if any errors were detected.

rem argument order is: 
rem   %1="called" (optional) to indicate this CMD file is being called by another CMD. No PAUSE before exit.
rem   %1=full path and source file name
rem   %2=output directory (relative to source file)
rem   %3=fxcore preprocessor library path
rem   %4=assembler_directive 1
rem   %5=assembler_directive 2
rem   %6=assembler_directive 3
rem   %7=assembler_directive 4

set mypath=%~dp0
set called=no
if %1.==called. (
	set called=yes
	shift
)
set source=%1
set sourcedir=%~dp1
set filename=%~n1
set outputdir=%sourcedir%%2
set libdir=%3
set asmoptions=%4 %5 %6 %7

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

rem If no preproc library, do not specify one
set libspec=-l %libdir%
IF NOT EXIST "%libdir%" (
	set libspec=
)

rem ... useful for debugging ...
rem echo source=%source%
rem echo sourcedir=%sourcedir%
rem echo filename=%filename%
rem echo outputdir=%outputdir%
rem echo libdir=%libdir%
rem echo asmoptions=%asmoptions%
rem echo libspec=%libspec%
rem echo called=%called%
rem echo mypath=%mypath%
rem echo jarfile=%jarfile%

rem if old .fxo exists delete it
echo -- Removing output files
IF EXIST "%outputdir%\%filename%.fxo" (
 del "%outputdir%\%filename%.fxo"
)
rem if old .fxc-mp exists delete it
IF EXIST "%outputdir%\%filename%.fxc-mp" (
 del "%outputdir%\%filename%.fxc-mp"
)
rem create output dir if not exists
IF NOT EXIST "%outputdir%" (
 mkdir "%outputdir%"
)


rem Macro processor: reads input file and outputs .fxc-mp
echo -- Running FXCore Macro Preprocessor
java -jar %jarfile% %source% "%outputdir%\%filename%.fxc-mp"
if not %errorlevel% EQU 0 (
	echo ERROR running macro preprocessor
	color 4F
	if %called%==yes exit /b 1
	pause
	exit /b 1
)

rem FXCore Preprocessor: read .fxc-mp and outputs (implied) .fxo
echo -- Running FXCore Preprocessor
"%fxcoretools%\FXCorePreProc.exe" %libspec% "%outputdir%\%filename%.fxc-mp"
if not %errorlevel% EQU 0 (
	echo ERROR running FXCore preprocessor see %outputdir%\%filename%.fpl
	color 4F
	if %called%==yes exit /b 1
	pause
	exit /b 1
)

rem preprocessor ran fine now assemble the new .fxo file to (implied) .hex
echo -- Running Assembler
"%fxcoretools%\FXCoreCmdAsm.exe" %asmoptions% "%outputdir%\%filename%.fxo"
if not %errorlevel% EQU 0 (
	echo ERROR running FXCore assembler
	color 4F
	if %called%==yes exit /b 1
	pause
	exit /b 1
)

echo Assembly complete, no errors detected
if %called%==no pause
exit /b 0
