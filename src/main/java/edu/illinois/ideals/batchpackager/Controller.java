package edu.illinois.ideals.batchpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

public class Controller {
    @FXML
    private TextArea messages;
    @FXML
    private TextArea console;
    @FXML
    private Label csvFilename;
    @FXML
    private Label sourceDirname;
    @FXML
    private Label archiveDirname;
    @FXML
    private Button chooseCsvButton;
    @FXML
    private Button chooseSourceDirectory;
    @FXML
    private Button chooseDestinationDirectory;

    private PrintStream printStream;
    private SAFPackage safPackageInstance;

    @FXML
    protected void handleVerifyButtonAction(ActionEvent event) {

        console.setText("");
        messages.setText("");

        boolean requiredSelectionsMade = true;

        if (csvFilename.getText() == null || csvFilename.getText().isEmpty()) {
            messages.appendText("\nERROR: Metadata CSV not selected");
            requiredSelectionsMade = false;
        }
        if (sourceDirname.getText() == null || sourceDirname.getText().isEmpty()) {
            messages.appendText("\nERROR: Content source directory not selected.");
            requiredSelectionsMade = false;
        }

        if (requiredSelectionsMade) {

            messages.setText("Creating archive...");

            List<String> report = safPackageInstance.verifyMetaPack(csvFilename.getText(), sourceDirname.getText());

            messages.setText("Verification Report Below:");

            for(String reportLine : report){
                console.appendText("\n" + reportLine);
            }

        }
        return;

    }

    @FXML
    protected void handlePackageButtonAction(ActionEvent event) {

        console.setText("");
        messages.setText("");

        boolean requiredSelectionsMade = true;

        if (csvFilename.getText() == null || csvFilename.getText().isEmpty()) {
            messages.appendText("\nERROR: Metadata CSV not selected");
            requiredSelectionsMade = false;
        }
        if (sourceDirname.getText() == null || sourceDirname.getText().isEmpty()) {
            messages.appendText("\nERROR: Content source directory not selected.");
            requiredSelectionsMade = false;
        }
        if (archiveDirname.getText() == null || archiveDirname.getText().isEmpty()) {
            messages.appendText("\nERROR: Archive destination directory not selected.");
            requiredSelectionsMade = false;
        }

        if (requiredSelectionsMade) {

            messages.setText("Creating archive...");

            List<String> report = safPackageInstance.processMetaPack(csvFilename.getText(), sourceDirname.getText(), archiveDirname.getText(), false);

            if(report.get(0).toLowerCase().contains("error")){
                messages.setText("Critial Error: PACKAGE NOT CREATED. See below for details.");
            } else {
                messages.setText("Archive created in " + archiveDirname.getText() + ". See below for details.");
            }

            for(String reportLine : report){

                console.appendText("\n" + reportLine);
            }

        }
        return;
    }

    @FXML
    protected void locateMetadataCsvFile(ActionEvent event) {

        console.clear();
        messages.clear();

        FileChooser chooser = new FileChooser();
        FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
        chooser.getExtensionFilters().add(extensionFilter);
        chooser.setTitle("Choose metadata CSV file");
        File file = chooser.showOpenDialog(chooseCsvButton.getScene().getWindow());
        if(file != null) {
            csvFilename.setText(file.getPath());
        }
    }

    @FXML
    protected void locateSourceDirectory(ActionEvent event) {
        console.clear();
        messages.clear();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose content files source directory");
        File sourceDirectory = chooser.showDialog(chooseSourceDirectory.getScene().getWindow());
        if(sourceDirectory != null) {
            sourceDirname.setText(sourceDirectory.getPath());
        }

    }

    @FXML
    protected void locateDestinationDirectory(ActionEvent event) {

        console.clear();
        messages.clear();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose simple archive format destination directory");
        File destinationDirectory = chooser.showDialog(chooseDestinationDirectory.getScene().getWindow());
        if(destinationDirectory != null){
            archiveDirname.setText(destinationDirectory.getPath());
        }
    }

    public void initialize() {

        console.clear();
        messages.clear();

        this.printStream = new PrintStream(new Console(console));
        //System.setOut(printStream);
        //System.setErr(printStream);
        safPackageInstance = new SAFPackage();
    }

    public class Console extends OutputStream {
        private TextArea console;

        public Console(TextArea console) {
            this.console = console;
        }

        public void appendText(String valueOf) {
            Platform.runLater(() -> console.appendText(valueOf));
        }

        public void write(int b) throws IOException {
            appendText(String.valueOf((char) b));
        }
    }

}