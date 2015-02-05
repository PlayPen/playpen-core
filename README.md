# PlayPen

PlayPen is a server management and load balancing system.

## Usage

Before using any playpen tools, PlayPen needs to set itself up. Simply place the
playpen jar wherever you wish playpen's home directory to be, and run it without
any arguments. It will create the default configuration files and exit.

It is generally recommended to use the bundled scripts to run playpen instead of
launching the jar manuall (after initial configuration, "playpen.sh" and
"playpen.bat" will be generated).

Running playpen is as simple as

    playpen <command>

To start the network coordinator, run

    playpen network

To start a local coordinator, run

    playpen local

Packaging tools are found at the "p3" command. Run it to see a list of them:

    playpen p3