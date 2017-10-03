package edu.illinois.ideals.batchpackager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.File;

public class Controller {
    @FXML private Label actiontarget;
    @FXML private Text csvFilename;
    @FXML private Text sourceDirname;
    @FXML private Text destinationDirname;
    @FXML private Button chooseCsvButton;
    @FXML private Button chooseSourceDirectory;
    @FXML private Button chooseDestinationDirectory;

    @FXML protected void handleValidateButtonAction(ActionEvent event) {
        actiontarget.setText("validate button pressed");
    }

    @FXML protected void handlePackageButtonAction(ActionEvent event) {
        actiontarget.setText("package button pressed");
    }

    @FXML protected void locateMetadataCsvFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv");
        chooser.getExtensionFilters().add(extensionFilter);
        chooser.setTitle("Choose metadata CSV file");
        File file = chooser.showOpenDialog(chooseCsvButton.getScene().getWindow());
        csvFilename.setText(file.getPath());
    }

    @FXML protected void locateSourceDirectory(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose content files source directory");
        File sourceDirectory = chooser.showDialog(chooseSourceDirectory.getScene().getWindow());
        sourceDirname.setText(sourceDirectory.getPath());

    }
    @FXML protected void locateDestinationDirectory(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose simple archive format destination directory");
        File destinationDirectory = chooser.showDialog(chooseDestinationDirectory.getScene().getWindow());
        destinationDirname.setText(destinationDirectory.getPath());

    }

}