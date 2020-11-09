# Jenkins Update Center Root CA

## `jenkins-update-center-root-ca`

This certificate and private key is used to generate another keypair, which is used to sign update center metadata.
It is included in [Jenkins 1.410 and newer][src] as a trust anchor.

This certificate is valid from 2011-04-19 to 2021-04-16.

The corresponding private key in this directory is PGP encrypted by the board key used to handle CLAs:

````
pub   4096R/6E33EEFA 2012-03-21
uid                  Jenkins project CLA (Used to encrypt Jenkins CLA papers) <jenkinsci-board@googlegroups.com>
uid                  [jpeg image of size 11091]
sub   4096R/FDDFA9FC 2012-03-21
````


## `jenkins-update-center-root-ca-2`

This certificate is a replacement for `jenkins-update-center-root-ca` and has been added to Jenkins in April 2018 for [INFRA-1502][INFRA-1502].
It is included in Jenkins 2.117 and newer as a trust anchor.

This certificate is valid from 2018-04-08 to 2028-04-05.

As of May 2020, Kohsuke, Oleg Nenashev, and Olivier Vernin have the corresponding private key.

[INFRA-1502]: https://issues.jenkins-ci.org/browse/INFRA-1502
[src]: https://github.com/jenkinsci/jenkins/blob/f5ac512bd4e6d3bf041672d179a97f8dfd900e8b/war/src/main/webapp/WEB-INF/update-center-rootCAs/jenkins-update-center-root-ca
