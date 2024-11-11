# Installation

In general the macro processor is used as part of a sequence of tools (the "tool chain") to completely assemble
source code into a HEX file suitable for loading into the FXCore. The tool chain consists of several programs
each of which takes an input file, performs some transformation on it, and writes an output file, which is then
the input for the next program in the chain.

It is recommended that the macro processor be the first tool in the chain as it allows macros to expand into
code that could use FXCore library functions. The sequence of tools to complete an assembly process is shown
below. The file extensions shown are by convention and helps identify files that are built at each stage of
the process. This can aid debugging assembler errors.

| Step | Input File | Program | Output File |
|------|------------|---------|-------------|
| 1 | myfile.xfc | Text Editor<br>Notepad++ | myfile.xfc |
| 2 | myfile.xfc | Macro Processor<br>FXCoreMP | myfile.xfc-mp |
| 3 | myfile.xfc-mp | FXCore Preprocessor<br>FXCorePreProc | myfile.fxo |
| 4 | myfile.fxo | Assembler<br>FXCoreCmdAsm | myfile.hex |

At this time FXCoreMP has been tested only on Windows. Since there is nothing inherently platform specific about it,
it should be possible to run it on any platform that supports Java including Windows, MacOS, and Linux.

The following sections describe how to install the Java runtime environment, FXCoreMP, and modify the
assembler scripts to run FXCoreMP as the first step in the tool chain.

## Java Installation

The FXCoreMP program is written in Java and requires a Java runtime to be installed. Any Java version 17 or above
should work (see the [OpenJDK site](https://jdk.java.net/) for current Java version downloads for most platforms).
To verify your Java installation run the command `java --version` from a command line. If the command is not
found you need to install Java on your system. If it is, verify the version is at least 17. For example:

```
> java --version
java 18 2022-03-22
Java(TM) SE Runtime Environment (build 18+36-2087)
Java HotSpot(TM) 64-Bit Server VM (build 18+36-2087, mixed mode, sharing)
```

## FXCoreMP Installation

FXCoreMP is fully contains in a single JAR file. Download the build/jar/FXCoreMP.jar file from 
this repository into the FXCore tools directory that
contain the FXCore executables (FXCoreCmdAsm, FXCorePreroc, etc).

## Update Command Line Build Tools

Modify the FXCore "assemble.cmd" script to run the macro processor as the first step. The lines that have
been added or modified are noted in the comments: 

```winbatch
@rem passed order is: full_current_path current_directory name_part preproc_library_path assembler_directive assembler_directive
@echo off
rem if old .fxo exists delete it
IF EXIST "%~2\%~3.fxo" (
 del "%~2\%~3.fxo"
)

rem ADDED if old .fxo-mp exists delete it
-IF EXIST "%~2\%~3.fxo-mp" (
 del "%~2\%~3.fxo-mp"
)

rem ADDED Macro processor: reads .fxc and outputs .fxc-mp
java -jar "%~dp0FXCoreMP.jar" "%~2\%~3.fxc" "%~2\%~3.fxc-mp"
if not %errorlevel% EQU 0 (
	echo ERROR running macro processor
	color 4F
	pause
)

rem MODIFIED NEXT LINE run the preprocessor on the .fxc-mp file, outputs .fxo file
"%~dp0FXCorePreProc.exe" -l %4 %~2\%~3.fxc-mp
if not %errorlevel% EQU 0 (
	echo ERROR running preprocessor see %~3.fpl
	color 4F
	pause
) else (
	rem preprocessor ran fine now assemble the new .fxo file
	"%~dp0FXCoreCmdAsm.exe" %5 %6 -a "%~2\%~3.fxo"
	if %errorlevel% EQU 0 (
		echo. & echo NO ERRORS 
		pause
	) Else ( 
		echo. & echo ERROR FAILED &color 4F 
		pause
	)
)
```

You can also completely replace the assemble.cmd file with the version shown below (and
provided in the build directory of this repo). This 
version puts all build artifacts in a bin/ subdirectory, outputs more status information,
and can be called from another .cmd file for batch builds. If you use this version and
are using the Notepad++ editor to run the assembler, you will need to modify the editor's
`shortcuts.xml` file to provide the correct parameters with proper quoting. 

By placing
output files in the bin subdirectory generated files are kept separate from source files
which makes for easier management by version control tools like git. The modified command
file also returns a status code so if it is called by other batch tools they can know
when the assembly has failed.

<pre>
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
</pre>

As can be seen in the above scripts, the basic command to run the FXCoreMP processor is:

```
java -jar FXCoreMP.jar <input-file> <output-file>
```

This will read source code (with macro statements) from the named input file, and write the expanded
source code to the named output file. Optionally, one or more macro environment variables can be
assigned a value by adding "-E" arguments to the end of the command line (see the `$set` macro statement
description):

```
java -jar FXCoreMP.jar <input-file> <output-file> -Eenvparm1=value1 -Eenvparm2=value2 ...
```

