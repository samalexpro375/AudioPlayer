module space.dev.audioplayer {
    requires javafx.controls;
    requires javafx.fxml;


    opens space.dev.audioplayer to javafx.fxml;
    exports space.dev.audioplayer;
}