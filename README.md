CRYO tool - provide means of creating branch somewhat automatically with minial interaction with engineer.

#Use case
------------

CRYO is simple tool that can use existing pull requests in order to create 'future' or 'frozen'. It will use current branch at target repo, fetch available pull requests and attempt to merge.
In case of failure of TS, it will perform bisect on pull requests and vet unpure and unworthy.
Result should be branch that pass TS and possibly can be used as next release.

#Configuration
------------
##pr-merge
------------
CRYO use external script to perform merge magic( in order to align with existing tooling). The script can be downloaded from [here](https://github.com/jboss-set/auxilia/blob/master/pr-merge), the script's permission must be set to executable ,and its name must be **pr-merge**. 

**At runtime this script has to be available through $PATH.**


##Authentication
------------
User running script or CI machine has to be authenticated with github in order to perform all operations. This can be achieved in any way. CRYO does not care.

##Aphrodite
------------
CRYO depends on aphrodie. Thus it requires valid configuration present. It has to be accessible as value of `-Daphrodite.config` proprety passed at runtime.

##Logging
------------
In order to make logging more transparent, it is encouraged to run CRYO with `-Djava.util.logging.SimpleFormatter.format='%4$s %5$s%6$s%n`.

##Basic Setup
------------
- The PRs to be merged should be created on the same repository's branch that you are currently on.
- export `NO_STOP_BEFORE_MERGE=x`, to stop CRYO if there's an error, otherwise it may run indefinitely.

##Command Line
------------
### -r
------------
Points to local clone of git repository. It is the one CRYO will work on
### -o
------------
Switch operation source. Default to mvn, it can be used to switch to harmonia, ie:
export HARMONIA_BUILD_SCRIPT=/opt/harmonia-build-script
java -jar ..... -o HarmoniaOperationCenter
### -d
------------
Flag to turn on dry run - CRYO wont push brach to remote end. User can do that after verifying.
Default: false
### -f
------------
Invert PRs in local storage. Github return newest PRs as first. Usually applying older ones is better solution.
Default: false, no inversion.

### -i
------------
Comma separated list of pull request URLs( as they appear in github UI) that should be included.

### -e
------------
Comma separated list of pull request URLs that should not be part of process.
### -s
------------
Branch suffix that will be used to create new branch.
Default: .future
### -q
------------
Switch on fast logging. By default CRYO will process output to at least at timestamp. In cases it's not needed this flag can alter it, so parts of build
that create big logs, CRYO wont interfere.
Default: false

