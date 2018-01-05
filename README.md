# IDEALS Batch Packager

**A custom JavaFX GUI wrapper for [Peter Dietz's Simple Archive Format Packager](https://wiki.duraspace.org/display/DSPACE/Simple+Archive+Format+Packager)**

# Users
  
  **GUI & FUCTIONALITY CUSTOMIZED FOR IDEALS**

  * custom branding
  * custom license
  * custom validation rules

  IDEALS staff and batch generators, please [contact the ideals staff](mailto:ideals@library.illinois.edu).

# Developers

## Adaptation Tips (not thoroughly investigated)

While this tool was developed to meet the specific needs of IDEALS, it could be adapted for use by other DSpace instances.
  * The custom license is in src/main/resources/license.txt.
  * The custom list of valid headers is in the buildValidHeadersList() method of the SAFPackage class.
  * Custom verification steps such as verifyHeaders() and verifyMetaBody() could be commented out altogether.

### 

## Build & run

### Command line

* `mvn package` will build two jars in target. Use the batchpackager-x.x.one-jar.jar for all dependencies included.
 

## Contribute

Contributions are welcome. The suggested process for contributing code changes
is:

1. Submit a "heads-up" issue in the tracker, ideally before beginning any
   work.
2. [Create a fork.](https://github.com/ideals-project/batchpackager/fork)
3. Create a feature branch, starting from either `release/x.x` or `develop`
   (see the "Versioning" section.)
4. Make your changes.
5. Commit your changes (`git commit -am 'Add some feature'`).
6. Push the branch (`git push origin feature/my-new-feature`).
7. Create a pull request.

## Other Notes

### Versioning

Batchpackager roughly uses semantic versioning. Major releases (n) involve major
rearchitecting that breaks backwards compatibility in a significant way. Minor
releases (n.n) either do not break compatibility, or only in a minor way.
Patch releases (n.n.n) are for bugfixes only.

## License

Batchpackager is open-source software distributed under the University of
Illinois/NCSA Open Source License; see the file LICENSE for terms.