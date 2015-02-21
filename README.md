# PlayPen

PlayPen is a server management and load balancing system.

## Protobuf Compilation

You must have the protoc compiler installed in order to compile the coordination
protocol. Run build_protocol.bat or build_protocol.sh from the project's root
directory.

## Usage

Before using any playpen tools, PlayPen needs to set itself up. Simply place the
playpen jar wherever you wish playpen's home directory to be, and run it without
any arguments. It will create the default configuration files and exit.

It is generally recommended to use the bundled scripts to run playpen instead of
launching the jar manually. To set this up, simply run

    java -jar PlayPen-1.0-SNAPSHOT.jar
    
This should copy some scripts and configuration files into the current folder.

To start the network coordinator, run

    playpen-network

To start a local coordinator, run

    playpen-local

Packaging tools are found at the "p3" command. Run it to see a list of them:

    playpen-p3
    
The playpen cli client can be run with:

    playpen-cli

Note that the cli uses the "local.json" configuration file as it represents itself to the
network as a local coordinator.