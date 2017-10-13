package edu.illinois.ideals.batchpackager;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs.*;
import org.apache.http.impl.client.DefaultHttpClient;
import org.mozilla.universalchardet.UniversalDetector;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class SAFPackage {
    private String seperatorRegex = "\\|\\|";   // Using double pipe || to separate multiple values in a field.

    private String licenseString;

    private List<String> validHeaders;

    private List<String> requiredHeaders;

    // Directory on file system of this input collection
    // private File input;

    // Metadata CSV File
    private File metadataCsvFile;

    // Content Source Directory
    private File sourceDir;

    // SAF Archive output directory
    private File archiveDir;

    // Storage of the csv data.
    private CsvReader metadataCsvReader;

    //Set a a Symbolic Link for filesinstead of copying them
    private boolean symbolicLink = false;

    private static String[][] sourceFileArray;

    private List<String> filesNotFoundInSourceDir;

    private List<String> invalidHeadersFound;

    private List<String> requiredHeadersNotFound;

    /**
     * Default constructor. Main method of this class is processMetaPack. The goal of this is to create a Simple Archive Format
     * package from input of files and csv metadata.
     */
    public SAFPackage() {

        filesNotFoundInSourceDir = new ArrayList<String>();
        requiredHeadersNotFound = new ArrayList<String>();
        invalidHeadersFound = new ArrayList<String>();

        buildValidHeadersList();
        buildRequiredHeadersList();

        URL licenseTextUrl = getClass().getResource("/license.txt");
        System.out.println(licenseTextUrl);

        try {
            licenseString = new Scanner(licenseTextUrl.openStream()).useDelimiter("\\Z").next();
            //System.out.println(licenseString);
        } catch (IOException ex) {
            System.out.println("error reading in license text file");
        }


    }

    public void setSymbolicLink(boolean symbolicLink) {
        this.symbolicLink = symbolicLink;
    }

    /**
     * Gets a "handle" on the metadata file
     * <p>
     * metadata csv File object is created in constructor
     */
    private void openCSV() {

        String absoluteFileName = metadataCsvFile.getPath().toString();

        try {
            InputStream csvStream = new FileInputStream(absoluteFileName);
            metadataCsvReader = new CsvReader(csvStream, detectCharsetOfFile(absoluteFileName));
        } catch (Exception e) {
            System.out.println(absoluteFileName);
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
//        System.out.println("Opened CSV File:" + absoluteFileName);
    }

    /**
     * Try to automatically detect charset/encoding of a file. UTF8 or iso-8859 are likely
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static Charset detectCharsetOfFile(String filePath) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);

        byte[] buf = new byte[4096];
        FileInputStream fis = new FileInputStream(filePath);
        int nread;
        while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }
        detector.dataEnd();

        fis.close();

        String charset = detector.getDetectedCharset();
        if (charset == null) {
            charset = "UTF-8";
            System.out.println("Didn't properly detect the charset of file. Setting to UTF-8 as a fallback");
        }
        Charset detectedCharset = Charset.forName(charset);
//        System.out.println("Detected input CSV as:" + detectedCharset.displayName());
        return detectedCharset;
    }


    /**
     * open metafile
     * foreach(metarows as metarow)
     * makeDirectory(increment)
     * copy filenames into directory
     * make contents file with entries for each filename
     * foreach(metarow.columns as column)
     * add meta entry to metadata xml
     * add license
     *
     * @param pathToMetadataCsvFile Path to the metadata CSV file
     * @param sourceDir             Path to the directory containing the content files as source for creating archive
     * @param archiveDir            Path to the directory to put the generated archive
     * @return a report as a list of Strings
     */
    public List<String> processMetaPack(String pathToMetadataCsvFile, String sourceDir, String archiveDir, Boolean exportToZip) {

        List<String> report = new ArrayList<String>();

        try {

            this.metadataCsvFile = new File(pathToMetadataCsvFile);
            this.sourceDir = new File(sourceDir);
            this.archiveDir = new File(archiveDir);

            openCSV();

            scanAllFiles();                                                         // For Reporting file usage

            processMetaHeader();

            verifyHeaders();

            verifyMetaBody();

            if((invalidHeadersFound.size() == 0) && (requiredHeadersNotFound.size() == 0) && (filesNotFoundInSourceDir.size() == 0)){
                processMetaBody();
            } else {
                report.add("At least one critical error -- BATCH NOT CREATED");
            }

            report.addAll(getReport());



            if (exportToZip) {
                exportToZip();
            }


        } catch (IOException ex) {
            report.add("Error accessing files: " + ex.getMessage());
        } finally {
            return report;
        }
    }

    /**
     * open metafile
     * verify valid headers
     * verify files in filenames exist in sourceDir
     * report
     *
     * @param pathToMetadataCsvFile Path to the metadata CSV file
     * @param pathToSourceDir       Path to the directory containing the content files as source for creating archive
     * @return report as a List of Strings
     */
    public List<String> verifyMetaPack(String pathToMetadataCsvFile, String pathToSourceDir) {

        List<String> report = new ArrayList<String>();

        try {

            this.metadataCsvFile = new File(pathToMetadataCsvFile);
            this.sourceDir = new File(pathToSourceDir);

            openCSV();

            scanAllFiles();                                                         // For Reporting file usage

            processMetaHeader();

            verifyHeaders();

            verifyMetaBody();

            if((invalidHeadersFound.size() > 0) || (requiredHeadersNotFound.size() > 0) || (filesNotFoundInSourceDir.size() > 0)){

                report.add("At least one critical error -- BATCH WOULD NOT BE CREATED");
            }

            report.addAll(getReport());

        } catch (IOException ex) {
            report.add("Error accessing files: " + ex.getMessage());

        } finally {
            return report;
        }

    }

    private void verifyHeaders() {

        try {
            String[] csvHeaders = metadataCsvReader.getHeaders();
            List<String> csvHeadersList = Arrays.asList(csvHeaders);

            for(String requiredHeader : requiredHeaders){
                if(!csvHeadersList.contains(requiredHeader)){
                    requiredHeadersNotFound.add(requiredHeader);
                }
            }

            for (String headerString: csvHeaders){
                if(!validHeaders.contains(headerString)){
                    invalidHeadersFound.add(headerString);
                }
            }
        } catch (IOException e) {
            invalidHeadersFound.add("error reading headers");
        }

    }

    public void exportToZip() {
        String safDirectory = archiveDir.getPath();
        String zipDest = archiveDir + "/" + "SimpleArchiveFormat" + ".zip";
        try {
            ZipUtil.createZip(safDirectory, zipDest);
            System.out.println("ZIP file located at: " + new File(zipDest).getAbsolutePath());
        } catch (IOException e) {
            System.out.println("ERROR Zipping SAF: " + e.getMessage());
        }
    }


    /**
     * Make a list of all the files in the input directory.
     * Initialize the count for each file found to have zero usages.
     */
    private void scanAllFiles() {
        String[] files = sourceDir.list();

        sourceFileArray = new String[files.length][2];
        for (int i = 0; i < files.length; i++) {
            sourceFileArray[i][0] = files[i];
            sourceFileArray[i][1] = "0";
        }
    }

    /**
     * Marks that the filename being referred to is counted as being used.
     * This method is used for scanning the files in the directory for ones that are used/unused.
     *
     * @param filename Name of file referred to in CSV
     */
    private void incrementFileHit(String filename) {
        int i = 0;
        boolean found = false;
        while (!found && (i < sourceFileArray.length)) {
            if (sourceFileArray[i][0].contentEquals(filename)) {
                int current = Integer.parseInt(sourceFileArray[i][1]);
                int increment = current + 1;
                sourceFileArray[i][1] = String.valueOf(increment);
                found = true;
            }

            i++;
        }
    }

    /**
     * Displays the files that exist in the directory that have been used the specified number of times.
     * Used for finding files that have not been used.
     *
     * @param numHits The specified number of times the file should have been used. Value of 0 means unused file.
     */
    private List<String> getFileUsedReport(Integer numHits) {
        List<String> report = new ArrayList<String>();
        for (int i = 0; i < sourceFileArray.length; i++) {
            if (sourceFileArray[i][1].contentEquals(numHits.toString())) {
                report.add("File: " + sourceFileArray[i][0] + " has been used " + numHits + " times.");
            }
        }

        return report;
    }

    /**
     * Scans the Header row of the metadata csv to usable object.
     *
     * @throws IOException If the CSV can't be found or read
     */
    private void processMetaHeader() throws IOException {
        metadataCsvReader.readHeaders();
    }

    /**
     * Gets the value for a specified header column
     *
     * @param columnNum The integer value
     * @return Text value for the specified header column
     * @throws IOException If the CSV can't be found or read
     */
    private String getHeaderField(int columnNum) throws IOException {
        return metadataCsvReader.getHeader(columnNum);
    }

    /**
     * Method to process the content/body of the metadata csv.
     * Delegate the work of processing each row to other methods.
     * Does not process the header.
     *
     * @throws IOException If the CSV can't be found or read
     */
    private void processMetaBody() throws IOException {
        // The implementation of processing CSV starts counting from 0. 0 = header, 1..n = body/content
        int rowNumber = 1;

        while (metadataCsvReader.readRecord()) {
            processMetaBodyRow(rowNumber++);
        }
    }

    /**
     * Method to verify the content/body of the metadata csv.
     * Delegate the work of processing each row to other methods.
     * Does not process the header.
     *
     * @throws IOException If the CSV can't be found or read
     */
    private void verifyMetaBody() throws IOException {
        // The implementation of processing CSV starts counting from 0. 0 = header, 1..n = body/content
        int rowNumber = 1;

        while (metadataCsvReader.readRecord()) {
            verifyMetaBodyRow(rowNumber++);
        }
    }

    /**
     * Verifies a row in the metadata CSV.
     * Verifying a row means checking all of the metadata fields, and checking that all of the files mentioned in the csv are in the package.
     *
     * @param rowNumber Row in the CSV.
     */
    private void verifyMetaBodyRow(int rowNumber) throws IOException {
        //Specify multiple alternatives for filename, to accept wider input.
        String[] filenameColumn = {"filename", "bitstream", "bitstreams", "BUNDLE:ORIGINAL"};
        String[] filenameWithPartsColumn = {"filename__", "bitstream__", "bitstreams__"};

        String[] currentLine = metadataCsvReader.getValues();

        for (int j = 0; j < metadataCsvReader.getHeaderCount(); j++) {
            if (j >= currentLine.length) {
                break;
            }
            if (currentLine[j].length() == 0) {
                continue;
            }
            if (Arrays.asList(filenameColumn).contains(getHeaderField(j))) {
                // filename
                verifyMetaBodyRowFile(currentLine[j]);
            }
        }
    }

    private void verifyMetaBodyRowFile(String filenames) {

        filenames = removeTrailingDoublePipes(filenames);

        String[] filenameArray = filenames.split(seperatorRegex);

        for (String filename : filenameArray) {

            int i = 0;
            boolean found = false;
            while (!found && (i < sourceFileArray.length)) {
                if (sourceFileArray[i][0].contentEquals(filename)) {
                    int current = Integer.parseInt(sourceFileArray[i][1]);
                    int increment = current + 1;
                    sourceFileArray[i][1] = String.valueOf(increment);
                    found = true;
                }

                i++;
            }

            if (!found) {
                filesNotFoundInSourceDir.add(filename);
            }
        }

    }

    private List<String> getFilesNotFoundInCsv(){
        List<String> filesNotFoundInCsv = new ArrayList<String>();

        for (int i = 0; i < sourceFileArray.length; i++) {
            if (sourceFileArray[i][1].contentEquals("0")) {
                filesNotFoundInCsv.add(sourceFileArray[i][0]);
            }
        }

        return  filesNotFoundInCsv;
    }



    /**
     * Processes a row in the metadata CSV.
     * Processing a row means using all of the metadata fields, and adding all of the files mentioned to the package.
     *
     * @param rowNumber Row in the CSV.
     */
    private void processMetaBodyRow(int rowNumber) {
        String currentItemDirectory = makeNewDirectory(rowNumber);
        String dcFileName = currentItemDirectory + "/dublin_core.xml";
        File contentsFile = new File(currentItemDirectory + "/contents");
        File collectionFile = new File(currentItemDirectory + "/collections");
        writeLicenseFile(currentItemDirectory);

        //Specify multiple alternatives for filename, to accept wider input.
        String[] filenameColumn = {"filename", "bitstream", "bitstreams", "BUNDLE:ORIGINAL"};
        String[] filenameWithPartsColumn = {"filename__", "bitstream__", "bitstreams__"};

        try {
//            BufferedWriter contentsWriter = new BufferedWriter(new FileWriter(contentsFile));

            //specify UTF-8 for output
            BufferedWriter contentsWriter = new BufferedWriter
                    (new OutputStreamWriter(new FileOutputStream(contentsFile), StandardCharsets.UTF_8));

            String[] currentLine = metadataCsvReader.getValues();

            OutputXML xmlWriter = new OutputXML(dcFileName);
            xmlWriter.start();
            Map<String, OutputXML> nonDCWriters = new HashMap<String, OutputXML>();

            for (int j = 0; j < metadataCsvReader.getHeaderCount(); j++) {
                if (j >= currentLine.length) {
                    break;
                }
                if (currentLine[j].length() == 0) {
                    continue;
                }

                if (Arrays.asList(filenameColumn).contains(getHeaderField(j))) {
                    // filename
                    processMetaBodyRowFile(contentsWriter, currentItemDirectory, currentLine[j], "");
                } else if (StringUtils.indexOfAny(getHeaderField(j), filenameWithPartsColumn) >= 0) {
                    // filename__
                    //This file has extra parameters, such as being destined for a bundle, or specifying primary
                    String[] filenameParts = getHeaderField(j).split("__", 2);
                    processMetaBodyRowFile(contentsWriter, currentItemDirectory, currentLine[j], filenameParts[1]);
                } else if (getHeaderField(j).contains("filegroup")) {
                    String[] parameterParts = getHeaderField(j).split("__", 2);
                    String extraParameter = (parameterParts.length == 1) ? "" : parameterParts[1];
                    processMetaBodyRowFilegroup(contentsWriter, currentItemDirectory, currentLine[j], extraParameter);
                } else if (getHeaderField(j).contains("collection")) {
                    //TODO, figure out strategy for validation
                    processMetaBodyRowCollections(collectionFile, currentLine[j]);
                } else {
                    //Metadata
                    String[] dublinPieces = getHeaderField(j).split("\\.");
                    if (dublinPieces.length < 2) {
                        // strange field, skip
                        continue;
                    }
                    String schema = dublinPieces[0];
                    if (schema.contentEquals("dc")) {
                        processMetaBodyRowField(getHeaderField(j), currentLine[j], xmlWriter);
                    } else {
                        if (!nonDCWriters.containsKey(schema)) {
                            OutputXML schemaWriter = new OutputXML(currentItemDirectory + File.separator + "metadata_" + schema + ".xml", schema);
                            schemaWriter.start();
                            nonDCWriters.put(schema, schemaWriter);
                        }
                        processMetaBodyRowField(getHeaderField(j), currentLine[j], nonDCWriters.get(schema));
                    }
                }
            }
            contentsWriter.newLine();
            contentsWriter.close();
            xmlWriter.end();
            for (String key : nonDCWriters.keySet()) {
                nonDCWriters.get(key).end();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the values for the specific piece of metadata to the output. Accepts
     * multiple values per value so long as they are separated by the separator character
     *
     * @param field_header Field name, such as dc.description or dc.description.abstract
     * @param field_value  Metadata value or values. Multiple values can be separated by a separator character.
     * @param xmlWriter    The xml file that the data is being written to
     */
    private void processMetaBodyRowField(String field_header, String field_value, OutputXML xmlWriter) {
        // process Metadata field. Multiple entries can be specified with separator character
        field_value = removeTrailingDoublePipes(field_value);
        String[] fieldValues = field_value.split(seperatorRegex);
        for (int valueNum = 0; valueNum < fieldValues.length; valueNum++) {
            if (fieldValues[valueNum].trim().length() > 0) {
                xmlWriter.writeOneDC(field_header, fieldValues[valueNum].trim());
            } else {
                continue;
            }
        }
        //TODO test that this works in both cases of single value and multiple value
    }

    /**
     * Processes the files for the filename column.
     * open contents
     * for-each files as file
     * copy file into directory
     * add file to contents
     *
     * @param contentsWriter       Writer to the contents file which tracks the files to ingest for item
     * @param itemDirectory        Absolute path to the directory to put the files in
     * @param filenames            String with filename / filenames separated by separator.
     * @param globalFileParameters Parameters for these files. Blank value means nothing special needs to happen.
     */
    private void processMetaBodyRowFile(BufferedWriter contentsWriter, String itemDirectory, String filenames, String globalFileParameters) {

        filenames = removeTrailingDoublePipes(filenames);
        String[] files = filenames.split(seperatorRegex);

        for (int j = 0; j < files.length; j++) {
            /* Trim whitespace and add a __ at the end to avoid array out of bounds exception
             * on the filenameParts[1] reference. (Could have also done if.. else..)
             * filenameParts[0] = the actual file name
             * filenameParts[1] = the remaining SAF parameters, still delimited by "__"
             */
            String[] filenameParts = (files[j].trim() + "__").split("__", 2);
            String currentFile = filenameParts[0];

            /* This takes the parameters as specified at the header row and adds them to the
             * parameters for this individual file. The order is important here: by taking
             * the local parameters first, they are able to override the global params.
             */
            String fileParameters = filenameParts[1] + "__" + globalFileParameters;

            try {

                //copying files
                if (!symbolicLink) {
                    FileUtils.copyFileToDirectory(new File(sourceDir.getPath() + "/" + currentFile), new File(itemDirectory));
                }
                //instead of copying them, set a symbolicLink
                else {
                    Path pathLink = (new File(sourceDir.getPath() + "/" + currentFile)).toPath();
                    Path pathTarget = (new File(itemDirectory + "/" + currentFile)).toPath();
                    Files.createSymbolicLink(pathTarget, pathLink);
                }
                incrementFileHit(currentFile); //TODO fix file counter to deal with multifiles

                String contentsRow = getFilenameName(currentFile);
                if (fileParameters.length() > 0) {
                    // bundle:SOMETHING, primary:TRUE or description:Something, or any combination with "__" in between
                    String[] parameters = fileParameters.split("__");
                    for (String parameter : parameters) {
                        contentsRow = contentsRow.concat("\t" + parameter.trim());
                    }
                }
                contentsRow = contentsRow.concat("\nlicense.txt" + "\t" + "BUNDLE:LICENSE");
                contentsWriter.append(contentsRow);

                contentsWriter.newLine();
            } catch (FileNotFoundException fnf) {
                System.out.println("There is no file named " + currentFile + " in " + sourceDir.getPath() + " while making " + itemDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Obtain just the filename from the string. The string could include some path information, which is un-needed
     *
     * @param filenameWithPath The filename, may or may not include paths
     * @return The filename with no path or slashes.
     */
    private String getFilenameName(String filenameWithPath) {
        if (filenameWithPath.contains("\\")) {
            String separator = "\\";
            String[] pathSegments = filenameWithPath.split(Pattern.quote(separator));
            return pathSegments[pathSegments.length - 1];
        } else if (filenameWithPath.contains("/")) {
            String[] pathSegments = filenameWithPath.split("/");
            return pathSegments[pathSegments.length - 1];
        } else {
            return filenameWithPath;
        }
    }

    /**
     * Makes a new directory for the item being processed
     * /path/to/input/SimpleArchiveFormat/item_27/
     *
     * @param itemNumber Iterator for the item being processed, Starts from zero.
     * @return Absolute path to the newly created directory
     */
    private String makeNewDirectory(int itemNumber) {
        File newDirectory = new File(archiveDir.getPath() + "/item_" + itemNumber);
        newDirectory.mkdir();
        return newDirectory.getAbsolutePath();
    }

    /**
     * Reads a .tar.gz file that would contain the files for the metadata row.
     *
     * @param itemDirectory
     * @param filename
     * @param fileParameters
     * @throws FileSystemException
     */
    @SuppressWarnings("unchecked")
    private void processMetaBodyRowFilegroup(BufferedWriter contentsWriter, String itemDirectory, String filename, String fileParameters) throws FileSystemException {
        ArrayList<FileObject> filesCollection = new ArrayList<FileObject>();

        FileSystemManager fileSystemManager = VFS.getManager();
        FileObject tarGZFile = fileSystemManager.resolveFile("tgz://" + sourceDir.getPath() + "/" + filename);
        // List the children of the Jar file
        FileObject[] children = tarGZFile.getChildren();
        for (int i = 0; i < children.length; i++) {
            FileObject[] grandChildren = children[i].getChildren();

            for (int j = 0; j < grandChildren.length; j++) {
                FileObject grandChild = grandChildren[j];
                if (grandChild.getName().getBaseName().equals(".htaccess") || grandChild.getType() != FileType.FILE) {
                    continue;
                }
                filesCollection.add(grandChildren[j]);
            }
        }

        Collections.sort(filesCollection, new FileObjectComparator());

        // Using reverse depend on your stance on which order to sort bitstreams from DS-749
        // TODO allow for custom sorting/ordering/reversing
        Collections.reverse(filesCollection);

        // TODO This method needs to be tested. Processing file groups in general needs to be tested.
        for (FileObject fileObject : filesCollection) {
            addFileObjectToItem(contentsWriter, fileObject, fileSystemManager.resolveFile("file://" + itemDirectory), fileParameters);
        }
    }

    public void processMetaBodyRowCollections(File collectionFile, String collectionsValues) throws IOException {
        collectionsValues = collectionsValues.trim();

        collectionsValues = removeTrailingDoublePipes(collectionsValues);
        String[] collections = collectionsValues.split(seperatorRegex);

        //TODO Validating collections is alpha, so leave disabled...
        boolean validateCollection = false;

        for (String collection : collections) {
            if (StringUtils.isEmpty(collection)) {
                continue;
            }

            if (validateCollection) {
                validateCollection(collection);
            }

            FileUtils.writeStringToFile(collectionFile, collection, true);
            FileUtils.writeStringToFile(collectionFile, System.getProperty("line.separator"), true);
        }
    }

    static HashSet<String> handleSet;

    public void initializeCollectionsSet() throws IOException {
        String restAPI = "http://localhost:8080/rest/collections";
        handleSet = new HashSet<String>();


        HttpGet request = new HttpGet(restAPI);
        request.setHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        //request.addHeader("rest-dspace-token", token);
        HttpClient httpClient = new DefaultHttpClient();
        HttpResponse httpResponse = httpClient.execute(request);

        if (httpResponse.getStatusLine().getStatusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode collNodes = mapper.readValue(httpResponse.getEntity().getContent(), JsonNode.class);
            for (JsonNode collNode : collNodes) {
                String handle = collNode.get("handle").asText();
                handleSet.add(handle);
            }

        } else {
            throw new IOException("REST API not available");
        }
    }

    public void validateCollection(String collectionHandle) throws IOException {
        if (handleSet == null) {
            initializeCollectionsSet();
        }

        if (!handleSet.contains(collectionHandle)) {
            throw new IOException("Handle did not exist in handleset:" + collectionHandle);
        }


    }

    public void generateManifest(String pathToCSV) {
        File csvFile = new File(pathToCSV);
        File directory = csvFile.getParentFile();
        System.out.println("Creating manifest of files in directory:" + directory + " will output results to: " + csvFile);

        //TODO, if CSV doesn't exist, errors get reported
        openCSV();

        CsvWriter csvWriter = new CsvWriter(pathToCSV);
        String[] header = new String[]{"filename", "dc.title", "dc.contributor.author", "dc.date.issued", "dc.description.abstract", "dc.subject"};
        try {
            csvWriter.writeRecord(header);
            csvWriter.endRecord();

            String[] files = sourceDir.list();

            System.out.print("Building manifest:");
            for (int i = 0; i < files.length; i++) {
                //Skip dot files, blanks, and the current CSV file.
                if (StringUtils.isEmpty(files[i]) || files[i].startsWith(".") || files[i].equals(csvFile.getName())) {
                    System.out.print("Skip:[" + files[i] + "]");
                    continue;
                }

                csvWriter.write(files[i]);
                csvWriter.endRecord();
                System.out.print(".");
            }

            System.out.println(" - " + files.length + " files were added to manifest.");

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.err.println(e.getMessage());
        } finally {
            csvWriter.close();
        }
    }

    /**
     * A comparator for the FileObject which does an alphanum sort of the filename (baseName) of the FileObject
     */
    class FileObjectComparator implements Comparator<FileObject> {
        public int compare(FileObject a, FileObject b) {
            AlphanumComparator alphanumComparator = new AlphanumComparator();

            try {

                return alphanumComparator.compare(a.getName().getBaseName(), b.getName().getBaseName());
            } catch (Exception e) {
                System.out.println("ERROR IN COMPARISON");
                return 0;
            }

        }
    }

    /**
     * Move the commons "FileObject" to the item's directory.
     *
     * @param contentsWriter
     * @param destinationDirectory
     */
    private void addFileObjectToItem(BufferedWriter contentsWriter, FileObject fileObject, FileObject destinationDirectory, String fileParameters) {
        try {
            if (fileObject.canRenameTo(destinationDirectory)) {
                fileObject.moveTo(destinationDirectory);
            } else {
                // Can't move the file, have to copy it.
                // Have to create an end-point file which will absorb the contents we are writing.
                FileSystemManager fsManager = VFS.getManager();
                FileObject localDestFile = fsManager.resolveFile(destinationDirectory.getName().getPath() + "/" + fileObject.getName().getBaseName());
                localDestFile.createFile();
                localDestFile.copyFrom(fileObject, Selectors.SELECT_ALL);
            }
            incrementFileHit(fileObject.getName().getBaseName()); //TODO Don't know if this file would exist

            String contentsRow = fileObject.getName().getBaseName();
            if (fileParameters.length() > 0) {
                // BUNDLE:SOMETHING or BUNDLE:SOMETHING__PRIMARY:TRUE or PRIMARY:TRUE
                String[] parameters = fileParameters.split("__");
                for (String parameter : parameters) {
                    contentsRow = contentsRow.concat("\t" + parameter.trim());
                }
            }
            contentsWriter.append(contentsRow);

            contentsWriter.newLine();
        } catch (FileNotFoundException fnf) {
            System.out.println("There is no file named " + fileObject.getName().getBaseName() + " while making " + destinationDirectory.getName().getBaseName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeLicenseFile(String itemDirectory) {

        try {

            File file = new File(itemDirectory + File.separator + "license.txt");
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(licenseString);
            fileWriter.flush();
            fileWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String removeTrailingDoublePipes(String str) {
        String last2chars = str.substring(Math.max(str.length() - 2, 0));

        if (last2chars.matches(seperatorRegex)) {
            return str.substring(0, str.length() - 2);
        } else {
            return str;
        }

    }

    private List<String> getValidFields(){
        return validHeaders;
    }

    private void buildValidHeadersList() {

        validHeaders = new ArrayList<String>();

        validHeaders.add("filename");
        validHeaders.add("BUNDLE:ORIGINAL");
        validHeaders.add("dc.relation.hasPart");
        validHeaders.add("dc.contributor.author");
        validHeaders.add("dc.description.version");
        validHeaders.add("dc.rights.license");
        validHeaders.add("dc.provenance");
        validHeaders.add("dc.subject.other");
        validHeaders.add("dc.subject.mesh");
        validHeaders.add("dc.subject.lcsh");
        validHeaders.add("dc.subject.lcc");
        validHeaders.add("dc.subject.ddc");
        validHeaders.add("dc.subject.classification");
        validHeaders.add("dc.description.peerReview");
        validHeaders.add("dc.description.status");
        validHeaders.add("dc.source.uri");
        validHeaders.add("dc.rights.uri");
        validHeaders.add("dc.relation.uri");
        validHeaders.add("dc.description.reason");
        validHeaders.add("dc.date.embargo");
        validHeaders.add("dc.type.material");
        validHeaders.add("dc.date.submitted");
        validHeaders.add("dc.contributor.committeeMember");
        validHeaders.add("dc.contributor.committeeChair");
        validHeaders.add("dc.contributor.advisor");
        validHeaders.add("dc.rights.holder");
        validHeaders.add("dc.language.rfc3066");
        validHeaders.add("dc.identifier.slug");
        validHeaders.add("dc.type.genre");
        validHeaders.add("dc.date.updated");
        validHeaders.add("dc.title.alternative");
        validHeaders.add("dc.rights.accessRights");
        validHeaders.add("dc.relation.isreplacedby");
        validHeaders.add("dc.relation.isreferencedby");
        validHeaders.add("dc.coverage.temporal");
        validHeaders.add("dc.description");
        validHeaders.add("dc.identifier.isbn");
        validHeaders.add("dc.identifier.localBib");
        validHeaders.add("dc.description.provenance");
        validHeaders.add("dc.coverage.spatial");
        validHeaders.add("dc.language");
        validHeaders.add("dc.identifier.issn");
        validHeaders.add("dc.relation.hasversion");
        validHeaders.add("dc.identifier.oclc");
        validHeaders.add("dc.relation.replaces");
        validHeaders.add("dc.description.tableofcontents");
        validHeaders.add("dc.relation.requires");
        validHeaders.add("dc.relation.isbasedon");
        validHeaders.add("dc.relation.haspart");
        validHeaders.add("dc.relation.ispartofseries");
        validHeaders.add("dc.description.terms");
        validHeaders.add("dc.description.abstract");
        validHeaders.add("dc.subject");
        validHeaders.add("dc.title");
        validHeaders.add("dc.relation.ispartof");
        validHeaders.add("dc.publisher");
        validHeaders.add("dc.date.issued");
        validHeaders.add("dc.type");
        validHeaders.add("dc.relation.isversionof");
        validHeaders.add("dc.relation.isformatof");
        validHeaders.add("dc.format.extent");
        validHeaders.add("dc.format.medium");
        validHeaders.add("dc.rights");
        validHeaders.add("dc.date.created");
        validHeaders.add("dc.format");
        validHeaders.add("dc.description.uri");
        validHeaders.add("dc.description.statementofresponsibility");
        validHeaders.add("dc.identifier.other");
        validHeaders.add("dc.identifier.ismn");
        validHeaders.add("dc.source");
        validHeaders.add("dc.identifier.sici");
        validHeaders.add("dc.relation");
        validHeaders.add("dc.identifier.bibapp");
        validHeaders.add("dc.creator");
        validHeaders.add("dc.contributor");
        validHeaders.add("dc.identifier.govdoc");
        validHeaders.add("dc.identifier.citation");
        validHeaders.add("dc.date.copyright");
        validHeaders.add("dc.date");
        validHeaders.add("dc.contributor.other");
        validHeaders.add("dc.contributor.illustrator");
        validHeaders.add("dc.contributor.editor");
        validHeaders.add("dc.identifier.doi");
        validHeaders.add("dc.description.sponsorship");
        validHeaders.add("dc.date.available");
        validHeaders.add("dc.language.iso");
        validHeaders.add("dc.identifier");
        validHeaders.add("dc.identifier.uri");
        validHeaders.add("dc.format.mimetype");
        validHeaders.add("dc.identifier.bibliographicCitation");
        validHeaders.add("dc.date.accessioned");
        validHeaders.add("mods.part.extentStart");
        validHeaders.add("mods.part.typeVolume");
        validHeaders.add("mods.part.typeIssue");
        validHeaders.add("mods.part.extentTotal");
        validHeaders.add("mods.part.extentEnd");
        validHeaders.add("thesis.degree.programCode");
        validHeaders.add("thesis.degree.program");
        validHeaders.add("thesis.degree.disciplineCode");
        validHeaders.add("thesis.degree.departmentCode");
        validHeaders.add("thesis.degree.department");
        validHeaders.add("thesis.degree.grantor");
        validHeaders.add("thesis.degree.discipline");
        validHeaders.add("thesis.degree.level");
        validHeaders.add("thesis.degree.name");
        validHeaders.add("owl.sameAs");
        validHeaders.add("dcterms.valid");
        validHeaders.add("dcterms.type");
        validHeaders.add("dcterms.title");
        validHeaders.add("dcterms.temporal");
        validHeaders.add("dcterms.tableOfContents");
        validHeaders.add("dcterms.subject");
        validHeaders.add("dcterms.spatial");
        validHeaders.add("dcterms.source");
        validHeaders.add("dcterms.rightsHolder");
        validHeaders.add("dcterms.rights");
        validHeaders.add("dcterms.requires");
        validHeaders.add("dcterms.replaces");
        validHeaders.add("dcterms.relation");
        validHeaders.add("dcterms.references");
        validHeaders.add("dcterms.publisher");
        validHeaders.add("dcterms.provenance");
        validHeaders.add("dcterms.modified");
        validHeaders.add("dcterms.medium");
        validHeaders.add("dcterms.mediator");
        validHeaders.add("dcterms.license");
        validHeaders.add("dcterms.language");
        validHeaders.add("dcterms.isVersionOf");
        validHeaders.add("dcterms.issued");
        validHeaders.add("dcterms.isRequiredBy");
        validHeaders.add("dcterms.isReplacedBy");
        validHeaders.add("dcterms.isReferencedBy");
        validHeaders.add("dcterms.isPartOf");
        validHeaders.add("dcterms.isFormatOf");
        validHeaders.add("dcterms.instructionalMethod");
        validHeaders.add("dcterms.identifier");
        validHeaders.add("dcterms.hasVersion");
        validHeaders.add("dcterms.hasPart");
        validHeaders.add("dcterms.hasFormat");
        validHeaders.add("dcterms.format");
        validHeaders.add("dcterms.extent");
        validHeaders.add("dcterms.educationLevel");
        validHeaders.add("dcterms.description");
        validHeaders.add("dcterms.dateSubmitted");
        validHeaders.add("dcterms.dateCopyrighted");
        validHeaders.add("dcterms.dateAccepted");
        validHeaders.add("dcterms.date");
        validHeaders.add("dcterms.creator");
        validHeaders.add("dcterms.created");
        validHeaders.add("dcterms.coverage");
        validHeaders.add("dcterms.contributor");
        validHeaders.add("dcterms.conformsTo");
        validHeaders.add("dcterms.bibliographicCitation");
        validHeaders.add("dcterms.available");
        validHeaders.add("dcterms.audience");
        validHeaders.add("dcterms.alternative");
        validHeaders.add("dcterms.accrualPolicy");
        validHeaders.add("dcterms.accrualPeriodicity");
        validHeaders.add("dcterms.accrualMethod");
        validHeaders.add("dcterms.accessRights");
        validHeaders.add("dcterms.abstract");
        validHeaders.add("eperson.language");
        validHeaders.add("eperson.phone");
        validHeaders.add("eperson.lastname");
        validHeaders.add("eperson.firstname");
    }

    private void buildRequiredHeadersList(){
        requiredHeaders = new ArrayList<String>();
        requiredHeaders.add("dc.title");
        requiredHeaders.add("dc.date.issued");
        requiredHeaders.add("dc.type");
        requiredHeaders.add("dc.subject");
    }

    private List<String> getReport(){

        List<String> report = new ArrayList<String>();

        // headers - valid and required present
        report.add("\n#######\n# Headers\n#######");

        if(invalidHeadersFound.size() == 0){
            report.add("[OK] All headers in metadata csv are valid headers.");
        } else {
            report.add("[CRITICAL ERROR] The following " + Integer.toString(invalidHeadersFound.size()) + " invalid header(s) found:");
            for (String invalidHeader : invalidHeadersFound){
                report.add("\t" + invalidHeader);
            }
        }

        if(requiredHeadersNotFound.size() == 0){
            report.add("[OK] All required headers were found.");
        } else {
            report.add("[CRITICAL ERROR] The following " + Integer.toString(requiredHeadersNotFound.size()) +  " required headers were not found in the metadata csv:");
            for (String requiredHeader : requiredHeadersNotFound) {
                report.add("\t" + requiredHeader);
            }
        }

        // files csv <-> source dir match
        report.add("\n#######\n# Files\n#######");
        if (filesNotFoundInSourceDir.size() == 0) {
            report.add("[OK] All filenames found in the metadata csv were found in content source directory.");
        } else {

            report.add("[CRITICAL ERROR] Filenames referred to in the csv that are not found will prevent the batch from being created.");

            report.add("The following " + Integer.toString(filesNotFoundInSourceDir.size()) + " filename(s) were found in the metadata CSV, but were NOT found in the content source directory:");

            for (String fileNotFound : filesNotFoundInSourceDir) {
                report.add("\t" + fileNotFound);
            }
        }

        List<String> filesNotFoundInCsv = getFilesNotFoundInCsv();

        if (filesNotFoundInCsv.size() == 0) {
            report.add("[OK] All filenames found in content source directory " + this.sourceDir.getName() + " were found in metadata csv " + metadataCsvFile.getName() + ".");
        } else {
            report.add("[INFO] Extra filenames in the source directory will not cause an error, but may indicate an oversight.");
            report.add("[INFO] The following " + Integer.toString(filesNotFoundInCsv.size()) +  "  filename(s) were found in the content source directory that were not found in the metadata CSV:");
            for (String fileNotUsed : filesNotFoundInCsv) {
                report.add("\t" + fileNotUsed);
            }
        }

        return report;
    }

}


