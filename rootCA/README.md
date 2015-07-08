Jenkins Update Center Root CA

This certificate and private key is used to generate another keypair, which is used to sign update center metadata. Every Jenkins out there is baked with this root certificate as a trust anchor.

The private key is PGP encrypted by the board key used to handle CLAs:

````
pub   4096R/6E33EEFA 2012-03-21
uid                  Jenkins project CLA (Used to encrypt Jenkins CLA papers) <jenkinsci-board@googlegroups.com>
uid                  [jpeg image of size 11091]
sub   4096R/FDDFA9FC 2012-03-21
````
