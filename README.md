[![OpenHIM Core](https://img.shields.io/badge/openhim--core-master-lightgrey.svg)](http://openhim.readthedocs.org/en/latest/user-guide/versioning.html) [![Build Status](https://travis-ci.org/jembi/openhim-mediator-xslt.svg?branch=master)](https://travis-ci.org/jembi/openhim-mediator-xslt)

# openhim-mediator-xslt
An OpenHIM mediator that applies XSLT transformations to requests

You can upload your XSLTs via the OpenHIM-console
![image](https://cloud.githubusercontent.com/assets/1872071/11436842/3bc93a80-94f1-11e5-98c1-dfd15f075325.png)

You can apply transforms on both the incoming request and the destination response. A transform will be dynamically mapped to a particular endpoint on the mediator.



## To Test
___

```
mvn -Dtest=XSLTAdapterTest test
```