CRYO tool - provide means of creating branch somewhat automatically with minial interaction with engineer.

#Use case
------------

CRYO is simple tool that can use existing pull requests in order to create 'future' or 'frozen'. It will use current branch at target repo, fetch available pull requests and attempt to merge.
In case of failure of TS, it will perform bisect on pull requests and vet unpure and unworthy.
Result should be branch that pass TS and possibly can be used as next release.

#Configuration
------------
##merge.sh
------------
CRYO use external script to perform mege magic( in order to align with existing tooling). Script is locate at root of repository. At runtime this script has to be available through $PATH.


##Authentication
------------
User running script or CI machine has to be authenticated with github in order to perform all operations. This can be achieved in any way. CRYO does not care.

##Aphrodite
------------
CRYO depends on aphrodie. Thus it requires valid configuration present. It has to be accessible as value of '-Daphrodite.config' proprety passed at runtime.

##Logging
------------
In order tomake logging more transparent, it is encouraged to run CRYO with '-Djava.util.logging.SimpleFormatter.format='%4$s %5$s%6$s%n''.

------------
