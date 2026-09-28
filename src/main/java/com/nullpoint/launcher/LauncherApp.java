package com.nullpoint.launcher;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

public final class LauncherApp extends Application {
    private static final List<GameVersion> VERSIONS = List.of(
        new GameVersion("1.16.5", JavaRuntime.JAVA_8),
        new GameVersion("1.21.4", JavaRuntime.JAVA_21),
        new GameVersion("1.21.11", JavaRuntime.JAVA_21),
        new GameVersion("26.2", JavaRuntime.JAVA_25)
    );

    @Override public void start(Stage stage) {
        var versionBox = new ComboBox<GameVersion>();
        versionBox.getItems().addAll(VERSIONS);
        versionBox.getSelectionModel().selectFirst();
        versionBox.setMaxWidth(Double.MAX_VALUE);

        var ram = new Slider(2048, Math.max(8192, Runtime.getRuntime().maxMemory() / 1024 / 1024),
                Math.min(6144, Math.max(2048, Runtime.getRuntime().maxMemory() / 1024 / 1024 / 2)));
        ram.setShowTickLabels(true);
        ram.setShowTickMarks(true);
        var ramLabel = new Label();
        ram.valueProperty().addListener((o,a,b) -> ramLabel.setText("RAM: " + (int)Math.round(b.doubleValue()) + " MB"));
        ramLabel.setText("RAM: " + (int)ram.getValue() + " MB");

        var play = new Button("PLAY");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setPrefHeight(56);
        play.setOnAction(e -> {
            try {
                LauncherService.launch(versionBox.getValue(), (int)ram.getValue(), Path.of("instances"));
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });

        var root = new BorderPane();
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color:#0b0d12;");

        var title = new Label("CHERKASH");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("System", 30));
        var subtitle = new Label("OPTIMIZED MINECRAFT LAUNCHER");
        subtitle.setTextFill(Color.web("#8c93a5"));

        var header = new VBox(4, title, subtitle);
        var card = new VBox(16);
        card.setPadding(new Insets(26));
        card.setMaxWidth(620);
        card.setStyle("-fx-background-color:#121620;-fx-background-radius:18;");
        var selected = new Label("GAME VERSION");
        selected.setTextFill(Color.web("#8c93a5"));
        card.getChildren().addAll(selected, versionBox, ramLabel, ram, play);

        var center = new StackPane(card);
        root.setTop(header);
        root.setCenter(center);

        var scene = new Scene(root, 980, 620);
        stage.setTitle("Cherkash Launcher");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
