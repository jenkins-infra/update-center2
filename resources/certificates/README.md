# Jenkins Update Center Root CA

## `jenkins-update-center-root-ca`

This certificate and private key is used to generate another keypair, which is used to sign update center metadata.
[Every Jenkins since 1.410][src] contains this root certificate as a trust anchor.


The corresponding private key in this directory is PGP encrypted by the board key used to handle CLAs:

````
pub   4096R/6E33EEFA 2012-03-21
uid                  Jenkins project CLA (Used to encrypt Jenkins CLA papers) <jenkinsci-board@googlegroups.com>
uid                  [jpeg image of size 11091]
sub   4096R/FDDFA9FC 2012-03-21
````

This certificate is valid from 2011-04-19 to 2021-04-16.
Its replacement has been added to Jenkins in April 2018 for [INFRA-1502][INFRA-1502] and is available from Jenkins 2.117 and Jenkins 2.121.

[INFRA-1502]: https://issues.jenkins-ci.org/browse/INFRA-1502
[src]: https://github.com/jenkinsci/jenkins/blob/f5ac512bd4e6d3bf041672d179a97f8dfd900e8b/war/src/main/webapp/WEB-INF/update-center-rootCAs/jenkins-update-center-root-ca
