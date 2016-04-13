# Building xl-reposotory-tools

1. Make sure that build.gradle file contains correct `mainClassName` property.
1. Execute `./gradlew clean distZip` or `./gradlew clean distZip`
1. Ship the resulted file from `build/distribution/xl-reposotory-tools.zip`

# What is Node to Node repository copier

The NodeToNodeCopier copies raw repository data from one installation of XLD to a fresh installation of XLD. The installation must be similar, i.e versions of the XL Deploy are the same, plugins and extensions are the same on both installations.

* The copier will ignore corrupted data, like missing files.
* It will ignore unused data. The resulting repository will have smaller size.  
* It does *NOT* copy version database. The new resulting repository will lack all history of modifications of configuration items.

# Using Node to node repository copier

1. Stop your instance of XL Deploy.
1. Prepare a fresh installation of the XL Deploy by coping all folders from old XL Deploy installation home but *skipping* `repository` folder.
1. Go to the new installation directory and execute

    ./bin/run.sh -setup -reinitialize -force
or
    ./bin/run.cmd -setup -reinitialize -force

1. Test that the fresh installation is bootable by running `./bin/run.sh` or `./bin/run.cmd`. If it's not bootable, repeat the previous steps.
1. Stop the instance of XL Deploy.
1. Extract `xl-reposotory-tools.zip` and navigate to the extracted directory.
1. Execute `./bin/xl-repository-tools` with following parameters
* `-srcHome` - full path to the original XL Deploy installation that contains your original repository.
* `-dstHome` - full path to the fresh XL Deploy installation.
* `-dstPassword` - admin password specified in fresh installation of the XL Deploy.
1. After execution, find output lines like

     ---------->>>>> STATS <<<<<-----------
     Stats{allNodes=31974, failedProperties=0}

If `failedProperties=0`, this means that no corrupted data was found. If it's not `0`, open `node-for-node-repository-copier.log` and search for all `ERROR` log entries.
