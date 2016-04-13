# Building xl-repository-tools

1. Ensure that the `build.gradle` file contains the correct `mainClassName` property.
1. Execute `./gradlew clean distZip` or `./gradlew clean distZip`.
1. Ship the resulting file from `build/distribution/xl-reposotory-tools.zip`.

# What is the Node to Node repository copier

The NodeToNodeCopier copies raw repository data from one installation of XL Deploy to a fresh installation of XL Deploy. The installation must be similar; that is, the versions of XL Deploy are the same and the plugins and extensions are the same on both installations.

* The copier will ignore corrupted data such as missing files.
* The copier will ignore unused data. The resulting repository will have a smaller size.  
* The copier does *NOT* copy the version database. The new resulting repository will lack all history of modifications of configuration items.

# Using the Node to Node repository copier

1. Stop your instance of XL Deploy.
1. Prepare a fresh installation of XL Deploy by coping all folders from the old XL Deploy installation home but *skipping* the `repository` folder.
1. Go to the new installation directory and execute:
```
./bin/run.sh -setup -reinitialize -force
```
Or:
```
./bin/run.cmd -setup -reinitialize -force
```
1. Test that the fresh installation is bootable by running `./bin/run.sh` or `./bin/run.cmd`. If it is not bootable, repeat the previous steps.
1. Stop the instance of XL Deploy.
1. Extract `xl-reposotory-tools.zip` and navigate to the extracted directory.
1. Execute `./bin/xl-repository-tools` with following parameters:
    * `-srcHome`: Full path to the original XL Deploy installation that contains your original repository.
    * `-dstHome`: Full path to the fresh XL Deploy installation.
    * `-dstPassword`: admin password specified in the fresh installation of the XL Deploy.
1. After execution, find output lines such as:
```
     ---------->>>>> STATS <<<<<-----------
     Stats{allNodes=31974, failedProperties=0}
```
If `failedProperties=0`, this means that no corrupted data was found. If it is not `0`, open `node-for-node-repository-copier.log` and search for all `ERROR` log entries.
