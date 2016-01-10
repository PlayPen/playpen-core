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

## Servers/Services

A service (or server) is a single instance of a running package (see below). When a service runs on a local coordinator,
it is given a unique working directory based on a UUID assigned by the network. Good practice dictates that any work
with the filesystem should ideally be done in this directory in order to not interrupt other services, but in cases
where that isn't possible it's fine to access other files on a coordinator provided you know what you are doing.

## Packages

PlayPen uses a package system known as _P3_ to send files across the network. The network coordinator acts as a package
repository which all local coordinators can pull from. All services run by playpen must be contained in a package.
Common components shared between services can be split off into their own package using P3's dependency system. Local
coordinators cache packages locally so that they do not have to be continuously sent over the network.

There are three main types of packages: script packages, standard packages, and asset packages. PlayPen doesn't
technically makea distinction between the types of packages, but it's good to think of each package as being one of
these.

### Script packages

A script package contains no files except the package metadata (package.json). As such, the package should not have an
"expand" provision step (see below). Script packages will generally just run some commands via the package metadata
file.

### Standard Packages

Standard packages contain a set of files that are extracted from the package into the service's working directory. These
packages will use an "expand" provision step to expand/extract the package files into the appropriate directory. They
will then generally run a set of commands during the execute step in order to start the service.

### Asset Packages

Asset packages contain files that should be extracted into a location where multiple services can access them. They
should be used for things like storing common map data for a game when you know the files will never be modified. That
way you only have to extract them a single time for each local coordinator, and all services will have access to those
files.

Asset packages use the "expand-assets" provision step almost exclusively. They generally should not be provisioned
directly, and should instead be listed as a dependency.

## Reliability

Local coordinators should be able to run for months on end without being restarted (bar needing to update to a newer version). The network coordinator can be restarted without affecting the operation of the network (aside from losing the ability to control the network for the time that the network coordinator is down).

## Warning

PlayPen is not an out of the box solution for server management. Even at The Chunk we had a huge custom stack that actually allowed us to make use of PlayPen. We had a plugin in PlayPen that would send the IP and port of every server to redis, then a plugin on bungeecord that read in the list of servers from redis. Finally we had a component on our hub server (which was not managed by PlayPen as it had to always exist in the same location -- not ephemeral) which also read the list of servers from redis in order to display available servers to players.

## Support

For consultation and support, please contact me [here](mailto:sam@redxdev.com).
