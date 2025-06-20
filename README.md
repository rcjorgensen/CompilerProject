# CompilerProject

This repository contains a collection of Kotlin modules that implement a simple compiler tool chain.  The code targets a small virtual machine and includes an assembler, a compiler for the CPRL language, and runtime utilities.  Each subdirectory represents an IntelliJ IDEA module.

## Modules

- **edu.citadel.compiler** – Common compiler utilities such as scanning, parsing, and error handling.
- **edu.citadel.cvm** – The CPRL Virtual Machine used to execute compiled programs.
- **edu.citadel.assembler** – Assembler that translates assembly files into CVM object code.
- **edu.citadel.cprl** – Compiler for the CPRL programming language.

## Requirements

- JDK 17 or later
- Kotlin compiler (tested with version 1.5 or later)
- IntelliJ IDEA is recommended for working with the provided `.iml` module files.

## Basic Setup

1. Open the project in IntelliJ IDEA using `Open` and select the repository root. The IDE will load each module automatically.
2. Build the modules from the IDE or invoke `kotlinc` on the sources manually. For example:
   ```bash
   kotlinc edu.citadel.compiler/src -d build/compiler.jar
   kotlinc -classpath build/compiler.jar edu.citadel.cvm/src -d build/cvm.jar
   ```
3. Run the main entry points directly from the IDE or via the command line. Example:
   ```bash
   kotlin -classpath build/cvm.jar edu.citadel.cvm.CVM program.obj
   ```

These steps provide a minimal workflow for compiling and executing programs on the CVM.  Adjust paths as needed for your environment.
