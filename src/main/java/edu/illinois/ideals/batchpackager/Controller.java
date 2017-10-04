package edu.illinois.ideals.batchpackager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class Controller {
    @FXML
    private TextArea messages;
    @FXML
    private TextArea console;
    @FXML
    private Text csvFilename;
    @FXML
    private Text sourceDirname;
    @FXML
    private Text archiveDirname;
    @FXML
    private Button chooseCsvButton;
    @FXML
    private Button chooseSourceDirectory;
    @FXML
    private Button chooseDestinationDirectory;
    private PrintStream printStream;
    private SAFPackage safPackageInstance;

    @FXML
    protected void handleValidateButtonAction(ActionEvent event) {
        messages.setText("validate button pressed");
    }

    @FXML
    protected void handlePackageButtonAction(ActionEvent event) {

        boolean requiredSelectionsMade = true;

        if (csvFilename.getText() == null) {
            messages.appendText("\nERROR: Metadata CSV not selected");
            requiredSelectionsMade = false;
        }
        if (sourceDirname.getText() == null) {
            messages.appendText("\nERROR: Content source directory not selected.");
            requiredSelectionsMade = false;
        }
        if (archiveDirname.getText() == null) {
            messages.appendText("\nERROR: Archive destination directory not selected.");
            requiredSelectionsMade = false;
        }

        if (requiredSelectionsMade) {

            messages.setText("Creating archive...");

            try {
                safPackageInstance.processMetaPack(csvFilename.getText(), sourceDirname.getText(), archiveDirname.getText(), false);
                messages.setText("Archive created in " + archiveDirname.getText());

            } catch (IOException e) {
                messages.setText("Error encounterd when attempting to create archive. Details below.");
            }
        }
        return;
    }

    @FXML
    protected void locateMetadataCsvFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
        chooser.getExtensionFilters().add(extensionFilter);
        chooser.setTitle("Choose metadata CSV file");
        File file = chooser.showOpenDialog(chooseCsvButton.getScene().getWindow());
        csvFilename.setText(file.getPath());
    }

    @FXML
    protected void locateSourceDirectory(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose content files source directory");
        File sourceDirectory = chooser.showDialog(chooseSourceDirectory.getScene().getWindow());
        sourceDirname.setText(sourceDirectory.getPath());

    }

    @FXML
    protected void locateDestinationDirectory(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose simple archive format destination directory");
        File destinationDirectory = chooser.showDialog(chooseDestinationDirectory.getScene().getWindow());
        archiveDirname.setText(destinationDirectory.getPath());
    }

//    public void initialize() {
//        this.printStream = new PrintStream(new Console(console));
//        System.setOut(printStream);
//        System.setErr(printStream);
//        safPackageInstance = new SAFPackage();
//    }

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