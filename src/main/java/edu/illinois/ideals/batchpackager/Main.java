package edu.illinois.ideals.batchpackager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Packager.fxml"));
        Parent root = (Parent)loader.load();

        Controller controller = loader.getController();
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setTitle("IDEALS Batch Packager");
        primaryStage.setScene(scene);

        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}