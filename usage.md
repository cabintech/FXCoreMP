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

The macro processor is a command-line application and must be run from a command window or as part of a
script. The basic command to run the macro processor is:

```
java -jar FXCoreMP.jar <input-file> <output-file>
```

This will read source code (with macro statements) from the named input file, and write the expanded
source code to the named output file. Optionally, one or more macro environment variables can be
assigned a value by adding "-E" arguments to the end of the command line:

```
java -jar FXCoreMP.jar <input-file> <output-file> -Eenvparm1=value1 -Eenvparm2=value2 ...
```

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
| 2 | myfile.xfc | Macro Processor<br>FXCoreMP | myfile.xfc-mpp |
| 3 | myfile.xfc-mpp | FXCore Preprocessor<br>FXCorePreProc | myfile.fxo |
| 4 | myfile.fxo | Assembler<br>FXCoreCmdAsm | myfile.hex |

This project includes a Windows .cmd file that implements the above steps and also directs all
build artifacts to a bin/ subdirectory to keep them separated from source files (which is handy when
using a source control system like git to manage the source files). You can also modify the
assemble.cmd file supplied with the FXCore assembler tools to run the macro processor as the first step.

At this time FXCoreMP has been tested only on Windows. Since there is nothing inherently platform specific about it,
it should be possible to run it on any platform that supports Java including Windows, MacOS, and Linux.
