module space.dev.audioplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.prefs;


    opens space.dev.audioplayer to javafx.fxml;
    exports space.dev.audioplayer;
}