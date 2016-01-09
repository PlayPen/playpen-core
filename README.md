# PlayPen Core

PlayPen is a generic cross-platform server management and load balancing framework. PlayPen is designed primarily for
ephemeral services, ones that do not need to store any permanent data and can be shut down or spun up at will.

By itself, playpen does not do anything and must receive commands from somewhere whether that be the command line
client, [PVI](https://github.com/PlayPen/PVI), another client, or a plugin.

PlayPen was originally developed to automatically balance minecraft game servers at [The Chunk](https://thechunk.net),
but can be used to deploy and manage any kind of self-contained service.

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

**Warning: You cannot run multiple instances of playpen from the same installation!** If you need to run multiple
instances of playpen (for example, a network coordinator and a local coordinator), use two separate directories and
playpen jars.

## Plugins

Plugins are currently only supported for the network coordinator. Local coordinator support will come soon, but until
then it is impossible to add custom package execution steps.

## Packages

PlayPen uses a package system known as _P3_ to send files across the network. The network coordinator acts as a package
repository which all local coordinators can pull from. All services run by playpen must be contained in a package.
Common components shared between services can be split off into their own package using P3's dependency system.